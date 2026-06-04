import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import { errMessage, useAsync } from '../lib/useAsync';
import { useToast } from '../components/Toast';
import { Card, ErrorState, PageHead, SkeletonCards, Stat } from '../components/ui';
import { fmtDateTime, fmtNum, fmtPctFromFraction, fmtRelativeTime, DASH } from '../lib/format';
import type {
  IngestResult,
  MlClass,
  MlJob,
  MlJobKind,
  MlPrediction,
  MlStatus,
  PredictResult,
  TrainResult,
} from '../api/types';

const POLL_MS = 1500;

type Action = {
  kind: MlJobKind;
  title: string;
  verb: string;
  busyVerb: string;
  lastLabel: string;
  desc: string;
};

const ACTIONS: Action[] = [
  {
    kind: 'ingest',
    title: 'Ingest data',
    verb: 'Run ingestion',
    busyVerb: 'Ingesting…',
    lastLabel: 'Last ingested',
    desc: 'Crawl the five public sources (Binance OHLCV, CoinGecko, RSS news, on-chain, fundamentals) into Postgres. Takes a few minutes.',
  },
  {
    kind: 'train',
    title: 'Train model',
    verb: 'Train model',
    busyVerb: 'Training…',
    lastLabel: 'Last trained',
    desc: 'Build features, tune + fit the calibrated XGBoost, evaluate on the held-out test split, and save models/v1/. Takes a few minutes.',
  },
  {
    kind: 'predict',
    title: 'Predict',
    verb: 'Run predict',
    busyVerb: 'Predicting…',
    lastLabel: 'Last predicted',
    desc: 'Write the latest 24h direction forecast + top-3 SHAP drivers for all 10 coins to the predictions table.',
  },
];

function labelOf(kind: MlJobKind): string {
  return ACTIONS.find((a) => a.kind === kind)?.title ?? kind;
}

function ClassBadge({ cls }: { cls: MlClass | null }) {
  const tone = cls === 'UP' ? 'up' : cls === 'DOWN' ? 'down' : 'flat';
  return <span className={`badge ${tone}`}>{cls ?? DASH}</span>;
}

function StateBadge({ state }: { state: MlJob['state'] }) {
  const tone = state === 'success' ? 'up' : state === 'error' ? 'down' : 'accent';
  const label = state === 'success' ? 'Success' : state === 'error' ? 'Failed' : 'Running…';
  return <span className={`badge ${tone}`}>{label}</span>;
}

/** A predictions table reused by the status panel and the predict result. */
function PredictionsTable({ rows }: { rows: MlPrediction[] }) {
  if (!rows.length) {
    return <p className="dim">No predictions yet — run predict (a trained model is required first).</p>;
  }
  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            <th>Coin</th>
            <th>Class</th>
            <th className="num">P(up)</th>
            <th className="num">P(flat)</th>
            <th className="num">P(down)</th>
            <th>As of</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((p) => (
            <tr key={p.symbol}>
              <td>{p.symbol}</td>
              <td>
                <ClassBadge cls={p.pred_class} />
              </td>
              <td className="num">{fmtPctFromFraction(p.prob_up, { decimals: 1 })}</td>
              <td className="num">{fmtPctFromFraction(p.prob_flat, { decimals: 1 })}</td>
              <td className="num">{fmtPctFromFraction(p.prob_down, { decimals: 1 })}</td>
              <td className="dim">{fmtRelativeTime(p.ts_utc)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function IngestResultView({ result }: { result: IngestResult }) {
  return (
    <>
      <div className="grid cols-2" style={{ marginTop: 12 }}>
        {Object.entries(result.counts).map(([source, n]) => (
          <Stat
            key={source}
            label={source}
            value={n < 0 ? 'failed' : fmtNum(n, 0)}
            className={n < 0 ? 'down' : ''}
          />
        ))}
      </div>
      <p className="dim" style={{ marginTop: 10 }}>
        {fmtNum(result.total, 0)} rows written this run. Table totals now —{' '}
        {Object.entries(result.tables)
          .map(([t, n]) => `${t}: ${fmtNum(n, 0)}`)
          .join(' · ')}
        .
      </p>
    </>
  );
}

function TrainResultView({ result }: { result: TrainResult }) {
  const t = result.metrics.test;
  const baselineF1 = result.metrics.baseline?.test_macro_f1 ?? null;
  const perSymbol = result.metrics.perSymbolMacroF1 ?? {};
  return (
    <>
      <div className="grid cols-2" style={{ marginTop: 12 }}>
        <Stat label="Macro F1 (test)" value={t ? fmtNum(t.macro_f1, 3) : DASH} className="up" />
        <Stat label="Macro ROC-AUC" value={t ? fmtNum(t.macro_auc, 3) : DASH} />
        <Stat label="Brier" value={t ? fmtNum(t.brier, 3) : DASH} />
        <Stat label="Baseline F1" value={baselineF1 != null ? fmtNum(baselineF1, 3) : DASH} sub="bar to beat" />
      </div>
      <p className="dim" style={{ marginTop: 10 }}>
        Model {result.modelVersion ?? DASH} · {result.featureCount} features · {result.timeframe ?? DASH} ·
        decision weights [{(result.decisionWeights ?? []).map((w) => fmtNum(w, 1)).join(', ')}]
      </p>
      {Object.keys(perSymbol).length > 0 && (
        <div className="table-wrap" style={{ marginTop: 12 }}>
          <table className="data">
            <thead>
              <tr>
                <th>Coin</th>
                <th className="num">Macro F1</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(perSymbol).map(([sym, f1]) => (
                <tr key={sym}>
                  <td>{sym}</td>
                  <td className="num">{fmtNum(f1, 3)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function PredictResultView({ result }: { result: PredictResult }) {
  return (
    <>
      <p className="dim" style={{ margin: '12px 0 4px' }}>
        Wrote {fmtNum(result.written.predictions, 0)} predictions and {fmtNum(result.written.drivers, 0)} SHAP
        drivers.
      </p>
      <PredictionsTable rows={result.predictions} />
    </>
  );
}

function JobResult({ job }: { job: MlJob }) {
  if (job.state === 'error') {
    return (
      <p className="down" style={{ marginTop: 12, whiteSpace: 'pre-wrap' }}>
        {job.error ?? 'The job failed.'}
      </p>
    );
  }
  if (job.state !== 'success' || job.result == null) return null;
  if (job.kind === 'ingest') return <IngestResultView result={job.result as IngestResult} />;
  if (job.kind === 'train') return <TrainResultView result={job.result as TrainResult} />;
  return <PredictResultView result={job.result as PredictResult} />;
}

/** The always-on status panel: model card, data counts, latest predictions. */
function StatusPanel({ status }: { status: MlStatus }) {
  const m = status.model;
  return (
    <div className="grid cards" style={{ marginBottom: 18 }}>
      <Card>
        <div className="section-title">Trained model</div>
        {m.exists ? (
          <>
            <div className="grid cols-2" style={{ marginTop: 8 }}>
              <Stat label="Version" value={m.version} sub={m.timeframe ?? undefined} />
              <Stat label="Trained" value={fmtRelativeTime(m.trainedAt)} />
              <Stat label="Macro F1" value={m.test ? fmtNum(m.test.macro_f1, 3) : DASH} className="up" />
              <Stat label="ROC-AUC" value={m.test ? fmtNum(m.test.macro_auc, 3) : DASH} />
            </div>
            <p className="dim" style={{ marginTop: 8 }}>{m.featureCount ?? DASH} features</p>
          </>
        ) : (
          <p className="dim" style={{ marginTop: 8 }}>
            No model trained yet — run <strong>Train model</strong> below.
          </p>
        )}
      </Card>

      <Card>
        <div className="section-title">Ingested data</div>
        <div className="grid cols-2" style={{ marginTop: 8 }}>
          {Object.entries(status.tables).map(([table, n]) => (
            <Stat key={table} label={table} value={n < 0 ? DASH : fmtNum(n, 0)} />
          ))}
        </div>
      </Card>
    </div>
  );
}

export function MlPipelinePage() {
  const toast = useToast();
  const status = useAsync(() => api.mlStatus(), []);
  const [running, setRunning] = useState<MlJobKind | null>(null);
  const [jobs, setJobs] = useState<Partial<Record<MlJobKind, MlJob>>>({});

  const alive = useRef(true);
  const timer = useRef<number | null>(null);
  useEffect(() => {
    return () => {
      alive.current = false;
      if (timer.current) window.clearTimeout(timer.current);
    };
  }, []);

  function finish(kind: MlJobKind, job: MlJob) {
    setRunning(null);
    if (job.state === 'success') {
      toast.showSuccess(`${labelOf(kind)} complete (${fmtNum(job.durationSec, 0)}s).`);
      status.reload();
    } else {
      toast.showError(`${labelOf(kind)} failed: ${job.error ?? 'unknown error'}`);
    }
  }

  function poll(kind: MlJobKind, id: string) {
    timer.current = window.setTimeout(async () => {
      if (!alive.current) return;
      try {
        const job = await api.mlJob(id);
        if (!alive.current) return;
        setJobs((j) => ({ ...j, [kind]: job }));
        if (job.state === 'running') poll(kind, id);
        else finish(kind, job);
      } catch (err) {
        if (!alive.current) return;
        setRunning(null);
        toast.showError(`${labelOf(kind)}: ${errMessage(err)}`);
      }
    }, POLL_MS);
  }

  async function start(kind: MlJobKind) {
    if (running) return;
    setRunning(kind);
    const starter =
      kind === 'ingest' ? api.mlIngest : kind === 'train' ? api.mlTrain : api.mlPredict;
    try {
      const job = await starter();
      if (!alive.current) return;
      setJobs((j) => ({ ...j, [kind]: job }));
      if (job.state === 'running') poll(kind, job.id);
      else finish(kind, job);
    } catch (err) {
      setRunning(null);
      toast.showError(`${labelOf(kind)}: ${errMessage(err)}`);
    }
  }

  const s = status.data;
  const activeJob = s?.activeJob ?? null;
  const lastRun: Record<MlJobKind, string | null> = {
    ingest: s?.lastIngestedAt ?? null,
    train: s?.lastTrainedAt ?? null,
    predict: s?.lastPredictedAt ?? null,
  };

  return (
    <>
      <PageHead
        title="ML Pipeline"
        subtitle="Trigger the data + model pipeline on demand: ingest, train, then predict. Each runs on the Python ML service and writes back to Postgres. Decision-support only — not financial advice."
      />

      {status.error ? (
        <ErrorState message={status.error} onRetry={status.reload} />
      ) : status.loading || !status.data ? (
        <SkeletonCards count={2} />
      ) : (
        <StatusPanel status={status.data} />
      )}

      {activeJob && running === null && (
        <div style={{ marginBottom: 14 }}>
          <Card>
            <div className="row gap">
              <StateBadge state={activeJob.state} />
              <span className="dim">
                A {activeJob.kind} job is currently running on the ML service (
                {fmtNum(activeJob.durationSec, 0)}s).
              </span>
            </div>
          </Card>
        </div>
      )}

      <div className="grid cards">
        {ACTIONS.map((a) => {
          const job = jobs[a.kind];
          const isRunning = running === a.kind;
          const last = lastRun[a.kind];
          return (
            <Card key={a.kind}>
              <div className="row between">
                <div className="section-title mb-0">{a.title}</div>
                {job && <StateBadge state={isRunning ? 'running' : job.state} />}
              </div>
              <p className="dim" style={{ margin: '8px 0 14px' }}>{a.desc}</p>
              <div className="row between">
                <button
                  className="btn primary"
                  onClick={() => start(a.kind)}
                  disabled={running !== null}
                >
                  {isRunning ? a.busyVerb : a.verb}
                </button>
                <span className="pill" title={last ? fmtDateTime(last) : undefined}>
                  {a.lastLabel}: {last ? fmtRelativeTime(last) : s ? 'never' : DASH}
                </span>
              </div>
              {job && !isRunning && <JobResult job={job} />}
            </Card>
          );
        })}
      </div>

      {status.data && (
        <>
          <div className="section-title" style={{ margin: '22px 0 10px' }}>
            Latest predictions
          </div>
          <PredictionsTable rows={status.data.predictions} />
        </>
      )}
    </>
  );
}
