# Stage 2 — The ML service: features + XGBoost + calibration + SHAP (Python)

> **Phase A of 3 (data + ML core).** This is Stage 2 of 7. Completes the Python side.
>
> **How to use this file:** New Claude Code session in the project root. First message: *"Read PROJECT.md and STATE.md before anything else."* Then paste everything below the line.

---

# CryptoCopilot — Stage 2: ML direction classifier as a batch worker

Read `PROJECT.md` and `STATE.md`. Stage 1 is done: all five sources are ingested into Postgres, the schema contract is live.

This stage builds the **ML brain** inside the `ml` container. It reads `ohlcv` from Postgres, engineers features, trains a 3-class (UP/DOWN/FLAT) XGBoost classifier with calibrated probabilities, computes SHAP top-3 drivers, and **writes results to the `predictions` and `prediction_drivers` tables**. It runs as a **batch worker** — training is manual, prediction is scheduled.

Critical: this container writes ONLY to `predictions` and `prediction_drivers` (its owned tables per PROJECT.md §3). Feature engineering stays internal to Python (parquet on a volume) — it does NOT go into the DB. The Java backend will read `predictions`/`prediction_drivers`; it never sees the model or the features.

## Goals

1. Feature engineering from `ohlcv` (classical indicators + Ichimoku from scratch).
2. Leakage-safe target + time-based splits.
3. XGBoost 3-class with isotonic calibration; LogReg baseline.
4. SHAP top-3 driver function.
5. A `predict` job that writes the latest forecast + drivers per coin to Postgres.
6. A batch worker (APScheduler) that runs `predict` periodically.

## Tasks

### 1. Add ML deps

Extend `ml/pyproject.toml`: `ta, xgboost, scikit-learn, optuna, shap, pyarrow, mlflow` (mlflow optional). Rebuild the `ml` image.

### 2. Feature engineering — `ml/ml/features/`

Read OHLCV from Postgres (`db.query_ohlcv(symbol, timeframe, start, end)`), default timeframe `4h`.

**`indicators.py`** (wrap the `ta` library), per `(symbol, ts)`:
returns 1h/4h/24h/7d; RSI(14); MACD(12,26,9) line + signal + histogram; Bollinger(20,2) → `bb_pct = (close-lower)/(upper-lower)`; ATR(14); volume z-score (rolling 24h); SMAs 7/30/90 + ratios.

**`ichimoku.py`** — implement from scratch (own the math):
Tenkan(9), Kijun(26), Senkou A `(Tenkan+Kijun)/2` (+26), Senkou B `(HH52+LL52)/2` (+26), Chikou `close` (−26). Emit boolean/categorical flags as features: `ichimoku_above_cloud`, `ichimoku_below_cloud`, `ichimoku_in_cloud`, `ichimoku_tk_cross_bull`, `ichimoku_tk_cross_bear`, `ichimoku_cloud_thickness = (SenkouA-SenkouB)/close`, `ichimoku_chikou_clear`.

**`calendar.py`** — hour-of-day, day-of-week, is_weekend.

**`build.py`** — `build_all_assets(timeframe="4h") -> DataFrame` (long format, `symbol` as a feature), cache to `data/processed/features_{timeframe}.parquet` on a mounted volume.

### 3. Target + splits — READ CAREFULLY, no leakage

```
r_24h = close[t + 24h] / close[t] - 1
class = UP   if r_24h >  +0.02
class = DOWN if r_24h <  -0.02
class = FLAT otherwise   →  column y_24h_3class
```
Features at time `T` use only data with `ts <= T`; target uses `(T, T+24h]`.

`ml/ml/modelling/splits.py` — time-based, no shuffle:
Train `2023-01-01 → 2024-06-30`, Val `2024-07-01 → 2024-09-30`, Test `2024-10-01 → present`. 6-fold expanding-window CV on train for tuning.

### 4. Models — `ml/ml/modelling/`

**Baseline** `sklearn.LogisticRegression(multi_class="multinomial", class_weight="balanced")` in a `StandardScaler` pipeline, small feature subset (`ret_1h, ret_24h, RSI, MACD_signal, bb_pct, ichimoku_above_cloud, ichimoku_tk_cross_bull`).

**Primary** `xgboost.XGBClassifier(objective="multi:softprob", num_class=3, n_estimators=500, max_depth=5, learning_rate=0.05, early_stopping_rounds=40)` against val, sample weights for class imbalance. One global model, `symbol` one-hot/target-encoded.

### 5. Tuning + calibration

Optuna, **40 trials max**, optimise val macro F1. Retrain on train+val with best params, evaluate on test. Calibrate with `CalibratedClassifierCV(method="isotonic", cv="prefit")` on val. Save the calibrated model to `models/v1/` (mounted volume) plus a short `MODEL_CARD.md`.

### 6. SHAP — `ml/ml/explain.py`

`TreeExplainer` over the test set. Save `reports/shap_summary.png` (beeswarm, top 15). Provide `top_drivers(symbol, ts) -> list[(feature, value, shap)]` returning the top 3 driving features for a given prediction — this powers the DB `prediction_drivers` rows.

### 7. The `predict` job — writes to Postgres

`ml/ml/predict.py::run_predict(timeframe="4h")`:
- For each of the 10 coins, take the latest available feature row.
- Predict calibrated probabilities → `pred_class` + `prob_up/prob_down/prob_flat`.
- Upsert one row per coin into `predictions` (PK `ts_utc, symbol, timeframe`), with `model_version` = the loaded model's version.
- Compute `top_drivers` and upsert 3 rows per coin into `prediction_drivers` (rank 1..3).
- Log how many coins were written.

Provide upsert helpers in `db.py` for these two tables (ON CONFLICT DO UPDATE).

### 8. Batch worker — `ml/ml/scheduler.py`

Extend the Stage 1 scheduler: keep the daily ingest job, ADD a `run_predict` job every few hours (e.g., every 4h). This is the container's long-running default command. Manual entry points:
- `docker compose run --rm ml python -m ml.train` — full train + calibrate + SHAP.
- `docker compose run --rm ml python -m ml.predict` — one-shot predict + write to DB.

### 9. Backtest (for honesty, not for the app)

`ml/ml/modelling/backtest.py`: over the test window, save predicted-probs vs true class to `reports/backtest_ml_v1.parquet`; per-symbol macro F1, top-1 accuracy, hit-rate when UP prob>0.5; write `reports/backtest_ml_v1_summary.md`.

### 10. Tests

`test_features.py` (no NaN after warmup), `test_splits.py` (no temporal overlap, no symbol leakage), `test_no_leakage.py` (feature at `(symbol, ts)` independent of data after `ts`), `test_ichimoku.py` (math on a toy DataFrame), `test_predict_writes.py` (after `run_predict`, `predictions` has 10 rows and `prediction_drivers` has 30).

### 11. STATE.md + Git

`STATE.md`: test macro F1, per-symbol best/worst F1, test macro AUC, Brier, calibration verdict, model_version, count of rows written to `predictions`/`prediction_drivers`. Commit `"Stage 2: ML classifier (features + XGBoost + calibration + SHAP) writing predictions to Postgres"`, tag `stage-2-done`.

## Definition of done

- `docker compose run --rm ml python -m ml.train` runs end-to-end in under ~30 min and saves a calibrated model to `models/v1/`.
- Test macro F1 ≥ 0.40, test macro AUC ≥ 0.55, Brier ≤ 0.65. **If macro F1 < 0.40, do not proceed — investigate leakage / target window / class balance.**
- `docker compose run --rm ml python -m ml.predict` writes 10 rows to `predictions` and 30 to `prediction_drivers`; verify in psql.
- All tests pass.
- `STATE.md` has the real numbers.

## What NOT to do

- Do NOT put features in the DB — they stay as Python-internal parquet. Only `predictions` + `prediction_drivers` cross the boundary.
- Do NOT write to any Java-owned table.
- Do NOT chase a 5% F1 gain; ship calibration over fit. Freeze the feature set after the first XGBoost run beats baseline (drop calendar/Bollinger nice-to-haves if time-pressed).
- Do NOT build the TA verdict here — that is Java/ta4j in Stage 3 (deliberate duplication, clean boundary).
- Do NOT build RAG, paper trading, or any UI — Stages 4, 5, 6.
- Do NOT turn the `ml` container into a web server — it is a batch worker.
