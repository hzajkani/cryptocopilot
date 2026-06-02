// Display formatters. Every one renders a graceful em-dash for null/NaN so the
// UI never shows "null" or "NaN" (many backend fields are legitimately sparse —
// Stage 6 brief). Note the two flavours of percent: backend `change24hPct` and
// `marketDominancePct` are ALREADY percent values (-2.5 = -2.5%), whereas
// `winRate` / `maxDrawdownPct` / `totalReturnPct` are FRACTIONS (0.12 = 12%).

export const DASH = '—';

function isNum(n: unknown): n is number {
  return typeof n === 'number' && Number.isFinite(n);
}

/** USD with adaptive decimals (2 for >= $1, 6 for sub-dollar coins). */
export function fmtUsd(n: number | null | undefined, decimals?: number): string {
  if (!isNum(n)) return DASH;
  const d = decimals ?? (Math.abs(n) >= 1 ? 2 : 6);
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: d,
    maximumFractionDigits: d,
  }).format(n);
}

/** Compact USD for big figures: $1.23B, $950.4M, $12.3K. */
export function fmtUsdCompact(n: number | null | undefined): string {
  if (!isNum(n)) return DASH;
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 2,
  }).format(n);
}

export function fmtNum(n: number | null | undefined, decimals = 2): string {
  if (!isNum(n)) return DASH;
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(n);
}

/** A coin quantity: trims trailing zeros, caps at 8 decimals. */
export function fmtQty(n: number | null | undefined): string {
  if (!isNum(n)) return DASH;
  return String(parseFloat(n.toFixed(8)));
}

type PctOpts = { signed?: boolean; decimals?: number };

/** Format a value that is ALREADY a percent (e.g. -2.5 -> "-2.50%"). */
export function fmtPct(n: number | null | undefined, opts: PctOpts = {}): string {
  if (!isNum(n)) return DASH;
  const d = opts.decimals ?? 2;
  const sign = opts.signed && n > 0 ? '+' : '';
  return `${sign}${n.toFixed(d)}%`;
}

/** Format a FRACTION as a percent (e.g. 0.12 -> "12.00%"). */
export function fmtPctFromFraction(n: number | null | undefined, opts: PctOpts = {}): string {
  return fmtPct(isNum(n) ? n * 100 : n, opts);
}

/** Direction class for colouring: positive -> up, negative -> down, else flat. */
export function signClass(n: number | null | undefined): 'up' | 'down' | 'flat' {
  if (!isNum(n) || n === 0) return 'flat';
  return n > 0 ? 'up' : 'down';
}

const RT_UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 31_536_000],
  ['month', 2_592_000],
  ['week', 604_800],
  ['day', 86_400],
  ['hour', 3_600],
  ['minute', 60],
  ['second', 1],
];

/** "3h ago", "yesterday", "in 2 days". */
export function fmtRelativeTime(iso: string | null | undefined): string {
  if (!iso) return DASH;
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return DASH;
  const diffSec = (t - Date.now()) / 1000;
  const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
  for (const [unit, secs] of RT_UNITS) {
    if (Math.abs(diffSec) >= secs || unit === 'second') {
      return rtf.format(Math.round(diffSec / secs), unit);
    }
  }
  return DASH;
}

export function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return DASH;
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return DASH;
  return new Date(t).toLocaleString();
}

export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return DASH;
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return DASH;
  return new Date(t).toLocaleDateString();
}
