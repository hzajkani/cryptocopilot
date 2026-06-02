import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';
import { useAsync } from '../lib/useAsync';
import { AnalystCard } from '../components/AnalystCard';
import { ErrorState, PageHead, SkeletonCards } from '../components/ui';
import { IconBack } from '../components/icons';

export function AnalystDetailPage() {
  const { symbol = '' } = useParams();
  const sym = symbol.toUpperCase();
  const { data, loading, error, reload } = useAsync(() => api.analyst(sym), [sym]);

  return (
    <>
      <Link to="/analyst" className="back-link">
        <IconBack width={15} height={15} /> All coins
      </Link>
      <PageHead title={`${sym} — Analyst`} subtitle="The fused opinion, expanded with the full fundamental snapshot." />

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading || !data ? (
        <SkeletonCards count={1} />
      ) : (
        <div style={{ maxWidth: 560 }}>
          <AnalystCard resp={data} expanded />
        </div>
      )}
    </>
  );
}
