# Stage 1 — Infrastructure + Postgres schema + all data ingestion (Python)

> **Phase A of 3 (data + ML core).** This is Stage 1 of 7.
>
> **How to use this file:** Copy `PROJECT.md` into an empty `cryptocopilot` folder, `git init`, then open a fresh Claude Code session in that folder. First message: *"Read PROJECT.md before anything else."* (No STATE.md yet — you create it here.) Then paste everything below the line.
>
> This is the **largest** stage (all ingestion lives here). If the session gets long, do it in two passes: first the skeleton + db + price/market ingestion (verify), then news + on-chain + fundamentals.

---

# CryptoCopilot — Stage 1: monorepo, Postgres + pgvector, and the Python data service (ingestion)

Read `PROJECT.md`. This stage builds the **infrastructure** (a 4-container monorepo, of which `db` and `ml` are real this stage) and the **Python data service**: it ingests all five public sources into Postgres. No ML yet (Stage 2), no Java yet (Stage 3).

The shared DB schema in `PROJECT.md` §5 is the contract. Materialise it exactly as `db/init.sql`.

## Goals

1. Monorepo skeleton with `ml/`, `backend/`, `frontend/` placeholders and a top-level `docker-compose.yml`.
2. Postgres 16 + pgvector container, schema created from `db/init.sql` on first boot.
3. The `ml` Python container that pulls OHLCV, market meta, RSS news, on-chain (BTC/ETH), and CoinGecko fundamentals into Postgres.
4. `STATE.md` with concrete row counts.

## Tasks

### 1. Monorepo structure

```
cryptocopilot/
├── PROJECT.md                 # already present — DO NOT MODIFY
├── STATE.md                   # you create
├── README.md
├── docker-compose.yml
├── .env.example
├── .gitignore
├── db/
│   └── init.sql               # the full schema contract from PROJECT.md §5
├── ml/
│   ├── Dockerfile
│   ├── pyproject.toml
│   └── ml/
│       ├── __init__.py
│       ├── config.py          # 10 coins, timeframes, history days, RSS sources, CoinGecko IDs
│       ├── db.py              # SQLAlchemy engine + upsert helpers (reads DATABASE_URL)
│       ├── ingest/
│       │   ├── __init__.py
│       │   ├── binance.py     # OHLCV via ccxt
│       │   ├── coingecko_market.py     # market_meta (market_chart)
│       │   ├── rss_news.py    # feedparser + VADER → news
│       │   ├── onchain.py     # Blockchain.com (BTC) + Etherscan (ETH) → onchain
│       │   ├── coingecko_fundamentals.py  # /coins/{id} community+developer+market → fundamentals
│       │   └── run_all.py     # orchestrates all of the above, logs counts
│       └── scheduler.py       # APScheduler entry (used more in Stage 2; stub a daily ingest job)
├── backend/
│   └── .gitkeep               # placeholder — built in Stage 3
└── frontend/
    └── .gitkeep               # placeholder — built in Stage 6
```

Pin Python 3.11 in `ml/`. `ml/pyproject.toml` initial deps (ingestion only; ML deps added in Stage 2):
`pandas, numpy, ccxt, requests, feedparser, vaderSentiment, sqlalchemy, psycopg2-binary, python-dotenv, apscheduler, ruff, pytest`

### 2. `db/init.sql`

Copy the **entire** DDL from `PROJECT.md` §5 verbatim, including `CREATE EXTENSION IF NOT EXISTS vector;`. Do **not** create the Spring AI `vector_store` table — Spring AI creates it in Stage 4.

### 3. `docker-compose.yml` (db + ml only this stage)

```yaml
services:
  db:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_USER: cc
      POSTGRES_PASSWORD: ccpass
      POSTGRES_DB: cryptocopilot
    ports: ["5432:5432"]
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./db/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U cc -d cryptocopilot"]
      interval: 5s
      timeout: 5s
      retries: 10

  ml:
    build: ./ml
    env_file: .env
    environment:
      DATABASE_URL: postgresql+psycopg2://cc:ccpass@db:5432/cryptocopilot
    depends_on:
      db:
        condition: service_healthy
    # batch worker: default command sleeps; jobs are run via `docker compose run`
    command: ["python", "-m", "ml.scheduler"]

volumes:
  pgdata:
```

`backend` and `frontend` services are intentionally **omitted** this stage — they are added to compose in Stage 3 and Stage 6. Leave a commented block at the bottom of the file noting this.

`ml/Dockerfile`: base `python:3.11-slim`, install `build-essential`, copy `pyproject.toml`, `pip install .`, copy `ml/`, default `CMD` matches compose.

### 4. `.env.example` and `.gitignore`

`.env.example`:
```env
COINGECKO_API_KEY=CG-...
ETHERSCAN_API_KEY=...
OPENAI_API_KEY=sk-...     # not used until Stage 4, but keep it here
```
`.gitignore`: `.env`, `__pycache__/`, `*.parquet`, `data/`, `target/`, `node_modules/`, `.idea/`, `.venv/`.

### 5. `ml/ml/config.py`

Hardcode: `ASSETS = ["BTC","ETH","SOL","BNB","XRP","ADA","AVAX","DOT","LINK","MATIC"]`, `QUOTE="USDT"`, `TIMEFRAMES=["1h","4h","1d"]`, `HISTORY_DAYS=730`, the 5 `RSS_SOURCES` (name+url from PROJECT.md §6), and `COIN_IDS` (CoinGecko ids from PROJECT.md §6).

### 6. `ml/ml/db.py`

SQLAlchemy engine from `DATABASE_URL`. Provide idempotent upsert helpers (ON CONFLICT … DO UPDATE) for each Python-owned table: `upsert_ohlcv`, `upsert_market_meta`, `upsert_news`, `upsert_onchain`, `upsert_fundamentals`. These must respect the primary keys in the contract. (Do **not** create tables here — `init.sql` owns the schema.)

### 7. Ingestion modules

- **`binance.py`** — `ccxt.binance({"enableRateLimit": True})`, public OHLCV, fetch ~2y per `(asset, timeframe)` in ≤1000-candle chunks, retry with backoff, return `[ts_utc, symbol, timeframe, open, high, low, close, volume]`. MATIC→POL fallback, log resolved symbol.
- **`coingecko_market.py`** — `/coins/{id}/market_chart?vs_currency=usd&days=730`, header `x-cg-demo-api-key`, sleep 2.5s between calls → `market_meta` (market cap, supplies).
- **`rss_news.py`** — `feedparser.parse(url)` for all 5 feeds. Per entry extract `title, summary, published, link, source`; tag currencies by ticker + name match (Bitcoin/Ethereum/Solana/…); VADER compound on `title + " " + summary` → POSITIVE(>0.2)/NEGATIVE(<-0.2)/NEUTRAL; dedupe by url hash (`id`); maintain 180-day rolling window (delete older). → `news`.
- **`onchain.py`** — BTC via Blockchain.com Charts (`n-unique-addresses`, `n-transactions`, `estimated-transaction-volume-usd`, `timespan=1year&format=json`, source `blockchain_com`); ETH via Etherscan (`ethsupply`, and daily endpoints where free — **if an endpoint is Pro-only, log and skip**, source `etherscan`). → `onchain` (long format: ts, symbol, metric, value, source).
- **`coingecko_fundamentals.py`** — `/coins/{id}?community_data=true&developer_data=true&market_data=true&tickers=false`, header `x-cg-demo-api-key`, sleep 2.5s between calls. Extract the market/community/developer fields into `fundamentals`. **If a field is missing for a coin, log and skip that field — do not crash.**
- **`run_all.py`** — runs all five in sequence, logs per-source counts + total inserts + current 180d news-window size.

### 8. `ml/ml/scheduler.py`

APScheduler `BlockingScheduler`: a stub daily job calling `run_all.main()`. This is the container's default command so it stays alive. (Stage 2 adds the prediction job.) Manual full ingest is `docker compose run --rm ml python -m ml.ingest.run_all`.

### 9. Tests

`ml/tests/test_ingestion.py`: pull 1 day of `BTC/USDT 1h` and assert non-empty DataFrame, expected columns, no NaN in OHLCV. (Network test — mark it so it can be skipped offline.)

### 10. README + STATE.md + Git

- `README.md`: one-paragraph pitch, the architecture diagram from PROJECT.md, a 3-command quickstart (`docker compose up -d db`, `docker compose run --rm ml python -m ml.ingest.run_all`, then connect with psql), and the data-sources table.
- `STATE.md`: status, what is done, concrete numbers — rows per `(symbol, timeframe)` in `ohlcv`, news rows per source, on-chain metrics per coin, fundamentals row count, any skipped Etherscan/CoinGecko fields.
- `git add -A && git commit -m "Stage 1: monorepo + Postgres/pgvector schema + Python ingestion"` then `git tag stage-1-done`.

## Definition of done

- `docker compose up -d db` → Postgres healthy; `\dt` in psql shows all contract tables and `\dx` shows the `vector` extension.
- `docker compose run --rm ml python -m ml.ingest.run_all` populates `ohlcv`, `market_meta`, `news`, `onchain`, `fundamentals` without crashing (sources that fail are logged and skipped, not fatal).
- `SELECT symbol, timeframe, count(*) FROM ohlcv GROUP BY 1,2;` returns ~2 years of bars for the 10 coins (POL/MATIC may be partial — that's fine).
- `news` has rows from multiple sources within the last 180 days.
- `onchain` has BTC (blockchain_com) and ETH (etherscan) rows.
- `fundamentals` has rows for the coins CoinGecko returned.
- `STATE.md` has real numbers, not placeholders.

## What NOT to do

- Do NOT build the ML model — Stage 2.
- Do NOT add the `backend` or `frontend` services to compose yet — Stages 3 and 6.
- Do NOT create the Spring AI `vector_store` table — Stage 4 does that.
- Do NOT use SQLite — this project is Postgres + pgvector.
- Do NOT compute technical indicators here — features belong to Stage 2 (Python ML) and ta4j belongs to Stage 3 (Java).
- If MATIC/POL or any source fails, **log and skip** — never block the pipeline.
- Do NOT write to any Java-owned table (`trades`, `orders`, `positions`, `account_state`).
