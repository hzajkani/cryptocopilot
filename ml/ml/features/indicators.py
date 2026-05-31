"""Classical technical indicators (wrappers around the ``ta`` library).

All functions operate on a **single symbol's** OHLCV frame, sorted ascending by
``ts_utc``, and only ever look *backwards* (rolling / ewm / positive shift) — no
feature at bar ``T`` uses data after ``T`` (see ``tests/test_no_leakage.py``).

Price-scale indicators (MACD, ATR, Bollinger width) are normalised by ``close`` so
one global model can compare a $100k BTC bar with a $0.50 ADA bar on the same
footing. Oscillators (RSI, Stochastic, ADX) are already 0–100 and comparable.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from ta.momentum import RSIIndicator, StochasticOscillator
from ta.trend import MACD, ADXIndicator
from ta.volatility import AverageTrueRange, BollingerBands

from ..config import RETURN_HORIZONS_H, TIMEFRAME_HOURS

# Indicator columns this module adds (order is stable for the feature contract).
INDICATOR_COLUMNS = [
    "ret_1h", "ret_4h", "ret_24h", "ret_7d",
    "rsi_7", "rsi_14", "rsi_21",
    "macd", "macd_signal", "macd_hist", "macd_bull",
    "stoch_k", "stoch_d",
    "adx",
    "bb_pct", "bb_bandwidth",
    "atr_pct",
    "ret_vol_24h", "ret_vol_7d",
    "vol_zscore",
    "sma7_ratio", "sma30_ratio", "sma90_ratio",
]


def _bars(hours: int, timeframe: str) -> int:
    """Convert an hour-horizon to a bar count for ``timeframe`` (min 1 bar)."""
    tf_h = TIMEFRAME_HOURS[timeframe]
    return max(1, round(hours / tf_h))


def add_indicators(df: pd.DataFrame, timeframe: str) -> pd.DataFrame:
    """Return ``df`` (one symbol, ts-sorted) with the classical indicators added.

    Warmup rows hold NaN until each rolling window fills; ``build`` drops them.
    """
    out = df.copy()
    close = out["close"].astype(float)
    high = out["high"].astype(float)
    low = out["low"].astype(float)
    volume = out["volume"].astype(float)

    # --- multi-horizon returns (1h/4h/24h/7d, expressed in bars) ---
    for hours, name in zip(RETURN_HORIZONS_H, ["ret_1h", "ret_4h", "ret_24h", "ret_7d"]):
        out[name] = close.pct_change(_bars(hours, timeframe))

    # --- RSI at three scales (fast/standard/slow momentum) ---
    out["rsi_7"] = RSIIndicator(close=close, window=7, fillna=False).rsi()
    out["rsi_14"] = RSIIndicator(close=close, window=14, fillna=False).rsi()
    out["rsi_21"] = RSIIndicator(close=close, window=21, fillna=False).rsi()

    # --- MACD(12,26,9), normalised by close, + a bullish-crossover flag ---
    macd = MACD(close=close, window_slow=26, window_fast=12, window_sign=9, fillna=False)
    out["macd"] = macd.macd() / close
    out["macd_signal"] = macd.macd_signal() / close
    out["macd_hist"] = macd.macd_diff() / close
    out["macd_bull"] = (out["macd"] > out["macd_signal"]).astype("int8")

    # --- Stochastic oscillator %K/%D (14,3) ---
    stoch = StochasticOscillator(high=high, low=low, close=close, window=14, smooth_window=3, fillna=False)
    out["stoch_k"] = stoch.stoch()
    out["stoch_d"] = stoch.stoch_signal()

    # --- ADX(14): trend strength (a 2% move trends -> direction is more learnable) ---
    out["adx"] = ADXIndicator(high=high, low=low, close=close, window=14, fillna=False).adx()

    # --- Bollinger %B + bandwidth (volatility regime) ---
    bb = BollingerBands(close=close, window=20, window_dev=2, fillna=False)
    out["bb_pct"] = bb.bollinger_pband()
    out["bb_bandwidth"] = (bb.bollinger_hband() - bb.bollinger_lband()) / close

    # --- ATR(14) as a fraction of price ---
    atr = AverageTrueRange(high=high, low=low, close=close, window=14, fillna=False)
    out["atr_pct"] = atr.average_true_range() / close

    # --- realised volatility of bar returns over 24h / 7d ---
    r1 = close.pct_change(1)
    out["ret_vol_24h"] = r1.rolling(max(2, _bars(24, timeframe))).std(ddof=0)
    out["ret_vol_7d"] = r1.rolling(max(2, _bars(168, timeframe))).std(ddof=0)

    # --- volume z-score over a rolling 24h window ---
    win = max(2, _bars(24, timeframe))
    roll = volume.rolling(win)
    std = roll.std(ddof=0)
    z = (volume - roll.mean()) / std
    # constant-volume windows give 0/0 -> NaN after warmup; pin those to 0.
    out["vol_zscore"] = z.where(std.ne(0), 0.0).replace([np.inf, -np.inf], 0.0)

    # --- SMA ratios (close / SMA): stationary, comparable across coins ---
    for window, name in [(7, "sma7_ratio"), (30, "sma30_ratio"), (90, "sma90_ratio")]:
        out[name] = close / close.rolling(window).mean()

    return out
