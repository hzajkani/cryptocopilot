"""Integration: run_predict writes exactly 10 predictions and 30 drivers.

Requires a live Postgres (DATABASE_URL) and a trained model bundle, so it is
marked ``integration`` and skips cleanly when either is missing:

    docker compose run --rm ml python -m ml.train      # produce models/v1/
    docker compose run --rm ml pytest -m integration
"""

from __future__ import annotations

import pytest

pytest.importorskip("xgboost")
pytest.importorskip("shap")

from sqlalchemy import text  # noqa: E402

from ml import db  # noqa: E402
from ml.config import DEFAULT_TIMEFRAME  # noqa: E402
from ml.modelling.artifacts import bundle_path  # noqa: E402

pytestmark = pytest.mark.integration


@pytest.fixture
def require_db_and_model():
    if not bundle_path().exists():
        pytest.skip(f"no trained model at {bundle_path()} — run ml.train first")
    try:
        db.count_rows("predictions")
    except Exception as exc:  # no DB reachable
        pytest.skip(f"database not reachable: {exc}")


def test_predict_writes_10_and_30(require_db_and_model):
    from ml.predict import run_predict

    # ML owns these tables — clear them for a deterministic count.
    with db.get_engine().begin() as conn:
        conn.execute(text("DELETE FROM prediction_drivers"))
        conn.execute(text("DELETE FROM predictions"))

    result = run_predict(DEFAULT_TIMEFRAME)

    assert result["predictions"] == 10
    assert result["drivers"] == 30
    assert db.count_rows("predictions") == 10
    assert db.count_rows("prediction_drivers") == 30

    # one row per coin, three ranked drivers per coin
    rows = db.fetch_all(
        "SELECT symbol, count(*) c FROM prediction_drivers GROUP BY symbol"
    )
    assert len(rows) == 10
    assert all(r["c"] == 3 for r in rows)
