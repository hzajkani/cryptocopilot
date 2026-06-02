import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAsync } from '../lib/useAsync';
import { fmtPct, fmtUsd, fmtUsdCompact, signClass } from '../lib/format';
import { CoinAvatar, ErrorState, PageHead, Skeleton } from '../components/ui';

export function MarketsPage() {
  const navigate = useNavigate();
  const { data, loading, error, reload } = useAsync(() => api.markets(), []);

  return (
    <>
      <PageHead
        title="Markets"
        subtitle="The 10-coin universe — price and 24h change from 4h candles, latest market cap from CoinGecko."
      />

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>Coin</th>
                <th className="num">Price</th>
                <th className="num">24h</th>
                <th className="num">Market cap</th>
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
