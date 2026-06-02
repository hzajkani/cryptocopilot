# Analyst card — the fused opinion

> One of three cards documenting CryptoCopilot's intelligence layer:
> [MODEL_CARD](MODEL_CARD.md) · [RAG_CARD](RAG_CARD.md) · **ANALYST_CARD** (this file).
> Decision-support only — **not financial advice**, paper trading only (PROJECT.md §9).

The **Analyst** fuses four perspectives on a coin — the ML signal, the ta4j TA verdict, a
fundamental health snapshot, and recent news sentiment — into one explainable opinion
(`direction` / `conviction` / `agreementScore`) with a short summary and citations. The scoring is
**fully deterministic** (package `com.cryptocopilot.analyst`, pure `AnalystScorer`); only the
*phrasing* of the summary is LLM-assisted, behind a hallucination guard. Endpoints:
`GET /api/analyst` (all 10) and `GET /api/analyst/{symbol}`.

## Per-input scoring (−2 … +2)

Each input contributes an integer score and a human-readable reason:

| input | score | rule |
|---|---|---|
| **ML** | +2 / +1 | `UP`, calibrated confidence `≥ τ` / `< τ` (τ = 0.50, configurable) |
| | −2 / −1 | `DOWN`, `≥ τ` / `< τ` |
| | 0 | `FLAT` or unavailable. *(The stored `pred_class` is trusted — never re-argmaxed.)* |
| **TA** | +2 / +1 | `BULLISH` × `STRONG` / `MODERATE` |
| | −2 / −1 | `BEARISH` × `STRONG` / `MODERATE` |
| | 0 | `NEUTRAL` |
| **Fundamental** | +1 / −1 / 0 | health `IMPROVING` / `DETERIORATING` / `STABLE`\|`UNKNOWN` |
| **News** | +1 / −1 / 0 | 7-day sentiment `POSITIVE` / `NEGATIVE` / `MIXED`\|`INSUFFICIENT_DATA` |

## Combine → direction / conviction / agreement

Let `sum` = the four scores added (range −6 … +6):

- **direction** — `sum ≥ +3` → **LEAN_BULLISH**; `sum ≤ −3` → **LEAN_BEARISH**; otherwise
  **CONFLICTED** when inputs hold genuinely opposite signs (some +, some −), else **NEUTRAL**.
- **conviction** — `|sum| ≥ 4` → **HIGH**; `|sum| ∈ {2,3}` → **MEDIUM**; else **LOW**.
- **agreementScore** — `1 − variance/4` of the four scores (max variance for values in [−2,+2] is 4),
  rounded to 3 dp: a unanimous panel → 1.0, a fully split panel → 0.0.

Missing inputs score 0 (never crash) — a coin with no data is `NEUTRAL` / `LOW`, not an error.

## Two-tier fundamental health (with `healthSource` transparency)

Health is deterministic and the chosen tier is surfaced at the top level as `healthSource`, so the
UI never implies more data than exists:

- **Tier 1 — on-chain** (`healthSource: "onchain"`): a real **daily** series — 7-day MA of active
  addresses + transfer volume, recent window vs prior; both rising → **IMPROVING**, both falling →
  **DETERIORATING**, else **STABLE**. **Only BTC qualifies today** (it has the daily blockchain.com
  series; ETH's on-chain rows are a single snapshot of supply/staking metrics, so ETH falls to Tier 2).
- **Tier 2 — CoinGecko** (`healthSource: "coingecko"`): a within-snapshot rule on the latest
  `fundamentals` row — 7d momentum ±5%, dev activity (`github_commit_count_4w` > 20 / ≤ 5), 24h
  market-cap ±3%; ≥ 2 positive & 0 negative → IMPROVING, ≥ 2 negative & 0 positive → DETERIORATING,
  else STABLE.
- **Tier 3** (`healthSource: "unknown"`): no usable data → **UNKNOWN** (scores 0).

Also computed: universe-relative market **dominance** + 7-day trend (`market_meta`), and
recency-weighted 7-day **news sentiment** (`POSITIVE` / `MIXED` / `NEGATIVE` / `INSUFFICIENT_DATA`).
News citations come from a **deterministic recency query** over the `news` table (symbol-tagged,
≤ 7d), not the semantic retriever — so the Analyst stays independent of the embedding model.

## Hallucination guard

The summary is phrased by the LLM (same `LlmClient` seam as the [Researcher](RAG_CARD.md)), but
**every number it emits must already appear in the four input objects**. `isGrounded` validates each
numeric claim; on **any** failure — an invented number, an LLM error, or an empty reply — the Analyst
falls back to a **deterministic template** summary. So `GET /api/analyst` always returns a valid,
grounded opinion **even with Ollama down** (the template path), which is the path exercised in the
default environment.

## Worked examples (Ollama down → guarded template summaries)

- **BTC** (`healthSource: onchain`) → `direction=NEUTRAL`, `conviction=LOW`. Scores: ML FLAT 0,
  TA BEARISH/MODERATE −1, fundamental STABLE 0, news MIXED 0 → sum −1, agreement 0.95.
- **SOL** (`healthSource: coingecko`) → `direction=LEAN_BEARISH`, `conviction=MEDIUM`. Scores:
  ML DOWN @ 0.30 (< τ) −1, TA BEARISH/STRONG −2 → sum −3, agreement 0.83.

## Disclaimer & limitations

Every response carries the persistent disclaimer — **decision-support, not financial advice; paper
trading only** (PROJECT.md §9). The Analyst synthesises only facts present in its four inputs; it
makes no forecast beyond them, and Tier-1 on-chain health is BTC-only until more daily on-chain
series are ingested.
