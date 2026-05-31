"""APScheduler entry point — the ``ml`` container's default command.

A blocking scheduler with a single stub daily job that runs the full ingestion.
This keeps the batch-worker container alive (PROJECT.md §2: "wakes on a schedule,
writes to DB, sleeps"). Stage 2 adds the prediction job here.

Manual full ingest (does not need the scheduler):
    docker compose run --rm ml python -m ml.ingest.run_all
"""

from __future__ import annotations

import logging

from apscheduler.schedulers.blocking import BlockingScheduler

from .ingest import run_all

log = logging.getLogger(__name__)

# Daily ingest time (UTC).
INGEST_HOUR = 2
INGEST_MINUTE = 0


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
    log.info(
        "Scheduler started — daily ingest at %02d:%02d UTC. "
        "Run a one-off ingest with: docker compose run --rm ml python -m ml.ingest.run_all",
        INGEST_HOUR,
        INGEST_MINUTE,
    )
    try:
        scheduler.start()
    except (KeyboardInterrupt, SystemExit):
        log.info("Scheduler stopped.")


if __name__ == "__main__":
    main()
