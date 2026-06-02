# Stage 6 — React frontend (Markets · Signals · Analyst · Chat · Paper Trades · Performance)

> **Phase C of 3 (frontend).** This is Stage 6 of 7.
>
> **How to use this file:** New Claude Code session in the project root. First message: *"Read PROJECT.md and STATE.md before anything else."* Then paste everything below the line.

---

# CryptoCopilot — Stage 6: the React app, wired to the real REST contract

Read `PROJECT.md` and `STATE.md`. Phase A + B done: data + ML in Postgres, and the Spring Boot backend serving markets, signals (ML + ta4j TA), RAG chat, the Analyst, and paper trading. This stage is the **`frontend` container** (Vite + React + TypeScript, served by nginx) — a thin, polished client that **renders backend data and submits paper orders**. It contains **no business logic** (PROJECT.md §2/§3) — all signals, scores, fills and summaries come from the backend.

## Reality from Phase B — wire to THESE exact endpoints (verified from the running backend)

Base path is `/api`. Field names below are the exact JSON keys (camelCase from Java records). Timestamps are ISO-8601 strings.

```
GET  /api/markets                         -> Market[]
GET  /api/coins/{symbol}/ohlcv?timeframe=4h&from=&to=  -> Candle[]   (timeframe 1h|4h|1d; default last 90d)
GET  /api/signals                         -> Signal[]
GET  /api/ta/{symbol}                     -> TAVerdict
POST /api/chat        body ChatRequest     -> AnswerWithCitations
GET  /api/rag/status                      -> { [sourceType: string]: number }
POST /api/rag/reindex                     -> { [sourceType: string]: number }
GET  /api/analyst                         -> AnalystResponse[]
GET  /api/analyst/{symbol}                -> AnalystResponse
POST /api/orders      body OrderRequest    -> FillResult
GET  /api/orders                          -> Order[]      (newest first)
GET  /api/positions                       -> Position[]
GET  /api/trades                          -> Trade[]      (newest first)
GET  /api/account                         -> AccountState
GET  /api/performance                     -> PerformanceReport
POST /api/account/reset?startingBalance=0 -> AccountState (0 -> default 10,000)
```

**TypeScript types — mirror these exactly** (put in `src/api/types.ts`):

```ts
export type Market = { symbol: string; price: number | null; change24hPct: number | null; marketCapUsd: number | null };
export type Candle = { ts: string; open: number|null; high: number|null; low: number|null; close: number|null; volume: number|null };

export type Driver = { rank: number; featureName: string; featureValue: number|null; shapValue: number|null };
export type TAVerdict = { symbol: string; tsUtc: string; direction: "BULLISH"|"BEARISH"|"NEUTRAL"; confidence: "STRONG"|"MODERATE"|"WEAK"; signals: string[]; score: number };
export type Signal = { symbol: string; ts: string; mlClass: "UP"|"DOWN"|"FLAT"; mlConfidence: number|null; probUp: number|null; probDown: number|null; probFlat: number|null; modelVersion: string; drivers: Driver[]; ta: TAVerdict };

export type ChatRequest = { query: string; symbols?: string[] };
export type Citation = { number: number; sourceType: string; symbol: string|null; source: string|null; url: string|null; tsUtc: string|null; snippet: string };
export type RetrievedChunk = { number: number; id: string; content: string; sourceType: string; symbol: string|null; source: string|null; url: string|null; tsUtc: string|null; section: string|null; similarity: number; score: number };
export type AnswerWithCitations = { answer: string; citations: Citation[]; retrievedChunks: RetrievedChunk[]; latencyMs: number; queryClassification: string };

export type FundamentalSnapshot = { symbol: string; tsUtc: string; health: "IMPROVING"|"STABLE"|"DETERIORATING"|"UNKNOWN"; healthSource: "onchain"|"coingecko"|"unknown"; reasons: string[]; marketDominancePct: number|null; marketDominanceTrend: string|null; newsSentiment7d: string; newsSentimentScore: number };
export type InputScore = { name: string; score: number; rationale: string };
export type AnalystInputs = {
  ml: { class: string; confidence: number|null; score: number };
  ta: { direction: string|null; confidence: string|null; score: number; signals: string[] };
  fundamental: FundamentalSnapshot;
  news: { label: string; score: number };
  scoreBreakdown: InputScore[];
  combined: number;
  agreementScore: number;
  mlConfidenceThreshold: number;
};
export type AnalystOpinion = { symbol: string; tsUtc: string; direction: "LEAN_BULLISH"|"LEAN_BEARISH"|"NEUTRAL"|"CONFLICTED"; conviction: "HIGH"|"MEDIUM"|"LOW"; summary: string; agreementScore: number; inputs: AnalystInputs; citations: string[] };
export type AnalystResponse = { opinion: AnalystOpinion; healthSource: string; disclaimer: string };

export type OrderRequest = { symbol: string; side: "BUY"|"SELL"; type: "MARKET"|"LIMIT"; quantity: number; limitPrice?: number|null };
export type FillResult = { orderId: string|null; symbol: string; side: string; type: string; status: "FILLED"|"PENDING"|"CANCELLED"; filledPrice: number|null; fees: number|null; realizedPnl: number|null; tsFilled: string|null; message: string };
export type Position = { symbol: string; size: number; avgEntryPrice: number; openedAt: string };
export type Trade = { id: string; tsUtc: string; symbol: string; side: string; quantity: number; price: number; fees: number; realizedPnl: number; notes: string|null };
export type Order = { id: string; tsSubmitted: string; tsFilled: string|null; symbol: string; side: string; type: string; quantity: number; limitPrice: number|null; status: string; filledPrice: number|null; fees: number|null };
export type AccountState = { tsUtc: string; cashUsd: number; totalEquityUsd: number };
export type EquityPoint = { ts: string; equity: number; cash: number };
export type TradingMetrics = { sharpe: number; sortino: number; maxDrawdownPct: number; winRate: number; avgWin: number; avgLoss: number; totalTrades: number; totalFees: number; finalEquity: number; totalReturnPct: number };
export type PerformanceReport = { equityCurve: EquityPoint[]; metrics: TradingMetrics };
```

**Two facts that shape the UI — handle them, don't fight them:**
- **The chat + the Analyst summary depend on a local Ollama** (chat `llama3.2:3b`). When Ollama is down, the backend returns a fixed refusal phrase (chat) or a deterministic template summary (Analyst) — both are valid responses. The UI must render them cleanly, not as errors.
- **Many fields are legitimately sparse/null:** `marketCapUsd` can be null; `newsSentiment7d` is often `"INSUFFICIENT_DATA"`; the **equity curve is near-flat / the metrics are zero until the user actually places paper trades** (the default backtest makes 0 trades by design — STATE.md). Render empty/zero states gracefully.

## No CORS on the backend → use the nginx proxy (important)

The backend has **no CORS config**. Do **not** call `http://backend:8080` cross-origin from the browser. Instead:
- **Production:** nginx (serving the React build) **reverse-proxies `/api/` → `http://backend:8080/`**, so the browser only ever talks to the nginx origin. No CORS needed. Also add an SPA fallback (`try_files $uri /index.html`) for client-side routing.
- **Dev:** Vite dev-server proxy (`server.proxy["/api"] = "http://localhost:8080"`).
- The API client uses a **relative base URL (`/api`)** in both modes. Do not hardcode a host.

(If you ever need direct cross-origin calls instead, add a `WebMvcConfigurer` CORS bean to the backend — but the proxy is the cleaner path and matches the "React + nginx" container in PROJECT.md.)

## Tasks

### 1. Scaffold + container

`frontend/` = Vite + React + TypeScript. Router: `react-router-dom`. Charts: **TradingView Lightweight Charts** (`lightweight-charts`) for candlesticks, **Recharts** for the equity curve + probability/score bars. A small typed `fetch` client in `src/api/` (one function per endpoint, returning the types above). Dark, clean, trading-app aesthetic; a left sidebar or top nav across the six pages; a **persistent disclaimer banner** ("Decision-support, not financial advice. Paper trading only.") on every page.

`frontend/Dockerfile`: multi-stage — `node:20` to `npm ci && npm run build`, then `nginx:alpine` serving `/usr/share/nginx/html` with the proxy config above. `frontend/nginx.conf` proxies `/api/` to `backend:8080` and SPA-falls-back to `index.html`.

### 2. Add the `frontend` service to `docker-compose.yml`

```yaml
  frontend:
    build: ./frontend
    depends_on: [ backend ]
    ports: ["3000:80"]
```

(Uncomment/replace the placeholder note at the bottom of the compose file. All four containers now run.)

### 3. Pages

1. **Markets** (`/`) — grid/table from `GET /api/markets`: symbol, price, 24h change (green/red), market cap (humanised; "—" if null). Row click → **Coin detail** (`/coins/:symbol`): a candlestick chart from `GET /api/coins/:symbol/ohlcv` (timeframe switch 1h/4h/1d) using Lightweight Charts, plus that coin's Signal, TA verdict, and Analyst opinion inline.
2. **Signals** (`/signals`) — from `GET /api/signals`: one card per coin with an ML class badge (UP green / DOWN red / FLAT grey) + confidence, a small prob bar (`probUp/probDown/probFlat`), `modelVersion`, the top-3 `drivers` (featureName + shapValue), and the TA block (`ta.direction` + `ta.confidence` + `ta.score` + the `ta.signals` list).
3. **Analyst** (`/analyst`) — from `GET /api/analyst`: one card per coin with the `direction` badge (LEAN_BULLISH/LEAN_BEARISH/NEUTRAL/CONFLICTED), `conviction`, an `agreementScore` gauge (0–1), the `summary` text, a **score breakdown** from `inputs.scoreBreakdown` (each `{name, score, rationale}`), a `healthSource` badge (onchain/coingecko/unknown), the string `citations`, and the `disclaimer`. Card click → `/analyst/:symbol` detail (same data, expanded with the full `inputs.fundamental`).
4. **Chat / Researcher** (`/chat`) — a chat UI hitting `POST /api/chat`. Render `answer` with inline **[N] citation chips** that map to `citations[n]` (show `source`, `symbol`, `snippet`, link out via `url`); show `queryClassification` + `latencyMs` as a subtle footer. Optional symbol filter (multi-select → `symbols`). When the answer is the exact refusal phrase or empty (Ollama down / no sources), show it as a calm system message, not an error.
5. **Paper Trades** (`/trade`) — an **order ticket**: symbol dropdown (the 10 coins), side BUY/SELL, type MARKET/LIMIT (show `limitPrice` only for LIMIT), quantity → `POST /api/orders`; render the `FillResult` (FILLED price + fees + realizedPnl, or the CANCELLED/PENDING `message`). Tables for `GET /api/positions`, `GET /api/trades`, `GET /api/orders`. An account header (cash + equity from `GET /api/account`) and a **Reset** button → `POST /api/account/reset` (confirm first).
6. **Performance** (`/performance`) — from `GET /api/performance`: an equity-curve line chart (Recharts) over `equityCurve` (`ts` → `equity`), and a metrics panel (Sharpe, Sortino, max drawdown %, win rate, avg win/loss, total trades, total fees, final equity, total return %). Show a friendly "no trades yet — place a paper order" empty state when the curve is flat / metrics are zero.

### 4. Cross-cutting

- A typed API client with one error path (toast/banner on non-2xx); loading skeletons; null/empty/zero states everywhere (see the sparsity note).
- Number/percent/USD formatters; relative-time for timestamps.
- The disclaimer banner is always visible.

### 5. Tests (modest — the value is the UI)

- `tsc --noEmit` type-checks against the types above.
- A couple of Vitest + React Testing Library render tests: Markets renders rows from a mocked client; the order ticket disables `limitPrice` for MARKET; the Chat view renders a refusal answer as a system message.

### 6. STATE.md + Git

Append a **Stage 6** section: the six routes, the charting libs + versions, the nginx-proxy approach (no CORS), and a note that Chat/Analyst summaries need Ollama up. Commit `"Stage 6: React frontend (Vite + nginx) wired to the backend REST API"`, tag `stage-6-done`. **Phase C complete.**

## Definition of done

- `docker compose up -d` brings up all four (`db`, `ml`, `backend`, `frontend`); the app loads at `http://localhost:3000` with **no CORS errors** (nginx proxy).
- **Markets** shows 10 coins; clicking one shows a candlestick chart that switches timeframe.
- **Signals** and **Analyst** render every coin (badges, prob/score bars, drivers, `healthSource`, disclaimer).
- **Chat** returns a cited answer when Ollama is up, and renders the refusal/empty case cleanly when it is not.
- **Paper Trades**: a MARKET BUY updates positions + trades + account; a SELL > held shows the rejection message; Reset re-seeds 10,000.
- **Performance** shows the equity curve + metrics (or a clean empty state).
- Type-check passes; the render tests pass.

## What NOT to do

- Do NOT call the backend cross-origin without the nginx/Vite proxy — you will hit CORS.
- Do NOT re-implement any business logic (scores, fills, TA, summaries) in the frontend — render what the backend returns.
- Do NOT add or change backend endpoints — the frontend is a read/act consumer.
- Do NOT hide the disclaimer; it appears on every page (PROJECT.md §9).
- Do NOT treat the refusal phrase, `INSUFFICIENT_DATA`, null `marketCapUsd`, or a flat equity curve as errors — they are valid states.
- Do NOT build demo mode / README / cards here — that is Stage 7.
