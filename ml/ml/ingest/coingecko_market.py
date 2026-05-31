"""Market metadata ingestion from CoinGecko ``/market_chart`` -> ``market_meta``.

``market_chart`` returns time series for prices, market caps and total volumes
(daily granularity for days > 90). It does **not** return supply series, so we
derive circulating supply as ``market_cap / price`` (which is how market cap is
defined); ``total_supply`` is not available historically and is left NULL.
"""

from __future__ import annotations

import logging

import pandas as pd

from ..config import COIN_ID_FALLBACKS, COIN_IDS, COINGECKO_MARKET_CHART_DAYS
from . import _coingecko

log = logging.getLogger(__name__)

MARKET_META_COLUMNS = ["ts_utc", "symbol", "market_cap_usd", "circulating_supply", "total_supply"]


def fetch_market_chart(coin_id: str, days: int = COINGECKO_MARKET_CHART_DAYS) -> dict | None:
    return _coingecko.get(
        f"/coins/{coin_id}/market_chart",
        params={"vs_currency": "usd", "days": days},
    )


def _build_rows(symbol: str, payload: dict) -> list[dict]:
    """Align prices + market_caps by timestamp into market_meta rows."""
    prices = {int(ts): val for ts, val in payload.get("prices", [])}
    mcaps = {int(ts): val for ts, val in payload.get("market_caps", [])}
    rows: list[dict] = []
    for ts_ms, mcap in mcaps.items():
        price = prices.get(ts_ms)
        circ = (mcap / price) if (price and mcap is not None) else None
        rows.append(
            {
                "ts_utc": pd.to_datetime(ts_ms, unit="ms", utc=True),
                "symbol": symbol,
                "market_cap_usd": mcap,
                "circulating_supply": circ,
                "total_supply": None,
            }
        )
    return rows


def ingest() -> int:
    """Fetch + upsert market metadata for all coins. Returns rows upserted."""
    from ..db import upsert_market_meta

    total = 0
    for symbol, coin_id in COIN_IDS.items():
        payload = fetch_market_chart(coin_id)
        if payload is None and symbol in COIN_ID_FALLBACKS:
            fallback = COIN_ID_FALLBACKS[symbol]
            log.info("%s: retrying market_chart with fallback id %s", symbol, fallback)
            payload = fetch_market_chart(fallback)
        if not payload:
            log.warning("market_meta %s: no data — skipping", symbol)
            continue
        rows = _build_rows(symbol, payload)
        if not rows:
            log.warning("market_meta %s: empty series — skipping", symbol)
            continue
        n = upsert_market_meta(rows)
        total += n
        log.info("market_meta %-5s: %d rows", symbol, n)
    log.info("market_meta total upserted: %d", total)
    return total


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    ingest()
