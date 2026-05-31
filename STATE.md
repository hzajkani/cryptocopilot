# STATE — CryptoCopilot

> Living handoff between stages. Each stage reads `PROJECT.md` (frozen spec) then this file.

## Current status

**Stage 3 — Spring Boot backend (data REST API + ta4j TA verdict): ✅ COMPLETE**
(tagged `stage-3-done`)

- Phase B of 3 (Java/Spring backend) has begun. Containers live: `db`, `ml`, `backend`.
- `frontend` (Stage 6) is still a placeholder.
- Next: **Stage 4** — RAG (Spring AI + pgvector): index from relational tables + KB, retrieve,
  generate with citations, chat endpoint.
- Stage 2 (ML) remains ✅ (`stage-2-done`); accepted data-limited macro **F1 0.375** /
  **AUC 0.578** — see its section below.

> **Stage 2 DoD note:** 2 of 3 metric gates pass — macro **AUC 0.578** ✓ (in the
> spec's honest 0.55–0.62 band) and **Brier 0.608** ✓. Test **macro F1 0.375** is
> short of the ≥0.40 gate. This was investigated against all three levers the DoD
> names (leakage / target window / class balance) and found to be a **genuine
> data-limited ceiling**, not a defect (details below). Accepted by the project
> owner as the honest result; the pipeline is production-grade, tested, and
> writing predictions to Postgres.

---

## Stage 3 — what is done

The `backend` container (Java 21 + Spring Boot 3.3.5, Maven) is live: it reads Python's tables
**read-only** over JDBC, serves a REST API over the existing data, and computes a deterministic
Ichimoku-centric **TA verdict with ta4j 0.17**. No RAG/trading/Analyst/frontend yet (Stages 4–6).
One modular monolith, not microservices (PROJECT.md §2).

**Live endpoints** (`docker compose up -d` → db + ml + backend; Tomcat on :8080, starts in ~2s):

- `GET /actuator/health` → `{"status":"UP"}`.
- `GET /api/markets` → 10 coins `{symbol, price, change24hPct, marketCapUsd}` — price + 24h
  change from 4h OHLCV (6 bars back); market cap from latest `market_meta` (null for the 3 coins
  without a snapshot — log-and-skip ingestion, PROJECT.md §9).
- `GET /api/coins/{symbol}/ohlcv?timeframe=4h&from=&to=` → candle array, default last 90 days
  (e.g. BTC 4h → 538 candles).
- `GET /api/signals` → 10 coins, each `{symbol, ts, mlClass, mlConfidence, probUp/Down/Flat,
  modelVersion, drivers[3], ta}`. **`mlConfidence` = calibrated prob of the stored `pred_class`**
  (e.g. BTC `FLAT` → 0.6997 = `prob_flat`) — never re-argmaxed from the probabilities.
- `GET /api/ta/{symbol}` → the full `TAVerdict`.

**TA verdict engine** (`com.cryptocopilot.ta`, pure ta4j from raw `ohlcv` — never Python
features, PROJECT.md §3): Ichimoku (9/26/52; the +26 displacement is applied as
`getValue(endIndex−26)` on offset-0 raw Senkou spans, mirroring the Python `shift(26)` —
leakage-safe), RSI(14), MACD(12,26)+signal(9) histogram, Bollinger %B(20,2). Spec scoring →
`score`; `direction` (≥+2 BULLISH / ≤−2 BEARISH); `confidence` (|s|≥3 STRONG / ≥2 MODERATE);
`signals` = every non-zero rule. **Sample (live BTC, 4h):** `NEUTRAL / WEAK`, score **−1.5** —
"Price below the Ichimoku cloud (−2.0)", "Bullish cloud: Senkou A above Senkou B (+0.5)".

**`ddl-auto: validate` ✅** — at startup Hibernate validated all 7 read-only JPA entities
(`Ohlcv`, `MarketMeta`, `News`, `Onchain`, `Fundamentals`, `Prediction`, `PredictionDriver`;
composite keys via `@IdClass`) against the real `db/init.sql` schema with zero errors; the app
started clean (no `HHH000…` schema-validation warnings).

**Tests — 8, all green (`mvn test`):**

- `TaVerdictTest` (4) — golden bullish ramp → **BULLISH / MODERATE, score 2.5**, exact 4 signals;
  bearish-cloud branches fire on a downtrend (nets NEUTRAL — the oversold guard hedges it, an
  intended property); `score→direction/confidence` thresholds; insufficient-history guard.
- `SignalsControllerTest` (`@WebMvcTest`, mocked `SignalService`) — `/api/signals` returns 10
  coins, each with `mlClass` + `mlConfidence` + a `ta` block.
- `OhlcvRepositoryTest` (`@DataJpaTest` vs the running `db`, read-only, `ddl-auto: validate`) —
  OHLCV range (ascending, bounded) + latest-prediction (`v1`) queries.

**Stack/versions:** Spring Boot 3.3.5, Java 21, ta4j 0.17, Hibernate 6.5.3, Postgres 16 +
pgvector. Build: `backend/Dockerfile` multi-stage (`maven:3.9-eclipse-temurin-21` →
`eclipse-temurin:21-jre`, port 8080).

### Definition of done — checklist

- [x] `docker compose up -d` brings up `db`, `ml`, `backend`; `GET /actuator/health` is UP.
- [x] `ddl-auto: validate` passes (entities match the real schema).
- [x] `GET /api/markets` → 10 coins with price + 24h change + market cap.
- [x] `GET /api/signals` → 10 coins, each ML class + confidence (= prob of predicted class) + top-3 drivers + TA verdict.
- [x] `GET /api/coins/BTC/ohlcv?timeframe=4h` → non-empty candle array (538).
- [x] All tests pass (8), including the TA golden test.

### Deviations from the Stage 3 prompt (documented)

1. **Repository slice test runs against the live `db`, not Testcontainers.** This host's
   docker-java ↔ Docker Desktop socket returns HTTP 400 on the client ping (the `docker` CLI and
   raw `curl` to the socket both work, but the JVM client does not), so Testcontainers cannot
   start a container here. The prompt allows `@DataJpaTest`; it runs read-only (transaction
   rolled back) against the running `db` and still validates the entities against `init.sql`.
   The test requires `db` up — which the DoD assumes.
2. **Confidence middle band generalised.** The spec writes it as "`==2` MODERATE"; implemented as
   `|score| ≥ 2` so the half-point scores the rules can produce (e.g. 2.5) read as a directional
   MODERATE rather than WEAK. `|score| ≥ 3` STRONG is unchanged.

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
