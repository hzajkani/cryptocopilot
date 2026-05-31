"""Calendar features derived from the bar timestamp (UTC).

Cheap seasonality hints: crypto microstructure differs by hour-of-day and across
the weekend. These depend only on ``ts_utc`` itself, so they are trivially
leakage-safe and need no per-symbol grouping.
"""

from __future__ import annotations

import pandas as pd

CALENDAR_COLUMNS = ["hour_of_day", "day_of_week", "is_weekend"]


def add_calendar(df: pd.DataFrame) -> pd.DataFrame:
    """Return ``df`` with hour-of-day, day-of-week and is_weekend columns."""
    out = df.copy()
    ts = pd.to_datetime(out["ts_utc"], utc=True)
    out["hour_of_day"] = ts.dt.hour.astype("int16")
    out["day_of_week"] = ts.dt.dayofweek.astype("int16")  # Mon=0 .. Sun=6
    out["is_weekend"] = (ts.dt.dayofweek >= 5).astype("int8")
    return out
