import { Fragment } from 'react';
import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { Signal } from '../api/types';
import { featureLabel } from '../lib/featureLabels';
import { SourceBadge } from './SourceBadge';

// A static, self-explaining picture of the Python ML pipeline (PROJECT.md §2,
// STATE.md Stage 2). The SHAP chart is the one live piece — it aggregates the
// per-prediction drivers already loaded on the Signals page (no extra fetch).

type Stage = { title: string; sub: string[]; terminal?: boolean; binance?: boolean };

const STAGES: Stage[] = [
  { title: 'Raw OHLCV', sub: ['226,200 rows', '10 coins · 2y'], binance: true },
  { title: '46 Features', sub: ['Python / pandas', 'indicators.py', 'ichimoku.py'] },
  { title: 'XGBoost', sub: ['multi:softprob', 'Optuna · 40 trials', 'depth=4 · lr=0.029'] },
  { title: 'Isotonic Calibration', sub: ['val set only', 'Brier 0.608'] },
  { title: 'Weighted Argmax', sub: ['w = [DOWN 1.5,', 'FLAT 1.0, UP 1.5]'] },
  { title: 'UP / FLAT / DOWN', sub: ['+ calibrated', 'probabilities'], terminal: true },
];

type Metric = { name: string; value: string; status: 'ok' | 'warn'; note: string };

const METRICS: Metric[] = [
  { name: 'Macro ROC-AUC', value: '0.578', status: 'ok', note: 'Target: 0.55–0.62' },
  { name: 'Multiclass Brier', value: '0.608', status: 'ok', note: 'Target: ≤ 0.65' },
  { name: 'Macro F1', value: '0.375', status: 'warn', note: 'Target: ≥ 0.40 (data-limited)' },
];

type ShapRow = { featureName: string; value: number };

/** Mean absolute SHAP per feature across every loaded coin's top drivers; top 8. */
function aggregateShap(signals: Signal[] | null | undefined): ShapRow[] {
  if (!signals?.length) return [];
  const acc = new Map<string, { sum: number; n: number }>();
  for (const s of signals) {
    for (const d of s.drivers) {
      if (d.shapValue == null || !d.featureName) continue;
      const cur = acc.get(d.featureName) ?? { sum: 0, n: 0 };
      cur.sum += Math.abs(d.shapValue);
      cur.n += 1;
      acc.set(d.featureName, cur);
    }
  }
  return [...acc.entries()]
    .map(([featureName, { sum, n }]) => ({ featureName, value: sum / n }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 8);
}

export function MlPipelineDiagram({ signals }: { signals?: Signal[] | null }) {
  const shap = aggregateShap(signals);

  return (
    <>
      <div className="pipeline">
        {STAGES.map((st, i) => (
          <Fragment key={st.title}>
            <div className={`pipe-stage ${st.terminal ? 'terminal' : ''}`}>
              <div className="pipe-title">{st.title}</div>
              {st.binance && (
                <div>
                  <SourceBadge source="binance" />
                </div>
              )}
              <div className="pipe-sub">
                {st.sub.map((line, j) => (
                  <div key={j}>{line}</div>
                ))}
              </div>
            </div>
            {i < STAGES.length - 1 && <div className="pipe-arrow">→</div>}
          </Fragment>
        ))}
      </div>

      <div className="metric-grid">
        {METRICS.map((m) => (
          <div className="metric-card" key={m.name}>
            <div className="m-head">
              <span className={`status-dot ${m.status}`} /> {m.name}
            </div>
            <div className="m-value">{m.value}</div>
            <div className="m-note">
              {m.status === 'ok' ? '✅' : '⚠️'} {m.note}
            </div>
          </div>
        ))}
      </div>

      <div className="section-title" style={{ marginTop: 18 }}>
        Top features by mean |SHAP|{signals ? ` · ${signals.length} coins` : ''}
      </div>
      {shap.length === 0 ? (
        <p className="dim" style={{ fontSize: 12.5 }}>
          No SHAP driver data loaded yet — run the predict job on the ML Pipeline page.
        </p>
      ) : (
        <div className="shap-chart">
          <ResponsiveContainer width="100%" height={Math.max(170, shap.length * 28)}>
            <BarChart
              data={shap}
              layout="vertical"
              margin={{ top: 4, right: 22, bottom: 4, left: 8 }}
            >
              <XAxis type="number" stroke="#6b7686" fontSize={10} tickLine={false} />
              <YAxis
                type="category"
                dataKey="featureName"
                tickFormatter={featureLabel}
                width={168}
                stroke="#9aa6b2"
                fontSize={10.5}
                tickLine={false}
                axisLine={false}
              />
              <Tooltip
                cursor={{ fill: 'rgba(76,141,255,0.08)' }}
                contentStyle={{
                  background: '#161c25',
                  border: '1px solid #232c38',
                  borderRadius: 8,
                  fontSize: 12,
                }}
                labelFormatter={(v) => featureLabel(String(v))}
                formatter={(v: number) => [v.toFixed(4), 'mean |SHAP|']}
              />
              <Bar dataKey="value" radius={[0, 3, 3, 0]} isAnimationActive={false}>
                {shap.map((_, i) => (
                  <Cell key={i} fill="#4c8dff" />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </>
  );
}
