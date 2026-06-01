# STATE — CryptoCopilot

> Living handoff between stages. Each stage reads `PROJECT.md` (frozen spec) then this file.

## Current status

**Stage 4 — RAG (Spring AI + pgvector + cited chat): ✅ COMPLETE** (tagged `stage-4-done`)

- Phase B of 3 (Java/Spring backend). Containers live: `db`, `ml`, `backend`.
- The **Researcher** is fully implemented, wired, and **verified live** (`com.cryptocopilot.rag`):
  Spring AI 1.0.8 + pgvector, corpus indexer (news + onchain + fundamental + KB), rule-based query
  classifier, recency-aware retriever, strictly-grounded generator, `POST /api/chat` +
  `GET /api/rag/status` + `POST /api/rag/reindex`, a 10-coin Knowledge Base, and a retrieval eval.
- **Runs on a free LOCAL Ollama** (chat `llama3.2:3b`, embeddings `nomic-embed-text` **768-dim**),
  not a paid API — €0 cost, no API key. (We pivoted to Ollama after the supplied `OPENAI_API_KEY`
  returned `429 insufficient_quota`; OpenAI remains a one-config-flip switch-back.) Setup for any PC:
  **`docs/OLLAMA_SETUP.md`**.
- Backend boots clean on **Spring Boot 3.4.13** (bumped from 3.3.5 for Spring AI); `vector_store`
  (768-dim, HNSW cosine) auto-created and owned by Spring AI; `ddl-auto: validate` still passes.
- **Live DoD met:** reindex → **news 124 · onchain 53 · fundamental 10 · kb 70 (257 chunks)**;
  mechanism chat answers with `[N]` citations from KB; out-of-corpus + trading-advice refused with
  the exact phrases; a zero-news coin refuses cleanly. **Retrieval eval recall@8 = 0.90** (news 0.88,
  mechanism 0.88, fundamental 1.00; classifier accuracy 1.00) — `reports/retrieval_eval.md`.
- **Tests: 39 offline pass (`mvn test`); live `RagLiveIT` 7/7 pass (`RAG_LIVE=1 mvn -Dtest=RagLiveIT test`).**
- `frontend` (Stage 6) is still a placeholder. Next: **Stage 5** (paper-trading engine + Analyst).
- Stage 3 ✅ (`stage-3-done`); Stage 2 ✅ (`stage-2-done`); accepted data-limited macro
  **F1 0.375** / **AUC 0.578** — see those sections below.

> **Stage 2 DoD note:** 2 of 3 metric gates pass — macro **AUC 0.578** ✓ (in the
> spec's honest 0.55–0.62 band) and **Brier 0.608** ✓. Test **macro F1 0.375** is
> short of the ≥0.40 gate. This was investigated against all three levers the DoD
> names (leakage / target window / class balance) and found to be a **genuine
> data-limited ceiling**, not a defect (details below). Accepted by the project
> owner as the honest result; the pipeline is production-grade, tested, and
> writing predictions to Postgres.

---

## Stage 4 — what is done (the Researcher: RAG)

The `backend` gained a strictly-grounded, cited RAG chat over the data already in Postgres (news +
on-chain + fundamentals) plus a curated 10-coin Knowledge Base, using **Spring AI 1.0.8 + pgvector**
with a **free local Ollama** model provider (chat `llama3.2:3b`, embeddings `nomic-embed-text`,
**768-dim**). New package `com.cryptocopilot.rag`. One modular monolith still (PROJECT.md §2); Java
reads Python's tables read-only and writes only the Spring-AI-owned `vector_store`.

> **Provider note:** the brief defaulted to OpenAI (`gpt-4o-mini` + `text-embedding-3-small`,
> 1536-dim). The supplied `OPENAI_API_KEY` authenticated but the account had **no quota
> (`429 insufficient_quota`)**, so we switched the active provider to **local Ollama** — €0, no API
> key, runs on any PC (see `docs/OLLAMA_SETUP.md`). The OpenAI starter is still on the classpath;
> switching back is `spring.ai.model.{chat,embedding}=openai` + `dimensions=1536` + re-index.

**Stack changes:** Spring Boot **3.3.5 → 3.4.13** (Spring AI 1.0.x requires 3.4.x/3.5.x), springdoc
**2.6.0 → 2.8.17**, added `spring-ai-bom:1.0.8` + `spring-ai-starter-model-ollama` (active) +
`spring-ai-starter-model-openai` (inactive, kept) + `spring-ai-starter-vector-store-pgvector`.
Provider chosen via `spring.ai.model.{chat,embedding}=ollama`. `vector_store` (id `uuid`,
`embedding vector(768)`, HNSW `vector_cosine_ops`) is auto-created at boot (`initialize-schema:
true`) — **not** hand-made. The backend container reaches host Ollama at
`host.docker.internal:11434` (wired in `docker-compose.yml`, Linux-safe via `host-gateway`).
Verified: backend boots in ~5s, Hibernate `validate` clean, `vector_store` present at 768-dim.

**Pipeline (all built, unit-tested):**

- **CorpusIndexer** — clear-and-rebuild into pgvector, idempotent via deterministic UUID ids
  (`UUID.nameUUIDFromBytes`); clears its own chunks by `source_type` filter, then `add()`. Sources:
  one `Document` per `news` row (`title\nsummary`, metadata symbol(s)/source/url/sentiment/ts);
  weekly **on-chain** synthesis per `(symbol, ISO-week)` mean; one **fundamental** synthesis per
  coin from the latest snapshot (null/zero fields omitted); **KB** split by `##` section.
- **QueryClassifier** — rule-based → `kb`/`news`/`onchain`/`fundamental`/`all`, priority
  onchain→fundamental→news→kb→all (so "recent on-chain transactions" → onchain, "current
  sentiment" → news, not kb). Deviation: "supply" routes to **KB** (supply schedules live only in
  the KB; `fundamentals` has no supply field).
- **Retriever** — `similaritySearch` with `source_type` (+ optional `symbol`) filter, oversample
  then recency re-rank `0.7*similarity + 0.3*exp(-ageDays/14)` for news/onchain only (KB/fundamental
  by similarity alone); returns numbered chunks `[1..k]`, k=8.
- **Generator** — `ChatClient` (Ollama `llama3.2:3b`, temp 0) behind a small `LlmClient` seam
  (`SpringAiLlmClient`, provider-agnostic, unit-testable). System prompt verbatim from the Stage 4
  brief. **Deterministic guards** so the exact refusal
  phrases never depend on the LLM: trading-advice → refuse before any call; empty retrieval →
  refuse before any call; **answer with no verifiable `[N]` citation → treated as ungrounded and
  replaced with the no-context refusal**. In-memory cache keyed by `(query, chunkIds)`.
- **REST:** `POST /api/chat {query, symbols?}` → `AnswerWithCitations(answer, citations,
  retrievedChunks, latencyMs, queryClassification)`; `GET /api/rag/status`; `POST /api/rag/reindex`.
  Documented in Swagger (tag "Researcher (RAG)").

**Knowledge Base:** `backend/src/main/resources/kb/{btc,eth,sol,bnb,xrp,ada,avax,dot,link,matic}.md`
(ships in the jar). Each has the 7 required `##` sections (Identity, Consensus, Supply schedule,
Use case, Key risks, On-chain footprint, Last updated), 339–425 words, factual mechanism/tokenomics
only — no price targets, nothing forward-looking (PROJECT.md §9). → **70 KB chunks** (10 × 7).

**Corpus reality (sized to the live DB; PROJECT.md Stage 4 §"Reality"):**
- `news` **124 rows** over a ~4-day window (2026-05-27 → 05-31); **73 untagged**, 38 BTC-tagged,
  rest sparse → news-category recall is corpus-dependent and grows with ingestion.
- `onchain` is **BTC-only** (1,084 rows, 3 daily metrics; **no ETH** — etherscan absent in the DB
  despite Stage 1's note) → **53 weekly BTC chunks**. Built generically over whatever symbols exist.
- `fundamentals` was found **empty (0 rows)** at the start of this stage (Stage 1's 10 were lost on
  a volume reset); **restored to 10** via `docker compose run --rm ml python -m
  ml.ingest.coingecko_fundamentals` (twitter_followers null for all; a few coins lack github
  code-add/del — log-and-skip, PROJECT.md §9) → **10 fundamental chunks**.
- **Actual reindex counts (live): news 124 · onchain 53 · fundamental 10 · kb 70 = 257 chunks**
  (embedded via Ollama in ~4s; verified in `vector_store` and via `GET /api/rag/status`).

**Retrieval eval:** `evals/retrieval_eval.yaml` — 20 questions (8 news / 8 mechanism / 4
fundamental), each with `expected_keywords/symbols/source_types`, `max_age_days`,
`expected_query_classification`, authored against the *actual* corpus (real headlines, real
fundamentals values). Runner = `RagLiveIT.retrievalEval` → writes `reports/retrieval_eval.md`.
recall@8 = fraction of questions with ≥1 of the top-8 chunks matching the expected source_type +
symbol + a keyword.

**Live results (Ollama `nomic-embed-text`):** **recall@8 overall 0.90** — **news 0.88** (7/8),
**mechanism 0.88** (7/8), **fundamental 1.00** (4/4); **classifier accuracy 1.00** (20/20); all news
age-gates ≤ 14d. The two misses are retrieval-quality artifacts of the small 768-dim model on
generic phrasing (n8 "Trump … legislation"; m6 "use case for Chainlink") — both above the DoD gates
(mechanism/fundamental ≥ 0.75, overall ≥ 0.70). News recall is corpus-dependent (~124 rows, ~4-day
window) and will rise as the `ml` scheduler ingests more news.

**Tests — 39 offline pass (`mvn test`); 7 live pass (`RAG_LIVE=1 mvn -Dtest=RagLiveIT test`):**
- `rag.QueryClassifierTest` (22) — all 5 classes incl. the tricky precedence cases.
- `rag.GeneratorTest` (6) — advice refusal & empty-retrieval refusal without any LLM call;
  citation extraction; the no-citation → refusal guard; out-of-range `[N]` ignored; response cache.
- `controller.RagControllerTest` (3, `@WebMvcTest`, mocked `RagService`) — `/api/chat`,
  `/api/rag/status`, `/api/rag/reindex` shapes.
- Existing 8 still green; `SignalsControllerTest` migrated `@MockBean` → `@MockitoBean` (Boot 3.4).
- `rag.RagLiveIT` (7, `@SpringBootTest`, gated `@EnabledIfEnvironmentVariable RAG_LIVE`) — reindex
  counts, mechanism retrieves a SOL KB chunk, **cited** mechanism chat, out-of-corpus and advice
  exact refusals, zero-news (LINK) clean refusal, and the recall eval. Named `*IT`, so it is **not**
  part of the default `mvn test` (which stays Ollama-free at 39); run it on demand with Ollama up.

### ✅ Live run (free local Ollama) — DoD verified

Done with **€0** spend (local models). To reproduce on any machine — install Ollama + pull the two
models per `docs/OLLAMA_SETUP.md`, then:

```bash
docker compose up -d db backend                   # backend reaches host Ollama via host.docker.internal
curl -s -X POST localhost:8080/api/rag/reindex     # -> {"news":124,"onchain":53,"fundamental":10,"kb":70}
curl -s localhost:8080/api/rag/status              # same counts
curl -s -X POST localhost:8080/api/chat -H 'Content-Type: application/json' \
  -d '{"query":"How does Solana achieve consensus?"}'   # cited answer "[5]" from the SOL KB chunk
curl -s -X POST localhost:8080/api/chat -H 'Content-Type: application/json' \
  -d '{"query":"What will BTC be worth in 2030?"}'      # "The available sources do not answer this question."
curl -s -X POST localhost:8080/api/chat -H 'Content-Type: application/json' \
  -d '{"query":"Should I buy ETH now?"}'                # "I can summarise what sources are saying, but I cannot give trading advice."
cd backend && RAG_LIVE=1 mvn -Dtest=RagLiveIT test      # 7/7 + writes reports/retrieval_eval.md
```

Observed: reindex 257 chunks in ~4s; mechanism chat cites a KB chunk (latency ~7s on `llama3.2:3b`);
both refusals exact; LINK (no news) refuses cleanly; recall@8 0.90. **OpenAI cost: €0 (not used).**

### Definition of done — checklist

- [x] Code complete: indexer, classifier, retriever, generator, REST, KB (10), eval harness, tests.
- [x] Backend boots on Spring AI; `vector_store` (768-dim) auto-created & owned by Spring AI;
      `GET /api/rag/status` works; `ddl-auto: validate` still passes.
- [x] `POST /api/rag/reindex` populates pgvector; `GET /api/rag/status` shows non-zero counts per
      source type (news 124 · onchain 53 · fundamental 10 · kb 70).
- [x] `POST /api/chat` answers a mechanism question with `[N]` citations from KB (verified live).
- [x] out-of-corpus + trading-advice refused with the exact phrases (live + deterministic guards).
- [x] a coin with no recent news (LINK) refuses cleanly — no hallucination, no crash.
- [x] retrieval eval recall@8 = 0.90 (mechanism/fundamental ≥ 0.75, overall ≥ 0.70). Cost **< €5**
      (€0 — local Ollama).
- [x] 39 offline tests pass; live `RagLiveIT` 7/7 pass.

### Deviations from the Stage 4 prompt (documented)

1. **Spring Boot bumped 3.3.5 → 3.4.13** — mandatory: Spring AI 1.0.x supports only Boot 3.4.x/3.5.x.
   Carried `@MockBean` → `@MockitoBean` in `SignalsControllerTest` (the Boot-3.4 replacement).
2. **Model provider = free local Ollama, not OpenAI** — the brief defaulted to OpenAI
   (`gpt-4o-mini` + `text-embedding-3-small`, 1536-dim), but the supplied key had no quota
   (`429 insufficient_quota`). Switched the active provider to local **Ollama** (`llama3.2:3b` +
   `nomic-embed-text`, **768-dim**) — €0, no key, runs on any PC (`docs/OLLAMA_SETUP.md`). The
   OpenAI starter stays on the classpath; switch back via `spring.ai.model.{chat,embedding}=openai`
   + `pgvector.dimensions=1536` + re-index. (The prompt explicitly allowed a free local model.)
3. **Classifier routes "supply" → KB** (not fundamental/onchain as the prompt lists): supply
   schedules exist only in the KB; the `fundamentals` table has no supply field, so KB is the only
   source that can answer supply questions.
4. **`onchain` is BTC-only** (no ETH in the DB) and **`fundamentals` was empty and was repopulated**
   (CoinGecko ingest) before indexing. On-chain synthesis is built generically per present symbol.
5. **Live eval runner is an `*IT` JUnit test** (`RagLiveIT`), gated by the `RAG_LIVE` env var, so it
   is out of the default `mvn test` (keeps that Ollama-free) and run on demand. Recall@8 (hit@8) is
   defined per the eval header; news recall is corpus-dependent (sparsity caveat per the brief).
6. **Unrelated:** while wiring the OpenAI key, the `ETHERSCAN_API_KEY` value in `.env` was
   accidentally overwritten and could not be recovered (`.env` is gitignored); a placeholder is in
   place. Not used by Stage 4 (only ETH on-chain ingestion, which the DB doesn't have anyway).

---

## Stage 3 — what is done

The `backend` container (Java 21 + Spring Boot 3.3.5, Maven) is live: it reads Python's tables
**read-only** over JDBC, serves a REST API over the existing data, and computes a deterministic
Ichimoku-centric **TA verdict with ta4j 0.17**. No RAG/trading/Analyst/frontend yet (Stages 4–6).
One modular monolith, not microservices (PROJECT.md §2). Code is organised in conventional
layered packages — `controller`, `service`, `repository`, `entity`, `dto`, `config`, `util`
(under `com.cryptocopilot`) — and the API is self-documented with OpenAPI 3 / Swagger UI
(springdoc). Entities use the JPA standard (`jakarta.persistence`) mapped by Spring Data JPA
repositories; that is the Spring Data JPA way (it builds on Hibernate, which `ddl-auto: validate`
requires).

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
- **Swagger UI** at `GET /swagger-ui.html`; the OpenAPI 3 spec at `GET /v3/api-docs` (documents
  all four `/api/**` endpoints; title "CryptoCopilot API" v1). Controllers carry
  `@Tag`/`@Operation`/`@Parameter`.

**TA verdict engine** (`com.cryptocopilot.service.TaVerdictEngine`, pure ta4j from raw `ohlcv` — never Python
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

- `service.TaVerdictTest` (4) — golden bullish ramp → **BULLISH / MODERATE, score 2.5**, exact 4
  signals; bearish-cloud branches fire on a downtrend (nets NEUTRAL — the oversold guard hedges
  it, an intended property); `score→direction/confidence` thresholds; insufficient-history guard.
- `controller.SignalsControllerTest` (`@WebMvcTest`, mocked `SignalService`) — `/api/signals`
  returns 10 coins, each with `mlClass` + `mlConfidence` + a `ta` block.
- `repository.OhlcvRepositoryTest` (`@DataJpaTest` vs the running `db`, read-only,
  `ddl-auto: validate`) — OHLCV range (ascending, bounded) + latest-prediction (`v1`) queries.

**Stack/versions:** Spring Boot 3.3.5, Java 21, ta4j 0.17, springdoc-openapi 2.6.0,
Hibernate 6.5.3, Postgres 16 + pgvector. Build: `backend/Dockerfile` multi-stage
(`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`, port 8080).

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
