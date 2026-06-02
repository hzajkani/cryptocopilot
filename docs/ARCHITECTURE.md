# Architecture — CryptoCopilot (one page)

The doc to walk an interviewer through. Full spec: [`PROJECT.md`](../PROJECT.md); live status +
numbers: [`STATE.md`](../STATE.md); the intelligence layer: [model](MODEL_CARD.md) ·
[RAG](RAG_CARD.md) · [Analyst](ANALYST_CARD.md) cards.

## The shape — four containers, one shared database

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

## The polyglot boundary is the database

Python and Java cooperate through **one Postgres schema** (`db/init.sql`) — **no RPC, no shared
`.pkl`, no `xgboost4j`**. Python writes numbers; Java reads numbers. The Java backend never loads a
Python model — it `SELECT`s probabilities and SHAP drivers from a table. The contract is the DDL, and
Hibernate `ddl-auto: validate` fails the backend on boot if any entity drifts from it.

**Consequences (all deliberate):**
- **Decoupled availability** — even with the `ml` container off, the app serves the last predictions.
- **Each language stays in its lane** — ML where Python is strongest (XGBoost, isotonic calibration,
  SHAP); a production fintech backend where Java/Spring is strongest (ta4j, Spring AI, JPA, REST).
- **No cross-language feature sharing** — feature engineering is Python-internal parquet; Java
  independently recomputes Ichimoku/RSI with ta4j. The small duplication keeps the boundary clean.

## Table ownership — exactly one writer per table

| Container | Owns / writes | Reads |
|---|---|---|
| **ml** (Python) | `ohlcv`, `market_meta`, `news`, `onchain`, `fundamentals`, `predictions`, `prediction_drivers` | its own tables |
| **backend** (Java) | `account_state`, `positions`, `trades`, `orders`, the Spring-AI `vector_store` | all of ml's tables (read-only) |
| **frontend** (React) | — | backend REST only |

Java never writes Python's tables; Python never writes Java's. The frontend holds **no business
logic** — every signal, score, fill and summary is rendered exactly as the backend returns it.

## Why a modular monolith, not microservices

Four containers is correct — they are **different runtimes** (Python, JVM, nginx, Postgres). But the
backend is **one** Spring Boot app with clean internal modules (`data`, `ta`, `rag`, `trading`,
`analyst`), **not** five services. Microservices solve organisational and independent-scaling
problems a single-user project does not have; splitting would only add service discovery,
inter-service calls and distributed transactions — operational pain for zero benefit here. The module
boundaries are real (packages + ownership); the deployment boundary is one process.

## Why ML is a batch job, not a service

24-hour direction changes slowly, so the `ml` container is an **APScheduler worker**: it wakes
(daily ingest + a predict every 4h), pulls fresh data, writes probabilities + SHAP drivers, and
sleeps. Training is manual/occasional (`docker compose run ml python -m ml.train`). No web server, no
request path, no model-serving latency — the backend just reads the freshest row.

## Per-stage decisions & honest results

| Stage | Decision | Honest result |
|---|---|---|
| **1** data | Multi-source ingestion, **log-and-skip** on any source failure (no single point of failure). | 231k rows. Binance gave ~2y (not ~3); CoinGecko caps history at 365d — both documented. |
| **2** ML | One global XGBoost + Optuna + **isotonic calibration on val** + SHAP; val-tuned weighted-argmax decision rule. | macro **F1 0.375** (data-limited, < 0.40 gate), **AUC 0.578** ✓, **Brier 0.606** ✓. Investigated: not leakage, not the decision rule — data volume. Accepted, not dressed up. |
| **3** backend | Spring Boot reads ml's tables read-only; deterministic **ta4j** TA verdict recomputed from raw OHLCV. | 4 REST groups + Swagger; `ddl-auto: validate` clean; TA golden-tested. |
| **4** RAG | **Spring AI + pgvector**; strict grounding with deterministic refusal guards; **local Ollama** (€0) after the OpenAI key hit `429 insufficient_quota`. | recall@8 **0.90**; 100% citation rate; €0 cost. Switching back to OpenAI is config-only. |
| **5** backend | Long-only **paper-trading** engine (single-sourced fill model) + deterministic **Analyst** fusion with a hallucination guard + two-tier health. | Engine + metrics tested; default backtest **0 trades** (single-snapshot ML), TA proxy **Sharpe −1.20** — an honest, fee-and-regime-driven ≤0 the DoD allows. |
| **6** frontend | Thin typed React client; **nginx reverse-proxies `/api`** → no CORS; sparsity rendered as valid states. | 6 routes wired to the verified contract; type-check + render tests green. |
| **7** polish | `make demo`, README, the three cards, this doc, Docker healthchecks + non-root backend + global exception handler, CI. | A clean clone → `make demo` → a populated app, Ollama up *or* down. |

## Hard rules (never broken)

No real-money trading — paper only. Crypto only; no shorts, no leverage. The Analyst summary may only
synthesise facts in its four inputs (hallucination guard → deterministic fallback). A persistent
**decision-support, not financial advice** disclaimer on every page. Multi-source data by design;
log-and-skip on any source failure.

## What would come next

More OHLCV history (the lever most likely to clear ML F1 0.40); a hosted LLM for richer chat/summaries
(one config flip); per-user auth + accounts; a historical prediction series so the ML-confirmed
backtest has a real signal to trade bar-by-bar.
