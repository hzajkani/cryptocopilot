import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { EquityPoint } from '../api/types';
import { fmtUsd, fmtUsdCompact } from '../lib/format';

type Row = { t: number; equity: number; cash: number };

function EquityTooltip({ active, payload }: { active?: boolean; payload?: { payload: Row }[] }) {
  if (!active || !payload || payload.length === 0) return null;
  const r = payload[0].payload;
  return (
    <div className="card" style={{ padding: '8px 11px' }}>
      <div className="dim" style={{ fontSize: 11, marginBottom: 4 }}>
        {new Date(r.t).toLocaleString()}
      </div>
      <div className="mono">Equity {fmtUsd(r.equity)}</div>
      <div className="mono dim">Cash {fmtUsd(r.cash)}</div>
    </div>
  );
}

/** Recharts equity-curve line over the account_state mark-to-market log. */
export function EquityChart({ points }: { points: EquityPoint[] }) {
  const data: Row[] = points.map((p) => ({
    t: Date.parse(p.ts),
    equity: p.equity,
    cash: p.cash,
  }));

  return (
    <ResponsiveContainer width="100%" height={340}>
      <LineChart data={data} margin={{ top: 10, right: 18, bottom: 0, left: 6 }}>
        <CartesianGrid stroke="#1b222c" vertical={false} />
        <XAxis
          dataKey="t"
          type="number"
          scale="time"
          domain={['dataMin', 'dataMax']}
          tickFormatter={(t) => new Date(t as number).toLocaleDateString()}
          stroke="#6b7686"
          fontSize={11}
          minTickGap={48}
        />
        <YAxis
          tickFormatter={(v) => fmtUsdCompact(v as number)}
          stroke="#6b7686"
          fontSize={11}
          width={62}
          domain={['auto', 'auto']}
        />
        <Tooltip content={<EquityTooltip />} />
        <Line type="monotone" dataKey="equity" stroke="#4c8dff" strokeWidth={2} dot={false} />
      </LineChart>
    </ResponsiveContainer>
  );
}
