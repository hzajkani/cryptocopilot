import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAsync } from '../lib/useAsync';
import { fmtPct, fmtUsd, fmtUsdCompact, signClass } from '../lib/format';
import { Collapsible, CoinAvatar, ErrorState, PageHead, Skeleton } from '../components/ui';
import { ArchitecturePanel } from '../components/ArchitecturePanel';
import { SourceBadge } from '../components/SourceBadge';
import { IconPipeline } from '../components/icons';

export function MarketsPage() {
  const navigate = useNavigate();
  const { data, loading, error, reload } = useAsync(() => api.markets(), []);

  return (
    <>
      <div className="row between wrap" style={{ alignItems: 'flex-start', gap: 12 }}>
        <PageHead
          title="Markets"
          subtitle="The 10-coin universe — price and 24h change from 4h candles, latest market cap from CoinGecko."
        />
        <Link to="/ml" className="btn primary" style={{ flexShrink: 0, marginTop: 4 }}>
          <IconPipeline width={16} height={16} /> ML Pipeline
        </Link>
      </div>

      <Collapsible
        title="How it works — system architecture"
        subtitle="Four containers, one shared Postgres. Click to see the live data flow and index stats."
      >
        <ArchitecturePanel />
      </Collapsible>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>Coin</th>
                <th className="num">
                  <div className="row gap" style={{ justifyContent: 'flex-end' }}>
                    Price <SourceBadge source="binance" />
                  </div>
                </th>
                <th className="num">24h</th>
                <th className="num">
                  <div className="row gap" style={{ justifyContent: 'flex-end' }}>
                    Market cap <SourceBadge source="coingecko" />
                  </div>
                </th>
              </tr>
            </thead>
            <tbody>
              {loading
                ? Array.from({ length: 10 }).map((_, i) => (
                    <tr key={i}>
                      <td>
                        <Skeleton height={16} width={90} />
                      </td>
                      <td>
                        <Skeleton height={16} width={80} />
                      </td>
                      <td>
                        <Skeleton height={16} width={56} />
                      </td>
                      <td>
                        <Skeleton height={16} width={90} />
                      </td>
                    </tr>
                  ))
                : data?.map((m) => (
                    <tr
                      key={m.symbol}
                      className="clickable"
                      onClick={() => navigate(`/coins/${m.symbol}`)}
                    >
                      <td>
                        <div className="row gap">
                          <CoinAvatar symbol={m.symbol} />
                          <strong>{m.symbol}</strong>
                        </div>
                      </td>
                      <td className="num">{fmtUsd(m.price)}</td>
                      <td className={`num ${signClass(m.change24hPct)}`}>
                        {fmtPct(m.change24hPct, { signed: true })}
                      </td>
                      <td className="num">{fmtUsdCompact(m.marketCapUsd)}</td>
                    </tr>
                  ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
