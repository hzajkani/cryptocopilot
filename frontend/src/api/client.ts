// A small typed fetch client — one function per backend endpoint, all returning
// the types in ./types. The base URL is RELATIVE (`/api`) in every mode: in dev
// the Vite proxy forwards it to the backend, in prod nginx does (no CORS, no
// hardcoded host — PROJECT.md §2 / Stage 6 brief). Non-2xx responses throw a
// single ApiError type, which the UI surfaces through one toast/banner path.

import type {
  AccountState,
  AnalystResponse,
  AnswerWithCitations,
  Candle,
  ChatRequest,
  FillResult,
  Market,
  Order,
  OrderRequest,
  PerformanceReport,
  Position,
  Signal,
  TAVerdict,
  Trade,
} from './types';

const BASE = '/api';

/** A failed (non-2xx or network) API call. `status` is 0 for a network/transport error. */
export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let detail = '';
    try {
      detail = (await res.text()).trim();
    } catch {
      /* body already consumed or empty */
    }
    throw new ApiError(detail || `${res.status} ${res.statusText}`, res.status);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

async function get<T>(path: string): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, { headers: { Accept: 'application/json' } });
  } catch {
    throw new ApiError('Network error — is the backend reachable?', 0);
  }
  return handle<T>(res);
}

async function post<T>(path: string, body?: unknown): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError('Network error — is the backend reachable?', 0);
  }
  return handle<T>(res);
}

export const api = {
  // --- market data ---
  markets: () => get<Market[]>('/markets'),
  ohlcv: (symbol: string, timeframe: string, from?: string, to?: string) => {
    const qs = new URLSearchParams({ timeframe });
    if (from) qs.set('from', from);
    if (to) qs.set('to', to);
    return get<Candle[]>(`/coins/${encodeURIComponent(symbol)}/ohlcv?${qs.toString()}`);
  },

  // --- signals + TA ---
  signals: () => get<Signal[]>('/signals'),
  ta: (symbol: string) => get<TAVerdict>(`/ta/${encodeURIComponent(symbol)}`),

  // --- researcher (RAG) ---
  chat: (req: ChatRequest) => post<AnswerWithCitations>('/chat', req),
  ragStatus: () => get<Record<string, number>>('/rag/status'),
  ragReindex: () => post<Record<string, number>>('/rag/reindex'),

  // --- analyst ---
  analystAll: () => get<AnalystResponse[]>('/analyst'),
  analyst: (symbol: string) => get<AnalystResponse>(`/analyst/${encodeURIComponent(symbol)}`),

  // --- paper trading ---
  submitOrder: (req: OrderRequest) => post<FillResult>('/orders', req),
  orders: () => get<Order[]>('/orders'),
  positions: () => get<Position[]>('/positions'),
  trades: () => get<Trade[]>('/trades'),
  account: () => get<AccountState>('/account'),
  performance: () => get<PerformanceReport>('/performance'),
  resetAccount: (startingBalance = 0) =>
    post<AccountState>(`/account/reset?startingBalance=${startingBalance}`),
};

export type Api = typeof api;
