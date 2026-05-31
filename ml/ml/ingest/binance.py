"""OHLCV ingestion from Binance public API via ccxt.

Pulls ~2 years of candles per (asset, timeframe) in <=1000-candle chunks with
retry/backoff. Stores the bare ticker (e.g. ``BTC``) in ``ohlcv.symbol`` so the
symbol space is consistent across every table; the resolved trading pair
(``BTC/USDT``, or ``POL/USDT`` for the MATIC rebrand) is only logged.
"""

from __future__ import annotations

import logging
import time

import ccxt
import pandas as pd

from ..config import ASSETS, HISTORY_DAYS, QUOTE, TIMEFRAMES

log = logging.getLogger(__name__)

OHLCV_COLUMNS = ["ts_utc", "symbol", "timeframe", "open", "high", "low", "close", "volume"]
_MS_PER_DAY = 86_400_000


def _exchange() -> ccxt.binance:
    return ccxt.binance({"enableRateLimit": True})


def _resolve_pairs(ex: ccxt.Exchange, asset: str) -> list[str]:
    """Return the ccxt market symbol(s) for an asset.

    For MATIC we keep *both* MATIC/USDT and POL/USDT: MATIC/USDT went stale at the
    late-2024 rebrand, so the two series stitched together give continuous history.
    """
    candidates = [f"{asset}/{QUOTE}"]
    if asset == "MATIC":
        candidates.append(f"POL/{QUOTE}")
    if not ex.markets:
        ex.load_markets()
    return [pair for pair in candidates if pair in ex.markets]


def _fetch_pair_rows(ex, pair, timeframe, days) -> list[list]:
    """Page through all candles for one pair over ``days``."""
    tf_ms = ex.parse_timeframe(timeframe) * 1000
    since = ex.milliseconds() - days * _MS_PER_DAY
    limit = 1000
    rows: list[list] = []
    while since < ex.milliseconds():
        chunk = _fetch_chunk(ex, pair, timeframe, since, limit)
        if not chunk:
            break
        rows.extend(chunk)
        next_since = chunk[-1][0] + tf_ms
        if next_since <= since:  # no forward progress -> stop
            break
        since = next_since
        if len(chunk) < limit:  # reached the present
            break
    return rows


def _fetch_chunk(ex, pair, timeframe, since, limit=1000, retries=4):
    """Fetch one chunk of candles with exponential backoff."""
    delay = 1.0
    for attempt in range(1, retries + 1):
        try:
            return ex.fetch_ohlcv(pair, timeframe=timeframe, since=since, limit=limit)
        except (ccxt.NetworkError, ccxt.ExchangeError) as exc:
            log.warning(
                "fetch_ohlcv %s %s attempt %d/%d failed: %s",
                pair, timeframe, attempt, retries, exc,
            )
            time.sleep(delay)
            delay *= 2
    log.error("giving up on %s %s after %d retries", pair, timeframe, retries)
    return []


def fetch_ohlcv(asset: str, timeframe: str, days: int = HISTORY_DAYS, exchange=None) -> pd.DataFrame:
    """Fetch ``days`` of candles for one (asset, timeframe) as a tidy DataFrame.

    Returns columns: ts_utc, symbol, timeframe, open, high, low, close, volume.
    Empty DataFrame (same columns) if the market cannot be resolved.
    """
    ex = exchange or _exchange()
    pairs = _resolve_pairs(ex, asset)
    if not pairs:
        log.warning("no Binance market for %s/%s — skipping", asset, QUOTE)
        return pd.DataFrame(columns=OHLCV_COLUMNS)
    if pairs != [f"{asset}/{QUOTE}"]:
        log.info("%s resolved to %s", asset, pairs)

    rows: list[list] = []
    for pair in pairs:
        rows.extend(_fetch_pair_rows(ex, pair, timeframe, days))

    if not rows:
        return pd.DataFrame(columns=OHLCV_COLUMNS)

    df = pd.DataFrame(rows, columns=["ts", "open", "high", "low", "close", "volume"])
    df = df.drop_duplicates(subset="ts").sort_values("ts").reset_index(drop=True)
    df["ts_utc"] = pd.to_datetime(df["ts"], unit="ms", utc=True)
    df["symbol"] = asset
    df["timeframe"] = timeframe
    return df[OHLCV_COLUMNS]


def ingest() -> int:
    """Fetch and upsert OHLCV for every (asset, timeframe). Returns rows upserted."""
    from ..db import upsert_ohlcv

    ex = _exchange()
    total = 0
    for asset in ASSETS:
        for timeframe in TIMEFRAMES:
            try:
                df = fetch_ohlcv(asset, timeframe, exchange=ex)
            except Exception as exc:  # never block the pipeline on one symbol
                log.exception("OHLCV fetch failed for %s %s: %s", asset, timeframe, exc)
                continue
            if df.empty:
                continue
            n = upsert_ohlcv(df)
            total += n
            log.info("ohlcv %-5s %-3s: %d rows", asset, timeframe, n)
    log.info("ohlcv total upserted: %d", total)
    return total


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    ingest()
