"""HTTP API for the CryptoCopilot ML/data service (FastAPI).

Puts a thin HTTP trigger in front of the **same** batch code the scheduler runs —
ingest (Stage 1), train + predict (Stage 2) — so the three operations can be
launched on demand from the Spring Boot backend and, through it, the React UI.
ML stays the sole owner of ``predictions``/``prediction_drivers`` (PROJECT.md §3);
this module only schedules and reports on the existing jobs, it owns no new logic.

Because ingest and train run for minutes (well past nginx's proxy timeout), they
execute as **background jobs**: a ``POST`` starts the job and returns immediately
with a job id; the caller polls ``GET /jobs/{id}`` for state + result. Only one
heavy job runs at a time — a single worker thread plus a global lock — so
UI-triggered runs never collide with each other.

Run it (matches the docker-compose ``ml-api`` service)::

    uvicorn ml.api:app --host 0.0.0.0 --port 8000

Interactive docs at ``/docs``; the backend reaches it at ``cryptocopilot.ml.base-url``.
"""

from __future__ import annotations

import json
import logging
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from typing import Any, Callable

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from . import db
from .config import DEFAULT_TIMEFRAME, MODEL_VERSION
from .modelling.artifacts import bundle_path, version_dir

log = logging.getLogger(__name__)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)

# Public data tables (the "did ingestion land?" health) + the ML output tables.
DATA_TABLES = ["ohlcv", "market_meta", "news", "onchain", "fundamentals"]
PRED_TABLES = ["predictions", "prediction_drivers"]
MAX_JOB_HISTORY = 50

app = FastAPI(
    title="CryptoCopilot ML API",
    version="0.2.0",
    description="On-demand triggers for the data/ML pipeline: ingest, train, predict "
    "(background jobs) + pipeline status. Decision-support only — not financial advice.",
)
# CORS so the API may ALSO be hit straight from a browser in local dev. The
# production path is browser -> nginx -> backend -> here, which needs no CORS;
# allowing it is harmless and keeps the standalone `uvicorn` dev story simple.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# --------------------------------------------------------------------------- #
# Job registry — a tiny in-process scheduler (single worker, one job at a time)
# --------------------------------------------------------------------------- #
class Job:
    """One background run of ingest/train/predict and its eventual result."""

    def __init__(self, kind: str) -> None:
        self.id = uuid.uuid4().hex[:12]
        self.kind = kind
        self.state = "running"  # running | success | error
        self.started_at = datetime.now(timezone.utc)
        self.finished_at: datetime | None = None
        self.result: Any = None
        self.error: str | None = None

    def public(self) -> dict[str, Any]:
        end = self.finished_at or datetime.now(timezone.utc)
        return {
            "id": self.id,
            "kind": self.kind,
            "state": self.state,
            "startedAt": self.started_at.isoformat(),
            "finishedAt": self.finished_at.isoformat() if self.finished_at else None,
            "durationSec": round((end - self.started_at).total_seconds(), 1),
            "result": self.result,
            "error": self.error,
        }


_jobs: dict[str, Job] = {}
_jobs_order: list[str] = []
_executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="ml-job")
_lock = threading.Lock()
_active_id: str | None = None


def _start(kind: str, fn: Callable[[], Any]) -> Job:
    """Register + dispatch a job, rejecting a second run while one is active."""
    global _active_id
    with _lock:
        if _active_id is not None:
            active = _jobs[_active_id]
            raise HTTPException(
                status_code=409,
                detail=f"A '{active.kind}' job is already running (id {active.id}). "
                "Wait for it to finish before starting another.",
            )
        job = Job(kind)
        _jobs[job.id] = job
        _jobs_order.append(job.id)
        _active_id = job.id
        while len(_jobs_order) > MAX_JOB_HISTORY:
            _jobs.pop(_jobs_order.pop(0), None)
    _executor.submit(_run, job, fn)
    return job


def _run(job: Job, fn: Callable[[], Any]) -> None:
    """Worker body: execute the job, capturing success/result or the error."""
    global _active_id
    log.info("job %s (%s) starting", job.id, job.kind)
    try:
        job.result = fn()
        job.state = "success"
        log.info("job %s (%s) succeeded", job.id, job.kind)
    except Exception as exc:  # noqa: BLE001 — any failure is reported back to the caller
        job.state = "error"
        job.error = f"{type(exc).__name__}: {exc}"
        log.exception("job %s (%s) failed", job.id, job.kind)
    finally:
        job.finished_at = datetime.now(timezone.utc)
        with _lock:
            _active_id = None


# --------------------------------------------------------------------------- #
# Job bodies — lazy imports keep API startup fast and resilient (a broken ML
# dependency still leaves /health and /status serving).
# --------------------------------------------------------------------------- #
def _job_ingest() -> dict[str, Any]:
    from .ingest import run_all

    counts = run_all.main()  # {source: rows, -1 = failed-and-skipped}
    total = sum(n for n in counts.values() if isinstance(n, int) and n > 0)
    return {"counts": counts, "total": total, "tables": _table_counts()}


def _job_train() -> dict[str, Any]:
    from .train import train as _train

    return _compact_bundle(_train(DEFAULT_TIMEFRAME))


def _job_predict() -> dict[str, Any]:
    from .predict import run_predict

    written = run_predict(DEFAULT_TIMEFRAME)  # {"predictions": n, "drivers": m}
    return {"written": written, "predictions": _latest_predictions()}


def _compact_bundle(bundle: dict[str, Any]) -> dict[str, Any]:
    """Drop the non-serialisable estimators; keep the JSON-safe metrics + meta."""
    metrics = bundle.get("metrics", {}) or {}
    return {
        "modelVersion": bundle.get("model_version"),
        "timeframe": bundle.get("timeframe"),
        "trainedAt": bundle.get("trained_at"),
        "featureCount": len(bundle.get("feature_cols", [])),
        "decisionWeights": bundle.get("decision_weights"),
        "metrics": {
            "test": metrics.get("test"),
            "perSymbolMacroF1": metrics.get("per_symbol_macro_f1"),
            "baseline": metrics.get("baseline"),
            "backtest": metrics.get("backtest"),
            "bestParams": metrics.get("best_params"),
        },
        "splits": bundle.get("splits"),
    }


# --------------------------------------------------------------------------- #
# Read helpers (status + result enrichment). Each tolerates a DB hiccup.
# --------------------------------------------------------------------------- #
def _table_counts() -> dict[str, int]:
    out: dict[str, int] = {}
    for table in DATA_TABLES + PRED_TABLES:
        try:
            out[table] = db.count_rows(table)
        except Exception as exc:  # noqa: BLE001
            log.warning("count_rows(%s) failed: %s", table, exc)
            out[table] = -1
    return out


def _ohlcv_freshness() -> list[dict[str, Any]]:
    try:
        return db.fetch_all(
            "SELECT symbol, max(ts_utc) AS latest, count(*) AS bars "
            "FROM ohlcv WHERE timeframe = :tf GROUP BY symbol ORDER BY symbol",
            tf=DEFAULT_TIMEFRAME,
        )
    except Exception as exc:  # noqa: BLE001
        log.warning("ohlcv freshness query failed: %s", exc)
        return []


def _latest_predictions() -> list[dict[str, Any]]:
    try:
        return db.fetch_all(
            "SELECT DISTINCT ON (symbol) symbol, ts_utc, timeframe, pred_class, "
            "prob_up, prob_flat, prob_down, model_version, created_at "
            "FROM predictions ORDER BY symbol, ts_utc DESC, created_at DESC"
        )
    except Exception as exc:  # noqa: BLE001
        log.warning("latest predictions query failed: %s", exc)
        return []


def _last_ingested_at() -> str | None:
    """Newest timestamp across the ingested data tables — how current the data is."""
    union = " UNION ALL ".join(f"SELECT max(ts_utc) AS m FROM {t}" for t in DATA_TABLES)
    try:
        rows = db.fetch_all(f"SELECT max(m) AS latest FROM ({union}) s")
    except Exception as exc:  # noqa: BLE001
        log.warning("last-ingested query failed: %s", exc)
        return None
    latest = rows[0]["latest"] if rows else None
    return latest.isoformat() if latest is not None else None


def _last_predicted_at() -> str | None:
    """When predict last wrote (``predictions.created_at`` is set to the run time)."""
    try:
        rows = db.fetch_all("SELECT max(created_at) AS latest FROM predictions")
    except Exception as exc:  # noqa: BLE001
        log.warning("last-predicted query failed: %s", exc)
        return None
    latest = rows[0]["latest"] if rows else None
    return latest.isoformat() if latest is not None else None


def _model_info() -> dict[str, Any]:
    info: dict[str, Any] = {"version": MODEL_VERSION, "exists": bundle_path().exists()}
    meta_path = version_dir() / "meta.json"
    if meta_path.exists():
        try:
            meta = json.loads(meta_path.read_text())
            info.update(
                trainedAt=meta.get("trained_at"),
                timeframe=meta.get("timeframe"),
                featureCount=len(meta.get("feature_cols") or []),
                test=(meta.get("metrics") or {}).get("test"),
                splits=meta.get("splits"),
            )
        except Exception as exc:  # noqa: BLE001
            log.warning("reading %s failed: %s", meta_path, exc)
    return info


# --------------------------------------------------------------------------- #
# Endpoints
# --------------------------------------------------------------------------- #
@app.get("/health", tags=["meta"])
def health() -> dict[str, str]:
    """Liveness probe (used by the docker healthcheck)."""
    return {"status": "ok", "service": "cryptocopilot-ml", "version": "0.2.0"}


@app.get("/status", tags=["pipeline"])
def status() -> dict[str, Any]:
    """A snapshot the UI renders on load: freshness, data counts, model card, predictions.

    The three ``last*At`` fields answer "when did each stage last run": ``lastIngestedAt`` is the
    newest ingested datapoint, ``lastTrainedAt`` is the model's training time, ``lastPredictedAt``
    is the most recent prediction write.
    """
    model = _model_info()
    return {
        "timeframe": DEFAULT_TIMEFRAME,
        "tables": _table_counts(),
        "ohlcv": _ohlcv_freshness(),
        "model": model,
        "predictions": _latest_predictions(),
        "activeJob": _jobs[_active_id].public() if _active_id else None,
        "lastIngestedAt": _last_ingested_at(),
        "lastTrainedAt": model.get("trainedAt"),
        "lastPredictedAt": _last_predicted_at(),
    }


@app.post("/ingest", status_code=202, tags=["pipeline"])
def ingest() -> dict[str, Any]:
    """Start a full ingestion run (five public sources -> Postgres). Poll /jobs/{id}."""
    return _start("ingest", _job_ingest).public()


@app.post("/train", status_code=202, tags=["pipeline"])
def train() -> dict[str, Any]:
    """Start training: features -> tune -> fit -> calibrate -> save models/v1/. Poll /jobs/{id}."""
    return _start("train", _job_train).public()


@app.post("/predict", status_code=202, tags=["pipeline"])
def predict() -> dict[str, Any]:
    """Write the latest forecast + SHAP drivers for all 10 coins. Poll /jobs/{id}."""
    return _start("predict", _job_predict).public()


@app.get("/jobs", tags=["pipeline"])
def jobs() -> list[dict[str, Any]]:
    """Recent jobs, newest first."""
    with _lock:
        ids = list(_jobs_order)
    return [_jobs[i].public() for i in reversed(ids) if i in _jobs]


@app.get("/jobs/{job_id}", tags=["pipeline"])
def job(job_id: str) -> dict[str, Any]:
    """State (running/success/error) + result payload for one job."""
    found = _jobs.get(job_id)
    if found is None:
        raise HTTPException(status_code=404, detail=f"No job with id {job_id}")
    return found.public()
