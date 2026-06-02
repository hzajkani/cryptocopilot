import { Link } from 'react-router-dom';
import type { AnalystResponse } from '../api/types';
import { fmtNum, fmtPct, fmtPctFromFraction } from '../lib/format';
import {
  AnalystDirectionBadge,
  ConvictionBadge,
  HealthBadge,
  HealthSourceBadge,
  SentimentBadge,
} from './badges';
import { Gauge, ScoreBar } from './bars';
import { Card } from './ui';

const INPUT_LABELS: Record<string, string> = {
  ml: 'ML forecast',
  ta: 'Technical',
  fundamental: 'Fundamental',
  news: 'News',
};

/**
 * One coin's fused Analyst opinion. Used on the Analyst grid (clickable, compact)
 * and the Analyst/Coin detail (expanded with the full fundamental snapshot).
 */
export function AnalystCard({
  resp,
  onClick,
  linkSymbol = false,
  expanded = false,
}: {
  resp: AnalystResponse;
  onClick?: () => void;
  linkSymbol?: boolean;
  expanded?: boolean;
}) {
  const { opinion, healthSource, disclaimer } = resp;
  const inputs = opinion.inputs;
  const fundamental = inputs.fundamental;

  return (
    <Card onClick={onClick}>
      <div className="row between" style={{ marginBottom: 12 }}>
        <h3 style={{ fontSize: 16 }}>
          {linkSymbol ? (
            <Link to={`/analyst/${opinion.symbol}`}>{opinion.symbol}</Link>
          ) : (
            opinion.symbol
          )}
        </h3>
        <div className="row gap wrap">
          <AnalystDirectionBadge value={opinion.direction} />
          <ConvictionBadge value={opinion.conviction} />
        </div>
      </div>

      <div className="row gap" style={{ marginBottom: 12 }}>
        <span className="section-title mb-0" style={{ minWidth: 78 }}>
          Agreement
        </span>
        <div style={{ flex: 1 }}>
          <Gauge value={opinion.agreementScore} />
        </div>
      </div>

      <p style={{ margin: '0 0 12px', fontSize: 13.5, lineHeight: 1.55 }}>{opinion.summary}</p>

      <div className="section-title">Score breakdown</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 12 }}>
        {inputs.scoreBreakdown.map((s) => (
          <div key={s.name}>
            <div className="row between" style={{ marginBottom: 4 }}>
              <span style={{ fontSize: 12.5, fontWeight: 600 }}>
                {INPUT_LABELS[s.name] ?? s.name}
              </span>
              <span
                className={`mono ${s.score > 0 ? 'up' : s.score < 0 ? 'down' : 'flat'}`}
                style={{ fontSize: 12.5 }}
              >
                {s.score > 0 ? '+' : ''}
                {s.score}
              </span>
            </div>
            <ScoreBar score={s.score} />
            <div className="faint" style={{ fontSize: 11.5, marginTop: 3 }}>
              {s.rationale}
            </div>
          </div>
        ))}
      </div>

      <div className="row gap wrap" style={{ marginBottom: 10 }}>
        <HealthSourceBadge value={healthSource} />
        <span className="pill mono">combined {inputs.combined > 0 ? '+' : ''}{inputs.combined}</span>
      </div>

      {expanded && (
        <>
          <div className="section-title" style={{ marginTop: 6 }}>
            Fundamental snapshot
          </div>
          <div className="row gap wrap" style={{ marginBottom: 8 }}>
            <HealthBadge value={fundamental.health} />
            <SentimentBadge value={fundamental.newsSentiment7d} />
          </div>
          <dl className="kv" style={{ marginBottom: 10 }}>
            <dt>Market dominance</dt>
            <dd>
              {fmtPct(fundamental.marketDominancePct, { decimals: 1 })}
              {fundamental.marketDominanceTrend ? (
                <span className="dim"> · {fundamental.marketDominanceTrend}</span>
              ) : null}
            </dd>
            <dt>News sentiment (7d)</dt>
            <dd>{fmtNum(fundamental.newsSentimentScore, 2)}</dd>
          </dl>
          {fundamental.reasons.length > 0 && (
            <ul className="reasons" style={{ marginBottom: 10 }}>
              {fundamental.reasons.map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}
        </>
      )}

      {opinion.citations.length > 0 && (
        <>
          <div className="section-title" style={{ marginTop: 6 }}>
            Cited headlines
          </div>
          <div className="citations" style={{ marginTop: 0, borderTop: 'none', paddingTop: 0 }}>
            {opinion.citations.map((c, i) => (
              <div className="citation" key={i}>
                <span>{c}</span>
              </div>
            ))}
          </div>
        </>
      )}

      <div className="faint" style={{ fontSize: 11, marginTop: 14, lineHeight: 1.4 }}>
        {disclaimer}
      </div>

      {/* The ML confidence threshold (τ) is shown for transparency of the scoring rule. */}
      <div className="faint" style={{ fontSize: 10.5, marginTop: 6 }}>
        τ (ML confidence threshold) = {fmtPctFromFraction(inputs.mlConfidenceThreshold, { decimals: 0 })}
      </div>
    </Card>
  );
}
