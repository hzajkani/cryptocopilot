"""Deterministic synthetic OHLCV for offline feature/split/leakage tests.

Produces the same columns ``build_from_raw`` expects, so the feature pipeline can
be exercised without a database or network.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from ml.config import TIMEFRAME_HOURS


def make_ohlcv(
    symbols=("AAA", "BBB"),
    n: int = 400,
    timeframe: str = "4h",
    start: str = "2024-06-01",
    seed: int = 7,
) -> pd.DataFrame:
    """Return a tidy OHLCV frame: a gentle random walk per symbol."""
    tf_h = TIMEFRAME_HOURS[timeframe]
    ts = pd.date_range(start=start, periods=n, freq=f"{tf_h}h", tz="UTC")
    frames = []
    for s, sym in enumerate(symbols):
        rng = np.random.default_rng(seed + s)
        steps = rng.normal(0, 0.01, size=n)
        close = 100.0 * np.exp(np.cumsum(steps))
        high = close * (1 + np.abs(rng.normal(0, 0.004, size=n)))
        low = close * (1 - np.abs(rng.normal(0, 0.004, size=n)))
        open_ = close * (1 + rng.normal(0, 0.002, size=n))
        volume = rng.uniform(1_000, 10_000, size=n)
        frames.append(pd.DataFrame({
            "ts_utc": ts,
            "symbol": sym,
            "timeframe": timeframe,
            "open": open_,
            "high": np.maximum.reduce([high, close, open_]),
            "low": np.minimum.reduce([low, close, open_]),
            "close": close,
            "volume": volume,
        }))
    return pd.concat(frames, ignore_index=True)
