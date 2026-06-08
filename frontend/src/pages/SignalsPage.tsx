import { api } from '../api/client';
import { useAsync } from '../lib/useAsync';
import { SignalCard } from '../components/SignalCard';
import { MlPipelineDiagram } from '../components/MlPipelineDiagram';
import { Collapsible, ErrorState, PageHead, SkeletonCards } from '../components/ui';

export function SignalsPage() {
  const { data, loading, error, reload } = useAsync(() => api.signals(), []);

  return (
    <>
      <PageHead
        title="Signals"
        subtitle="The calibrated ML forecast (class · confidence · top SHAP drivers) beside the independent ta4j verdict, per coin."
      />

      <Collapsible
        title="ML model — how the forecast is built"
        subtitle="Raw OHLCV → 46 features → calibrated XGBoost → weighted argmax. Test metrics + live SHAP importance."
        defaultOpen
      >
        <MlPipelineDiagram signals={data} />
      </Collapsible>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <SkeletonCards count={6} />
      ) : (
        <div className="grid cards">
          {data?.map((s) => (
            <SignalCard key={s.symbol} signal={s} />
          ))}
        </div>
      )}
    </>
  );
}
