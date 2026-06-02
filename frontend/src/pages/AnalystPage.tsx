import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAsync } from '../lib/useAsync';
import { AnalystCard } from '../components/AnalystCard';
import { ErrorState, PageHead, SkeletonCards } from '../components/ui';

export function AnalystPage() {
  const navigate = useNavigate();
  const { data, loading, error, reload } = useAsync(() => api.analystAll(), []);

  return (
    <>
      <PageHead
        title="Analyst"
        subtitle="One fused, deterministic opinion per coin — ML + technical + fundamental + news — with a guarded summary. Click a card for the full fundamental snapshot."
      />

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <SkeletonCards count={6} />
      ) : (
        <div className="grid cards">
          {data?.map((r) => (
            <AnalystCard
              key={r.opinion.symbol}
              resp={r}
              onClick={() => navigate(`/analyst/${r.opinion.symbol}`)}
            />
          ))}
        </div>
      )}
    </>
  );
}
