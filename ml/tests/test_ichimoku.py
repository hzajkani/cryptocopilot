"""Ichimoku math on a toy ramp — exact, hand-computable values.

For a strictly increasing series with high = low = close = [1, 2, 3, ...]:
  HHk = close[t], LLk = close[t-(k-1)]  -> midpoint = (close[t] + close[t-(k-1)])/2
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from ml.features.ichimoku import compute_ichimoku

N = 120
RAMP = pd.Series(np.arange(1, N + 1, dtype=float))  # close[t] == t + 1


def _lines():
    return compute_ichimoku(RAMP, RAMP, RAMP)


def test_tenkan_kijun_exact():
    lines = _lines()
    # tenkan[t] = (close[t] + close[t-8]) / 2 = ((t+1) + (t-7))/2 = t - 3
    assert lines["tenkan"][50] == 47.0
    # kijun[t] = (close[t] + close[t-25]) / 2 = ((t+1) + (t-24))/2 = t - 11.5
    assert lines["kijun"][50] == 38.5


def test_senkou_displacement_and_warmup():
    lines = _lines()
    # senkou_a is the *displaced* (shift +26) leading span: needs t >= 51.
    assert np.isnan(lines["senkou_a"][50])
    # senkou_a[t] = senkou_a_raw[t-26]; raw[u] = ((u-3)+(u-11.5))/2 = u - 7.25
    #   -> senkou_a[60] = (60-26) - 7.25 = 26.75
    assert lines["senkou_a"][60] == 26.75
    # senkou_b[t] = senkou_b_raw[t-26]; raw[u] = (close[u]+close[u-51])/2 = u - 24.5
    #   -> senkou_b[80] = (80-26) - 24.5 = 29.5
    assert lines["senkou_b"][80] == 29.5


def test_no_forward_leakage_in_lines():
    """Truncating the series after t must not change the lines at t (backward-only)."""
    full = _lines()
    t = 90
    trunc = compute_ichimoku(RAMP.iloc[: t + 1], RAMP.iloc[: t + 1], RAMP.iloc[: t + 1])
    for col in ("tenkan", "kijun", "senkou_a", "senkou_b"):
        assert full[col][t] == trunc[col][t]
