"""Optuna hyperparameter search — maximise validation macro F1.

40 trials max (PROJECT.md / Stage 2 §5). Each trial fits XGBoost on train with
early stopping on val and scores macro F1 on val; the held-out val split is the
tuning signal (a single expanding step), keeping test pristine. ``n_estimators``
stays fixed at 500 and is governed by early stopping, so it is not searched.
"""

from __future__ import annotations

import logging

import numpy as np
import optuna
import pandas as pd

from ..config import OPTUNA_TRIALS, RANDOM_STATE
from .metrics import macro_f1
from .xgb_model import build_xgb, fit_xgb, sample_weights

log = logging.getLogger(__name__)
optuna.logging.set_verbosity(optuna.logging.WARNING)


def _suggest(trial: optuna.Trial) -> dict:
    return {
        "max_depth": trial.suggest_int("max_depth", 3, 7),
        "learning_rate": trial.suggest_float("learning_rate", 0.01, 0.1, log=True),
        "subsample": trial.suggest_float("subsample", 0.6, 1.0),
        "colsample_bytree": trial.suggest_float("colsample_bytree", 0.6, 1.0),
        "min_child_weight": trial.suggest_int("min_child_weight", 1, 10),
        "gamma": trial.suggest_float("gamma", 0.0, 5.0),
        "reg_lambda": trial.suggest_float("reg_lambda", 1e-3, 10.0, log=True),
        "reg_alpha": trial.suggest_float("reg_alpha", 1e-3, 10.0, log=True),
    }


def search(
    X_train: pd.DataFrame,
    y_train: np.ndarray,
    X_val: pd.DataFrame,
    y_val: np.ndarray,
    *,
    n_trials: int = OPTUNA_TRIALS,
) -> dict:
    """Run the study and return the best hyperparameters (val macro F1)."""
    weights = sample_weights(y_train)

    def objective(trial: optuna.Trial) -> float:
        params = _suggest(trial)
        model = build_xgb(params)
        fit_xgb(model, X_train, y_train, X_val, y_val, weights=weights)
        pred = model.predict(X_val)
        return macro_f1(y_val, pred)

    study = optuna.create_study(
        direction="maximize",
        sampler=optuna.samplers.TPESampler(seed=RANDOM_STATE),
    )
    log.info("optuna: %d trials, optimising val macro F1", n_trials)
    study.optimize(objective, n_trials=n_trials, show_progress_bar=False)
    log.info("optuna best val macro F1 %.4f with %s", study.best_value, study.best_params)
    return study.best_params
