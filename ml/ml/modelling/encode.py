"""Turn a feature DataFrame into the model design matrix ``X`` (and labels ``y``).

One global model with ``symbol`` one-hot encoded (leakage-free, unlike target
encoding which would need per-fold fitting). The exact trained column order is
saved in the model bundle and replayed at predict time via ``feature_cols`` so a
single-coin predict frame lines up with the matrix the model was fit on.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from ..config import CLASS_TO_CODE
from ..features.build import FEATURE_COLUMNS
from .splits import TARGET_COL

SYMBOL_PREFIX = "symbol"


def make_X(df: pd.DataFrame, feature_cols: list[str] | None = None) -> pd.DataFrame:
    """Build the design matrix: base features + one-hot ``symbol``.

    If ``feature_cols`` is given (the trained column order), the result is
    reindexed onto it — missing dummies become 0, extras are dropped — so predict
    matrices always match the fitted model.
    """
    base = df[FEATURE_COLUMNS].astype("float32").reset_index(drop=True)
    dummies = pd.get_dummies(df["symbol"], prefix=SYMBOL_PREFIX).astype("float32").reset_index(drop=True)
    X = pd.concat([base, dummies], axis=1)
    if feature_cols is not None:
        X = X.reindex(columns=feature_cols, fill_value=0.0)
    return X


def make_y(df: pd.DataFrame) -> np.ndarray:
    """Encode the 3-class string target to integer codes (DOWN=0, FLAT=1, UP=2)."""
    return df[TARGET_COL].map(CLASS_TO_CODE).to_numpy(dtype=int)


def make_xy(
    df: pd.DataFrame, feature_cols: list[str] | None = None
) -> tuple[pd.DataFrame, np.ndarray]:
    return make_X(df, feature_cols), make_y(df)
