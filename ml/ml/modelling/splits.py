"""Leakage-safe target construction and time-based train/val/test splits.

Target (PROJECT.md / Stage 2 §3)::

    r_24h = close[t + 24h] / close[t] - 1
    UP   if r_24h > +0.02
    DOWN if r_24h < -0.02
    FLAT otherwise                       -> column ``y_24h_3class``

Features at ``T`` use only data with ``ts <= T``; the target looks *forward* into
``(T, T+24h]`` — that asymmetry is the whole point and is allowed (the target may
peek ahead, features may not).

Splits are strictly chronological (no shuffle) with an **embargo**: rows whose
24h target window would spill into the next split are dropped, so no training
label is computed from validation/test bars. Boundaries come from ``config`` and
are anchored to the real data span (see the deviation note there + STATE.md).
"""

from __future__ import annotations

import logging

import pandas as pd

from ..config import (
    CLASSES,
    TARGET_DOWN_THRESHOLD,
    TARGET_HORIZON_H,
    TARGET_UP_THRESHOLD,
    TEST_START,
    TIMEFRAME_HOURS,
    TRAIN_END,
    TRAIN_START,
    VAL_END,
    VAL_START,
)

log = logging.getLogger(__name__)

TARGET_COL = "y_24h_3class"
RETURN_COL = "r_24h"


def _classify(r: float) -> str | float:
    if pd.isna(r):
        return float("nan")
    if r > TARGET_UP_THRESHOLD:
        return "UP"
    if r < TARGET_DOWN_THRESHOLD:
        return "DOWN"
    return "FLAT"


def add_target(df: pd.DataFrame, timeframe: str) -> pd.DataFrame:
    """Add ``r_24h`` and ``y_24h_3class`` per symbol (forward 24h return)."""
    periods = max(1, round(TARGET_HORIZON_H / TIMEFRAME_HOURS[timeframe]))
    out = df.sort_values(["symbol", "ts_utc"]).reset_index(drop=True)
    # close 24h ahead, computed within each symbol's own series
    fwd = out.groupby("symbol", sort=False)["close"].shift(-periods)
    out[RETURN_COL] = fwd / out["close"] - 1.0
    out[TARGET_COL] = out[RETURN_COL].map(_classify)
    return out


def _ts(value) -> pd.Timestamp:
    return pd.Timestamp(value, tz="UTC")


def time_splits(
    df: pd.DataFrame,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """Split into (train, val, test) chronologically, with a 24h embargo.

    Each split keeps only rows with a defined target. Train/val additionally drop
    the final ``horizon`` of bars so their labels never reach into the next split.
    """
    if TARGET_COL not in df.columns:
        raise ValueError("call add_target() before time_splits()")

    ts = df["ts_utc"]
    horizon = pd.Timedelta(hours=TARGET_HORIZON_H)
    train_start, train_end = _ts(TRAIN_START), _ts(TRAIN_END)
    val_start, val_end = _ts(VAL_START), _ts(VAL_END)
    test_start = _ts(TEST_START)

    train = df[(ts >= train_start) & (ts <= train_end) & (ts + horizon < val_start)]
    val = df[(ts >= val_start) & (ts <= val_end) & (ts + horizon < test_start)]
    test = df[ts >= test_start]

    train = train.dropna(subset=[TARGET_COL]).reset_index(drop=True)
    val = val.dropna(subset=[TARGET_COL]).reset_index(drop=True)
    test = test.dropna(subset=[TARGET_COL]).reset_index(drop=True)

    log.info(
        "splits — train %d (%s→%s) | val %d (%s→%s) | test %d (%s→%s)",
        len(train), _span(train), "",
        len(val), _span(val), "",
        len(test), _span(test), "",
    )
    for name, part in (("train", train), ("val", val), ("test", test)):
        if part.empty:
            log.warning("split %s is EMPTY — check config split dates vs ingested data span", name)
        else:
            dist = part[TARGET_COL].value_counts(normalize=True).reindex(CLASSES).round(3).to_dict()
            log.info("  %-5s class balance: %s", name, dist)
    return train, val, test


def _span(part: pd.DataFrame) -> str:
    if part.empty:
        return "∅"
    return f"{part['ts_utc'].min():%Y-%m-%d}..{part['ts_utc'].max():%Y-%m-%d}"


def expanding_window_folds(train: pd.DataFrame, n_folds: int):
    """Yield ``(fit_idx, eval_idx)`` for expanding-window CV over the train span.

    Provided for completeness / tests. The main tuning signal is the dedicated
    held-out validation split (see ``modelling.tune``); each fold here grows the
    fit window and evaluates on the next contiguous block of timestamps — never
    shuffling and never letting a fold evaluate on its own past.
    """
    times = train["ts_utc"].sort_values().unique()
    if len(times) < n_folds + 1:
        return
    bounds = pd.Series(times).quantile([i / (n_folds + 1) for i in range(1, n_folds + 2)]).tolist()
    ordered = train.sort_values("ts_utc")
    for k in range(n_folds):
        fit_end = bounds[k]
        eval_end = bounds[k + 1]
        fit_idx = ordered.index[ordered["ts_utc"] <= fit_end]
        eval_idx = ordered.index[(ordered["ts_utc"] > fit_end) & (ordered["ts_utc"] <= eval_end)]
        if len(fit_idx) and len(eval_idx):
            yield fit_idx.to_numpy(), eval_idx.to_numpy()
