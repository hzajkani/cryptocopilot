import { api } from '../api/client';
import { useAsync } from '../lib/useAsync';
import { fmtNum } from '../lib/format';

// A live picture of the 4-container system (PROJECT.md §2). Health is probed via
// GET /api/rag/status — that call rides the same nginx /api proxy and touches the
// DB (pgvector counts), so a success means the React→Spring Boot→Postgres path is
// up. We use it instead of /actuator/health because nginx only proxies /api (and
// the brief forbids changing the proxy). Mounted only when the panel is open.

type Health = 'ok' | 'pending' | 'bad';

function HealthDot({ state }: { state: Health }) {
  const label = state === 'ok' ? 'healthy' : state === 'bad' ? 'unreachable' : 'checking…';
  return <span className={`status-dot ${state}`} title={label} aria-label={label} />;
}

export function ArchitecturePanel() {
  const rag = useAsync(() => api.ragStatus(), []);
  const backend: Health = rag.error ? 'bad' : rag.loading ? 'pending' : 'ok';
  // Postgres is reachable iff the backend just read pgvector counts from it.
  const db: Health = rag.error ? 'bad' : rag.loading ? 'pending' : 'ok';

  const counts = rag.data ?? {};
  const ragText = rag.loading
    ? 'querying…'
    : rag.error
      ? 'unavailable (start Ollama + reindex)'
      : `${fmtNum(counts.news ?? 0, 0)} news · ${fmtNum(counts.onchain ?? 0, 0)} on-chain · ${fmtNum(
          counts.kb ?? 0,
          0,
        )} KB chunks`;

  return (
    <>
      <div className="arch-diagram">
        <div className="arch-box">
          <div className="ab-name">
            <HealthDot state="ok" /> browser
          </div>
          <div className="ab-tech">your machine</div>
        </div>
        <div className="arch-arrow">→</div>

        <div className="arch-box">
          <div className="ab-name">
            <HealthDot state="ok" /> frontend
          </div>
          <div className="ab-tech">React + nginx · :3000</div>
        </div>
        <div className="arch-arrow">→</div>

        <div className="arch-box">
          <div className="ab-name">
            <HealthDot state={backend} /> backend
          </div>
          <div className="ab-tech">Spring Boot · :8080</div>
        </div>
        <div className="arch-arrow">→</div>

        <div className="arch-box">
          <div className="ab-name">
            <HealthDot state={db} /> db
          </div>
          <div className="ab-tech">Postgres 16 + pgvector</div>
        </div>
      </div>

      {/* The Python ML worker writes to the same DB out of band (batch, not a server). */}
      <div className="arch-ml-row">
        <div className="arch-box dashed">
          <div className="ab-name">
            <HealthDot state="pending" /> ml worker
          </div>
          <div className="ab-tech">Python · XGBoost · SHAP · batch → db</div>
        </div>
      </div>

      <div className="arch-stats">
        <div className="arch-stat">
          <div className="as-value">226,200</div>
          <div className="as-label">OHLCV candles ingested</div>
        </div>
        <div className="arch-stat">
          <div className="as-value">10 · 3 · ~2y</div>
          <div className="as-label">coins · timeframes · history</div>
        </div>
        <div className="arch-stat">
          <div className="as-value" style={{ fontSize: 14 }}>
            {ragText}
          </div>
          <div className="as-label">RAG index (live · /api/rag/status)</div>
        </div>
        <div className="arch-stat">
          <div className="as-value" style={{ fontSize: 14 }}>
            v1 · AUC 0.578 · Brier 0.608
          </div>
          <div className="as-label">ML model</div>
        </div>
      </div>
    </>
  );
}
