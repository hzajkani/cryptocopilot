"""End-to-end training: features → target → splits → baseline → tune → fit →
calibrate → evaluate → SHAP → save bundle + MODEL_CARD + backtest.

Manual / occasional (PROJECT.md §2 — training is not scheduled):

    docker compose run --rm ml python -m ml.train

Writes the calibrated bundle to ``models/v1/`` and reports to ``reports/``. Prints a
Definition-of-Done verdict (macro F1 ≥ 0.40, AUC ≥ 0.55, Brier ≤ 0.65).
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone

import numpy as np

from .config import (
    CLASSES,
    DEFAULT_TIMEFRAME,
    MODEL_VERSION,
    RANDOM_STATE,
    TEST_START,
    TRAIN_END,
    TRAIN_START,
    VAL_END,
    VAL_START,
)
from .explain import save_shap_summary
from .features.build import build_all_assets
from .modelling.artifacts import save_bundle, version_dir
from .modelling.backtest import run_backtest
from .modelling.baseline import train_baseline
from .modelling.calibrate import calibrate
from .modelling.encode import make_X, make_y
from .modelling.metrics import (
    aligned_proba,
    class_priors,
    decide,
    per_symbol_macro_f1,
    select_weights,
    summarize,
)
from .modelling.splits import add_target, time_splits
from .modelling.tune import search
from .modelling.xgb_model import build_xgb, fit_xgb

log = logging.getLogger(__name__)

# Definition of done (Stage 2).
MIN_MACRO_F1 = 0.40
MIN_MACRO_AUC = 0.55
MAX_BRIER = 0.65
SHAP_SAMPLE = 1500


def train(timeframe: str = DEFAULT_TIMEFRAME) -> dict:
    np.random.seed(RANDOM_STATE)

    # 1) features + target + splits -----------------------------------------
    feats = build_all_assets(timeframe, use_cache=False, save=True)
    feats = add_target(feats, timeframe)
    train_df, val_df, test_df = time_splits(feats)
    for name, part in (("train", train_df), ("val", val_df), ("test", test_df)):
        if part.empty:
            raise RuntimeError(f"{name} split is empty — check ingested data span vs config split dates")

    # 2) design matrices (one-hot symbol; column order frozen from train) ----
    X_train = make_X(train_df)
    feature_cols = list(X_train.columns)
    X_val, X_test = make_X(val_df, feature_cols), make_X(test_df, feature_cols)
    y_train, y_val, y_test = make_y(train_df), make_y(val_df), make_y(test_df)

    # 3) baseline (the bar to beat) -----------------------------------------
    baseline = train_baseline(train_df, val_df, test_df)

    # 4) tune (val macro F1) then fit final XGBoost on train ------------------
    best_params = search(X_train, y_train, X_val, y_val)
    xgb = fit_xgb(build_xgb(best_params), X_train, y_train, X_val, y_val)

    # 5) calibrate on val; tune the balanced decision weights on val ---------
    calibrated = calibrate(xgb, X_val, y_val)
    priors = class_priors(y_train)
    proba_val = aligned_proba(calibrated, X_val)
    weights = select_weights(proba_val, y_val)
    log.info("decision rule: weighted argmax, weights=%s (val priors %s)",
             dict(zip(CLASSES, [round(w, 2) for w in weights])),
             dict(zip(CLASSES, class_priors(y_val).round(3))))

    # 6) evaluate on the untouched test split --------------------------------
    proba_test = aligned_proba(calibrated, X_test)
    pred_test = decide(proba_test, weights)
    metrics = summarize(y_test, pred_test, proba_test)
    per_symbol = per_symbol_macro_f1(test_df["symbol"], y_test, pred_test)
    log.info("TEST — macro F1 %.3f | macro AUC %.3f | Brier %.3f",
             metrics["macro_f1"], metrics["macro_auc"], metrics["brier"])

    beats_baseline = metrics["macro_f1"] >= baseline["test_macro_f1"]
    log.info("baseline test macro F1 %.3f -> XGBoost %s baseline",
             baseline["test_macro_f1"], "beats" if beats_baseline else "DOES NOT beat")

    # 7) SHAP global summary -------------------------------------------------
    n = min(SHAP_SAMPLE, len(X_test))
    idx = np.random.choice(len(X_test), size=n, replace=False)
    save_shap_summary(xgb, X_test.iloc[idx])

    # 8) assemble + persist bundle ------------------------------------------
    splits_meta = {
        "train": {"start": TRAIN_START, "end": TRAIN_END, "rows": int(len(train_df))},
        "val": {"start": VAL_START, "end": VAL_END, "rows": int(len(val_df))},
        "test": {"start": TEST_START, "rows": int(len(test_df))},
        "note": "Split dates anchored to the real 2024-05-31→2026-05-31 data span "
                "(deviation from the prompt's 2023 dates; see STATE.md).",
    }
    bundle = {
        "calibrated": calibrated,
        "xgb": xgb,
        "feature_cols": feature_cols,
        "classes": CLASSES,
        "model_version": MODEL_VERSION,
        "timeframe": timeframe,
        # Balanced decision rule (val-tuned weighted argmax); probabilities stay calibrated.
        "priors": priors.tolist(),
        "decision_weights": weights,
        "metrics": {
            "test": metrics,
            "per_symbol_macro_f1": per_symbol,
            "baseline": {k: baseline[k] for k in baseline if k.endswith(("_f1", "_auc"))},
            "best_params": best_params,
            "best_iteration": int(getattr(xgb, "best_iteration", -1) or -1),
            "decision_weights": weights,
        },
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "splits": splits_meta,
    }
    save_bundle(bundle)
    _write_model_card(bundle, baseline, beats_baseline)

    # 9) backtest over test --------------------------------------------------
    backtest = run_backtest(test_df, calibrated, feature_cols, weights=weights)
    bundle["metrics"]["backtest"] = {
        k: backtest[k] for k in ("overall_macro_f1", "top1_accuracy", "up_hit_rate", "n_rows")
    }

    _log_mlflow_if_available(bundle)
    _verdict(metrics)
    return bundle


def _verdict(m: dict) -> None:
    checks = [
        ("macro F1 ≥ 0.40", m["macro_f1"] >= MIN_MACRO_F1, m["macro_f1"]),
        ("macro AUC ≥ 0.55", (not np.isnan(m["macro_auc"])) and m["macro_auc"] >= MIN_MACRO_AUC, m["macro_auc"]),
        ("Brier ≤ 0.65", m["brier"] <= MAX_BRIER, m["brier"]),
    ]
    log.info("=== Definition of done ===")
    for label, ok, val in checks:
        log.info("  [%s] %s (%.3f)", "PASS" if ok else "FAIL", label, val)
    if not all(ok for _, ok, _ in checks):
        log.error("DoD NOT met — investigate leakage / target window / class balance before proceeding.")
    else:
        log.info("All DoD thresholds met.")


def _write_model_card(bundle: dict, baseline: dict, beats_baseline: bool) -> None:
    m = bundle["metrics"]["test"]
    ps = bundle["metrics"]["per_symbol_macro_f1"]
    best = max(ps, key=ps.get) if ps else "—"
    worst = min(ps, key=ps.get) if ps else "—"
    s = bundle["splits"]
    card = f"""# Model card — ML direction classifier `{bundle['model_version']}`

**Task.** Per-coin 24h price-direction over 10 crypto assets, 3 classes
(UP > +2%, DOWN < −2%, FLAT otherwise) on the **{bundle['timeframe']}** timeframe.
Output is a calibrated probability per class + the top-3 SHAP drivers, written to
Postgres (`predictions`, `prediction_drivers`). Decision-support only — **not
financial advice**, paper trading only.

**Model.** One global `xgboost` `multi:softprob` (symbol one-hot), early-stopped on
validation, tuned with Optuna (40 trials, val macro F1), then **isotonic-calibrated
on validation**. A LogReg baseline is trained as the bar to beat.

**Data / splits** (chronological, no shuffle, 24h embargo between splits):
- train {s['train']['start']}→{s['train']['end']} ({s['train']['rows']} rows)
- val   {s['val']['start']}→{s['val']['end']} ({s['val']['rows']} rows)
- test  {s['test']['start']}→present ({s['test']['rows']} rows)
- {s['note']}

**Calibration honesty.** The base XGBoost is fit on train only; isotonic
calibration is fit on validation (`cv='prefit'`/`FrozenEstimator`). We do **not**
refit on train+val before calibrating, because isotonic-on-val is only valid if
the estimator has not seen val. Test is never touched during model selection.

**Decision rule.** Probabilities are the calibrated ones, but the *class label* is
a **weighted argmax**: `pred = argmax_k w_k · p_k`, with per-class weights
w = {bundle['decision_weights']} (DOWN, FLAT, UP) grid-tuned on validation. The
test window is heavily FLAT-skewed, so plain argmax of calibrated probabilities
collapses to FLAT and tanks macro F1; up-weighting the minority directional
classes restores balanced UP/DOWN recall without altering the stored
probabilities or AUC/Brier (a standard decision-threshold tune, val-only).

**Test metrics (out-of-sample).**
- macro F1: **{m['macro_f1']:.3f}** (baseline {baseline['test_macro_f1']:.3f} → XGBoost {'beats' if beats_baseline else 'does not beat'})
- macro ROC-AUC: **{m['macro_auc']:.3f}**
- multiclass Brier: **{m['brier']:.3f}**
- per-symbol macro F1 — best **{best}** ({ps.get(best, float('nan')):.3f}), worst **{worst}** ({ps.get(worst, float('nan')):.3f})

**Honest scope.** Expected ROC-AUC is 0.55–0.62; above ~0.65 would suggest
leakage. The goal is production-grade, calibrated, explainable ML — not beating
the market. SHAP beeswarm in `reports/shap_summary.png`; backtest in
`reports/backtest_{bundle['model_version']}_summary.md`.

**Features ({len(bundle['feature_cols'])} total).** Multi-horizon returns (1h/4h/24h/7d), RSI
(7/14/21), MACD(12,26,9)+crossover (close-normalised), Stochastic %K/%D, ADX(14),
Bollinger %B + bandwidth, ATR%, realised vol (24h/7d), volume z-score, SMA ratios
(7/30/90), Ichimoku flags + continuous distances (from scratch), calendar
(hour/day/weekend), and symbol one-hot. All backward-only (no look-ahead).

_Generated by `ml.train` at {bundle['trained_at']}._
"""
    path = version_dir() / "MODEL_CARD.md"
    path.write_text(card)
    log.info("wrote model card -> %s", path)


def _log_mlflow_if_available(bundle: dict) -> None:
    """Optional experiment tracking — only if mlflow is installed (extra)."""
    try:
        import mlflow  # type: ignore
    except ImportError:
        return
    try:
        mlflow.set_experiment("cryptocopilot-direction")
        with mlflow.start_run(run_name=bundle["model_version"]):
            mlflow.log_params(bundle["metrics"]["best_params"])
            mlflow.log_metrics({f"test_{k}": v for k, v in bundle["metrics"]["test"].items()
                                if isinstance(v, (int, float)) and not np.isnan(v)})
        log.info("logged run to mlflow")
    except Exception:
        log.warning("mlflow logging skipped (non-fatal)", exc_info=True)


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    train()


if __name__ == "__main__":
    main()
