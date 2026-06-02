// Domain badges that map a backend enum string to a coloured pill. Unknown
// values fall back to a neutral 'ghost' style so nothing ever renders raw.

type Variant = 'up' | 'down' | 'flat' | 'accent' | 'warn' | 'ghost';

function Badge({ text, variant }: { text: string; variant: Variant }) {
  return <span className={`badge ${variant}`}>{text}</span>;
}

export function MlClassBadge({ value }: { value: string }) {
  const v: Variant = value === 'UP' ? 'up' : value === 'DOWN' ? 'down' : 'flat';
  return <Badge text={value} variant={v} />;
}

export function TaDirectionBadge({ value }: { value: string | null | undefined }) {
  if (!value) return <Badge text="N/A" variant="ghost" />;
  const v: Variant = value === 'BULLISH' ? 'up' : value === 'BEARISH' ? 'down' : 'flat';
  return <Badge text={value} variant={v} />;
}

export function AnalystDirectionBadge({ value }: { value: string }) {
  const map: Record<string, Variant> = {
    LEAN_BULLISH: 'up',
    LEAN_BEARISH: 'down',
    NEUTRAL: 'flat',
    CONFLICTED: 'warn',
  };
  return <Badge text={value.replace('_', ' ')} variant={map[value] ?? 'ghost'} />;
}

export function ConvictionBadge({ value }: { value: string }) {
  const map: Record<string, Variant> = { HIGH: 'accent', MEDIUM: 'ghost', LOW: 'ghost' };
  return <Badge text={`${value} conviction`} variant={map[value] ?? 'ghost'} />;
}

export function ConfidenceBadge({ value }: { value: string | null | undefined }) {
  if (!value) return null;
  const v: Variant = value === 'STRONG' ? 'accent' : value === 'MODERATE' ? 'ghost' : 'ghost';
  return <Badge text={value} variant={v} />;
}

export function HealthBadge({ value }: { value: string }) {
  const map: Record<string, Variant> = {
    IMPROVING: 'up',
    DETERIORATING: 'down',
    STABLE: 'flat',
    UNKNOWN: 'ghost',
  };
  return <Badge text={value} variant={map[value] ?? 'ghost'} />;
}

export function HealthSourceBadge({ value }: { value: string }) {
  // Transparency requirement (PROJECT.md): always surface where health came from.
  const label =
    value === 'onchain'
      ? 'on-chain'
      : value === 'coingecko'
        ? 'CoinGecko'
        : value;
  const v: Variant = value === 'onchain' ? 'accent' : value === 'coingecko' ? 'ghost' : 'ghost';
  return <Badge text={`health: ${label}`} variant={v} />;
}

export function SentimentBadge({ value }: { value: string }) {
  const map: Record<string, Variant> = {
    POSITIVE: 'up',
    NEGATIVE: 'down',
    MIXED: 'flat',
    INSUFFICIENT_DATA: 'ghost',
  };
  const label = value === 'INSUFFICIENT_DATA' ? 'insufficient data' : value.toLowerCase();
  return <Badge text={label} variant={map[value] ?? 'ghost'} />;
}

export function OrderStatusBadge({ value }: { value: string }) {
  const map: Record<string, Variant> = {
    FILLED: 'up',
    PENDING: 'warn',
    CANCELLED: 'down',
  };
  return <Badge text={value} variant={map[value] ?? 'ghost'} />;
}

export function SideBadge({ value }: { value: string }) {
  const v: Variant = value === 'BUY' ? 'up' : value === 'SELL' ? 'down' : 'ghost';
  return <Badge text={value} variant={v} />;
}
