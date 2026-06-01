# CryptoCopilot — Polyglot Edition

CryptoCopilot is a personal, **paper-only** crypto trading assistant rebuilt as a polyglot
system: a Python data + ML service, a Java/Spring Boot application service, and a React
frontend, arranged as four Docker containers around one shared **Postgres + pgvector**
database. It fuses four perspectives on each of 10 coins — an ML direction signal, a
deterministic technical-analysis verdict, a fundamental snapshot, and a cited RAG chat over
news + on-chain + a knowledge base — into one explainable **Analyst** opinion, and lets you
act with a paper trade. Decision-support, **not** financial advice.

> **Stages 1–5 of 7 are complete — Phase B (the Java backend) is done.** The `db` + `ml`
> containers ingest all five public sources into Postgres (Stage 1) and train a calibrated
> XGBoost direction classifier that writes predictions back (Stage 2). The **Spring Boot
> `backend`** serves market data + fused ML/TA signals + the ta4j TA verdict (Stage 3), a cited
> RAG **Researcher** over the corpus (Stage 4), and the **Analyst** opinion + long-only
> **paper-trading** engine (Stage 5). Next: the React `frontend` (Stage 6). See
> [`PROJECT.md`](PROJECT.md) for the frozen spec and [`STATE.md`](STATE.md) for live status.

## Architecture — four containers, one shared database

```
                        ┌──────────────────────────────┐
   browser  ─────────►  │  frontend  (React + nginx)   │
                        └───────────────┬──────────────┘
                                        │ REST / JSON
                                        ▼
                        ┌──────────────────────────────┐
                        │  backend  (Spring Boot)      │   ◄── MODULAR MONOLITH
                        │  ta4j · Spring AI · trading  │       (ONE container, many
                        │  analyst · REST API          │        internal modules)
                        └───────────────┬──────────────┘
                                        │ JDBC
                                        ▼
       ┌──────────────────────►  ┌──────────────────────────────┐
       │  (writes predictions,   │  db  (Postgres 16 + pgvector)│  ◄── SINGLE SOURCE
       │   raw data)             └──────────────────────────────┘       OF STATE, SHARED
       │
┌──────┴───────────────────────┐
│  ml  (Python)                │   ◄── BATCH WORKER, not a web server.
│  ingestion · XGBoost · SHAP  │       Wakes on a schedule, writes to DB, sleeps.
└──────────────────────────────┘
```

The **database is the polyglot boundary**: Python writes its tables, Java reads them — no RPC,
no shared model files. Each table has exactly one writer (see `PROJECT.md` §3).

## Quickstart

Prerequisites: Docker + Docker Compose. Get a free [CoinGecko Demo](https://www.coingecko.com/en/api)
key and a free [Etherscan](https://etherscan.io/apis) key.

> **Tip — set your editor once:** the commands below open `.env` with `$EDITOR` (falling back
> to `nano`). To use VS Code / vim / etc. permanently, add one line to your shell rc:
> ```bash
> echo 'export EDITOR=code' >> ~/.zshrc && source ~/.zshrc   # or: nano / vim / "code -w"
> ```

```bash
# 0. Configure secrets (CoinGecko + Etherscan free keys). The Stage 4 RAG chat runs on a
#    free local Ollama (no API key) — see docs/OLLAMA_SETUP.md.
#    `cp -n` is non-destructive: it will NOT overwrite an existing .env.
cp -n .env.example .env
${EDITOR:-nano} .env   # use $EDITOR if set, otherwise fall back to nano

# 1. Start Postgres + pgvector (schema is created from db/init.sql on first boot)
docker compose up -d db

# 2. Run the full ingestion (OHLCV, market meta, news, on-chain, fundamentals)
docker compose run --rm ml python -m ml.ingest.run_all

# 3. Inspect the data
docker compose exec db psql -U cc -d cryptocopilot -c \
  "SELECT symbol, timeframe, count(*) FROM ohlcv GROUP BY 1,2 ORDER BY 1,2;"
```

The `ml` container's default command is the APScheduler loop (`python -m ml.scheduler`):
a daily ingest at 02:00 UTC plus a predict job every 4h that keeps the batch worker alive.
One-off fetch / train / predict runs are launched on demand with `docker compose run`
(below).

## Fetch → train → predict

These are the three ML entry points. All three read/write the **same shared Postgres**;
training also produces local model + report files. Run them in this order the first time
(`predict` needs both data and a trained model).

| Step | Module | Reads | Writes |
|---|---|---|---|
| **Fetch** | `ml.ingest.run_all` | the five public APIs | `ohlcv`, `market_meta`, `news`, `onchain`, `fundamentals` (Postgres) |
| **Train** | `ml.train` | `ohlcv` (Postgres) | `ml/models/v1/` + `ml/reports/` (files); `ml/data/processed/features_4h.parquet` cache |
| **Predict** | `ml.predict` | `ohlcv` (Postgres) + `models/v1/` | `predictions`, `prediction_drivers` (Postgres) |

### In Docker (recommended)

```bash
# 0. one-time: secrets + start the database (schema auto-created from db/init.sql)
#    `cp -n` will NOT overwrite an existing .env (so re-running this block is safe).
cp -n .env.example .env
${EDITOR:-nano} .env   # use $EDITOR if set, otherwise fall back to nano
docker compose up -d db
docker compose build ml

# 1. FETCH — ingest all five sources into Postgres (Stage 1)
docker compose run --rm ml python -m ml.ingest.run_all

# 2. TRAIN — features → XGBoost → Optuna → isotonic calibration → SHAP → backtest
#    Saves the calibrated bundle to ml/models/v1/ and reports to ml/reports/ (~40s).
docker compose run --rm ml python -m ml.train

# 3. PREDICT — latest forecast per coin → 10 predictions + 30 driver rows in Postgres
docker compose run --rm ml python -m ml.predict
```

The `models/`, `data/`, and `reports/` folders are **bind-mounted** (see
`docker-compose.yml`), so the model trained by one `compose run` is visible to the next
`predict` run and inspectable on the host under `ml/models/`, `ml/data/`, `ml/reports/`.

To run fetch + predict automatically on a schedule, just leave the worker up
(`docker compose up -d ml`); training stays manual.

### Locally (without Docker)

The Python service runs on the host too — only the **database** needs to be up. Point
`DATABASE_URL` at the Postgres exposed on `localhost:5432` by the `db` container (or any
Postgres initialised with `db/init.sql`).

```bash
# Postgres must be reachable; the compose db publishes localhost:5432
docker compose up -d db

cd ml
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
# macOS only: XGBoost needs the OpenMP runtime (not a pip dependency)
#   brew install libomp   (or: conda install -c conda-forge llvm-openmp)

# .env (loaded automatically by config.py) — or export inline:
export DATABASE_URL="postgresql+psycopg2://cc:ccpass@localhost:5432/cryptocopilot"

python -m ml.ingest.run_all   # FETCH
python -m ml.train            # TRAIN
python -m ml.predict          # PREDICT
```

Locally the artifacts land in `ml/models/v1/`, `ml/data/processed/`, and `ml/reports/`
(the same paths the bind mounts expose). Override the locations with
`ML_MODELS_DIR` / `ML_REPORTS_DIR` / `ML_DATA_DIR` if needed.

## Where the data is stored

Everything lives in the one shared Postgres database (`cryptocopilot`), **except** the ML
feature matrix and the trained model, which are Python-internal files — they never cross the
language boundary, so they are deliberately *not* in the DB (PROJECT.md §3).

### Raw ingested data → Postgres (Python-owned tables)

| Table | One row per | Key columns |
|---|---|---|
| `ohlcv` | candle | `ts_utc, symbol, timeframe`, `open, high, low, close, volume` |
| `market_meta` | snapshot | `ts_utc, symbol`, `market_cap_usd, circulating_supply, total_supply` |
| `news` | article | `id` (url hash), `ts_utc, title, summary, source, url, currencies, sentiment, sentiment_score` |
| `onchain` | metric reading | `ts_utc, symbol, metric, value, source` |
| `fundamentals` | snapshot | `ts_utc, symbol`, price-change %s, volume, reddit/twitter/github activity |

### Predicted data → Postgres (the model's output)

`ml.predict` writes the **latest 4h forecast per coin** (10 coins → 10 + 30 rows) into:

- **`predictions`** — one row per coin. Columns:
  `ts_utc, symbol, timeframe` (= `4h`), `pred_class` (`UP` / `DOWN` / `FLAT`),
  `prob_up, prob_down, prob_flat` (calibrated probabilities), `model_version` (`v1`),
  `created_at`.
- **`prediction_drivers`** — the **top-3 SHAP drivers** behind each prediction (3 rows per
  coin). Columns: `ts_utc, symbol, timeframe, rank` (1–3), `feature_name`, `feature_value`,
  `shap_value`.

Both are **upserts** keyed on `(ts_utc, symbol, timeframe[, rank])`, so re-running `predict`
refreshes in place rather than duplicating. Inspect them with:

```bash
docker compose exec db psql -U cc -d cryptocopilot -c \
  "SELECT symbol, pred_class, prob_up, prob_down, prob_flat, model_version
     FROM predictions ORDER BY symbol;"

docker compose exec db psql -U cc -d cryptocopilot -c \
  "SELECT symbol, rank, feature_name, shap_value
     FROM prediction_drivers ORDER BY symbol, rank;"
```

### Model + features → local files (not in the DB)

| Path | What |
|---|---|
| `ml/data/processed/features_4h.parquet` | engineered feature matrix cache (46 features, long format) |
| `ml/models/v1/bundle.joblib` | calibrated XGBoost bundle (model + decision weights + feature list) |
| `ml/models/v1/meta.json` | model metadata (version, metrics) |
| `ml/models/v1/MODEL_CARD.md` | human-readable model card |
| `ml/reports/shap_summary.png` | SHAP beeswarm plot |
| `ml/reports/backtest_v1_summary.md` / `backtest_v1.parquet` | out-of-sample backtest |

### Tests

```bash
# ml (Python) — in Docker (pytest/ML deps are baked into the image)
docker compose run --rm ml pytest -q

# the network test hits Binance; skip it offline:
docker compose run --rm ml pytest -q -m "not network"

# backend (Java) — 67 offline tests (needs the db up for the @DataJpaTest slice)
cd backend && mvn test

# gated live runs (need the db; RAG also needs a local Ollama):
RAG_LIVE=1      mvn -Dtest=RagLiveIT test        # RAG retrieval eval  -> reports/retrieval_eval.md
BACKTEST_LIVE=1 mvn -Dtest=BacktestLiveIT test   # real-window backtest -> reports/backtest_strategy_v1.md
```

## Data sources

All sources are public and free. If a source goes paid or down, the pipeline **logs and skips**
it — it never crashes (PROJECT.md §9).

| Source | Used for | Auth |
|---|---|---|
| Binance public API | OHLCV (1h / 4h / 1d, ~2 years) | none |
| CoinGecko Demo | market cap/supply + community + developer + market data (all 10 coins) | free key, 10k/mo, 30/min |
| RSS (CoinDesk, Cointelegraph, Decrypt, The Block, Bitcoin Magazine) | news, 180-day rolling window | none |
| Blockchain.com Charts | BTC on-chain | none |
| Etherscan | ETH on-chain | free key, 5/sec, 100k/day |
| Curated KB | coin mechanism / tokenomics markdown (Stage 4) | — |

**Assets (10):** BTC, ETH, SOL, BNB, XRP, ADA, AVAX, DOT, LINK, MATIC/POL.
(MATIC was rebranded POL in late 2024; the OHLCV loader stitches `MATIC/USDT` and `POL/USDT`
together under the `MATIC` symbol.)

## Repo layout

```
cryptocopilot/
├── PROJECT.md            # frozen spec (do not modify during the build)
├── STATE.md              # living handoff between stages — current status + row counts
├── docker-compose.yml    # db + ml + backend (frontend added in Stage 6)
├── db/init.sql           # the shared schema contract (PROJECT.md §5)
├── reports/              # backend reports: retrieval_eval.md, backtest_strategy_v1.md
├── ml/                   # Python data + ML service
    ├── models/v1/        # trained bundle + MODEL_CARD (bind-mounted)
    ├── data/processed/   # features_4h.parquet cache (bind-mounted)
    ├── reports/          # SHAP plot + backtest (bind-mounted)
    └── ml/
        ├── config.py     # assets, timeframes, RSS sources, CoinGecko ids, paths
        ├── db.py         # SQLAlchemy engine + idempotent upserts
        ├── ingest/       # binance, coingecko_market, rss_news, onchain, coingecko_fundamentals, run_all
        ├── features/     # indicators, ichimoku (from scratch), calendar, build → parquet
        ├── modelling/    # splits, xgb_model, tune (Optuna), calibrate, backtest, metrics
        ├── explain.py    # SHAP TreeExplainer → top-3 drivers
        ├── train.py      # FETCH-free: train → calibrate → SHAP → save bundle + reports
        ├── predict.py    # latest forecast per coin → predictions + prediction_drivers
        └── scheduler.py  # APScheduler batch-worker entry point (daily ingest + 4h predict)
└── backend/             # Java/Spring Boot application service (Stages 3–5)
    └── src/main/java/com/cryptocopilot/
        ├── controller/  # REST: markets, signals, ta, chat (RAG), analyst, trading
        ├── service/ entity/ repository/ dto/   # market data + ta4j TA verdict (Stage 3)
        ├── rag/          # the Researcher: indexer, retriever, grounded generator (Stage 4)
        ├── analyst/      # FundamentalSnapshot + deterministic Analyst + guarded summary (Stage 5)
        └── trading/      # long-only paper-trading engine + backtest (Stage 5)
```
