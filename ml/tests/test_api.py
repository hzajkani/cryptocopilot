"""Offline tests for the FastAPI trigger layer (``ml.api``).

No DB and no network: the DB read helpers and the heavy job bodies are stubbed,
and the background executor is swapped for an inline one so a ``POST`` completes
synchronously and the assertions stay deterministic.
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from ml import api


class _InlineExecutor:
    """Run the submitted job on the calling thread (deterministic in tests)."""

    def submit(self, fn, *args, **kwargs):
        fn(*args, **kwargs)
        return None


class _NoopExecutor:
    """Accept a job but never run it — leaves it 'running' to test the 409 path."""

    def submit(self, *args, **kwargs):
        return None


@pytest.fixture(autouse=True)
def _reset_registry():
    api._jobs.clear()
    api._jobs_order.clear()
    api._active_id = None
    yield
    api._active_id = None


@pytest.fixture()
def client():
    return TestClient(api.app)


def test_health(client):
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json()["status"] == "ok"


def test_unknown_job_is_404(client):
    assert client.get("/jobs/does-not-exist").status_code == 404


def test_status_shape(client, monkeypatch):
    monkeypatch.setattr(api.db, "count_rows", lambda table: 42)
    monkeypatch.setattr(api.db, "fetch_all", lambda *a, **k: [])
    monkeypatch.setattr(api, "_model_info", lambda: {"version": "v1", "exists": False})

    body = client.get("/status").json()
    assert body["tables"]["ohlcv"] == 42
    assert body["model"]["version"] == "v1"
    assert body["activeJob"] is None
    assert body["predictions"] == []


def test_ingest_runs_and_reports_result(client, monkeypatch):
    monkeypatch.setattr(api, "_executor", _InlineExecutor())
    monkeypatch.setattr(api, "_job_ingest", lambda: {"counts": {"ohlcv": 10}, "total": 10})

    started = client.post("/ingest")
    assert started.status_code == 202
    job_id = started.json()["id"]

    # Inline executor => the job already finished by the time POST returned.
    job = client.get(f"/jobs/{job_id}").json()
    assert job["kind"] == "ingest"
    assert job["state"] == "success"
    assert job["result"]["total"] == 10
    # The active slot is released so the next run is allowed.
    assert client.get("/status").json()["activeJob"] is None


def test_job_failure_is_captured(client, monkeypatch):
    monkeypatch.setattr(api, "_executor", _InlineExecutor())

    def _boom():
        raise RuntimeError("no data ingested yet")

    monkeypatch.setattr(api, "_job_train", _boom)

    job_id = client.post("/train").json()["id"]
    job = client.get(f"/jobs/{job_id}").json()
    assert job["state"] == "error"
    assert "no data ingested yet" in job["error"]


def test_second_job_while_one_runs_is_409(client, monkeypatch):
    # Job is accepted but never executed, so it stays 'running' and holds the slot.
    monkeypatch.setattr(api, "_executor", _NoopExecutor())
    monkeypatch.setattr(api, "_job_predict", lambda: {})

    first = client.post("/predict")
    assert first.status_code == 202
    assert client.post("/predict").status_code == 409
