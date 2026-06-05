// TypeScript mirror of the backend's JSON contract (verified against the Java
// records in com.cryptocopilot.{dto,rag,analyst,trading}). camelCase keys;
// timestamps are ISO-8601 strings. Many fields are legitimately null/sparse —
// see the Stage 6 brief "two facts that shape the UI".

export type Market = {
  symbol: string;
  price: number | null;
  change24hPct: number | null; // already a percent value, e.g. -2.5 means -2.5%
  marketCapUsd: number | null;
};

export type Candle = {
  ts: string;
  open: number | null;
  high: number | null;
  low: number | null;
  close: number | null;
  volume: number | null;
};

export type Driver = {
  rank: number;
  featureName: string;
  featureValue: number | null;
  shapValue: number | null;
};

export type TADirection = 'BULLISH' | 'BEARISH' | 'NEUTRAL';
export type TAConfidence = 'STRONG' | 'MODERATE' | 'WEAK';

export type TAVerdict = {
  symbol: string;
  tsUtc: string;
  direction: TADirection;
  confidence: TAConfidence;
  signals: string[];
  score: number;
};

export type MlClass = 'UP' | 'DOWN' | 'FLAT';

export type Signal = {
  symbol: string;
  ts: string;
  mlClass: MlClass;
  mlConfidence: number | null;
  probUp: number | null;
  probDown: number | null;
  probFlat: number | null;
  modelVersion: string;
  drivers: Driver[];
  ta: TAVerdict;
};

// 'ollama' (free local default) or 'openai' (gpt-4o-mini). Mirrors the backend LlmProvider.
export type LlmProviderId = 'ollama' | 'openai';

export type ChatRequest = { query: string; symbols?: string[]; provider?: LlmProviderId };

/** GET /api/llm/providers — which providers can answer right now; `default` is always 'ollama'. */
export type LlmProviders = { default: LlmProviderId; ollama: boolean; openai: boolean };

export type Citation = {
  number: number;
  sourceType: string;
  symbol: string | null;
  source: string | null;
  url: string | null;
  tsUtc: string | null;
  snippet: string;
};

export type RetrievedChunk = {
  number: number;
  id: string;
  content: string;
  sourceType: string;
  symbol: string | null;
  source: string | null;
  url: string | null;
  tsUtc: string | null;
  section: string | null;
  similarity: number;
  score: number;
};

export type AnswerWithCitations = {
  answer: string;
  citations: Citation[];
  retrievedChunks: RetrievedChunk[];
  latencyMs: number;
  queryClassification: string;
  provider?: LlmProviderId; // the model that actually answered (may differ from requested on fallback)
};

export type FundamentalHealth = 'IMPROVING' | 'STABLE' | 'DETERIORATING' | 'UNKNOWN';
export type HealthSource = 'onchain' | 'coingecko' | 'unknown';

export type FundamentalSnapshot = {
  symbol: string;
  tsUtc: string;
  health: FundamentalHealth;
  healthSource: HealthSource;
  reasons: string[];
  marketDominancePct: number | null; // already a percent value
  marketDominanceTrend: string | null;
  newsSentiment7d: string; // POSITIVE | MIXED | NEGATIVE | INSUFFICIENT_DATA
  newsSentimentScore: number;
};

export type InputScore = { name: string; score: number; rationale: string };

export type AnalystInputs = {
  ml: { class: string; confidence: number | null; score: number };
  ta: { direction: string | null; confidence: string | null; score: number; signals: string[] };
  fundamental: FundamentalSnapshot;
  news: { label: string; score: number };
  scoreBreakdown: InputScore[];
  combined: number;
  agreementScore: number;
  mlConfidenceThreshold: number;
};

export type AnalystDirection = 'LEAN_BULLISH' | 'LEAN_BEARISH' | 'NEUTRAL' | 'CONFLICTED';
export type AnalystConviction = 'HIGH' | 'MEDIUM' | 'LOW';

export type AnalystOpinion = {
  symbol: string;
  tsUtc: string;
  direction: AnalystDirection;
  conviction: AnalystConviction;
  summary: string;
  agreementScore: number;
  inputs: AnalystInputs;
  citations: string[];
};

export type AnalystResponse = {
  opinion: AnalystOpinion;
  healthSource: string;
  disclaimer: string;
};

export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'MARKET' | 'LIMIT';
export type OrderStatus = 'FILLED' | 'PENDING' | 'CANCELLED';

export type OrderRequest = {
  symbol: string;
  side: OrderSide;
  type: OrderType;
  quantity: number;
  limitPrice?: number | null;
};

export type FillResult = {
  orderId: string | null;
  symbol: string;
  side: string;
  type: string;
  status: OrderStatus;
  filledPrice: number | null;
  fees: number | null;
  realizedPnl: number | null;
  tsFilled: string | null;
  message: string;
};

export type Position = {
  symbol: string;
  size: number;
  avgEntryPrice: number;
  openedAt: string;
};

export type Trade = {
  id: string;
  tsUtc: string;
  symbol: string;
  side: string;
  quantity: number;
  price: number;
  fees: number;
  realizedPnl: number;
  notes: string | null;
};

export type Order = {
  id: string;
  tsSubmitted: string;
  tsFilled: string | null;
  symbol: string;
  side: string;
  type: string;
  quantity: number;
  limitPrice: number | null;
  status: string;
  filledPrice: number | null;
  fees: number | null;
};

export type AccountState = {
  tsUtc: string;
  cashUsd: number;
  totalEquityUsd: number;
};

export type EquityPoint = { ts: string; equity: number; cash: number };

export type TradingMetrics = {
  sharpe: number;
  sortino: number;
  maxDrawdownPct: number; // fraction (0.12 = 12%)
  winRate: number; // fraction
  avgWin: number;
  avgLoss: number;
  totalTrades: number;
  totalFees: number;
  finalEquity: number;
  totalReturnPct: number; // fraction
};

export type PerformanceReport = { equityCurve: EquityPoint[]; metrics: TradingMetrics };

// ===== ML pipeline (mirrors the Python FastAPI payloads in ml/ml/api.py) =====
// Ingest/train/predict run as background jobs: POST returns a job, then poll
// /api/ml/jobs/{id} until `state` is terminal.

export type MlJobKind = 'ingest' | 'train' | 'predict';
export type MlJobState = 'running' | 'success' | 'error';

/** Test-set metrics from a training run (snake_case keys are the metric names). */
export type TrainMetrics = { macro_f1: number; macro_auc: number; brier: number };

/** Per-source row counts from an ingestion run (-1 = that source failed and was skipped). */
export type IngestResult = {
  counts: Record<string, number>;
  total: number;
  tables: Record<string, number>;
};

export type TrainResult = {
  modelVersion: string | null;
  timeframe: string | null;
  trainedAt: string | null;
  featureCount: number;
  decisionWeights: number[] | null;
  metrics: {
    test: TrainMetrics | null;
    perSymbolMacroF1: Record<string, number> | null;
    baseline: Record<string, number> | null;
    backtest: Record<string, number> | null;
    bestParams: Record<string, number> | null;
  };
  splits: Record<string, { start?: string; end?: string; rows?: number; note?: string }> | null;
};

/** One row of the `predictions` table (snake_case — straight from SQL). */
export type MlPrediction = {
  symbol: string;
  ts_utc: string;
  timeframe: string;
  pred_class: MlClass | null;
  prob_up: number | null;
  prob_flat: number | null;
  prob_down: number | null;
  model_version: string | null;
  created_at: string | null;
};

export type PredictResult = {
  written: { predictions: number; drivers: number };
  predictions: MlPrediction[];
};

export type MlJob = {
  id: string;
  kind: MlJobKind;
  state: MlJobState;
  startedAt: string;
  finishedAt: string | null;
  durationSec: number;
  result: IngestResult | TrainResult | PredictResult | null;
  error: string | null;
};

export type OhlcvFreshness = { symbol: string; latest: string | null; bars: number };

export type MlModelInfo = {
  version: string;
  exists: boolean;
  trainedAt?: string | null;
  timeframe?: string | null;
  featureCount?: number;
  test?: TrainMetrics | null;
  splits?: Record<string, { start?: string; end?: string; rows?: number; note?: string }>;
};

export type MlStatus = {
  timeframe: string;
  tables: Record<string, number>;
  ohlcv: OhlcvFreshness[];
  model: MlModelInfo;
  predictions: MlPrediction[];
  activeJob: MlJob | null;
  // "When did each stage last run" (ISO timestamps, null if never):
  lastIngestedAt: string | null; // newest ingested datapoint
  lastTrainedAt: string | null; // model training time
  lastPredictedAt: string | null; // most recent prediction write
};

/** The fixed 10-coin universe, in the backend's canonical display order (util.Symbols). */
export const UNIVERSE = [
  'BTC',
  'ETH',
  'SOL',
  'BNB',
  'XRP',
  'ADA',
  'AVAX',
  'DOT',
  'LINK',
  'MATIC',
] as const;
