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

export type ChatRequest = { query: string; symbols?: string[] };

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
