import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useAsync } from '../lib/useAsync';
import { EquityChart } from '../components/EquityChart';
import { EmptyState, ErrorState, PageHead, Skeleton, Stat } from '../components/ui';
import { fmtNum, fmtPctFromFraction, fmtUsd, signClass } from '../lib/format';

export function PerformancePage() {
  const { data, loading, error, reload } = useAsync(() => api.performance(), []);

  if (error) return (
    <>
      <PageHead title="Performance" />
      <ErrorState message={error} onRetry={reload} />
    </>
  );

  if (loading || !data) {
    return (
      <>
        <PageHead title="Performance" />
        <Skeleton height={340} />
        <div style={{ height: 18 }} />
        <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))' }}>
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} height={78} />
          ))}
        </div>
      </>
    );
  }

  const { equityCurve, metrics } = data;
  const hasCurve = equityCurve.length >= 2;

  return (
    <>
      <PageHead
        title="Performance"
        subtitle="Mark-to-market equity curve and risk/return metrics over your paper-trading account."
      />

      <div className="chart-box" style={{ marginBottom: 18 }}>
        {hasCurve ? (
          <EquityChart points={equityCurve} />
        ) : (
          <EmptyState title="No trades yet" emoji="🌱">
            Your equity curve is flat and the metrics are zero until you place a paper order.{' '}
            <Link to="/trade">Place one on the Paper Trades page →</Link>
          </EmptyState>
        )}
      </div>

      <div
        className="grid"
        style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))' }}
      >
        <Stat label="Final equity" value={fmtUsd(metrics.finalEquity)} />
        <Stat
          label="Total return"
          value={fmtPctFromFraction(metrics.totalReturnPct, { signed: true })}
          className={signClass(metrics.totalReturnPct)}
        />
        <Stat label="Sharpe" value={fmtNum(metrics.sharpe, 2)} className={signClass(metrics.sharpe)} />
        <Stat label="Sortino" value={fmtNum(metrics.sortino, 2)} className={signClass(metrics.sortino)} />
        <Stat label="Max drawdown" value={fmtPctFromFraction(metrics.maxDrawdownPct)} />
        <Stat label="Win rate" value={fmtPctFromFraction(metrics.winRate)} />
        <Stat label="Avg win" value={fmtUsd(metrics.avgWin)} className="up" />
        <Stat label="Avg loss" value={fmtUsd(metrics.avgLoss)} className="down" />
        <Stat label="Total trades" value={metrics.totalTrades} />
        <Stat label="Total fees" value={fmtUsd(metrics.totalFees)} />
      </div>

      {metrics.totalTrades === 0 && hasCurve && (
        <p className="dim" style={{ fontSize: 12.5, marginTop: 14 }}>
          The default backtest makes 0 trades by design (STATE.md). Place paper orders to populate
          these metrics.
        </p>
      )}
    </>
  );
}
