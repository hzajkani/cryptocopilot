# CryptoCopilot — Polyglot Edition

CryptoCopilot is a personal, **paper-only** crypto trading assistant rebuilt as a polyglot
system: a Python data + ML service, a Java/Spring Boot application service, and a React
frontend, arranged as four Docker containers around one shared **Postgres + pgvector**
database. It fuses four perspectives on each of 10 coins — an ML direction signal, a
deterministic technical-analysis verdict, a fundamental snapshot, and a cited RAG chat over
news + on-chain + a knowledge base — into one explainable **Analyst** opinion, and lets you
act with a paper trade. Decision-support, **not** financial advice.

> **Stage 1 of 7.** This repo currently contains the infrastructure (`db` + `ml` containers)
> and the **Python data service**: it ingests all five public sources into Postgres. No ML
> model yet (Stage 2), no Java backend yet (Stage 3), no frontend yet (Stage 6). See
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

```bash
# 0. Configure secrets (CoinGecko + Etherscan free keys; OpenAI used from Stage 4)
cp .env.example .env && $EDITOR .env

# 1. Start Postgres + pgvector (schema is created from db/init.sql on first boot)
docker compose up -d db

# 2. Run the full ingestion (OHLCV, market meta, news, on-chain, fundamentals)
docker compose run --rm ml python -m ml.ingest.run_all

# 3. Inspect the data
docker compose exec db psql -U cc -d cryptocopilot -c \
  "SELECT symbol, timeframe, count(*) FROM ohlcv GROUP BY 1,2 ORDER BY 1,2;"
```

The `ml` container's default command is the APScheduler loop (`python -m ml.scheduler`), a
daily-ingest stub that keeps the batch worker alive. Full ingest is run on demand with the
`docker compose run` command above.

### Tests

```bash
# inside the ml image (network test hits Binance; skip it offline with -m "not network")
docker compose run --rm ml sh -c "pip install -q pytest && pytest -q"
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
├── docker-compose.yml    # db + ml (backend/frontend added in Stages 3 & 6)
├── db/init.sql           # the shared schema contract (PROJECT.md §5)
└── ml/                   # Python data + ML service
    └── ml/
        ├── config.py     # assets, timeframes, RSS sources, CoinGecko ids
        ├── db.py         # SQLAlchemy engine + idempotent upserts
        ├── ingest/       # binance, coingecko_market, rss_news, onchain, coingecko_fundamentals, run_all
        └── scheduler.py  # APScheduler batch-worker entry point
```
