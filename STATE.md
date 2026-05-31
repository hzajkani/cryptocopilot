# STATE — CryptoCopilot

> Living handoff between stages. Each stage reads `PROJECT.md` (frozen spec) then this file.

## Current status

**Stage 2 — ML service (features + XGBoost + calibration + SHAP → predictions): ✅ COMPLETE**
(tagged `stage-2-done`)

- Phase A of 3 (data + ML core) is now finished. Containers live: `db`, `ml`.
- `backend` (Stage 3) and `frontend` (Stage 6) are still placeholders.
- Next: **Stage 3** — Spring Boot foundation + REST API over the data + TA verdict (ta4j).

> **DoD note (read this):** 2 of 3 metric gates pass — macro **AUC 0.578** ✓ (in the
> spec's honest 0.55–0.62 band) and **Brier 0.608** ✓. Test **macro F1 0.375** is
> short of the ≥0.40 gate. This was investigated against all three levers the DoD
> names (leakage / target window / class balance) and found to be a **genuine
> data-limited ceiling**, not a defect (details below). Accepted by the project
> owner as the honest result; the pipeline is production-grade, tested, and
> writing predictions to Postgres.

---

## Stage 2 — what is done

- **Feature engineering** (`ml/ml/features/`, Python-internal parquet, never in the DB):
  `indicators.py` (returns 1h/4h/24h/7d, RSI 7/14/21, MACD+crossover, Stochastic,
  ADX, Bollinger %B+bandwidth, ATR%, realised vol 24h/7d, volume z-score, SMA
  ratios), `ichimoku.py` (**from scratch** — Tenkan/Kijun/Senkou A·B, cloud flags,
  continuous distances; leakage-safe displacement), `calendar.py`, `build.py`
  (long-format, cached to `data/processed/features_4h.parquet`). **46 model
  features** (incl. symbol one-hot). All backward-only.
- **Target + splits** (`modelling/splits.py`): `y_24h_3class` (±2% / 24h), strictly
  chronological with a **24h embargo** between splits.
- **Models** (`modelling/`): LogReg baseline → **XGBoost** `multi:softprob`,
  **Optuna** (40 trials, val macro F1), **isotonic calibration** on val
  (`FrozenEstimator`/prefit). Bundle saved to `models/v1/` + `MODEL_CARD.md`.
- **SHAP** (`explain.py`): `TreeExplainer`, beeswarm `reports/shap_summary.png`,
  `top_drivers()` → the `prediction_drivers` rows (symbol one-hot excluded so
  drivers are market-state, not "this coin is BTC").
- **`predict` job** (`predict.py`): writes the latest forecast per coin →
  **10 `predictions` + 30 `prediction_drivers`** (upserts in `db.py`).
- **Batch worker** (`scheduler.py`): keeps the daily ingest, **adds a predict job
  every 4h** (log-and-skips until a model exists). Training stays manual.
- **Backtest** (`modelling/backtest.py`) + **5 test files** + Docker (`pytest`/ML
  deps baked in, `models`/`data`/`reports` bind-mounts).

## Concrete numbers (this run; deterministic, seed=42)

**Splits** (anchored to the real 2-year span — see deviation #1):

| split | rows | window | DOWN / FLAT / UP |
|---|---|---|---|
| train | 20,961 | 2024-06-15 → 2025-05-30 | 0.268 / 0.466 / 0.265 |
| val   | 5,460  | 2025-06-01 → 2025-08-30 | 0.246 / 0.473 / 0.282 |
| test  | 16,290 | 2025-09-01 → 2026-05-30 | 0.261 / 0.537 / 0.202 |

**Model** `v1` (46 features). Optuna best (val macro F1 0.412): `max_depth=4,
lr=0.029, subsample=0.97, colsample_bytree=0.82, min_child_weight=4, gamma=4.45,
reg_lambda=0.073, reg_alpha=2.97`. Decision rule: **val-tuned weighted argmax**
`w = [DOWN 1.5, FLAT 1.0, UP 1.5]` (probabilities stay calibrated — see deviation #2).

**Test metrics (out-of-sample):**

| metric | value | gate | |
|---|---|---|---|
| macro F1 | **0.375** | ≥ 0.40 | ✗ (data-limited, accepted) |
| macro ROC-AUC | **0.578** | ≥ 0.55 | ✓ |
| multiclass Brier | **0.608** | ≤ 0.65 | ✓ |
| baseline LogReg macro F1 | 0.292 | — | XGBoost beats baseline |

- **Per-symbol macro F1** — best **MATIC 0.377**, worst **AVAX 0.295** (full table:
  `reports/backtest_v1_summary.md`).
- **Calibration verdict**: isotonic on val; calibrated probabilities are honest
  (Brier 0.608). The class *label* uses the balanced weighted-argmax rule.
- **Backtest** (16,290 rows): top-1 accuracy 0.430, hit-rate when P(UP)>0.5 = 0.349.
- **Rows written to Postgres** by `predict`: **`predictions` = 10**,
  **`prediction_drivers` = 30** (verified in psql).

## Why macro F1 is 0.375 (the investigation)

The weak class is **UP** (test F1 ≈ 0.17): predicting >+2% / 24h rallies from
OHLCV-only TA is near the noise floor in the calm 2025–26 regime.

- **Not leakage** — AUC 0.578 is squarely in the spec's expected 0.55–0.62 band;
  `test_no_leakage.py` (truncation-invariance) passes; the **oracle (hindsight-
  optimal) decision weights also cap at 0.375**, so the decision rule is not the
  bottleneck.
- **Target window** — tightening the FLAT band to ±1% made it *worse* (a 1% 24h
  move is mostly noise → lower AUC); a longer/earlier test window gave the same
  ~0.375. Kept the spec's ±2%.
- **Class balance** — handled at decision time (prior-correction → val-tuned
  weights lifted macro F1 0.28 → 0.37) rather than by redefining the target.
- **Root cause is data volume**: Binance returned ~2 years (from 2024-05-31, per
  Stage 1), vs the prompt's assumed ~3 (its split dates start 2023-01). More
  history (Stage 1 scope) is the lever most likely to clear 0.40.

## Deviations from the Stage 2 prompt (all documented, leakage-safe)

1. **Split dates** — prompt: Train 2023-01→2024-06 etc. Our OHLCV is 2024-05-31→
   2026-05-31, so those dates leave ~1 month of train. Methodology preserved
   (chronological, no shuffle, embargo); boundaries anchored to the real span as
   **train 12mo / val 3mo / test 9mo→present** (a robust, multi-regime test).
   Override via `ML_TRAIN_START`/`…_END`/`ML_VAL_*`/`ML_TEST_START` env.
2. **Decision rule** — the *class label* is a validation-tuned weighted argmax
   `argmax_k w_k·p_k` (probabilities remain the calibrated ones). Plain argmax of
   calibrated probs collapses to the dominant FLAT class and tanks macro F1; this
   is a standard, val-only decision-threshold tune. Stored in the bundle.
3. **Calibration** — base XGBoost is fit on train only and calibrated on val; it is
   **not** refit on train+val before calibration (isotonic-on-val is only valid if
   the estimator hasn't seen val). Test is never touched in model selection.
4. **macro F1 < 0.40** — accepted as the honest data-limited ceiling (above).

## How to reproduce

```bash
docker compose up -d db
docker compose build ml
# full train + calibrate + SHAP + backtest -> models/v1/ (+ MODEL_CARD), reports/
docker compose run --rm ml python -m ml.train
# one-shot predict -> 10 predictions + 30 drivers
docker compose run --rm ml python -m ml.predict
docker compose exec db psql -U cc -d cryptocopilot \
  -c "SELECT count(*) FROM predictions; SELECT count(*) FROM prediction_drivers;"
docker compose run --rm ml pytest -q          # 13 tests
```

The `ml` container's default command is the APScheduler worker (`python -m
ml.scheduler`): daily ingest 02:00 UTC + predict every 4h.

## Definition of done — checklist

- [x] `docker compose run --rm ml python -m ml.train` runs end-to-end (~40s ≪ 30 min), saves a calibrated model to `models/v1/`.
- [~] Test macro F1 ≥ 0.40 → **0.375** (data-limited, investigated & accepted); macro AUC 0.578 ✓; Brier 0.608 ✓.
- [x] `docker compose run --rm ml python -m ml.predict` writes 10 `predictions` + 30 `prediction_drivers` (verified in psql).
- [x] All 13 tests pass (`pytest -q`).
- [x] `STATE.md` has the real numbers (this file).

---

## Stage 1 — Infrastructure + Postgres schema + ingestion: ✅ COMPLETE (tagged `stage-1-done`)

Monorepo scaffold, Postgres 16 + pgvector with the full `db/init.sql` contract
(11 tables), and all five-source ingestion. **231,082 rows**: `ohlcv` 226,200
(10 coins × {1h,4h,1d}, ~2y from Binance; MATIC = MATIC+POL stitched),
`market_meta` 3,660, `news` 124 (180d window, 5 sources), `onchain` 1,088
(BTC blockchain.com + ETH etherscan), `fundamentals` 10. CoinGecko Demo caps
`market_chart` history at 365d; some social/dev fields sparse — all log-and-skip
(PROJECT.md §9). Reproduce: `docker compose run --rm ml python -m ml.ingest.run_all`.
