"""Logistic-regression baseline — the bar XGBoost must clear before we ship it.

Small, interpretable feature subset (PROJECT.md / Stage 2 §4), standardised, with
balanced class weights. No symbol one-hot: the baseline is deliberately weak so
"XGBoost beats baseline" is a meaningful gate.
"""

from __future__ import annotations

import logging

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from ..config import RANDOM_STATE
from .encode import make_y
from .metrics import aligned_proba, macro_auc, macro_f1

log = logging.getLogger(__name__)

BASELINE_FEATURES = [
    "ret_1h", "ret_24h", "rsi_14", "macd_signal", "bb_pct",
    "ichimoku_above_cloud", "ichimoku_tk_cross_bull",
]


def train_baseline(
    train: pd.DataFrame, val: pd.DataFrame, test: pd.DataFrame
) -> dict:
    """Fit the LogReg baseline on train; report macro F1/AUC on val and test."""
    pipe = make_pipeline(
        StandardScaler(),
        # NB: scikit-learn 1.7+ removed the `multi_class` kwarg — LogisticRegression
        # is multinomial by default with the lbfgs solver, which is what we want.
        LogisticRegression(
            class_weight="balanced",
            max_iter=2000,
            random_state=RANDOM_STATE,
        ),
    )
    pipe.fit(train[BASELINE_FEATURES].to_numpy(dtype=float), make_y(train))

    out = {"model": pipe, "features": BASELINE_FEATURES}
    for name, part in (("val", val), ("test", test)):
        X = part[BASELINE_FEATURES].to_numpy(dtype=float)
        y = make_y(part)
        pred = pipe.predict(X)
        proba = aligned_proba(pipe, X)
        out[f"{name}_macro_f1"] = macro_f1(y, pred)
        out[f"{name}_macro_auc"] = macro_auc(y, proba)
    log.info(
        "baseline LogReg — val macro F1 %.3f | test macro F1 %.3f (AUC %.3f)",
        out["val_macro_f1"], out["test_macro_f1"], out["test_macro_auc"],
    )
    return out
