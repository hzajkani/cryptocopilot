"""Run every ingestion source in sequence and report counts.

Each source is isolated: if one fails it is logged and skipped — the pipeline
never crashes as a whole (PROJECT.md §9: "log-and-skip on any source failure").

Manual full ingest:
    docker compose run --rm ml python -m ml.ingest.run_all
"""

from __future__ import annotations

import logging

from ..config import NEWS_WINDOW_DAYS
from . import (
    binance,
    coingecko_fundamentals,
    coingecko_market,
    onchain,
    rss_news,
)

log = logging.getLogger(__name__)

# (label -> ingest callable); order matters only for readability of the logs.
SOURCES = [
    ("ohlcv", binance.ingest),
    ("market_meta", coingecko_market.ingest),
    ("news", rss_news.ingest),
    ("onchain", onchain.ingest),
    ("fundamentals", coingecko_fundamentals.ingest),
]


def main() -> dict[str, int]:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    log.info("=== CryptoCopilot ingestion: starting ===")

    results: dict[str, int] = {}
    for label, fn in SOURCES:
        log.info("--- %s ---", label)
        try:
            results[label] = fn()
        except Exception:
            log.exception("source %s failed — logged and skipped", label)
            results[label] = -1

    total = sum(n for n in results.values() if n > 0)
    log.info("=== ingestion complete ===")
    for label, n in results.items():
        log.info("  %-13s: %s", label, "FAILED" if n < 0 else f"{n} rows")
    log.info("  %-13s: %d rows", "TOTAL", total)

    # Current size of the rolling news window.
    try:
        from ..db import count_rows

        log.info("  news window  : %d rows within %dd", count_rows("news"), NEWS_WINDOW_DAYS)
    except Exception as exc:
        log.warning("could not read news window size: %s", exc)

    return results


if __name__ == "__main__":
    main()
