"""The primary model: a 3-class XGBoost direction classifier.

One global model (``symbol`` one-hot), ``multi:softprob`` over {DOWN, FLAT, UP},
early-stopped on the validation split, with balanced sample weights to offset
class imbalance. Hyperparameters are tuned by ``modelling.tune`` and the resulting
estimator is calibrated by ``modelling.calibrate``.
"""

from __future__ import annotations

import logging

import numpy as np
import pandas as pd
from sklearn.utils.class_weight import compute_sample_weight
from xgboost import XGBClassifier

from ..config import RANDOM_STATE

log = logging.getLogger(__name__)

# Fixed structural params (PROJECT.md / Stage 2 §4); the tunable ones live in
# ``tune.py`` and are merged on top of these.
BASE_PARAMS = dict(
    objective="multi:softprob",
    eval_metric="mlogloss",
    n_estimators=500,
    max_depth=5,
    learning_rate=0.05,
    early_stopping_rounds=40,
    tree_method="hist",
    n_jobs=-1,
    random_state=RANDOM_STATE,
)


def sample_weights(y: np.ndarray) -> np.ndarray:
    """Balanced per-sample weights so FLAT does not swamp UP/DOWN."""
    return compute_sample_weight(class_weight="balanced", y=y)


def build_xgb(params: dict | None = None, **overrides) -> XGBClassifier:
    """Construct an XGBClassifier from ``BASE_PARAMS`` + tuned overrides."""
    merged = {**BASE_PARAMS, **(params or {}), **overrides}
    return XGBClassifier(**merged)


def fit_xgb(
    model: XGBClassifier,
    X_train: pd.DataFrame,
    y_train: np.ndarray,
    X_val: pd.DataFrame,
    y_val: np.ndarray,
    *,
    weights: np.ndarray | None = None,
) -> XGBClassifier:
    """Fit with early stopping on (X_val, y_val); balanced weights by default."""
    if weights is None:
        weights = sample_weights(y_train)
    model.fit(
        X_train, y_train,
        sample_weight=weights,
        eval_set=[(X_val, y_val)],
        verbose=False,
    )
    best = getattr(model, "best_iteration", None)
    if best is not None:
        log.info("xgboost stopped at iteration %s/%s", best, model.n_estimators)
    return model
