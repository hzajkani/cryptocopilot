# Stage 7 — Polish: demo mode, README, cards, Docker hardening, v1.0

> **Phase D of 3 (polish) — final stage.** This is Stage 7 of 7.
>
> **How to use this file:** New Claude Code session in the project root. First message: *"Read PROJECT.md and STATE.md before anything else."* Then paste everything below the line.

---

# CryptoCopilot — Stage 7: make it demo-able, documented, and hardened, then tag v1.0

Read `PROJECT.md` and `STATE.md`. All four containers work (`db`, `ml`, `backend`, `frontend`); the full flow runs end-to-end. This stage adds nothing functional to the product logic — it makes the project **reviewable in five minutes by someone who has never seen it** (a recruiter, an interviewer), and hardens the Docker setup. No schema changes, no new trading behaviour, no new model.

## Reality to respect (don't undo it)

- **Honest scope stays honest** (PROJECT.md §9): ML macro F1 ≈ 0.375 (data-limited, documented), AUC 0.578; the default backtest makes 0 trades (single-snapshot ML); the TA-only proxy is negative. Do **not** dress these up — the README and cards present them as deliberate, honest results. That honesty is itself a portfolio signal.
- **Chat + Analyst summaries need a local Ollama** (`llama3.2:3b` + `nomic-embed-text`); with it down, the backend falls back deterministically. Demo mode must work **with Ollama both up and down** (the deterministic paths already do).
- **The polyglot boundary and table ownership are frozen** (PROJECT.md §3). Polish does not cross them.

## Tasks

### 1. Demo mode — "clone → up → see a populated app"

Goal: a reviewer runs a short, documented sequence and gets a **populated** UI without waiting on a from-scratch crawl/train. Provide a one-command path (a `Makefile` target and/or a `scripts/demo.sh`) that runs, in order, against a fresh stack:

```
docker compose up -d db backend frontend
docker compose run --rm ml python -m ml.ingest.run_all   # data
docker compose run --rm ml python -m ml.train            # model -> models/v1/
docker compose run --rm ml python -m ml.predict          # predictions + drivers
curl -X POST localhost:8080/api/rag/reindex              # build the pgvector index
# optionally seed a few illustrative paper trades so Performance isn't empty:
scripts/seed_demo_trades.sh                              # 3–4 MARKET orders via POST /api/orders
```

- Wrap it as `make demo` (document the one-time ~minutes cost of ingest/train).
- `scripts/seed_demo_trades.sh` places a handful of paper orders so **Markets, Signals, Analyst, Chat, Paper Trades, and Performance are all non-empty** for a first look. Keep it honest — it's seeded demo activity, labelled as such.
- Confirm the deterministic paths render with **Ollama off** (chat shows the refusal, Analyst shows template summaries) and the richer paths render with **Ollama on**. Document both in the README.

### 2. README (top-level, the front door)

Rewrite `README.md` into a portfolio-grade front page:
- One-paragraph pitch + the **architecture diagram** (the 4-container ASCII from PROJECT.md §2).
- **Quickstart**: prerequisites (Docker, free CoinGecko + Etherscan keys, optional Ollama), the `.env` setup, and the `make demo` flow. A separate "manual" section with the individual commands.
- A **screenshots** section (placeholders `docs/img/*.png` for Markets / Signals / Analyst / Chat / Performance — leave the files as TODO placeholders the owner will fill).
- The **data-sources** table (PROJECT.md §6) and the **table-ownership** table (PROJECT.md §3).
- **Honest scope** + the persistent **disclaimer** (PROJECT.md §9), and links to the three cards (below).
- Per-stage links and the `stage-N-done` tags so the build story is legible.

### 3. The three cards (`docs/`)

Honest, concise, interview-ready:
- **`docs/MODEL_CARD.md`** — refine the Stage-2 card: features (46), target (±2% / 24h), splits (the real 2-year span), metrics (macro F1 0.375 / AUC 0.578 / Brier 0.608), the data-limited explanation, calibration (isotonic on val), the val-tuned weighted-argmax decision rule, and intended-use + limitations.
- **`docs/RAG_CARD.md`** — provider (local Ollama: `llama3.2:3b` chat, `nomic-embed-text` 768-dim), chunking (news / on-chain weekly / fundamental / KB), retrieval (k=8, recency re-rank for news+onchain), the query classifier, recall@8 from the eval (with the corpus-sparsity caveat), the strict-grounding + exact-refusal behaviour, and cost (≈€0 on local Ollama).
- **`docs/ANALYST_CARD.md`** — the deterministic −2..+2 scoring per input, the combine→direction/conviction/agreement rules, the two-tier health (Tier-1 on-chain = BTC only today; Tier-2 CoinGecko within-snapshot), the hallucination guard, the `healthSource` transparency rule, and the disclaimer.

### 4. Docker + repo hardening

- **Healthchecks**: backend → `GET /actuator/health`; frontend → an nginx `200`. `db` already has one. Add sensible `depends_on` conditions so `make demo` ordering is reliable.
- **`.dockerignore`** for `backend/` (`target/`) and `frontend/` (`node_modules/`, `dist/`) to keep build context small and builds fast.
- A **global exception handler** in the backend (`@RestControllerAdvice`) returning clean JSON `{error, message, status}` instead of stack traces; confirm Swagger UI (`/swagger-ui.html`) is reachable and the four feature tags are documented.
- Make sure a **clean clone + `make demo`** works end-to-end on a machine that has only Docker (and, for the rich path, Ollama). Pin image tags already in use; run containers as non-root where it's a one-line change.
- Re-confirm `.env.example` documents every key (`COINGECKO_API_KEY`, `ETHERSCAN_API_KEY`, `OPENAI_API_KEY` (inactive), `OLLAMA_*`).

### 5. Optional but recommended — CI (interview-visible)

A GitHub Actions workflow (`.github/workflows/ci.yml`) that on push: builds the `ml` image + runs `pytest -q -m "not network"`; builds the `backend` + runs `mvn -q test` (the offline suite); builds the `frontend` + runs `tsc --noEmit` and the Vitest tests. A green badge in the README is a strong signal on a public repo. (Skip live/Ollama/network-gated tests in CI.)

### 6. `docs/ARCHITECTURE.md` (one page, for interviews)

A single page: the polyglot boundary (DB as the contract), table ownership, why a modular monolith and not microservices, why ML is a batch job, and the per-stage decisions + honest results. This is the doc to walk an interviewer through.

### 7. STATE.md + Git

Append a final **Stage 7** section + a short "Project complete" note: what's live, the honest metrics, what would come next (more OHLCV history to clear F1 0.40; a hosted LLM; auth). Commit `"Stage 7: demo mode, README, model/RAG/Analyst cards, Docker hardening"`, then tag **`v1.0`** and push tags.

## Definition of done

- A clean clone + documented `make demo` brings up all four containers and yields a **populated** app (Markets, Signals, Analyst, Chat, Paper Trades, Performance all non-empty), with both the Ollama-up and Ollama-down paths documented.
- `README.md` has the pitch, the architecture diagram, the quickstart, the data-source + ownership tables, the honest scope + disclaimer, and links to the three cards.
- `docs/MODEL_CARD.md`, `docs/RAG_CARD.md`, `docs/ANALYST_CARD.md`, `docs/ARCHITECTURE.md` exist and are accurate.
- Backend + frontend healthchecks pass; `.dockerignore`s present; the global exception handler returns clean JSON; Swagger UI loads.
- (If done) CI is green on push.
- All existing tests still pass across the three services. **`v1.0` is tagged.**

## What NOT to do

- Do NOT change the DB schema, the table ownership, or the polyglot boundary (PROJECT.md §3).
- Do NOT add real-money trading, shorts, or leverage; do NOT remove the disclaimer (PROJECT.md §9).
- Do NOT inflate the honest metrics — present F1 0.375 / the 0-trade default backtest as the deliberate, documented results they are.
- Do NOT introduce a new model or retrain to chase a number; this stage is polish, not modelling.
- Do NOT make demo mode depend on a paid API — it must run on the free stack (local Ollama, free data keys).
