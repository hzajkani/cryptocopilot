"""Isotonic probability calibration on the validation split.

The base XGBoost is fit on **train** (early-stopped on val); we then calibrate it
on **val** with isotonic regression. We deliberately do *not* refit on train+val
before calibrating — isotonic-on-val is only honest if the base estimator has not
seen val (this is the leakage-safe reading of "calibrate with cv='prefit' on
val"; documented in the model card). Test stays untouched for final evaluation.

Handles the sklearn 1.6 transition where ``cv="prefit"`` was replaced by wrapping
the fitted estimator in ``FrozenEstimator``.
"""

from __future__ import annotations

import logging

import numpy as np
import pandas as pd
from sklearn.calibration import CalibratedClassifierCV

log = logging.getLogger(__name__)


def calibrate(base_model, X_val: pd.DataFrame, y_val: np.ndarray) -> CalibratedClassifierCV:
    """Return an isotonic-calibrated wrapper around a prefit ``base_model``."""
    try:  # sklearn >= 1.6
        from sklearn.frozen import FrozenEstimator

        cal = CalibratedClassifierCV(FrozenEstimator(base_model), method="isotonic")
    except ImportError:  # sklearn < 1.6
        cal = CalibratedClassifierCV(base_model, method="isotonic", cv="prefit")
    cal.fit(X_val, y_val)
    log.info("calibrated base model with isotonic regression on %d val rows", len(X_val))
    return cal
