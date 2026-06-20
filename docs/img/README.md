# Screenshots

The top-level `README.md` embeds these screenshots. Five are **real captures** of the running app
(2026-06-06); one — the cited-answer **chat** — is a documented manual step (see below).

| file | page | status |
|---|---|---|
| `markets.png` | Markets (`/`) | ✅ real — the 10-coin table with price / 24h % / market cap |
| `signals.png` | Signals (`/signals`) | ✅ real — per-coin ML badge + confidence, prob bar, SHAP drivers, TA block |
| `analyst.png` | Analyst (`/analyst`) | ✅ real — the fused opinion: direction/conviction/agreement + score breakdown + `healthSource` + cited headlines |
| `performance.png` | Performance (`/performance`) | ✅ real — equity curve + risk/return metrics |
| `ml-pipeline.png` | ML Pipeline (`/ml`) | ✅ real — ingest/train/predict triggers + latest predictions |
| `chat.png` | Researcher (`/chat`) | ⏳ **manual step** — a cited answer with `[N]` chips + sources |

## The one manual step — `chat.png`

The cited-answer chat is the screenshot that best shows the RAG grounding, but it needs a *live
interaction* (type a question → wait for the grounded, cited answer), which can't be captured
headlessly in CI. It's a five-minute manual capture:

```bash
make demo                 # bring up the stack + ingest/train/predict/reindex
# open http://localhost:3000/chat  (with Ollama up, or flip the sidebar toggle to OpenAI)
# ask: "What is driving Ethereum right now?"  → wait for the answer with [N] citation chips
# capture a clean ~1400px-wide PNG and save it here as chat.png
```

Optional: a 10–20s screen recording of the same flow saved as `chat-demo.gif` (the README references
it near the GenAI section).

> More raw captures (coin detail, paper-trades desk, the ER diagram) live in
> `docs/guide/screenshots/`.
