import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';
import { useAsync } from '../lib/useAsync';
import { CandleChart } from '../components/CandleChart';
import { SignalCard } from '../components/SignalCard';
import { AnalystCard } from '../components/AnalystCard';
import { SourceBadge } from '../components/SourceBadge';
import { IconBack } from '../components/icons';
import { EmptyState, ErrorState, PageHead, Skeleton } from '../components/ui';

const TIMEFRAMES = ['1h', '4h', '1d'] as const;
type Timeframe = (typeof TIMEFRAMES)[number];

export function CoinDetailPage() {
  const { symbol = '' } = useParams();
  const sym = symbol.toUpperCase();
  const [tf, setTf] = useState<Timeframe>('4h');

  const candles = useAsync(() => api.ohlcv(sym, tf), [sym, tf]);
  // There is no single-signal endpoint; the signals list carries the TA verdict too.
  const signal = useAsync(async () => {
    const all = await api.signals();
    return all.find((s) => s.symbol === sym) ?? null;
  }, [sym]);
  const analyst = useAsync(() => api.analyst(sym), [sym]);

  return (
    <>
      <Link to="/" className="back-link">
        <IconBack width={15} height={15} /> Markets
      </Link>
      <PageHead title={sym} subtitle="Candles, the fused signal + TA verdict, and the Analyst opinion." />

      <div className="chart-box" style={{ marginBottom: 18 }}>
        <div className="row between" style={{ marginBottom: 12 }}>
          <div className="row gap">
            <h3 style={{ fontSize: 15 }}>Price ({tf})</h3>
            <SourceBadge source="binance" />
          </div>
          <div className="seg">
            {TIMEFRAMES.map((t) => (
              <button key={t} className={t === tf ? 'active' : ''} onClick={() => setTf(t)}>
                {t}
              </button>
            ))}
          </div>
        </div>

        {candles.error ? (
          <ErrorState message={candles.error} onRetry={candles.reload} />
        ) : candles.loading ? (
          <Skeleton height={380} />
        ) : candles.data && candles.data.length > 0 ? (
          <CandleChart candles={candles.data} />
        ) : (
          <EmptyState title="No candles for this range" emoji="📉">
            The backend returned no OHLCV rows for {sym} on the {tf} timeframe.
          </EmptyState>
        )}
      </div>

      <div className="grid cards">
        {signal.error ? (
          <ErrorState message={signal.error} onRetry={signal.reload} />
        ) : signal.loading ? (
          <Skeleton height={320} />
        ) : signal.data ? (
          <SignalCard signal={signal.data} linkSymbol={false} />
        ) : (
          <EmptyState title="No signal" emoji="🤖">
            No ML/TA signal is available for {sym} yet.
          </EmptyState>
        )}

        {analyst.error ? (
          <ErrorState message={analyst.error} onRetry={analyst.reload} />
        ) : analyst.loading || !analyst.data ? (
          <Skeleton height={320} />
        ) : (
          <AnalystCard resp={analyst.data} expanded />
        )}
      </div>
    </>
  );
}
