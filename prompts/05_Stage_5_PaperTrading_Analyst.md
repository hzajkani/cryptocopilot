# Stage 5 — Paper-trading engine + Analyst aggregator (completes the backend)

> **Phase B of 3 (Java/Spring backend) — final stage.** This is Stage 5 of 7.
>
> **How to use this file:** New Claude Code session in the project root. First message: *"Read PROJECT.md and STATE.md before anything else."* Then paste everything below the line.

---

# CryptoCopilot — Stage 5: paper trading + the Analyst View

Read `PROJECT.md` and `STATE.md`. Stages 1–4 done: data + ML predictions, Spring Boot backend with markets/signals + ta4j TA verdict + RAG chat.

This stage adds the two headline pieces of business logic, both in the backend:
1. A **long-only paper-trading engine** that simulates orders against 1h OHLCV bars with realistic slippage + fees, persisted to the Java-owned tables.
2. The **Analyst aggregator** — fuses ML + TA + Fundamental + News into one **deterministic, explainable** opinion per coin, with an LLM summary guarded against hallucination.

After this, Phase B (backend) is done; the React frontend is Stage 6.

## Reality from Phase A — the corrections that matter here

- **OHLCV is ~2024-06 → 2026-05 (~2y), not 3y.** Backtests must use the **real available range**. The natural default backtest/paper window = the ML test window **2025-09-01 → present** (or "last N months"). Do **not** hardcode 2023.
- **Trust `pred_class` from `predictions` as the ML label** (it's a val-tuned weighted-argmax, not `argmax(prob_*)`). **ML confidence = the calibrated probability of the predicted class.**
- **`fundamentals` is a single snapshot per coin (10 rows, NO time series).** The original capstone Tier-2 logic ("GitHub commits 4w vs prior 4 weeks", "Reddit 30d trend", "volume 7d vs 30d avg") **cannot be computed — there is no history.** Compute Tier-2 health from the **latest snapshot's self-contained fields** instead (see task 3). Only use cross-time trends if ≥2 snapshots exist for the symbol; otherwise use the within-snapshot momentum rule.
- `onchain` covers **BTC + ETH only** → Tier-1 health for those two; Tier-2 (CoinGecko) for the other 8; `UNKNOWN` if neither.
- `news` is sparse → `news_sentiment_7d` is often `INSUFFICIENT_DATA` (scores 0). The Analyst summary must not claim news when there is none.

## Tasks

### 1. JPA entities (write side — Java-owned, now used)

`com.cryptocopilot.trading.domain`: `AccountState`, `Position`, `Trade`, `Order` mapped to the contract tables. These are read-write (the only tables the backend writes to).

### 2. Paper-trading engine — `com.cryptocopilot.trading`

- Account starts at **10,000 USD**. `resetAccount(startingBalance=10000)`.
- **MARKET** order fills at the **next 1h candle's open + 0.05% slippage** (configurable).
- **LIMIT** order fills when the next 1h bar's `low <= limit <= high`; fill price = limit; else stays `PENDING`.
- **0.1% fee** on every fill (taker approximation).
- **Long-only**: no shorts, no leverage; reject a SELL larger than held quantity.
- All times UTC, aligned to the 1h OHLCV grid.
- Public API: `submitOrder(order)`, `markToMarket(ts)`, `equityCurve()`, `metrics()` (Sharpe, Sortino, max drawdown, win rate, avg win/loss, total trades, total fees).
- Persist orders/trades/positions/account_state.

### 3. Fundamental snapshot module — `com.cryptocopilot.analyst.FundamentalSnapshot`

```java
record FundamentalSnapshot(String symbol, Instant tsUtc, String health,
    String healthSource, List<String> reasons,
    Double marketDominancePct, String marketDominanceTrend,
    String newsSentiment7d, double newsSentimentScore) {}
// health ∈ IMPROVING/STABLE/DETERIORATING/UNKNOWN
// healthSource ∈ onchain/coingecko/unknown
```

- **Tier 1 — on-chain (BTC, ETH only)** from `onchain`: 7-day MA of active addresses and transfer volume — both rising → IMPROVING; both falling → DETERIORATING; else STABLE. `healthSource="onchain"`.
- **Tier 2 — CoinGecko (other 8)** from the **latest `fundamentals` snapshot** (self-contained, since there's no history). Score three within-snapshot signals:
  - momentum: `price_change_pct_7d > +5` → +1; `< -5` → −1;
  - developer activity: `github_commit_count_4w` present and above a small floor (e.g. > 20) → +1; near zero → −1;
  - volume/cap: `market_cap_change_pct_24h > +3` → +1; `< -3` → −1.
  - `>=2` positive & 0 negative → IMPROVING; `>=2` negative & 0 positive → DETERIORATING; else STABLE. `healthSource="coingecko"`.
  - (If ≥2 snapshots exist for the symbol, you MAY instead use the trend-vs-prior logic — but the single-snapshot rule is the required default.)
- **Tier 3** — neither source has data → `UNKNOWN`, `healthSource="unknown"`.
- `marketDominancePct` + 7-day trend from `market_meta` (note: ~365d of history available — recent trend is fine).
- `newsSentiment7d` from `news` (use the stored `sentiment_score`), recency-weighted over 7d → POSITIVE/MIXED/NEGATIVE; `INSUFFICIENT_DATA` if too few items. `newsSentimentScore` = weighted mean.

Pure rule-based, deterministic. No ML, no LLM.

### 4. Analyst aggregator — `com.cryptocopilot.analyst`

```java
record AnalystOpinion(String symbol, Instant tsUtc, String direction,
    String conviction, String summary, double agreementScore,
    Map<String,Object> inputs, List<String> citations) {}
// direction ∈ LEAN_BULLISH/LEAN_BEARISH/NEUTRAL/CONFLICTED
// conviction ∈ HIGH/MEDIUM/LOW
```

Inputs: ML signal (from `predictions`), TA verdict (Stage 3 engine), FundamentalSnapshot (task 3), news context (top-3 cited headlines for the coin, last 7d, from Stage 4 retrieval).

**Deterministic scoring**, each input on −2..+2:
- **ML**: `pred_class=UP` with confidence ≥ τ → +2, else +1; `FLAT` → 0; `DOWN` with confidence ≥ τ → −2, else −1. (Confidence = calibrated prob of the predicted class. **τ default 0.50, configurable**; note that in the current calm regime most confidences fall below 0.50, so most ML scores will be ±1 — that is the honest result.)
- **TA**: BULLISH_STRONG +2, BULLISH_MODERATE +1, NEUTRAL 0, BEARISH_MODERATE −1, BEARISH_STRONG −2.
- **Fundamental health**: IMPROVING +1, STABLE 0, DETERIORATING −1, UNKNOWN 0.
- **News sentiment**: POSITIVE +1, MIXED 0, NEGATIVE −1, INSUFFICIENT_DATA 0.

Combined = sum (−6..+6). `direction`: `>=+3` LEAN_BULLISH; `<=-3` LEAN_BEARISH; if inputs disagree (a pair has opposite signs with no clear majority) → CONFLICTED; else NEUTRAL. `conviction`: `|sum|>=4` HIGH, `2..3` MEDIUM, else LOW. `agreementScore = 1 - normalisedVariance(inputs)`.

**Summary** via Spring AI `ChatClient` (gpt-4o-mini) from a deterministic template given the four input objects — the LLM may **only synthesise existing facts**, no new claims (2–3 sentences). **Hallucination guard:** validate that every numeric value in the summary appears in the input objects; on failure, fall back to a deterministic template string. The response must surface `healthSource` (transparency requirement).

Persistent disclaimer text on the Analyst response:
> *This is decision-support, not financial advice. The Analyst combines ML, technical, fundamental, and news inputs deterministically. You are responsible for your decisions.*

### 5. Backtest runner — `com.cryptocopilot.trading.backtest`

`strategy(signalRow) -> Optional<Order>`. **Default "ML-confirmed-by-TA":** when `pred_class=UP` with `prob_up > 0.55` AND TA verdict BULLISH → BUY $1000; close when ML flips or TA → BEARISH. Run over the **real available window** (default 2025-09-01 → present). Save `reports/backtest_strategy_v1.md`. Report positive Sharpe **or** explain in STATE.md why not (fees + a calm regime + a data-limited model may well give ≤0 — that is an acceptable, honest result).

### 6. REST

- **`GET /api/analyst`** → all 10 opinions; **`GET /api/analyst/{symbol}`** → one (with inputs, citations, `healthSource`, disclaimer).
- **`POST /api/orders`** → submit an order (returns fill + fees); **`GET /api/positions`**, **`GET /api/trades`**, **`GET /api/account`**.
- **`GET /api/performance`** → equity curve + metrics.
- **`POST /api/account/reset`**.

### 7. Tests

- `EngineTest`: MARKET BUY creates a position with correct avg-entry + fees; SELL produces correct P&L; LIMIT below the bar low stays PENDING; SELL > held is rejected.
- `BacktestTest`: default strategy over a 1-month fixture; verify equity-curve shape.
- `AnalystTest` (golden, the scenarios from PROJECT/the original spec): all-aligned-bullish (BTC, on-chain IMPROVING); all-aligned-bullish (SOL, CoinGecko IMPROVING); all-aligned-bearish; conflicted; neutral; **missing-onchain (SOL) → `healthSource="coingecko"`**; missing-news; **missing-everything → NEUTRAL/LOW, no crash**.
- `HallucinationGuardTest`: a summary with a number not in the inputs fails the guard → deterministic fallback.

### 8. STATE.md + Git

Append a **Stage 5** section: default-strategy Sharpe + max drawdown + win rate + fee drag over the real window, an Analyst-opinion sample for one on-chain coin (BTC/ETH) AND one CoinGecko-only coin (e.g. SOL) showing `healthSource`. Two commits: `"Stage 5a: paper-trading engine + two-tier fundamental snapshot + Analyst aggregator"`, `"Stage 5b: analyst + trading REST endpoints"`. Tag `stage-5-done`. **Phase B (backend) complete.**

## Definition of done

- `docker compose up -d` → backend serves the Analyst + trading endpoints.
- `GET /api/analyst` → a structured opinion + reasoning + citations + `healthSource` for all 10 coins; missing-data coins still produce NEUTRAL/LOW without crashing.
- `POST /api/orders` for a BUY → a position is created, a trade is logged, fees applied; `GET /api/performance` returns an equity curve + metrics.
- Default-strategy backtest over the real window → positive Sharpe **or** a documented honest explanation in STATE.md.
- All tests pass, including every missing-data Analyst scenario and the hallucination guard.

## What NOT to do

- Do NOT connect to a real exchange. Ever. Paper money only.
- Do NOT add shorts, leverage, or stop-loss/take-profit automation in v1.
- Do NOT track real-time prices — use the 1h OHLCV bars.
- Do NOT use cross-time fundamental trends as the default — `fundamentals` has one snapshot per coin; use the within-snapshot rule.
- Do NOT recompute `pred_class` from probabilities — trust the stored label.
- Do NOT let the Analyst summary state any fact not in its four input objects; the hallucination guard must catch it.
- Do NOT hide `healthSource` (on-chain vs CoinGecko) — it is a transparency requirement.
- Do NOT build the React frontend — that is Stage 6.
