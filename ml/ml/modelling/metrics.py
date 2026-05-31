"""Evaluation metrics shared by training, tuning and backtest.

Everything works in the integer class space (DOWN=0, FLAT=1, UP=2) and expects
probabilities already aligned to that column order — use ``aligned_proba`` so a
model whose ``classes_`` is permuted still lines up.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.metrics import f1_score, roc_auc_score

N_CLASSES = 3
LABELS = list(range(N_CLASSES))


def aligned_proba(model, X) -> np.ndarray:
    """``predict_proba`` reordered so column ``j`` is the probability of code ``j``."""
    proba = np.asarray(model.predict_proba(X))
    classes = list(model.classes_)
    order = [classes.index(c) for c in LABELS]
    return proba[:, order]


def class_priors(y: np.ndarray) -> np.ndarray:
    """Empirical class priors in code order [DOWN, FLAT, UP]."""
    p = np.bincount(np.asarray(y, dtype=int), minlength=N_CLASSES).astype(float)
    return p / p.sum()


DEFAULT_WEIGHTS = [1.0, 1.0, 1.0]


def decide(proba: np.ndarray, weights=None) -> np.ndarray:
    """Predicted class codes via a (re)weighted argmax: ``argmax_k w_k · p_k``.

    Probabilities are untouched — only the *chosen label* is rebalanced, which is
    what a macro-averaged objective needs under heavy class imbalance (here FLAT
    dominates). ``weights`` are tuned on **validation**, never on test.
    """
    proba = np.asarray(proba, dtype=float)
    if weights is None:
        return proba.argmax(axis=1)
    return (proba * np.asarray(weights, dtype=float)).argmax(axis=1)


def select_weights(
    proba_val: np.ndarray,
    y_val: np.ndarray,
    grid=(1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 4.0),
) -> list[float]:
    """Grid-search per-class decision weights to maximise **validation** macro F1.

    FLAT is pinned to 1.0; DOWN/UP weights are searched over ``grid`` (boosting the
    minority directional classes against the dominant FLAT). Returns ``[w_down, 1,
    w_up]``. This is a validation-only decision threshold — it never sees test.
    """
    best_w, best_f1 = list(DEFAULT_WEIGHTS), -1.0
    for w_down in grid:
        for w_up in grid:
            w = [w_down, 1.0, w_up]
            f1 = macro_f1(y_val, decide(proba_val, w))
            if f1 > best_f1:
                best_w, best_f1 = w, f1
    return best_w


def macro_f1(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    return float(f1_score(y_true, y_pred, labels=LABELS, average="macro", zero_division=0))


def macro_auc(y_true: np.ndarray, proba: np.ndarray) -> float:
    """One-vs-rest macro ROC-AUC. Returns NaN if a class is absent from y_true."""
    if len(np.unique(y_true)) < N_CLASSES:
        return float("nan")
    return float(roc_auc_score(y_true, proba, labels=LABELS, multi_class="ovr", average="macro"))


def multiclass_brier(y_true: np.ndarray, proba: np.ndarray) -> float:
    """Mean squared error vs one-hot truth, summed over classes (range 0..2)."""
    onehot = np.eye(N_CLASSES)[y_true]
    return float(np.mean(np.sum((proba - onehot) ** 2, axis=1)))


def per_symbol_macro_f1(symbols: pd.Series, y_true: np.ndarray, y_pred: np.ndarray) -> dict[str, float]:
    """Macro F1 computed within each symbol (for the per-coin best/worst report)."""
    s = pd.DataFrame({"symbol": np.asarray(symbols), "y": y_true, "p": y_pred})
    return {
        sym: macro_f1(g["y"].to_numpy(), g["p"].to_numpy())
        for sym, g in s.groupby("symbol", sort=True)
    }


def summarize(y_true: np.ndarray, y_pred: np.ndarray, proba: np.ndarray) -> dict[str, float]:
    return {
        "macro_f1": macro_f1(y_true, y_pred),
        "macro_auc": macro_auc(y_true, proba),
        "brier": multiclass_brier(y_true, proba),
    }
