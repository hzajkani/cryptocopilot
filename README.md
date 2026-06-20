# CryptoCopilot — applied GenAI / RAG engineering

[![CI](https://github.com/hzajkani/cryptocopilot/actions/workflows/ci.yml/badge.svg)](https://github.com/hzajkani/cryptocopilot/actions/workflows/ci.yml)
&nbsp;![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.8-6db33f)
&nbsp;![pgvector](https://img.shields.io/badge/pgvector-768--dim%20HNSW-336791)
&nbsp;![paper-only](https://img.shields.io/badge/trading-paper--only-blue)
&nbsp;![status](https://img.shields.io/badge/release-v1.0-success)

CryptoCopilot is an **AI-engineering project that happens to use crypto market data as its domain.**
Its centerpiece is a production **retrieval-augmented generation (RAG)** pipeline built on
**Spring AI 1.0.8 + pgvector**: a corpus indexer, a recency-aware retriever, and a strictly-grounded
generator that answers **only** from indexed sources, **cites every claim `[N]`**, and **refuses
out-of-corpus questions** with a fixed phrase. Around it sit a **numeric hallucination guard** (the
LLM may *phrase* an opinion but never *invent a number*), **explainable ML** (calibrated XGBoost +
SHAP drivers), and an **LLM provider that is swappable at runtime — local Ollama ↔ OpenAI — with no
code changes.**

Crypto is the **chosen domain used to exercise this stack — the test bed, not the product.** Ten
coins, five free public data sources, and a paper-trading sandbox give the RAG / ML / Analyst layers
something real and messy to reason over. The engineering is the point — strict grounding, honest
evaluation, a clean polyglot boundary — and the trading is the playground. The numbers below are
**honest, not inflated**: an ML macro-F1 of 0.375 is presented as the deliberate, data-limited result
it is (see [Honest scope](#honest-scope)).

> ⚠️ **Educational / analytics demo — not financial advice.**
> Paper trading only — no real money, ever. A persistent *decision-support, not financial advice*
> disclaimer is rendered on every page.

---

## GenAI / RAG engineering — the centerpiece

A three-stage pipeline (**indexer → retriever → generator**) on **Spring AI 1.0.8**, package
`com.cryptocopilot.rag`. The vector store is **pgvector**, created and owned by Spring AI — **HNSW
index, cosine distance, `vector(768)`** — never hand-rolled. Three interview-ready cards document the
intelligence layer: **[RAG card](docs/RAG_CARD.md)** · **[Model card](docs/MODEL_CARD.md)** ·
**[Analyst card](docs/ANALYST_CARD.md)**.

### 1. Indexer — what gets embedded

`CorpusIndexer` does an **idempotent clear-and-rebuild** (deterministic UUID ids), one writer per
`source_type`, embedding straight out of the relational tables + a curated Knowledge Base:

| `source_type` | one chunk per | content | live count |
|---|---|---|---|
| **news** | `news` row | `title` + `summary` (+ symbols / source / url / sentiment / ts) | 124 |
| **onchain** | `(symbol, ISO-week)` | weekly **mean** synthesis of the daily metrics | 53 |
| **fundamental** | coin | synthesis of the latest `fundamentals` snapshot (null/zero fields omitted) | 10 |
| **kb** | `##` section | curated mechanism / tokenomics markdown — 7 sections × 10 coins (ships in the jar) | 70 |

→ **257 chunks**, embedded in ~4 s.

### 2. Retriever — `k = 8`, recency-aware

A rule-based `QueryClassifier` routes each question to a `source_type` (`kb` / `news` / `onchain` /
`fundamental` / `all`; **classifier accuracy 1.00** on the eval). The retriever runs a
`similaritySearch` filtered by `source_type` (+ optional `symbol`), oversamples, then **recency
re-ranks news + on-chain only** (mechanism facts don't age):

```
score = 0.7 · similarity + 0.3 · exp(−ageDays / 14)
```

KB and fundamental chunks rank by similarity alone. Returns numbered chunks `[1..k]`.

### 3. Generator — grounding & refusal are *deterministic*, not left to the LLM

The generator gives actionable, signal-based views, but every judgement stays tied to the cited
context. The system prompt forbids ungrounded claims, and **two deterministic guards enforce it
regardless of what the model emits**:

1. **Empty retrieval** → refuse *before any LLM call*.
2. **Answer with no verifiable `[N]` citation** → treated as ungrounded and replaced with the
   refusal.

The fixed refusal phrase is: *"The available sources do not answer this question."* → **citation rate
is 100 % by construction** (PROJECT.md §9).

### The Analyst's hallucination guard

The Analyst opinion is computed **deterministically** — a −2..+2 fusion of ML + technical + funda­
mental + news into a direction / conviction / agreement score. The LLM **only phrases the summary;
it may never invent a number.** A numeric guard (`isGrounded`) validates every number in the
generated text against the deterministic inputs — on any invented number, LLM error, or empty reply
it falls back to a **deterministic template**. So `/api/analyst` works even with the LLM offline.

### Evaluation — the real numbers

`evals/retrieval_eval.yaml` — 20 questions (8 news / 8 mechanism / 4 fundamental) authored against
the **actual** corpus. `recall@8` = fraction with ≥ 1 of the top-8 chunks matching the expected
`source_type` + `symbol` + a keyword. Runner: `RAG_LIVE=1 mvn -Dtest=RagLiveIT test` →
`reports/retrieval_eval.md`.

| category | n | recall@8 | gate (PROJECT.md §9) |
|---|---|---|---|
| news | 8 | 0.88 | per-category ≥ 0.70 ✓ |
| mechanism | 8 | 0.88 | ✓ |
| fundamental | 4 | 1.00 | ✓ |
| **overall** | 20 | **0.90** | ≥ 0.75 ✓ |

Plus **citation rate 100 %**, **classifier accuracy 1.00**. (News recall is corpus-dependent — only
~124 rows over a ~4-day window — and rises as the `ml` scheduler ingests more.)

### Swappable LLM provider — no code changes

The **chat + Analyst generation model is toggled at runtime from the UI sidebar**:

- **Local Ollama `llama3.2:3b`** — the default, free, **≈ €0**, no API key
  ([`docs/OLLAMA_SETUP.md`](docs/OLLAMA_SETUP.md)).
- **OpenAI `gpt-4o-mini`** — active when `OPENAI_API_KEY` is set; an OpenAI request with no key
  **transparently falls back to Ollama**, and every response **reports the provider actually used**.

The choice is persisted app-wide. **Embeddings stay on Ollama `nomic-embed-text` (768-dim) in both
modes**, so flipping the chat provider **never triggers a reindex**. A full-OpenAI embedding path
(`text-embedding-3-small`, 1536-dim) is wired and documented as a config-only switch (`+` reindex)
for anyone who wants it. Both paths are documented because the project is meant to run **on a free
local model out of the box**, while still proving the cloud path.

> ▶ **The cited-answer chat** is the screenshot that best shows grounding in action. It needs a live
> interaction (ask → grounded, cited answer), so it's a five-minute manual capture — see
> [`docs/img/README.md`](docs/img/README.md). Drop-ins: `docs/img/chat.png` (still) /
> `docs/img/chat-demo.gif` (a 10–20 s clip).

## Screenshots

Real captures of the running app (2026-06-06). The cited-answer **Researcher chat** is the one
documented manual step above.

| Markets | Signals | Analyst |
|---|---|---|
| ![Markets](docs/img/markets.png) | ![Signals](docs/img/signals.png) | ![Analyst](docs/img/analyst.png) |

| Performance | ML Pipeline |
|---|---|
| ![Performance](docs/img/performance.png) | ![ML Pipeline](docs/img/ml-pipeline.png) |

---

## Architecture — five containers, one shared database

Beyond the AI layer, CryptoCopilot is a **polyglot system**: a Python data + ML service, a
Java/Spring Boot application service, and a React frontend — Docker containers around one shared
**Postgres + pgvector** database.

```
                        ┌──────────────────────────────┐
   browser  ─────────►  │  frontend  (React + nginx)   │
                        └───────────────┬──────────────┘
                                        │ REST / JSON  (same-origin; nginx proxies /api)
                                        ▼
                        ┌──────────────────────────────┐
                        │  backend  (Spring Boot)      │   ◄── MODULAR MONOLITH
                        │  ta4j · Spring AI · trading  │       (ONE container, many
                        │  analyst · REST API          │        internal modules)
                        └──────┬──────────────────┬────┘
                               │ JDBC             │ HTTP  /api/ml/* → ingest·train·predict
                               ▼                  ▼
       ┌──────────────►  ┌─────────────────┐   ┌──────────────────────────────┐
       │ (writes preds,  │  db  (Postgres  │   │  ml-api  (Python · FastAPI)  │ ◄─ ON-DEMAND
       │  raw data)      │  16 + pgvector) │   │  triggers the same jobs      │    TRIGGERS
       │                 └─────────────────┘   └───────────────┬──────────────┘
       │                          ▲                            │ (same code,
┌──────┴───────────────────────┐  │ JDBC reads                 │  shared model dir)
│  ml  (Python)                │  │                            ▼
│  ingestion · XGBoost · SHAP  │  └──────────────────  writes predictions / raw data
└──────────────────────────────┘   ◄── BATCH WORKER: wakes on a schedule, writes, sleeps.
```

The **database is the polyglot boundary**: Python writes its tables, Java reads them — no RPC, no
shared model files. Each table has exactly one writer. The backend is a **modular monolith**, not
microservices. The ML pipeline runs two ways over the *same* code: the **`ml`** container is a
scheduled **batch worker**, and the **`ml-api`** container puts a thin **FastAPI** in front of the
same ingest/train/predict jobs so they can be launched on demand from the backend (`/api/ml/*`) and
the **ML Pipeline** page in the UI. The full rationale is one page:
**[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)**.

## Quickstart — `make demo`

**Prerequisites:** Docker + Docker Compose. Two free API keys:
[CoinGecko Demo](https://www.coingecko.com/en/api) and [Etherscan](https://etherscan.io/apis).
**Optional:** a local [Ollama](docs/OLLAMA_SETUP.md) for the cited chat + LLM-phrased Analyst
summaries (everything still works without it — see [below](#ollama-up-or-down)).

```bash
# 1. Configure secrets (cp -n will NOT overwrite an existing .env)
cp -n .env.example .env
$EDITOR .env          # add COINGECKO_API_KEY + ETHERSCAN_API_KEY
                      # (optional: OPENAI_API_KEY to enable the OpenAI toggle)

# 2. One command: up + ingest + train + predict + reindex + seed a few paper trades
make demo
```

`make demo` brings up `db` + `backend` + `frontend` (waiting on healthchecks), ingests the five
public data sources, trains the calibrated model, writes predictions + SHAP drivers, builds the RAG
index, and seeds a handful of illustrative paper trades — so **Markets, Signals, Analyst, Chat, Paper
Trades and Performance are all non-empty** on first look.

> ⏱️ **One-time cost:** the ingest + train steps take a few minutes (a network crawl + a model fit).
> Subsequent `docker compose up` is instant. Run `make help` to see all targets.

When it finishes:

| | URL |
|---|---|
| **Frontend** | <http://localhost:3000> |
| **API docs (Swagger UI)** | <http://localhost:8080/swagger-ui.html> |
| **Health** | <http://localhost:8080/actuator/health> |

### Ollama up or down

The Researcher chat and the LLM-phrased Analyst summary use a **free local Ollama**
(`llama3.2:3b` + `nomic-embed-text`) by default. The demo works **either way**:

- **Ollama up** → chat returns **cited** answers; the Analyst summary is LLM-phrased (behind the
  numeric hallucination guard); `make demo` builds the RAG index.
- **Ollama down** → chat **refuses cleanly** (it can't embed the query); the Analyst falls back to a
  **deterministic template** summary; the RAG-index step is skipped (not fatal). Markets, Signals,
  Analyst, Paper Trades and Performance are fully populated regardless.

To enable the rich path later: install Ollama + pull the two models ([`docs/OLLAMA_SETUP.md`](docs/OLLAMA_SETUP.md)),
then `make reindex`. To answer with OpenAI instead, set `OPENAI_API_KEY` and flip the **sidebar
toggle** — embeddings stay local, so no reindex is needed.

## Manual setup (the individual commands)

`make demo` is just this sequence — run the pieces by hand if you prefer:

```bash
cp -n .env.example .env && $EDITOR .env        # secrets
docker compose up -d --build --wait db backend frontend ml-api

docker compose run --rm ml python -m ml.ingest.run_all   # FETCH  → ohlcv/market_meta/news/onchain/fundamentals
docker compose run --rm ml python -m ml.train            # TRAIN  → ml/models/v1/ (+ reports)
docker compose run --rm ml python -m ml.predict          # PREDICT→ predictions + prediction_drivers

curl -X POST localhost:8080/api/rag/reindex              # build the pgvector index (needs Ollama)
bash scripts/seed_demo_trades.sh                          # seed a few illustrative paper trades
```

The same three jobs can be launched **on demand** instead of by `compose run` — from the
**ML Pipeline** page in the UI (a button each for ingest / train / predict, with live status and
results), or via the backend proxy. They run as background jobs; the `POST` returns a job to poll:

```bash
JOB=$(curl -fsS -X POST localhost:8080/api/ml/ingest | python -c 'import sys,json;print(json.load(sys.stdin)["id"])')
curl -fsS localhost:8080/api/ml/jobs/$JOB                 # poll: state → running | success | error
curl -fsS localhost:8080/api/ml/status                    # row counts · model card · latest predictions
# Swagger for the Python service itself: http://localhost:8000/docs
```

The `ml` container's default command is an **APScheduler** worker (daily ingest + a predict every
4h); the sibling **`ml-api`** container serves the on-demand FastAPI over the *same* code. Training
stays off the schedule. `ml/models/`, `ml/data/`, `ml/reports/` are **bind-mounted** and shared by
both, so a model trained by either path is visible to the next `predict` and inspectable on the host.

## Honest scope

The point is production-grade polyglot **engineering**, not beating the market. These results are
presented as the deliberate, documented outcomes they are (PROJECT.md §9):

| Layer | Result | Note |
|---|---|---|
| **RAG** retrieval | **recall@8 = 0.90** · 100 % citation rate | strict grounding; cited, signal-based views; refuses out-of-corpus with a fixed phrase. ≈ €0 (local Ollama). |
| **ML** 3-class direction | macro **F1 0.375** · **AUC 0.578** · **Brier 0.606** | F1 is below the 0.40 stretch gate — a **data-limited** ceiling (~2y of OHLCV), investigated (not leakage, not the decision rule). AUC + Brier pass. |
| **Paper-trading** backtest | default **0 trades**; TA proxy **Sharpe −1.20** | the default needs a historical ML series that doesn't exist (single-snapshot ML); the TA proxy is an honest fee-and-regime-driven ≤ 0. |

**Hard rules (never broken):** no real money, ever — paper only. Crypto only; no shorts, no leverage.
The Analyst may only synthesise facts present in its four inputs (a hallucination guard falls back to
a deterministic template). A persistent **decision-support, not financial advice** disclaimer on every
page. Multi-source data by design; log-and-skip on any source failure.

## Data sources

All sources are public and free. If a source goes paid or down, the pipeline **logs and skips** it —
it never crashes (PROJECT.md §9).

| Source | Used for | Auth |
|---|---|---|
| Binance public API | OHLCV (1h / 4h / 1d, ~2 years) | none |
| CoinGecko Demo | market cap/supply + community + developer + market data (all 10 coins) | free key, 10k/mo, 30/min |
| RSS (CoinDesk, Cointelegraph, Decrypt, The Block, Bitcoin Magazine) | news, 180-day rolling window | none |
| Blockchain.com Charts | BTC on-chain | none |
| Etherscan | ETH on-chain | free key, 5/sec, 100k/day |
| Curated KB | coin mechanism / tokenomics markdown (Stage 4) | — |

**Assets (10):** BTC, ETH, SOL, BNB, XRP, ADA, AVAX, DOT, LINK, MATIC/POL. (MATIC was rebranded POL
in late 2024; the OHLCV loader stitches `MATIC/USDT` and `POL/USDT` under the `MATIC` symbol.)

## Table ownership — exactly one writer per table

The shared DB stays clean by **strict table ownership** (PROJECT.md §3). Java never writes Python's
tables; Python never writes Java's. The frontend holds no business logic — it only reads backend REST.

| Container | Owns / writes | Reads |
|---|---|---|
| **ml** (Python) | `ohlcv`, `market_meta`, `news`, `onchain`, `fundamentals`, `predictions`, `prediction_drivers` | its own tables |
| **backend** (Java) | `account_state`, `positions`, `trades`, `orders`, the Spring-AI `vector_store` | all of ml's tables (read-only) |
| **frontend** (React) | — | backend REST only |

## The build story — 7 stages, one tag each

Built in 7 stages across 3 phases; each closes with a `STATE.md` update and a git tag. The
[`PROJECT.md`](PROJECT.md) frozen spec and [`STATE.md`](STATE.md) living handoff tell the full story.

| Stage | Phase | Deliverable | Tag |
|---|---|---|---|
| 1 | A · data+ML | Monorepo + compose + Postgres/pgvector + schema + all ingestion | [`stage-1-done`](https://github.com/hzajkani/cryptocopilot/tree/stage-1-done) |
| 2 | A · data+ML | XGBoost + isotonic calibration + SHAP → `predictions` (batch worker) | [`stage-2-done`](https://github.com/hzajkani/cryptocopilot/tree/stage-2-done) |
| 3 | B · backend | Spring Boot REST over the data + ta4j TA verdict | [`stage-3-done`](https://github.com/hzajkani/cryptocopilot/tree/stage-3-done) |
| 4 | B · backend | RAG (Spring AI + pgvector): indexed corpus, cited grounded chat | [`stage-4-done`](https://github.com/hzajkani/cryptocopilot/tree/stage-4-done) |
| 5 | B · backend | Paper-trading engine + deterministic Analyst aggregator | [`stage-5-done`](https://github.com/hzajkani/cryptocopilot/tree/stage-5-done) |
| 6 | C · frontend | React app (Markets, Signals, Analyst, Chat, Paper Trades, Performance) | [`stage-6-done`](https://github.com/hzajkani/cryptocopilot/tree/stage-6-done) |
| 7 | D · polish | Demo mode, README, cards, Docker hardening, CI, **v1.0** | [`v1.0`](https://github.com/hzajkani/cryptocopilot/releases/tag/v1.0) |

## Tests

```bash
# ml (Python) — offline suite (skip the Binance network test)
docker compose run --rm ml pytest -q -m "not network"

# backend (Java) — offline suite. The repository slice (OhlcvRepositoryTest) needs the running
# db with ingested data; exclude it when the stack is down:
cd backend && mvn -q test -Dtest='!OhlcvRepositoryTest'   # or plain `mvn test` with `db` up + seeded

# frontend (TypeScript/React)
cd frontend && npm ci && npm run build && npm test

# gated live runs (need the db; RAG also needs a local Ollama):
RAG_LIVE=1      mvn -Dtest=RagLiveIT test        # RAG retrieval eval   → reports/retrieval_eval.md
BACKTEST_LIVE=1 mvn -Dtest=BacktestLiveIT test   # real-window backtest → reports/backtest_strategy_v1.md
```

CI (`.github/workflows/ci.yml`) runs the three offline suites on every push (the badge above).

## Repo layout

```
cryptocopilot/
├── PROJECT.md            # frozen spec (do not modify during the build)
├── STATE.md              # living handoff between stages — current status + row counts
├── README.md             # this file
├── Makefile              # `make demo`, plus up/down/ingest/train/predict/reindex/seed/test
├── docker-compose.yml    # db + ml + ml-api + backend + frontend (healthchecks + depends_on)
├── scripts/              # demo.sh (one-command demo) + seed_demo_trades.sh
├── docs/                 # ARCHITECTURE.md + MODEL_CARD / RAG_CARD / ANALYST_CARD + OLLAMA_SETUP + LINKEDIN_POST + img/
├── db/init.sql           # the shared schema contract (PROJECT.md §5)
├── reports/              # retrieval_eval.md, backtest_strategy_v1.md
├── ml/                   # Python data + ML service (ingest, features, modelling, predict, scheduler, FastAPI)
└── backend/             # Java/Spring Boot service (data, ta, rag, analyst, trading, web)
    └── src/main/java/com/cryptocopilot/
        ├── controller/ service/ entity/ repository/ dto/   # market data + ta4j TA verdict (Stage 3)
        ├── rag/          # the Researcher: indexer, retriever, grounded generator (Stage 4)
        ├── analyst/      # FundamentalSnapshot + deterministic Analyst + guarded summary (Stage 5)
        ├── trading/      # long-only paper-trading engine + backtest (Stage 5)
        └── web/          # global exception handler → clean JSON errors (Stage 7)
```

---

*CryptoCopilot is an engineering portfolio piece. **Educational / analytics demo — not financial
advice; paper trading only.***
