"""SHAP explainability over the raw XGBoost (TreeExplainer).

Two jobs:
  * ``save_shap_summary`` — a global beeswarm (top 15) for the report.
  * ``top_drivers`` / ``top_drivers_from_row`` — the top-3 features pushing a
    single prediction, which become the ``prediction_drivers`` rows.

SHAP runs on the *raw* fitted XGBoost (calibration wraps it and is not a tree
model). Symbol one-hot columns are excluded from the per-row top-3 so drivers
describe market state (RSI, returns, Ichimoku…), not "this coin is BTC".
"""

from __future__ import annotations

import logging

import matplotlib

matplotlib.use("Agg")  # headless container — no display
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402
import shap  # noqa: E402

from .config import CODE_TO_CLASS, DEFAULT_TIMEFRAME, REPORTS_DIR  # noqa: E402
from .modelling.encode import SYMBOL_PREFIX, make_X  # noqa: E402
from .modelling.metrics import aligned_proba  # noqa: E402

log = logging.getLogger(__name__)

UP_CODE = 2  # CLASSES = [DOWN, FLAT, UP]


def get_explainer(xgb) -> shap.TreeExplainer:
    return shap.TreeExplainer(xgb)


def _class_shap(shap_values, code: int) -> np.ndarray:
    """Normalise SHAP output (list-of-arrays or 3D ndarray) to (n, n_features)."""
    if isinstance(shap_values, list):
        return np.asarray(shap_values[code])
    arr = np.asarray(shap_values)
    if arr.ndim == 3:  # (n_samples, n_features, n_classes)
        return arr[:, :, code]
    return arr  # already (n, n_features)


def save_shap_summary(xgb, X_sample: pd.DataFrame, path=None) -> str:
    """Save a beeswarm summary (top 15 features, UP class) to reports/."""
    path = str(path or (REPORTS_DIR / "shap_summary.png"))
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    explainer = get_explainer(xgb)
    sv = _class_shap(explainer.shap_values(X_sample), UP_CODE)
    plt.figure()
    shap.summary_plot(sv, X_sample, max_display=15, show=False, plot_type="dot")
    plt.title("SHAP — drivers of P(UP) (test sample)")
    plt.tight_layout()
    plt.savefig(path, dpi=120, bbox_inches="tight")
    plt.close("all")
    log.info("saved SHAP summary -> %s", path)
    return path


def top_drivers_from_row(
    explainer: shap.TreeExplainer,
    x_row: pd.DataFrame,
    pred_code: int,
    *,
    k: int = 3,
) -> list[tuple[str, float, float]]:
    """Top-``k`` (feature, value, shap) for one prediction's predicted class.

    ``x_row`` is a single-row design matrix. Symbol one-hot columns are skipped.
    """
    sv = _class_shap(explainer.shap_values(x_row), pred_code)[0]
    values = x_row.iloc[0]
    drivers = [
        (col, float(values[col]), float(sv[i]))
        for i, col in enumerate(x_row.columns)
        if not col.startswith(f"{SYMBOL_PREFIX}_")
    ]
    drivers.sort(key=lambda t: abs(t[2]), reverse=True)
    return drivers[:k]


def top_drivers(symbol: str, ts, timeframe: str = DEFAULT_TIMEFRAME, *, k: int = 3):
    """Standalone: top-``k`` drivers for ``(symbol, ts)`` (rebuilds features).

    Mainly for ad-hoc inspection; ``predict`` uses ``top_drivers_from_row`` with the
    rows it already computed to avoid recomputing features per coin.
    """
    from .features.build import build_all_assets
    from .modelling.artifacts import load_bundle

    bundle = load_bundle()
    feats = build_all_assets(timeframe, use_cache=False, save=False)
    row = feats[(feats["symbol"] == symbol) & (feats["ts_utc"] == pd.Timestamp(ts, tz="UTC"))]
    if row.empty:
        raise ValueError(f"no feature row for {symbol} at {ts}")
    X = make_X(row, bundle["feature_cols"])
    pred_code = int(np.argmax(aligned_proba(bundle["calibrated"], X)[0]))
    explainer = get_explainer(bundle["xgb"])
    log.info("top drivers for %s @ %s -> predicted %s", symbol, ts, CODE_TO_CLASS[pred_code])
    return top_drivers_from_row(explainer, X, pred_code, k=k)
