import { useState, type ReactNode } from 'react';
import { api } from '../api/client';
import { errMessage, useAsync } from '../lib/useAsync';
import { useToast } from '../components/Toast';
import { EmptyState, ErrorState, PageHead, Skeleton, Stat } from '../components/ui';
import { OrderStatusBadge, SideBadge } from '../components/badges';
import { fmtQty, fmtRelativeTime, fmtUsd, signClass } from '../lib/format';
import {
  UNIVERSE,
  type FillResult,
  type OrderSide,
  type OrderType,
} from '../api/types';

function FillBanner({ fill }: { fill: FillResult }) {
  const variant = fill.status === 'FILLED' ? 'up' : fill.status === 'PENDING' ? 'warn' : 'down';
  return (
    <div className={`badge ${variant}`} style={{ display: 'block', padding: '12px 14px', borderRadius: 10 }}>
      <div className="row gap" style={{ marginBottom: fill.status === 'FILLED' ? 6 : 0 }}>
        <OrderStatusBadge value={fill.status} />
        <strong>
          {fill.side} {fill.symbol} ({fill.type})
        </strong>
      </div>
      {fill.status === 'FILLED' ? (
        <div className="mono" style={{ fontSize: 12.5 }}>
          filled @ {fmtUsd(fill.filledPrice)} · fee {fmtUsd(fill.fees)} · realized P&L{' '}
          <span className={signClass(fill.realizedPnl)}>{fmtUsd(fill.realizedPnl)}</span>
        </div>
      ) : (
        <div style={{ fontSize: 12.5, marginTop: 4 }}>{fill.message}</div>
      )}
    </div>
  );
}

export function TradePage() {
  const toast = useToast();
  const [refreshKey, setRefreshKey] = useState(0);
  const refresh = () => setRefreshKey((k) => k + 1);

  const account = useAsync(() => api.account(), [refreshKey]);
  const positions = useAsync(() => api.positions(), [refreshKey]);
  const trades = useAsync(() => api.trades(), [refreshKey]);
  const orders = useAsync(() => api.orders(), [refreshKey]);

  // --- order ticket state ---
  const [symbol, setSymbol] = useState<string>(UNIVERSE[0]);
  const [side, setSide] = useState<OrderSide>('BUY');
  const [type, setType] = useState<OrderType>('MARKET');
  const [quantity, setQuantity] = useState('');
  const [limitPrice, setLimitPrice] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [lastFill, setLastFill] = useState<FillResult | null>(null);

  const qtyNum = Number(quantity);
  const limitNum = Number(limitPrice);
  const qtyValid = quantity !== '' && Number.isFinite(qtyNum) && qtyNum > 0;
  const limitValid = type === 'MARKET' || (limitPrice !== '' && Number.isFinite(limitNum) && limitNum > 0);
  const canSubmit = qtyValid && limitValid && !submitting;

  async function submit() {
    if (!canSubmit) return;
    setSubmitting(true);
    try {
      const fill = await api.submitOrder({
        symbol,
        side,
        type,
        quantity: qtyNum,
        limitPrice: type === 'LIMIT' ? limitNum : undefined,
      });
      setLastFill(fill);
      if (fill.status === 'FILLED') toast.showSuccess(`Filled ${side} ${symbol} @ ${fmtUsd(fill.filledPrice)}`);
      else if (fill.status === 'PENDING') toast.showInfo(`Order pending: ${fill.message}`);
      else toast.showError(fill.message);
      refresh();
    } catch (err) {
      toast.showError(errMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function reset() {
    if (!window.confirm('Reset the paper account? This wipes positions, trades and orders, and re-seeds 10,000 USD.')) {
      return;
    }
    try {
      await api.resetAccount(0); // 0 -> backend default 10,000
      setLastFill(null);
      toast.showSuccess('Account reset to 10,000 USD.');
      refresh();
    } catch (err) {
      toast.showError(errMessage(err));
    }
  }

  return (
    <>
      <PageHead title="Paper Trades" subtitle="Long-only market/limit orders against the live OHLCV grid. No real money, ever." />

      {/* account header */}
      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', marginBottom: 18 }}>
        {account.error ? (
          <ErrorState message={account.error} onRetry={account.reload} />
        ) : account.loading || !account.data ? (
          <>
            <Skeleton height={78} />
            <Skeleton height={78} />
          </>
        ) : (
          <>
            <Stat label="Cash" value={fmtUsd(account.data.cashUsd)} />
            <Stat
              label="Total equity"
              value={fmtUsd(account.data.totalEquityUsd)}
              sub={`as of ${fmtRelativeTime(account.data.tsUtc)}`}
            />
            <div className="stat" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <button className="btn ghost" onClick={reset}>
                Reset account
              </button>
            </div>
          </>
        )}
      </div>

      <div className="grid" style={{ gridTemplateColumns: 'minmax(280px, 360px) 1fr', alignItems: 'start' }}>
        {/* order ticket */}
        <div className="card">
          <h3 style={{ fontSize: 15, marginBottom: 14 }}>Order ticket</h3>

          <label className="field" style={{ marginBottom: 12 }}>
            Coin
            <select className="input" value={symbol} onChange={(e) => setSymbol(e.target.value)}>
              {UNIVERSE.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>

          <div className="field" style={{ marginBottom: 12 }}>
            Side
            <div className="seg" style={{ width: '100%' }}>
              <button
                style={{ flex: 1 }}
                className={side === 'BUY' ? 'active' : ''}
                onClick={() => setSide('BUY')}
              >
                BUY
              </button>
              <button
                style={{ flex: 1 }}
                className={side === 'SELL' ? 'active' : ''}
                onClick={() => setSide('SELL')}
              >
                SELL
              </button>
            </div>
          </div>

          <div className="field" style={{ marginBottom: 12 }}>
            Type
            <div className="seg" style={{ width: '100%' }}>
              <button
                style={{ flex: 1 }}
                className={type === 'MARKET' ? 'active' : ''}
                onClick={() => setType('MARKET')}
              >
                MARKET
              </button>
              <button
                style={{ flex: 1 }}
                className={type === 'LIMIT' ? 'active' : ''}
                onClick={() => setType('LIMIT')}
              >
                LIMIT
              </button>
            </div>
          </div>

          <label className="field" style={{ marginBottom: 12 }}>
            Quantity
            <input
              className="input"
              type="number"
              min="0"
              step="any"
              inputMode="decimal"
              placeholder="0.00"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
          </label>

          <label className="field" style={{ marginBottom: 16 }}>
            Limit price {type === 'MARKET' && <span className="faint">(LIMIT only)</span>}
            <input
              className="input"
              type="number"
              min="0"
              step="any"
              inputMode="decimal"
              placeholder={type === 'LIMIT' ? 'required' : '—'}
              value={type === 'LIMIT' ? limitPrice : ''}
              disabled={type === 'MARKET'}
              onChange={(e) => setLimitPrice(e.target.value)}
            />
          </label>

          <button
            className={`btn ${side === 'BUY' ? 'buy' : 'sell'}`}
            style={{ width: '100%' }}
            disabled={!canSubmit}
            onClick={submit}
          >
            {submitting ? 'Submitting…' : `${side} ${symbol}`}
          </button>

          {lastFill && (
            <div style={{ marginTop: 14 }}>
              <FillBanner fill={lastFill} />
            </div>
          )}
        </div>

        {/* blotters */}
        <div className="col" style={{ gap: 18 }}>
          <section>
            <div className="section-title">Positions</div>
            <Blotter
              state={positions}
              empty="No open positions."
              head={['Coin', 'Size', 'Avg entry', 'Opened']}
              row={(p) => [
                <strong>{p.symbol}</strong>,
                <span className="num">{fmtQty(p.size)}</span>,
                <span className="num">{fmtUsd(p.avgEntryPrice)}</span>,
                <span className="dim">{fmtRelativeTime(p.openedAt)}</span>,
              ]}
              keyOf={(p) => p.symbol}
            />
          </section>

          <section>
            <div className="section-title">Trades (newest first)</div>
            <Blotter
              state={trades}
              empty="No trades yet."
              head={['When', 'Coin', 'Side', 'Qty', 'Price', 'Fees', 'Realized P&L']}
              row={(t) => [
                <span className="dim">{fmtRelativeTime(t.tsUtc)}</span>,
                <strong>{t.symbol}</strong>,
                <SideBadge value={t.side} />,
                <span className="num">{fmtQty(t.quantity)}</span>,
                <span className="num">{fmtUsd(t.price)}</span>,
                <span className="num">{fmtUsd(t.fees)}</span>,
                <span className={`num ${signClass(t.realizedPnl)}`}>{fmtUsd(t.realizedPnl)}</span>,
              ]}
              keyOf={(t) => t.id}
            />
          </section>

          <section>
            <div className="section-title">Orders (newest first)</div>
            <Blotter
              state={orders}
              empty="No orders yet."
              head={['Submitted', 'Coin', 'Side', 'Type', 'Qty', 'Limit', 'Status', 'Fill']}
              row={(o) => [
                <span className="dim">{fmtRelativeTime(o.tsSubmitted)}</span>,
                <strong>{o.symbol}</strong>,
                <SideBadge value={o.side} />,
                <span className="dim">{o.type}</span>,
                <span className="num">{fmtQty(o.quantity)}</span>,
                <span className="num">{o.limitPrice == null ? '—' : fmtUsd(o.limitPrice)}</span>,
                <OrderStatusBadge value={o.status} />,
                <span className="num">{o.filledPrice == null ? '—' : fmtUsd(o.filledPrice)}</span>,
              ]}
              keyOf={(o) => o.id}
            />
          </section>
        </div>
      </div>
    </>
  );
}

// A small generic table bound to a useAsync result, with loading/error/empty states.
function Blotter<T>({
  state,
  head,
  row,
  keyOf,
  empty,
}: {
  state: { data: T[] | null; loading: boolean; error: string | null; reload: () => void };
  head: string[];
  row: (item: T) => ReactNode[];
  keyOf: (item: T) => string;
  empty: string;
}) {
  if (state.error) return <ErrorState message={state.error} onRetry={state.reload} />;
  if (state.loading) return <Skeleton height={90} />;
  if (!state.data || state.data.length === 0) {
    return <EmptyState title={empty} emoji="📭" />;
  }
  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            {head.map((h, i) => (
              <th key={i} className={i === 0 ? '' : 'num'}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {state.data.map((item) => (
            <tr key={keyOf(item)}>
              {row(item).map((cell, i) => (
                <td key={i} className={i === 0 ? '' : 'num'}>
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
