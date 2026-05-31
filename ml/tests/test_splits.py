"""Time-based splits: chronological, embargoed, and free of row/symbol leakage."""

from __future__ import annotations

import pandas as pd
import pytest

pytest.importorskip("ta")

from ml.config import TARGET_HORIZON_H  # noqa: E402
from ml.features.build import build_from_raw  # noqa: E402
from ml.modelling.splits import add_target, time_splits  # noqa: E402
from tests._synthetic import make_ohlcv  # noqa: E402

SYMBOLS = ("AAA", "BBB", "CCC")


def _splits():
    # ~2 years at 4h so the configured train/val/test windows are all populated.
    raw = make_ohlcv(symbols=SYMBOLS, n=4300, timeframe="4h", start="2024-06-01")
    feats = add_target(build_from_raw(raw, "4h"), "4h")
    return time_splits(feats)


def test_splits_nonempty():
    train, val, test = _splits()
    assert len(train) and len(val) and len(test)


def test_no_temporal_overlap_with_embargo():
    train, val, test = _splits()
    horizon = pd.Timedelta(hours=TARGET_HORIZON_H)
    # strictly ordered in time
    assert train["ts_utc"].max() < val["ts_utc"].min()
    assert val["ts_utc"].max() < test["ts_utc"].min()
    # embargo: a train/val label's 24h window never reaches the next split
    assert train["ts_utc"].max() + horizon <= val["ts_utc"].min()
    assert val["ts_utc"].max() + horizon <= test["ts_utc"].min()


def test_no_row_or_symbol_leakage():
    train, val, test = _splits()
    keys = [set(zip(p["symbol"], p["ts_utc"])) for p in (train, val, test)]
    # no (symbol, ts) appears in more than one split
    assert keys[0] & keys[1] == set()
    assert keys[0] & keys[2] == set()
    assert keys[1] & keys[2] == set()
    # the global model sees every symbol in every split (split is by time, not coin)
    for part in (train, val, test):
        assert set(part["symbol"]) == set(SYMBOLS)
