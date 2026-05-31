"""Assemble the full feature matrix from ``ohlcv`` and cache it as parquet.

``build_all_assets(timeframe)`` returns a **long-format** DataFrame (``symbol`` is
a column / feature, not a wide pivot), one row per ``(symbol, ts_utc)`` after the
indicator warmup is dropped. Output is cached to
``data/processed/features_{timeframe}.parquet`` on the mounted volume — features
never enter the database (PROJECT.md §3).

Columns: ``ts_utc, symbol, close`` (meta; ``close`` is kept only so the target can
be derived and so predict knows the latest price) + the engineered features.
"""

from __future__ import annotations

import logging

import pandas as pd

from ..config import DEFAULT_TIMEFRAME, PROCESSED_DIR
from .. import db
from .calendar import CALENDAR_COLUMNS, add_calendar
from .ichimoku import ICHIMOKU_COLUMNS, add_ichimoku_features
from .indicators import INDICATOR_COLUMNS, add_indicators

log = logging.getLogger(__name__)

# The canonical engineered-feature list (no symbol one-hot — that is added in the
# modelling layer). Order is stable so saved models line up on reload.
FEATURE_COLUMNS = INDICATOR_COLUMNS + ICHIMOKU_COLUMNS + CALENDAR_COLUMNS
# Non-feature columns carried through the frame.
META_COLUMNS = ["ts_utc", "symbol", "close"]


def _features_for_symbol(g: pd.DataFrame, timeframe: str) -> pd.DataFrame:
    """Compute all per-symbol (backward-only) features for one coin."""
    g = g.sort_values("ts_utc").reset_index(drop=True)
    g = add_indicators(g, timeframe)
    g = add_ichimoku_features(g)
    return g


def build_from_raw(raw: pd.DataFrame, timeframe: str) -> pd.DataFrame:
    """Core feature build from a raw OHLCV frame (no DB) — used by tests too.

    ``raw`` needs columns ``ts_utc, symbol, open, high, low, close, volume``.
    Returns ``META_COLUMNS + FEATURE_COLUMNS`` with the indicator warmup dropped.
    """
    parts = [
        _features_for_symbol(g, timeframe)
        for _, g in raw.groupby("symbol", sort=True)
    ]
    df = pd.concat(parts, ignore_index=True)
    df = add_calendar(df)

    # Drop the indicator warmup: any row still holding a NaN feature.
    before = len(df)
    df = df.dropna(subset=FEATURE_COLUMNS).reset_index(drop=True)
    log.info("dropped %d warmup rows; %d feature rows remain", before - len(df), len(df))

    return df[META_COLUMNS + FEATURE_COLUMNS].sort_values(["symbol", "ts_utc"]).reset_index(drop=True)


def build_all_assets(
    timeframe: str = DEFAULT_TIMEFRAME,
    *,
    use_cache: bool = False,
    save: bool = True,
) -> pd.DataFrame:
    """Build (or load) the feature matrix for every asset at ``timeframe``.

    Parameters
    ----------
    use_cache : read the parquet cache if present instead of recomputing.
    save      : write the parquet cache after building.
    """
    cache_path = PROCESSED_DIR / f"features_{timeframe}.parquet"
    if use_cache and cache_path.exists():
        log.info("loading cached features from %s", cache_path)
        return pd.read_parquet(cache_path)

    raw = db.query_ohlcv_all(timeframe)
    if raw.empty:
        raise RuntimeError(f"no OHLCV rows for timeframe={timeframe!r} — run Stage 1 ingestion first")
    log.info("building features for %d symbols, %d raw bars (%s)",
             raw["symbol"].nunique(), len(raw), timeframe)

    df = build_from_raw(raw, timeframe)

    if save:
        PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
        df.to_parquet(cache_path, index=False)
        log.info("cached features -> %s", cache_path)
    return df


def latest_feature_row_per_symbol(timeframe: str = DEFAULT_TIMEFRAME) -> pd.DataFrame:
    """Return the single most-recent feature row for each symbol (for predict)."""
    df = build_all_assets(timeframe, use_cache=False, save=False)
    return df.sort_values("ts_utc").groupby("symbol", as_index=False).tail(1).reset_index(drop=True)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    out = build_all_assets()
    print(out.shape)
    print(out.head())
