"""Honest backtest over the test window (for the record, not for the live app).

Saves the per-row calibrated probabilities vs the true class to
``reports/backtest_ml_v1.parquet`` and a human-readable
``reports/backtest_ml_v1_summary.md`` with overall + per-symbol macro F1, top-1
accuracy, and the hit-rate when the model is confident UP (prob_up > 0.5).
"""

from __future__ import annotations

import logging

import numpy as np
import pandas as pd

from ..config import CODE_TO_CLASS, MODEL_VERSION, REPORTS_DIR
from .encode import make_X, make_y
from .metrics import aligned_proba, decide, macro_f1, per_symbol_macro_f1
from .splits import TARGET_COL

log = logging.getLogger(__name__)


def _up_hit_rate(df: pd.DataFrame) -> float:
    """Among rows where prob_up > 0.5, fraction whose actual class is UP."""
    confident = df[df["prob_up"] > 0.5]
    if confident.empty:
        return float("nan")
    return float((confident["y_true"] == "UP").mean())


def run_backtest(
    test: pd.DataFrame,
    calibrated,
    feature_cols: list[str],
    version: str = MODEL_VERSION,
    *,
    weights=None,
) -> dict:
    """Score the calibrated model on the test split; write parquet + summary.

    Uses the same balanced decision rule as training/predict (val-tuned weighted
    argmax) so the backtest reflects what is actually written to the DB.
    """
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    X = make_X(test, feature_cols)
    y = make_y(test)
    proba = aligned_proba(calibrated, X)
    pred = decide(proba, weights)

    rows = pd.DataFrame({
        "ts_utc": test["ts_utc"].to_numpy(),
        "symbol": test["symbol"].to_numpy(),
        "y_true": test[TARGET_COL].to_numpy(),
        "pred_class": [CODE_TO_CLASS[c] for c in pred],
        "prob_down": proba[:, 0],
        "prob_flat": proba[:, 1],
        "prob_up": proba[:, 2],
    })
    parquet_path = REPORTS_DIR / f"backtest_{version}.parquet"
    rows.to_parquet(parquet_path, index=False)

    overall_f1 = macro_f1(y, pred)
    top1 = float((pred == y).mean())
    up_hit = _up_hit_rate(rows)
    per_symbol = per_symbol_macro_f1(test["symbol"], y, pred)
    per_symbol_top1 = {
        sym: float((g["pred_class"] == g["y_true"]).mean())
        for sym, g in rows.groupby("symbol", sort=True)
    }
    per_symbol_uphit = {sym: _up_hit_rate(g) for sym, g in rows.groupby("symbol", sort=True)}

    summary_path = REPORTS_DIR / f"backtest_{version}_summary.md"
    summary_path.write_text(_render_summary(
        version, len(rows), overall_f1, top1, up_hit,
        per_symbol, per_symbol_top1, per_symbol_uphit,
    ))
    log.info(
        "backtest %s: macro F1 %.3f | top-1 %.3f | UP hit-rate %.3f -> %s",
        version, overall_f1, top1, up_hit, parquet_path.name,
    )
    return {
        "overall_macro_f1": overall_f1,
        "top1_accuracy": top1,
        "up_hit_rate": up_hit,
        "per_symbol_macro_f1": per_symbol,
        "n_rows": len(rows),
        "parquet": str(parquet_path),
        "summary": str(summary_path),
    }


def _render_summary(version, n, f1, top1, up_hit, ps_f1, ps_top1, ps_uphit) -> str:
    def fmt(x) -> str:
        return "—" if x is None or (isinstance(x, float) and np.isnan(x)) else f"{x:.3f}"

    lines = [
        f"# Backtest — ML direction model `{version}`",
        "",
        "Calibrated 3-class (UP/DOWN/FLAT) forecast over the **test window** "
        "(out-of-sample, chronological). This is the honest record, not a live trading claim.",
        "",
        f"- Rows scored: **{n}**",
        f"- Overall macro F1: **{fmt(f1)}**",
        f"- Top-1 accuracy: **{fmt(top1)}**",
        f"- Hit-rate when P(UP) > 0.5: **{fmt(up_hit)}**",
        "",
        "## Per-symbol",
        "",
        "| symbol | macro F1 | top-1 acc | UP hit-rate |",
        "|---|---|---|---|",
    ]
    for sym in sorted(ps_f1):
        lines.append(f"| {sym} | {fmt(ps_f1[sym])} | {fmt(ps_top1.get(sym))} | {fmt(ps_uphit.get(sym))} |")
    if ps_f1:
        best = max(ps_f1, key=ps_f1.get)
        worst = min(ps_f1, key=ps_f1.get)
        lines += ["", f"Best: **{best}** ({fmt(ps_f1[best])}) · Worst: **{worst}** ({fmt(ps_f1[worst])})."]
    lines.append("")
    return "\n".join(lines)
