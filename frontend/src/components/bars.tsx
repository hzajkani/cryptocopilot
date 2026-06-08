import { fmtPctFromFraction } from '../lib/format';

/**
 * A stacked UP/FLAT/DOWN probability bar. Inputs are the calibrated class
 * probabilities (each may be null); the bar normalises whatever is present.
 */
export function ProbBar({
  probUp,
  probDown,
  probFlat,
}: {
  probUp: number | null;
  probDown: number | null;
  probFlat: number | null;
}) {
  const up = probUp ?? 0;
  const flat = probFlat ?? 0;
  const down = probDown ?? 0;
  const total = up + flat + down;
  if (total <= 0) {
    return <div className="probbar" aria-label="probabilities unavailable" />;
  }
  const pct = (x: number) => `${(x / total) * 100}%`;
  return (
    <div>
      <div className="probbar" role="img" aria-label="UP/FLAT/DOWN probabilities">
        <span className="seg-up" style={{ width: pct(up) }} />
        <span className="seg-flat" style={{ width: pct(flat) }} />
        <span className="seg-down" style={{ width: pct(down) }} />
      </div>
      <div className="prob-legend">
        <span>
          <i className="dot up" /> UP {fmtPctFromFraction(up, { decimals: 0 })}
        </span>
        <span>
          <i className="dot flat" /> FLAT {fmtPctFromFraction(flat, { decimals: 0 })}
        </span>
        <span>
          <i className="dot down" /> DOWN {fmtPctFromFraction(down, { decimals: 0 })}
        </span>
      </div>
    </div>
  );
}

/**
 * A taller, prominent UP/FLAT/DOWN probability bar — the core ML output. Each
 * segment carries its percentage inline when wide enough to read. Same inputs
 * as ProbBar (calibrated class probabilities, any may be null).
 */
export function ProbBarLarge({
  probUp,
  probDown,
  probFlat,
}: {
  probUp: number | null;
  probDown: number | null;
  probFlat: number | null;
}) {
  const up = probUp ?? 0;
  const flat = probFlat ?? 0;
  const down = probDown ?? 0;
  const total = up + flat + down;
  if (total <= 0) {
    return <div className="probbar-lg" aria-label="probabilities unavailable" />;
  }
  const seg = (x: number, cls: string, label: string) => {
    const pct = (x / total) * 100;
    return (
      <span className={cls} style={{ width: `${pct}%` }}>
        {pct >= 12 ? `${label} ${Math.round(pct)}%` : pct >= 6 ? `${Math.round(pct)}%` : ''}
      </span>
    );
  };
  return (
    <div className="probbar-lg" role="img" aria-label="UP/FLAT/DOWN probabilities">
      {seg(up, 'seg-up', 'UP')}
      {seg(flat, 'seg-flat', 'FLAT')}
      {seg(down, 'seg-down', 'DOWN')}
    </div>
  );
}

/** A 0..1 agreement gauge with the numeric value alongside. */
export function Gauge({ value }: { value: number }) {
  const clamped = Math.max(0, Math.min(1, value));
  return (
    <div className="gauge">
      <div className="gauge-track">
        <div className="gauge-fill" style={{ width: `${clamped * 100}%` }} />
      </div>
      <div className="gauge-val mono">{clamped.toFixed(2)}</div>
    </div>
  );
}

/**
 * A center-anchored score bar for an input on the −2..+2 scale: positive scores
 * fill green to the right of centre, negative scores fill red to the left.
 */
export function ScoreBar({ score, max = 2 }: { score: number; max?: number }) {
  const frac = Math.max(-1, Math.min(1, score / max));
  const widthPct = `${Math.abs(frac) * 50}%`;
  return (
    <div className="scorebar-track" role="img" aria-label={`score ${score}`}>
      <div className="scorebar-mid" />
      {score === 0 ? (
        <div className="scorebar-fill zero" />
      ) : frac > 0 ? (
        <div className="scorebar-fill pos" style={{ width: widthPct }} />
      ) : (
        <div className="scorebar-fill neg" style={{ width: widthPct }} />
      )}
    </div>
  );
}
