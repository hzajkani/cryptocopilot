# RAG card — the Researcher (cited chat)

> One of three cards documenting CryptoCopilot's intelligence layer:
> [MODEL_CARD](MODEL_CARD.md) · **RAG_CARD** (this file) · [ANALYST_CARD](ANALYST_CARD.md).
> Decision-support only — **not financial advice** (PROJECT.md §9).

The **Researcher** answers questions about the 10 coins **strictly from the corpus already in
Postgres** (news + on-chain + fundamentals) plus a curated Knowledge Base — every answer carries
`[N]` citations, and anything the sources don't cover is **refused**, never hallucinated.
Built with **Spring AI 1.0.8 + pgvector** (package `com.cryptocopilot.rag`).

## Provider — free, local, ~€0

| role | model | notes |
|---|---|---|
| chat | Ollama **`llama3.2:3b`** (temperature 0) | grounded, near-deterministic phrasing |
| embeddings | Ollama **`nomic-embed-text`** | **768-dim** → `vector_store` is `vector(768)` |

Runs on a **local Ollama** — no API key, **≈ €0** (`docs/OLLAMA_SETUP.md`). We pivoted to Ollama
after the supplied `OPENAI_API_KEY` returned `429 insufficient_quota`; the OpenAI starter stays on
the classpath, so switching back is config-only (`spring.ai.model.{chat,embedding}=openai` +
`pgvector.dimensions=1536` + re-index). **With Ollama down the chat refuses cleanly** (it cannot embed
the query) — a valid state the UI renders, not a crash.

## Corpus & chunking

`vector_store` is created and **owned by Spring AI** (`initialize-schema: true`, HNSW / cosine).
`CorpusIndexer` does an idempotent clear-and-rebuild (deterministic UUID ids), one writer per source:

| source_type | one chunk per | content |
|---|---|---|
| **news** | `news` row | `title` + `summary` (metadata: symbols, source, url, sentiment, ts) |
| **onchain** | `(symbol, ISO-week)` | weekly **mean** synthesis of the daily metrics |
| **fundamental** | coin | synthesis of the latest `fundamentals` snapshot (null/zero fields omitted) |
| **kb** | `##` section | curated mechanism/tokenomics markdown, 7 sections × 10 coins |

**Live index counts:** news **124** · onchain **53** · fundamental **10** · kb **70** = **257 chunks**
(embedded in ~4s). The KB ships in the jar (`backend/src/main/resources/kb/*.md`).

## Query classifier (rule-based)

Routes each query to a `source_type`: `kb` / `news` / `onchain` / `fundamental` / `all`, with
precedence **onchain → fundamental → news → kb → all** (so "recent on-chain transactions" → onchain,
"current sentiment" → news). **Deviation (documented):** "supply" routes to **KB** — supply schedules
live only in the KB; the `fundamentals` table has no supply field. Classifier accuracy on the eval: **1.00**.

## Retrieval (k = 8)

`similaritySearch` filtered by `source_type` (+ optional `symbol`), oversampled then **recency
re-ranked for news + on-chain only**:

```
score = 0.7 · similarity + 0.3 · exp(−ageDays / 14)
```

KB and fundamental chunks rank by similarity alone (no recency decay — mechanism facts don't age).
Returns numbered chunks `[1..k]`.

## Grounding & refusal (deterministic guards, not left to the LLM)

The system prompt forbids ungrounded claims, but three **deterministic** guards enforce it
regardless of what the model emits:

1. **Trading-advice** queries → refuse *before any LLM call*:
   `"I can summarise what sources are saying, but I cannot give trading advice."`
2. **Empty retrieval** → refuse *before any LLM call*:
   `"The available sources do not answer this question."`
3. **Answer with no verifiable `[N]` citation** → treated as ungrounded and replaced with the
   no-context refusal above.

Citation rate is therefore **100%** by construction (PROJECT.md §9): any non-refusal answer cites.

## Evaluation — recall@8

`evals/retrieval_eval.yaml` — 20 questions (8 news / 8 mechanism / 4 fundamental) authored against
the **actual** corpus. recall@8 = fraction with ≥1 of the top-8 chunks matching the expected
source_type + symbol + a keyword. Runner: `RAG_LIVE=1 mvn -Dtest=RagLiveIT test` →
`reports/retrieval_eval.md`.

| category | n | recall@8 | gate (PROJECT.md §9) |
|---|---|---|---|
| news | 8 | 0.88 | per-category ≥ 0.70 ✓ |
| mechanism | 8 | 0.88 | ✓ |
| fundamental | 4 | 1.00 | ✓ |
| **overall** | 20 | **0.90** | ≥ 0.75 ✓ |

**Corpus-sparsity caveat:** news is only ~124 rows over a ~4-day window, so news recall is
corpus-dependent and rises as the `ml` scheduler ingests more. The two misses (n8, m6) are
retrieval-quality artifacts of a small 768-dim model on generic phrasing — both still above the gates.

## Cost & limitations

- **Cost ≈ €0** — everything runs on local Ollama; no paid API.
- **Limitations:** answers only as good as the corpus (sparse news); a 3B local model is weaker than
  a hosted LLM on generic phrasing; on-chain is BTC-only today; refuses rather than guesses by design.
- **Not financial advice** — the Researcher summarises sources; it never recommends a trade.
