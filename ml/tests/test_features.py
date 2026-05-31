"""Feature build: every engineered column is present and NaN-free after warmup."""

from __future__ import annotations

import pytest

pytest.importorskip("ta")

from ml.features.build import FEATURE_COLUMNS, META_COLUMNS, build_from_raw  # noqa: E402
from tests._synthetic import make_ohlcv  # noqa: E402


def test_features_present_and_no_nan_after_warmup():
    raw = make_ohlcv(symbols=("AAA", "BBB"), n=400, timeframe="4h")
    df = build_from_raw(raw, "4h")

    assert not df.empty
    # all contract columns exist, in the expected order
    assert list(df.columns) == META_COLUMNS + FEATURE_COLUMNS
    # warmup dropped -> zero NaN in the feature block
    assert df[FEATURE_COLUMNS].isna().to_numpy().sum() == 0
    # both symbols survive the warmup drop
    assert set(df["symbol"]) == {"AAA", "BBB"}


def test_bounded_features_in_range():
    raw = make_ohlcv(symbols=("AAA",), n=400, timeframe="4h")
    df = build_from_raw(raw, "4h")
    assert df["rsi_14"].between(0, 100).all()
    # the ichimoku flags are 0/1
    for col in ["ichimoku_above_cloud", "ichimoku_below_cloud", "ichimoku_in_cloud",
                "ichimoku_tk_cross_bull", "ichimoku_tk_cross_bear", "ichimoku_chikou_clear"]:
        assert set(df[col].unique()) <= {0, 1}
    # exactly one of above/below/in cloud is true per row
    tri = df[["ichimoku_above_cloud", "ichimoku_below_cloud", "ichimoku_in_cloud"]].sum(axis=1)
    assert (tri == 1).all()
