"""Ichimoku Kinko Hyo — implemented from scratch (we own the math).

Standard parameters (9, 26, 52, 26-displacement):

    Tenkan-sen (conversion) = (HH9  + LL9 ) / 2
    Kijun-sen  (base)       = (HH26 + LL26) / 2
    Senkou A   (lead 1)     = (Tenkan + Kijun) / 2   plotted 26 bars AHEAD
    Senkou B   (lead 2)     = (HH52 + LL52) / 2      plotted 26 bars AHEAD
    Chikou     (lagging)    = close                  plotted 26 bars BEHIND

**Leakage note.** Senkou A/B are displaced *forward* by 26 bars, so the cloud
visible at bar ``T`` was computed from data at ``T-26`` — i.e. ``shift(+26)`` of a
backward quantity. That is past-only, so it is safe to compare against
``close[T]``. We deliberately do **not** emit the raw Chikou span (``close``
shifted *backwards*, i.e. ``shift(-26)``) as a feature — that would peek 26 bars
into the future. Instead ``ichimoku_chikou_clear`` is defined the leakage-safe
way: is the current close above the price 26 bars ago (``close > close.shift(26)``).
"""

from __future__ import annotations

import numpy as np
import pandas as pd

# Flag + continuous columns this module adds (stable order).
ICHIMOKU_COLUMNS = [
    "ichimoku_above_cloud",
    "ichimoku_below_cloud",
    "ichimoku_in_cloud",
    "ichimoku_tk_cross_bull",
    "ichimoku_tk_cross_bear",
    "ichimoku_cloud_thickness",
    "ichimoku_chikou_clear",
    "ichimoku_dist_tenkan",
    "ichimoku_dist_kijun",
    "ichimoku_tk_diff",
]

TENKAN_P = 9
KIJUN_P = 26
SENKOU_B_P = 52
DISPLACEMENT = 26


def _midpoint(high: pd.Series, low: pd.Series, window: int) -> pd.Series:
    """(highest-high + lowest-low) / 2 over a backward rolling ``window``."""
    return (high.rolling(window).max() + low.rolling(window).min()) / 2.0


def compute_ichimoku(high: pd.Series, low: pd.Series, close: pd.Series) -> pd.DataFrame:
    """Return the raw Ichimoku lines as a DataFrame (used by features + tests).

    Columns: ``tenkan, kijun, senkou_a, senkou_b`` — all leakage-safe (the
    Senkou spans are the values *currently visible*, derived from data 26 bars
    back). The forward-looking Chikou span is intentionally not returned.
    """
    tenkan = _midpoint(high, low, TENKAN_P)
    kijun = _midpoint(high, low, KIJUN_P)
    # raw leading spans, then displaced forward so row T shows the T-26 cloud
    senkou_a = ((tenkan + kijun) / 2.0).shift(DISPLACEMENT)
    senkou_b = _midpoint(high, low, SENKOU_B_P).shift(DISPLACEMENT)
    return pd.DataFrame(
        {"tenkan": tenkan, "kijun": kijun, "senkou_a": senkou_a, "senkou_b": senkou_b}
    )


def add_ichimoku_features(df: pd.DataFrame) -> pd.DataFrame:
    """Return ``df`` (one symbol, ts-sorted) with Ichimoku flag features added."""
    out = df.copy()
    high = out["high"].astype(float)
    low = out["low"].astype(float)
    close = out["close"].astype(float)

    lines = compute_ichimoku(high, low, close)
    tenkan, kijun = lines["tenkan"], lines["kijun"]
    senkou_a, senkou_b = lines["senkou_a"], lines["senkou_b"]

    cloud_top = np.maximum(senkou_a, senkou_b)
    cloud_bottom = np.minimum(senkou_a, senkou_b)

    out["ichimoku_above_cloud"] = (close > cloud_top).astype("int8")
    out["ichimoku_below_cloud"] = (close < cloud_bottom).astype("int8")
    out["ichimoku_in_cloud"] = (
        (close >= cloud_bottom) & (close <= cloud_top)
    ).astype("int8")

    tk_prev_diff = (tenkan - kijun).shift(1)
    out["ichimoku_tk_cross_bull"] = (
        (tenkan > kijun) & (tk_prev_diff <= 0)
    ).astype("int8")
    out["ichimoku_tk_cross_bear"] = (
        (tenkan < kijun) & (tk_prev_diff >= 0)
    ).astype("int8")

    # NaN during warmup -> guarantees the row is dropped by build.dropna().
    out["ichimoku_cloud_thickness"] = (senkou_a - senkou_b) / close
    out["ichimoku_chikou_clear"] = (close > close.shift(DISPLACEMENT)).astype("int8")

    # Continuous trend distances (close-normalised) — richer than the flags alone.
    out["ichimoku_dist_tenkan"] = (close - tenkan) / close
    out["ichimoku_dist_kijun"] = (close - kijun) / close
    out["ichimoku_tk_diff"] = (tenkan - kijun) / close

    return out
