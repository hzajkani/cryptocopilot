# Model card — ML direction classifier `v1`

> One of three cards documenting CryptoCopilot's intelligence layer:
> **MODEL_CARD** (this file) · [RAG_CARD](RAG_CARD.md) · [ANALYST_CARD](ANALYST_CARD.md).
> Decision-support only — **not financial advice**, paper trading only (PROJECT.md §9).

## Task

Per-coin **24-hour price direction** over the 10-asset universe (BTC, ETH, SOL, BNB, XRP, ADA,
AVAX, DOT, LINK, MATIC), as a **3-class** problem on the **4h** timeframe:

| class | rule (forward 24h return) |
|---|---|
| **UP** | `> +2%` |
| **DOWN** | `< −2%` |
| **FLAT** | otherwise |

Output per coin is a **calibrated probability** for each class plus the **top-3 SHAP drivers**,
written to Postgres (`predictions`, `prediction_drivers`) by the Python `ml.predict` batch job.
The Java backend reads those numbers — it never loads the model (the polyglot boundary, PROJECT.md §3).

## Model

A single **global** XGBoost classifier (`multi:softprob`, symbol one-hot so one model serves all
10 coins), early-stopped on validation, hyper-parameter-tuned with **Optuna** (val macro-F1
objective), then **isotonic-calibrated on the validation split**. A logistic-regression baseline is
trained as the bar to beat.

Tuned bundle (`ml/models/v1/bundle.joblib`; full set in `meta.json`):
`max_depth=4, learning_rate≈0.0148, subsample≈0.95, colsample_bytree≈0.77, min_child_weight=3,
gamma≈1.60, reg_lambda≈3.12, reg_alpha≈4.76`, **best_iteration=301**.

## Features (46)

All **backward-only** (no look-ahead), engineered in Python and cached as a parquet matrix — the
features never cross into the DB (PROJECT.md §3); the Java backend recomputes its own TA with ta4j.

- **Multi-horizon returns** — 1h / 4h / 24h / 7d
- **Momentum / oscillators** — RSI(7/14/21), MACD(12,26,9) + crossover (close-normalised),
  Stochastic %K/%D, ADX(14)
- **Volatility / bands** — Bollinger %B + bandwidth, ATR%, realised vol (24h/7d)
- **Volume** — volume z-score
- **Trend** — SMA ratios (7/30/90)
- **Ichimoku** (from scratch, leakage-safe displacement) — above/below/in-cloud flags, TK
  cross flags, cloud thickness, Chikou-clear flag, continuous distances to Tenkan/Kijun, TK diff
- **Calendar** — hour-of-day, day-of-week, is-weekend
- **Symbol** — one-hot (10 coins; **excluded from the SHAP drivers** so drivers are market-state,
  not "this coin is BTC")

## Data & splits

Source: `ohlcv` (Binance, ~2 years). Splits are **strictly chronological** (no shuffle) with a
**24h embargo** between them; the test set is never touched during model selection.

| split | window | rows | DOWN / FLAT / UP |
|---|---|---|---|
| train | 2024-06-01 → 2025-05-31 | 20,891 | 0.27 / 0.47 / 0.27 |
| val | 2025-06-01 → 2025-08-31 | 5,460 | 0.25 / 0.47 / 0.28 |
| test | 2025-09-01 → present | 16,360 | 0.26 / 0.54 / 0.20 |

> **Split deviation (documented):** the original spec assumed ~3 years from 2023-01. Binance
> returned **~2 years (from 2024-05-31)**, so dates are anchored to the real span as
> train 12mo / val 3mo / test 9mo→present (a robust, multi-regime test). Overridable via
> `ML_TRAIN_START` / `ML_VAL_*` / `ML_TEST_START`.

## Calibration

The base XGBoost is fit on **train only**; **isotonic** calibration is fit on **validation**
(`FrozenEstimator` / `cv='prefit'`). It is deliberately **not** refit on train+val before
calibrating — isotonic-on-val is only valid if the estimator has not seen val. The stored
probabilities are the calibrated ones.

## Decision rule

Probabilities stay calibrated, but the **class label** is a **validation-tuned weighted argmax**:
`pred = argmax_k (w_k · p_k)`, with `w = [DOWN 1.5, FLAT 1.0, UP 1.5]` grid-tuned on validation.
The test window is heavily FLAT-skewed, so plain argmax collapses to FLAT and tanks macro-F1;
up-weighting the minority directional classes restores balanced UP/DOWN recall **without** altering
the stored probabilities, AUC or Brier (a standard, val-only decision-threshold tune). The Analyst
trusts the stored `pred_class` — it never re-argmaxes the probabilities.

## Metrics (out-of-sample, test split)

| metric | value | gate (PROJECT.md §9) | |
|---|---|---|---|
| macro **F1** | **0.375** | ≥ 0.40 | ✗ — data-limited, accepted (below) |
| macro **ROC-AUC** | **0.578** | 0.55–0.62 | ✓ (above 0.65 would suggest leakage) |
| multiclass **Brier** | **0.606** | ≤ 0.65 | ✓ |
| baseline LogReg macro F1 | 0.290 | — | XGBoost beats the baseline |

- **Per-symbol macro F1** — best **MATIC 0.379**, worst **LINK 0.310** (full table in
  `reports/backtest_v1_summary.md`).
- **Backtest** (16,360 test rows): top-1 accuracy **0.430**; hit-rate when P(UP) > 0.5 = **0.349**.
- SHAP beeswarm: `reports/shap_summary.png`.

## Why macro F1 is 0.375 (honest, investigated)

The weak class is **UP** (test F1 ≈ 0.17): predicting > +2% / 24h rallies from OHLCV-only TA is near
the noise floor in the calm 2025–26 regime. This was checked against all three levers the spec names:

- **Not leakage** — AUC 0.578 sits squarely in the expected 0.55–0.62 band; a truncation-invariance
  leakage test passes; the **hindsight-optimal decision weights also cap at 0.375**, so the decision
  rule is not the bottleneck.
- **Target window** — tightening the FLAT band to ±1% made it *worse* (a 1% 24h move is mostly
  noise → lower AUC). Kept the spec's ±2%.
- **Class balance** — handled at decision time (val-tuned weights lifted macro-F1 ≈ 0.28 → 0.37),
  not by redefining the target.
- **Root cause is data volume** — ~2 years of OHLCV vs the assumed ~3. **More history is the lever
  most likely to clear 0.40.**

Accepted by the project owner as the honest, data-limited result. The deliverable is a
production-grade, calibrated, explainable pipeline — **not** beating the market.

## Intended use & limitations

- **Intended use:** decision-support — one of four inputs the [Analyst](ANALYST_CARD.md) fuses.
  Educational / portfolio demonstration of calibrated, explainable, polyglot ML.
- **Out of scope / limitations:** not investment advice; no real-money trading; crypto only; a single
  *latest* forecast per coin (no historical prediction series — the batch job stores only the current
  view, PROJECT.md §2); a calm-regime 2-year window limits the directional (UP/DOWN) signal; SHAP
  drivers explain the model, not causal market mechanics.

## Reproduce

```bash
docker compose up -d db
docker compose run --rm ml python -m ml.train      # features → XGBoost → Optuna → isotonic → SHAP
docker compose run --rm ml python -m ml.predict     # 10 predictions + 30 driver rows → Postgres
```

_Numbers above are from the saved bundle `ml/models/v1/meta.json` (trained 2026-06-01). The auto-generated
sibling card lives at `ml/models/v1/MODEL_CARD.md`; this file is its interview-ready refinement._
