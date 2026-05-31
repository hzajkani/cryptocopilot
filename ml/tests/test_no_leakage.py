"""No look-ahead: a feature at (symbol, ts) must not depend on data after ts.

We build features on the full series, then rebuild on the series truncated at ts.
Because every feature is backward-only (rolling / ewm / positive shift), the row
at ts must be identical in both — truncating the future changes nothing.
"""

from __future__ import annotations

import numpy as np
import pytest

pytest.importorskip("ta")

from ml.features.build import FEATURE_COLUMNS, build_from_raw  # noqa: E402
from tests._synthetic import make_ohlcv  # noqa: E402


@pytest.mark.parametrize("cut", [200, 300, 399])
def test_feature_row_independent_of_future(cut: int):
    raw = make_ohlcv(symbols=("AAA",), n=400, timeframe="4h")
    ts_cut = raw["ts_utc"].iloc[cut]

    full = build_from_raw(raw, "4h")
    truncated = build_from_raw(raw.iloc[: cut + 1].copy(), "4h")

    full_row = full[full["ts_utc"] == ts_cut]
    trunc_row = truncated[truncated["ts_utc"] == ts_cut]
    assert len(full_row) == 1 and len(trunc_row) == 1

    a = full_row[FEATURE_COLUMNS].to_numpy(dtype=float)[0]
    b = trunc_row[FEATURE_COLUMNS].to_numpy(dtype=float)[0]
    assert np.allclose(a, b, rtol=1e-5, atol=1e-7), "feature row changed when future was truncated"
