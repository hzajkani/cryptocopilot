"""Ingestion smoke tests.

The OHLCV test hits the live Binance API, so it is marked ``network`` and can be
skipped offline:  ``pytest -m "not network"``.
"""

from __future__ import annotations

import pytest

pytest.importorskip("ccxt")
pytest.importorskip("pandas")

OHLCV_COLUMNS = ["ts_utc", "symbol", "timeframe", "open", "high", "low", "close", "volume"]


@pytest.mark.network
def test_fetch_btc_1h_one_day():
    """Pull 1 day of BTC/USDT 1h candles and sanity-check the frame."""
    from ml.ingest.binance import fetch_ohlcv

    df = fetch_ohlcv("BTC", "1h", days=1)

    assert not df.empty, "expected at least a few 1h candles for the last day"
    assert list(df.columns) == OHLCV_COLUMNS
    assert (df["symbol"] == "BTC").all()
    assert (df["timeframe"] == "1h").all()
    # No NaN in the OHLCV numeric columns.
    assert df[["open", "high", "low", "close", "volume"]].notna().all().all()
    # Timestamps are timezone-aware UTC.
    assert str(df["ts_utc"].dt.tz) == "UTC"
