"""APScheduler entry point — the ``ml`` container's default command.

A blocking scheduler with two jobs (PROJECT.md §2: "wakes on a schedule, writes
to DB, sleeps"):
  * **daily_ingest** — the Stage 1 full ingestion (02:00 UTC).
  * **predict**      — the Stage 2 forecast job, every 4 hours, writing
    ``predictions``/``prediction_drivers``. It log-and-skips until a model exists.

Training stays manual (it is occasional and heavier):
    docker compose run --rm ml python -m ml.train
    docker compose run --rm ml python -m ml.predict     # one-shot predict
    docker compose run --rm ml python -m ml.ingest.run_all
"""

from __future__ import annotations

import logging

from apscheduler.schedulers.blocking import BlockingScheduler

from .config import DEFAULT_TIMEFRAME
from .ingest import run_all
from .predict import run_predict

log = logging.getLogger(__name__)

# Daily ingest time (UTC).
INGEST_HOUR = 2
INGEST_MINUTE = 0
# Predict cadence (24h direction changes slowly) — every 4h, offset off the hour.
PREDICT_EVERY_HOURS = 4
PREDICT_MINUTE = 15


def _predict_job() -> None:
    """Run predict, never letting a missing model or transient error kill the loop."""
    try:
        run_predict(DEFAULT_TIMEFRAME)
    except FileNotFoundError:
        log.warning("predict skipped — no trained model yet "
                    "(run: docker compose run --rm ml python -m ml.train)")
    except Exception:
        log.exception("predict job failed — logged and skipped")


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    scheduler = BlockingScheduler(timezone="UTC")
    scheduler.add_job(
        run_all.main,
        trigger="cron",
        hour=INGEST_HOUR,
        minute=INGEST_MINUTE,
        id="daily_ingest",
        name="Daily full ingestion",
        misfire_grace_time=3600,
        coalesce=True,
    )
    scheduler.add_job(
        _predict_job,
        trigger="cron",
        hour=f"*/{PREDICT_EVERY_HOURS}",
        minute=PREDICT_MINUTE,
        id="predict",
        name="ML direction predict",
        misfire_grace_time=3600,
        coalesce=True,
    )
    log.info(
        "Scheduler started — daily ingest %02d:%02d UTC, predict every %dh at :%02d. "
        "Train manually: docker compose run --rm ml python -m ml.train",
        INGEST_HOUR, INGEST_MINUTE, PREDICT_EVERY_HOURS, PREDICT_MINUTE,
    )
    try:
        scheduler.start()
    except (KeyboardInterrupt, SystemExit):
        log.info("Scheduler stopped.")


if __name__ == "__main__":
    main()
