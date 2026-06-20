# CryptoCopilot — Polyglot Edition

*A personal crypto trading assistant, rebuilt as a polyglot system: a Python data + ML service, a Java/Spring Boot application service, and a React frontend — four Docker containers around one shared Postgres + pgvector database.*

**Project Description and Engineering Plan**

Build a **production-grade Spring Boot fintech backend** with (Spring AI, ta4j, REST, paper-trading engine, deterministic Analyst aggregator).

Keep **ML where Python is strongest** (XGBoost, isotonic calibration, SHAP).

Demonstrate a clean **polyglot architecture** where two languages cooperate through a shared database — no RPC, no shared model files.

---

## 1. The core idea

CryptoCopilot answers ONE question for a single retail crypto trader:

> *Given everything happening in the market and news right now, what should I think about BTC/ETH/SOL — and if I make a trade, how would it perform?*

It does this by fusing four perspectives on each of 10 coins — an ML direction signal, a deterministic technical-analysis verdict, a fundamental snapshot, and a cited RAG chat over news + on-chain + knowledge base — into one explainable **Analyst** opinion. The user can act by placing a **paper trade** (no real money, ever). Every signal is auditable; every trade is logged.

**Honest scope.** Expected ML test ROC-AUC is 0.55–0.62. Above 0.65 is suspicious of leakage. The point is not to beat the market — it is to demonstrate production-grade polyglot ML + RAG + full-stack engineering.

---

## 2. Architecture — four containers, one shared database

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

### Why this shape

- **Container ≠ microservice.** Four containers is correct (different runtimes). But the backend is a **modular monolith** — one Spring Boot app with clean internal modules (`ingestion-readers`, `ta`, `rag`, `trading`, `analyst`). Do **not** split it into separate services. Microservices solve organisational and scaling problems this single-user project does not have; they would only add operational pain (service discovery, inter-service calls, distributed transactions).
- **The database is the polyglot boundary.** Python writes, Java reads. No RPC, no shared `.pkl`, no `xgboost4j`. The Java backend never touches a Python model — it reads numbers from a table. Even if the `ml` container is off, the app still serves the last predictions.
- **ML is a batch job, not a service.** 24h direction changes slowly. The `ml` container wakes periodically (APScheduler inside it), pulls fresh data, runs the model, writes probabilities + SHAP drivers to the DB, and sleeps. Training is run manually/occasionally (`docker compose run ml python -m ml.train`).

---

## 3. Container responsibilities and table ownership

The shared DB stays clean by **strict table ownership** — each table has exactly one writer.

| Container | Owns / writes | Reads |
|---|---|---|
| **ml** (Python) | `ohlcv`, `market_meta`, `news`, `onchain`, `fundamentals`, `predictions`, `prediction_drivers` | its own tables |
| **backend** (Java) | `account_state`, `positions`, `trades`, `orders`, the pgvector store (Spring AI) | all of `ml`'s tables (read-only) |
| **db** (Postgres) | — | — |
| **frontend** (React) | — | backend REST only |

**Rule:** Java never writes to Python's tables; Python never writes to Java's tables. Feature engineering for ML stays internal to Python (parquet on a volume) — it does not cross the boundary, so it is not in the DB contract. Java independently recomputes Ichimoku/RSI/etc. with **ta4j** for its TA verdict; the small duplication is intentional and keeps the boundary clean — do not try to "share features" across languages.

---

## 4. Tech stack per container

**ml (Python 3.11)**
- Ingestion: `ccxt` (Binance OHLCV), `requests` (Blockchain.com, Etherscan, CoinGecko), `feedparser` (RSS), `vaderSentiment` (local sentiment)
- Data: `pandas`, `numpy`, `ta`
- ML: `scikit-learn` (LogReg baseline + isotonic calibration), `xgboost`, `optuna` (tuning), `shap` (explainability)
- DB: `sqlalchemy` + `psycopg2-binary`
- Scheduling: `apscheduler`
- Tracking (optional): `mlflow`

**backend (Java 21 + Spring Boot 3.x)**
- `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, PostgreSQL driver
- **RAG/LLM:** `spring-ai-starter-model-openai` + `spring-ai-starter-vector-store-pgvector` (Spring AI 1.0 GA)
- **Technical analysis:** `ta4j` (Ichimoku, RSI, MACD, Bollinger built in)
- **RSS (only if needed Java-side):** `rome` — but RSS ingestion lives in Python, so usually not needed
- Build: Maven. Tests: JUnit 5

**frontend (React + nginx)**
- Vite + React + TypeScript
- Charts: **TradingView Lightweight Charts** (candlesticks) + **Recharts** (dashboards)
- Served by nginx in the container

**db**
- Image: `pgvector/pgvector:pg16` (Postgres 16 with the `vector` extension)

---

## 5. The database contract (single source of truth)

This DDL is materialised as `db/init.sql`, mounted into the Postgres container's `docker-entrypoint-initdb.d/` so it runs once on first boot. Both languages obey this schema exactly. (Flyway/Liquibase can replace `init.sql` later if migrations are needed; for now `init.sql` is the contract.)

```sql
CREATE EXTENSION IF NOT EXISTS vector;

-- ===================== PYTHON-OWNED (data + ML service) =====================

CREATE TABLE ohlcv (
  ts_utc      TIMESTAMPTZ NOT NULL,
  symbol      TEXT        NOT NULL,
  timeframe   TEXT        NOT NULL,
  open        DOUBLE PRECISION,
  high        DOUBLE PRECISION,
  low         DOUBLE PRECISION,
  close       DOUBLE PRECISION,
  volume      DOUBLE PRECISION,
  PRIMARY KEY (ts_utc, symbol, timeframe)
);

CREATE TABLE market_meta (
  ts_utc              TIMESTAMPTZ NOT NULL,
  symbol              TEXT        NOT NULL,
  market_cap_usd      DOUBLE PRECISION,
  circulating_supply  DOUBLE PRECISION,
  total_supply        DOUBLE PRECISION,
  PRIMARY KEY (ts_utc, symbol)
);

CREATE TABLE news (
  id              TEXT PRIMARY KEY,           -- hash of url
  ts_utc          TIMESTAMPTZ NOT NULL,
  title           TEXT,
  summary         TEXT,
  source          TEXT,                       -- CoinDesk / Cointelegraph / ...
  url             TEXT,
  currencies      TEXT,                       -- CSV of tagged symbols
  sentiment       TEXT,                       -- POSITIVE / NEGATIVE / NEUTRAL
  sentiment_score DOUBLE PRECISION
);
CREATE INDEX idx_news_ts ON news (ts_utc);

CREATE TABLE onchain (
  ts_utc  TIMESTAMPTZ NOT NULL,
  symbol  TEXT        NOT NULL,
  metric  TEXT        NOT NULL,
  value   DOUBLE PRECISION,
  source  TEXT,                               -- blockchain_com / etherscan
  PRIMARY KEY (ts_utc, symbol, metric)
);

CREATE TABLE fundamentals (
  ts_utc                    TIMESTAMPTZ NOT NULL,
  symbol                    TEXT        NOT NULL,
  price_change_pct_24h      DOUBLE PRECISION,
  price_change_pct_7d       DOUBLE PRECISION,
  price_change_pct_30d      DOUBLE PRECISION,
  total_volume_usd          DOUBLE PRECISION,
  market_cap_change_pct_24h DOUBLE PRECISION,
  reddit_subscribers        INTEGER,
  reddit_active_48h         INTEGER,
  reddit_avg_posts_48h      DOUBLE PRECISION,
  twitter_followers         INTEGER,
  github_commit_count_4w    INTEGER,
  github_prs_merged         INTEGER,
  github_code_additions_4w  INTEGER,
  github_code_deletions_4w  INTEGER,
  PRIMARY KEY (ts_utc, symbol)
);

CREATE TABLE predictions (
  ts_utc        TIMESTAMPTZ NOT NULL,
  symbol        TEXT        NOT NULL,
  timeframe     TEXT        NOT NULL,
  pred_class    TEXT,                          -- UP / DOWN / FLAT
  prob_up       DOUBLE PRECISION,
  prob_down     DOUBLE PRECISION,
  prob_flat     DOUBLE PRECISION,
  model_version TEXT,
  created_at    TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (ts_utc, symbol, timeframe)
);

CREATE TABLE prediction_drivers (
  ts_utc        TIMESTAMPTZ NOT NULL,
  symbol        TEXT        NOT NULL,
  timeframe     TEXT        NOT NULL,
  rank          INTEGER     NOT NULL,          -- 1..3 (top SHAP drivers)
  feature_name  TEXT,
  feature_value DOUBLE PRECISION,
  shap_value    DOUBLE PRECISION,
  PRIMARY KEY (ts_utc, symbol, timeframe, rank)
);

-- ===================== JAVA-OWNED (application + API service) ================

CREATE TABLE account_state (
  ts_utc           TIMESTAMPTZ PRIMARY KEY,
  cash_usd         DOUBLE PRECISION,
  total_equity_usd DOUBLE PRECISION
);

CREATE TABLE positions (
  symbol          TEXT PRIMARY KEY,
  size            DOUBLE PRECISION,
  avg_entry_price DOUBLE PRECISION,
  opened_at       TIMESTAMPTZ
);

CREATE TABLE trades (
  id           TEXT PRIMARY KEY,
  ts_utc       TIMESTAMPTZ,
  symbol       TEXT,
  side         TEXT,                           -- BUY / SELL
  quantity     DOUBLE PRECISION,
  price        DOUBLE PRECISION,
  fees         DOUBLE PRECISION,
  realized_pnl DOUBLE PRECISION,
  notes        TEXT
);

CREATE TABLE orders (
  id           TEXT PRIMARY KEY,
  ts_submitted TIMESTAMPTZ,
  ts_filled    TIMESTAMPTZ,
  symbol       TEXT,
  side         TEXT,                           -- BUY / SELL
  type         TEXT,                           -- MARKET / LIMIT
  quantity     DOUBLE PRECISION,
  limit_price  DOUBLE PRECISION,
  status       TEXT,                           -- PENDING / FILLED / CANCELLED
  filled_price DOUBLE PRECISION,
  fees         DOUBLE PRECISION
);

-- Spring AI manages its own pgvector table (default name: vector_store),
-- created automatically when spring.ai.vectorstore.pgvector.initialize-schema=true.
-- Do NOT create it by hand here.
```

---

## 6. Asset universe and data sources

**Assets (10):** BTC, ETH, SOL, BNB, XRP, ADA, AVAX, DOT, LINK, MATIC/POL
(MATIC was rebranded POL in late 2024 — try `MATIC/USDT`, fall back to `POL/USDT`, log which resolved.)
**Timeframes:** 1h and 4h for signals; 1d for backtest. **History:** ~2 years.

All sources are public and free (the 2026 API landscape forced this — no Glassnode, no CryptoPanic). If a source goes paid or down: **log and skip — never crash the pipeline.**

| Source | Used for | Auth |
|---|---|---|
| Binance public API | OHLCV | none |
| CoinGecko Demo | market cap/supply + community + developer + market data (all 10) | free key, 10k/mo, 30/min |
| RSS (CoinDesk, Cointelegraph, Decrypt, The Block, Bitcoin Magazine) | news, 180d rolling | none |
| Blockchain.com Charts | BTC on-chain | none |
| Etherscan | ETH on-chain | free key, 5/sec, 100k/day |
| Curated KB | coin mechanism/tokenomics markdown (authored) | — |

RSS feed URLs:
- CoinDesk: `https://www.coindesk.com/arc/outboundfeeds/rss/`
- Cointelegraph: `https://cointelegraph.com/rss`
- Decrypt: `https://decrypt.co/feed`
- The Block: `https://www.theblock.co/rss.xml`
- Bitcoin Magazine: `https://bitcoinmagazine.com/.rss/full/`

CoinGecko IDs: `bitcoin, ethereum, solana, binancecoin, ripple, cardano, avalanche-2, polkadot, chainlink, polygon-ecosystem-token` (fallback `matic-network`).

---

## 7. Build roadmap — 7 stages, 3 phases

| Stage | Phase | Container(s) | Deliverable |
|---|---|---|---|
| 1 | A — data+ML | db, ml | Monorepo + docker-compose + Postgres/pgvector + schema + **all ingestion** into Postgres |
| 2 | A — data+ML | ml | ML service: features + XGBoost + calibration + SHAP → `predictions` + `prediction_drivers`, run as batch worker |
| 3 | B — backend | backend | Spring Boot foundation + REST API over the data + **TA verdict (ta4j)** |
| 4 | B — backend | backend | **RAG** (Spring AI + pgvector): index from relational tables + KB, retrieve, generate with citations, chat endpoint |
| 5 | B — backend | backend | **Paper-trading engine** + **Analyst aggregator** (fuse ML+TA+FA+News) + endpoints |
| 6 | C — frontend | frontend | **React app** — Markets, Signals, Analyst, Chat, Paper Trades, Performance — wired to REST |
| 7 | D — polish | all | Demo mode, README, model/RAG/analyst cards, final Docker hardening, `v1.0` tag |

Each stage closes with a `STATE.md` update and a git tag `stage-N-done`.

---

## 8. Working method (per stage)

1. Open a fresh Claude Code session in the project root.
2. First message: **"Read PROJECT.md and STATE.md before anything else."** (For Stage 1, STATE.md does not exist yet — it gets created in Stage 1.)
3. Paste the stage prompt file verbatim.
4. Let it run; answer only essential clarifying questions.
5. Verify the stage's **Definition of done** checklist.
6. Let it update `STATE.md`, then `git tag stage-N-done && git push --tags`.

`STATE.md` is the living handoff between sessions — keep it accurate, with concrete numbers (row counts, F1, recall@k, Sharpe).

---

## 9. Honest expectations and hard rules

| Layer | Minimum target |
|---|---|
| ML 3-class macro F1 | ≥ 0.40 on test (random = 0.33) |
| ML macro ROC-AUC | 0.55–0.62 (above 0.65 → check for leakage) |
| RAG recall@8 | ≥ 0.75 overall, ≥ 0.70 per category |
| RAG citation rate | 100% (refuse if context doesn't cover the question) |
| Paper-trading Sharpe | > 0 (stretch > 0.8) |

**Hard rules (never break):**
- No real-money trading. Ever. Paper only.
- Crypto only. No forex/stocks. No shorts, no leverage.
- The Analyst summary may only synthesise facts present in its four input objects — a hallucination guard validates every numerical claim; on failure, fall back to a deterministic template.
- Persistent disclaimer on every page: *decision-support, not financial advice.*
- No single point of failure in data: multi-source by design; log-and-skip on any source failure.

---