import { Link } from 'react-router-dom';
import type { Signal } from '../api/types';
import { fmtNum, fmtPctFromFraction, signClass } from '../lib/format';
import { ConfidenceBadge, MlClassBadge, TaDirectionBadge } from './badges';
import { ProbBar } from './bars';
import { Card } from './ui';

function signed(n: number | null, decimals = 3): string {
  if (n == null || !Number.isFinite(n)) return '—';
  return `${n > 0 ? '+' : ''}${fmtNum(n, decimals)}`;
}

/** One coin's fused ML + TA signal. Reused on the Signals page and Coin detail. */
export function SignalCard({ signal, linkSymbol = true }: { signal: Signal; linkSymbol?: boolean }) {
  const ta = signal.ta;
  return (
    <Card>
      <div className="row between" style={{ marginBottom: 12 }}>
        <h3 style={{ fontSize: 16 }}>
          {linkSymbol ? <Link to={`/coins/${signal.symbol}`}>{signal.symbol}</Link> : signal.symbol}
        </h3>
        <div className="row gap">
          <MlClassBadge value={signal.mlClass} />
          <span className="mono dim" style={{ fontSize: 12 }}>
            {fmtPctFromFraction(signal.mlConfidence, { decimals: 1 })}
          </span>
        </div>
      </div>

      <ProbBar probUp={signal.probUp} probDown={signal.probDown} probFlat={signal.probFlat} />

      <div className="faint" style={{ fontSize: 11, marginTop: 8 }}>
        model {signal.modelVersion || 'n/a'}
      </div>

      <div className="section-title" style={{ marginTop: 14 }}>
        Top drivers (SHAP)
      </div>
      {signal.drivers.length === 0 ? (
        <div className="dim" style={{ fontSize: 12.5 }}>
          No driver attribution available.
        </div>
      ) : (
        <div>
          {signal.drivers.map((d) => (
            <div className="listrow" key={d.rank}>
              <span className="dim mono" style={{ fontSize: 11 }}>
                #{d.rank}
              </span>
              <span style={{ flex: 1 }}>{d.featureName}</span>
              <span className={`mono ${signClass(d.shapValue)}`}>{signed(d.shapValue)}</span>
            </div>
          ))}
        </div>
      )}

      <div className="section-title" style={{ marginTop: 14 }}>
        Technical analysis (ta4j)
      </div>
      <div className="row gap wrap" style={{ marginBottom: 8 }}>
        <TaDirectionBadge value={ta?.direction} />
        <ConfidenceBadge value={ta?.confidence} />
        <span className="pill mono">score {fmtNum(ta?.score ?? null, 1)}</span>
      </div>
      {ta && ta.signals.length > 0 ? (
        <div className="signal-tags">
          {ta.signals.map((s, i) => (
            <span className="tag" key={i}>
              {s}
            </span>
          ))}
        </div>
      ) : (
        <div className="dim" style={{ fontSize: 12.5 }}>
          No active TA rules.
        </div>
      )}
    </Card>
  );
}
