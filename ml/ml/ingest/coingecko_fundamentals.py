"""Fundamentals ingestion from CoinGecko ``/coins/{id}`` -> ``fundamentals``.

Pulls market + community + developer data for each coin (point-in-time
snapshot). Missing fields are logged and left NULL — never fatal.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone

from ..config import COIN_ID_FALLBACKS, COIN_IDS
from . import _coingecko

log = logging.getLogger(__name__)

_COIN_PARAMS = {
    "localization": "false",
    "tickers": "false",
    "market_data": "true",
    "community_data": "true",
    "developer_data": "true",
    "sparkline": "false",
}


def _f(value) -> float | None:
    try:
        return float(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def _i(value) -> int | None:
    try:
        if value is None or value == "":
            return None
        return int(round(float(value)))
    except (TypeError, ValueError):
        return None


def fetch_coin(coin_id: str) -> dict | None:
    return _coingecko.get(f"/coins/{coin_id}", params=_COIN_PARAMS)


def _coin_to_row(symbol: str, payload: dict, now: datetime) -> dict:
    md = payload.get("market_data") or {}
    cd = payload.get("community_data") or {}
    dd = payload.get("developer_data") or {}
    cad = dd.get("code_additions_deletions_4_weeks") or {}

    row = {
        "ts_utc": now,
        "symbol": symbol,
        "price_change_pct_24h": _f(md.get("price_change_percentage_24h")),
        "price_change_pct_7d": _f(md.get("price_change_percentage_7d")),
        "price_change_pct_30d": _f(md.get("price_change_percentage_30d")),
        "total_volume_usd": _f((md.get("total_volume") or {}).get("usd")),
        "market_cap_change_pct_24h": _f(md.get("market_cap_change_percentage_24h")),
        "reddit_subscribers": _i(cd.get("reddit_subscribers")),
        "reddit_active_48h": _i(cd.get("reddit_accounts_active_48h")),
        "reddit_avg_posts_48h": _f(cd.get("reddit_average_posts_48h")),
        "twitter_followers": _i(cd.get("twitter_followers")),
        "github_commit_count_4w": _i(dd.get("commit_count_4_weeks")),
        "github_prs_merged": _i(dd.get("pull_requests_merged")),
        "github_code_additions_4w": _i(cad.get("additions")),
        "github_code_deletions_4w": _i(cad.get("deletions")),
    }
    missing = [k for k, v in row.items() if v is None and k not in ("ts_utc", "symbol")]
    if missing:
        log.info("fundamentals %-5s: missing fields %s", symbol, missing)
    return row


def ingest() -> int:
    """Fetch + upsert fundamentals for all coins. Returns rows upserted."""
    from ..db import upsert_fundamentals

    now = datetime.now(timezone.utc)
    rows: list[dict] = []
    for symbol, coin_id in COIN_IDS.items():
        payload = fetch_coin(coin_id)
        if payload is None and symbol in COIN_ID_FALLBACKS:
            fallback = COIN_ID_FALLBACKS[symbol]
            log.info("%s: retrying /coins with fallback id %s", symbol, fallback)
            payload = fetch_coin(fallback)
        if not payload:
            log.warning("fundamentals %s: no data — skipping", symbol)
            continue
        rows.append(_coin_to_row(symbol, payload, now))

    n = upsert_fundamentals(rows) if rows else 0
    log.info("fundamentals total upserted: %d (%d coins)", n, len(rows))
    return n


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    ingest()
