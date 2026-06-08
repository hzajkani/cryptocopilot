# Stage 4 — RAG: Spring AI + pgvector + cited chat (the Researcher)

> **Phase B of 3 (Java/Spring backend).** This is Stage 4 of 7.
>
> **How to use this file:** New Claude Code session in the project root. First message: *"Read PROJECT.md and STATE.md before anything else."* Then paste everything below the line.

---

# CryptoCopilot — Stage 4: strictly-grounded RAG chat with Spring AI

Read `PROJECT.md` and `STATE.md`. Stages 1–3 done: data in Postgres, Spring Boot backend serving markets/signals + a ta4j TA verdict.

This stage builds the **Researcher** — a RAG chat over the data already in Postgres (news + on-chain + fundamentals) plus a curated coin **Knowledge Base**, using **Spring AI + pgvector + OpenAI gpt-4o-mini**. It answers three question types with citations: news-driven, mechanism/educational, and fundamental. It is **strictly grounded** (refuses when sources don't cover the question) and gives **actionable, cited signal-based views** with an educational/paper-trading disclaimer.

## Reality from Phase A — size expectations to the actual corpus

- **`news` is sparse: ~124 rows right now** (180-day window, 5 sources; it grows as the `ml` scheduler ingests daily). Many coins have little or no recent news. The chat and retrieval MUST handle thin/empty coverage gracefully — refuse cleanly rather than hallucinate.
- **`fundamentals` is one snapshot per coin (10 rows)** — so the "fundamental synthesis" is one chunk per coin for now (it grows over time). That is fine.
- `onchain` covers BTC + ETH only (~1,088 rows). On-chain synthesis chunks exist for those two.
- Set RAG recall targets realistically and note in `STATE.md` that news-category recall scales with corpus growth.

## Tasks

### 1. Add Spring AI

Add the Spring AI BOM and starters: the OpenAI chat/embedding starter and the **pgvector vector-store** starter. **Verify the exact artifact coordinates and version against the current Spring AI 1.0 docs / start.spring.io** (Spring AI artifact names changed at GA). You also need the OpenAI API key wired (`OPENAI_API_KEY` is already in the backend env from Stage 3).

`application.yml`:
- `spring.ai.openai.api-key: ${OPENAI_API_KEY}`
- Chat model: `gpt-4o-mini`.
- Embeddings: default to OpenAI `text-embedding-3-small` (1536-dim) for simplicity — cheap, one provider. (Optional free alternative: Spring AI's local ONNX `TransformersEmbeddingModel` with a MiniLM model — mention it but OpenAI embeddings is the default path.)
- pgvector store: `spring.ai.vectorstore.pgvector.initialize-schema: true`, dimensions matching the embedding model (**1536** for `text-embedding-3-small`). Spring AI creates and owns its `vector_store` table — do not hand-create it.

### 2. Curated Knowledge Base (10 coin docs)

Create `backend/src/main/resources/kb/{btc,eth,sol,bnb,xrp,ada,avax,dot,link,matic}.md` (ship in the jar). One per coin, **under 1500 words**, sections by `##`: Identity, Consensus, Supply schedule, Use case, Key risks, On-chain footprint, Last updated. Factual mechanism/tokenomics only. **No price targets, no recommendations, nothing forward-looking** (PROJECT.md §9). Author from publicly-known information.

### 3. Corpus assembly + indexing — `com.cryptocopilot.rag`

A component (run via a CLI flag / `CommandLineRunner`, or `POST /api/rag/reindex`) that builds Spring AI `Document`s from four source types and adds them to the `VectorStore`. Make it idempotent (clear-and-rebuild, or track ids) — it must NOT run on every boot.

- **News** — one `Document` per `news` row: content = `title + "\n" + summary`; metadata `{source_type:"news", symbol(s), ts_utc, source, url, sentiment}`. A post tagged with multiple symbols gets one chunk with all of them in metadata.
- **On-chain weekly synthesis (BTC, ETH)** — aggregate `onchain` by `(symbol, ISO-week)` into a one-sentence paragraph, e.g. *"Week of 2026-04-13: BTC unique addresses 925k, est. transfer volume $24.1B."* metadata `{source_type:"onchain", symbol, ts_utc:weekStart, source}`.
- **Fundamental synthesis (all 10)** — from the latest `fundamentals` snapshot per coin, one `Document`: *"SOL fundamentals: price 7d +4.2%, 30d −3%, GitHub commits 4w 248, Reddit active 48h N, total volume $V."* (omit fields that are null). metadata `{source_type:"fundamental", symbol, ts_utc}`.
- **Knowledge Base** — split each coin `.md` by `##` headers into chunks; metadata `{source_type:"kb", symbol, section}`.

Log chunk counts per `source_type`.

### 4. Retriever — `com.cryptocopilot.rag.Retriever`

`retrieve(query, k=8, symbols?, sourceFilter?)`:
- `VectorStore.similaritySearch(SearchRequest.builder().query(q).topK(k)...build())` with a `filterExpression` on `symbol` and/or `source_type` when provided.
- **Recency re-rank for `news` and `onchain`**: final = `0.7*similarity + 0.3*recency`, `recency = exp(-ageDays/14)`. **Skip recency boost for `kb`.**
- **Rule-based query classifier** → biases the `source_type` filter: "how does X work"/"what is X" → `kb`; "today"/"this week"/"moving"/"why is X" → `news`; "supply"/"developer"/"active addresses"/"commits" → `fundamental`/`onchain`; else `all`.

### 5. Generator — `com.cryptocopilot.rag.Generator`

Spring AI `ChatClient` (model `gpt-4o-mini`). System prompt (use verbatim intent):

```
You are a precise crypto market research assistant. Answer ONLY from the provided context.
- Every factual claim must end with a citation [N] (the chunk number).
- If the context does not answer the question, reply EXACTLY: "The available sources do not answer this question." Do not guess.
- Be concise — 5 sentences max unless asked for detail.
- When asked for a recommendation or signal, give an actionable, balanced view (leaning bullish, bearish or neutral): weigh the supporting AND opposing evidence, name the single biggest risk, and tie every judgement to a citation [N].
- End any recommendation with this exact sentence on its own line: "This is educational decision-support for paper trading — not financial advice."
- Distinguish news sentiment (what people say) from on-chain signal (what they do).
- Prefer KB chunks for mechanism questions, recent news for "what's happening", on-chain/fundamental chunks for fundamentals.
```

User message = query + numbered chunks with their metadata. Return:
```java
record AnswerWithCitations(String answer, List<Citation> citations,
                           List<RetrievedChunk> retrievedChunks,
                           long latencyMs, String queryClassification) {}
```
Cache responses in-memory keyed by `(query, retrievedChunkIds)`.

### 6. REST

- **`POST /api/chat`** `{query, symbols?}` → `AnswerWithCitations`.
- **`GET /api/rag/status`** → chunk counts per `source_type`.
- **`POST /api/rag/reindex`** → rebuild the index (return counts).

### 7. Retrieval eval

`evals/retrieval_eval.yaml` — ~20 questions across news (8) / mechanism (8) / fundamental (4), each with `expected_keywords, expected_symbols, expected_source_types, max_age_days, expected_query_classification`. A runner computes recall@8 per category → `reports/retrieval_eval.md`. Given news sparsity, treat the news-category number as corpus-dependent and say so.

### 8. Tests

- An **out-of-corpus** question (*"What will BTC be worth in 2030?"*) returns the exact refusal phrase.
- A **trading-advice** question (*"Should I buy ETH now?"*) returns a grounded, cited view (no longer refused), closing with the educational/paper-trading disclaimer.
- A **mechanism** question retrieves the right coin's `kb` chunk (e.g., SOL consensus).
- Reindex produces non-zero chunks for `kb` and `news`.
- A coin with zero recent news does not crash the chat — it refuses cleanly.

### 9. STATE.md + Git

Append a **Stage 4** section: chunk counts per source type, recall@8 per category (with the sparsity caveat), OpenAI cost-to-date, embedding model + dimensions. Commit `"Stage 4: RAG (Spring AI + pgvector) with cited, grounded chat"`, tag `stage-4-done`. Keep total OpenAI cost for this stage **< €5**.

## Definition of done

- `POST /api/rag/reindex` populates the pgvector store; `GET /api/rag/status` shows non-zero counts per source type.
- `POST /api/chat` answers a mechanism question with `[N]` citations drawn from KB.
- An out-of-corpus question is refused with the exact phrase; a trading-advice question returns a grounded, cited view.
- A coin with no recent news refuses cleanly (no hallucination).
- All tests pass; OpenAI cost < €5.

## What NOT to do

- Do NOT scrape news — use the `news` table the `ml` container populated.
- Do NOT put price targets or forward-looking claims in the KB.
- Do NOT let the LLM answer without citations or invent sources.
- Do NOT crash when a coin has zero news — refuse.
- Do NOT hand-create the pgvector table — Spring AI owns it.
- Do NOT build trading or the Analyst (Stage 5) or the frontend (Stage 6).
