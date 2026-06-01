# Retrieval eval — CryptoCopilot Stage 4 (RAG)

Generated: 2026-06-01T13:16:23.862537Z  
Embedding: Ollama `nomic-embed-text` (768-dim) · chat: Ollama `llama3.2:3b` · top-k = 8 · score = 0.7·similarity + 0.3·exp(-ageDays/14) for news/onchain.

Corpus: {fundamental=10, kb=70, news=124, onchain=53}

**recall@8** = fraction of questions for which ≥1 of the top-8 chunks matches the expected source_type, symbol and a keyword. News is corpus-dependent (~124 rows, ~4-day window) and grows with ingestion.

| category | n | recall@8 | classifier accuracy |
|---|---|---|---|
| news | 8 | 0.88 | 1.00 |
| mechanism | 8 | 0.88 | 1.00 |
| fundamental | 4 | 1.00 | 1.00 |
| **overall** | 20 | **0.90** | 1.00 |

## Per-question

| id | category | hit@8 | classification | matched age | age gate |
|---|---|---|---|---|---|
| n1 | news | ✅ | news | 1d | ok |
| n2 | news | ✅ | news | 1d | ok |
| n3 | news | ✅ | news | 2d | ok |
| n4 | news | ✅ | news | 2d | ok |
| n5 | news | ✅ | news | 1d | ok |
| n6 | news | ✅ | news | 2d | ok |
| n7 | news | ✅ | news | 1d | ok |
| n8 | news | ❌ | news | — | ok |
| m1 | mechanism | ✅ | kb | — | ok |
| m2 | mechanism | ✅ | kb | — | ok |
| m3 | mechanism | ✅ | kb | — | ok |
| m4 | mechanism | ✅ | kb | — | ok |
| m5 | mechanism | ✅ | kb | — | ok |
| m6 | mechanism | ❌ | kb | — | ok |
| m7 | mechanism | ✅ | kb | — | ok |
| m8 | mechanism | ✅ | kb | — | ok |
| f1 | fundamental | ✅ | fundamental | 0d | ok |
| f2 | fundamental | ✅ | fundamental | 0d | ok |
| f3 | fundamental | ✅ | onchain | 7d | ok |
| f4 | fundamental | ✅ | fundamental | 0d | ok |
