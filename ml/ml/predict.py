"""The ``predict`` job — the only thing in this service that writes model output.

For each of the 10 coins it takes the latest feature row, produces calibrated
probabilities + the predicted class, and computes the top-3 SHAP drivers. It then
upserts one row per coin into ``predictions`` and three per coin into
``prediction_drivers`` (PROJECT.md §3 — these are the only tables ML writes).

    docker compose run --rm ml python -m ml.predict
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone

from .config import CODE_TO_CLASS, DEFAULT_TIMEFRAME
from .explain import get_explainer, top_drivers_from_row
from .features.build import latest_feature_row_per_symbol
from .modelling.artifacts import load_bundle
from .modelling.encode import make_X
from .modelling.metrics import aligned_proba, decide

log = logging.getLogger(__name__)


def run_predict(timeframe: str = DEFAULT_TIMEFRAME) -> dict[str, int]:
    """Predict + write the latest forecast and drivers for every coin.

    Returns ``{"predictions": n, "drivers": m}``.
    """
    from . import db  # local import: keep module import side-effect free

    bundle = load_bundle()
    calibrated, xgb = bundle["calibrated"], bundle["xgb"]
    feature_cols, model_version = bundle["feature_cols"], bundle["model_version"]
    weights = bundle.get("decision_weights")

    latest = latest_feature_row_per_symbol(timeframe)
    if latest.empty:
        log.warning("no feature rows available — nothing to predict")
        return {"predictions": 0, "drivers": 0}

    X = make_X(latest, feature_cols)
    proba = aligned_proba(calibrated, X)  # columns = [DOWN, FLAT, UP], calibrated
    pred_codes = decide(proba, weights)  # balanced (val-tuned weighted) class

    explainer = get_explainer(xgb)
    now = datetime.now(timezone.utc)

    pred_rows: list[dict] = []
    driver_rows: list[dict] = []
    for i, (_, row) in enumerate(latest.iterrows()):
        ts = row["ts_utc"].to_pydatetime()
        symbol = row["symbol"]
        code = int(pred_codes[i])
        pred_rows.append({
            "ts_utc": ts,
            "symbol": symbol,
            "timeframe": timeframe,
            "pred_class": CODE_TO_CLASS[code],
            "prob_down": float(proba[i, 0]),
            "prob_flat": float(proba[i, 1]),
            "prob_up": float(proba[i, 2]),
            "model_version": model_version,
            "created_at": now,
        })
        for rank, (feat, value, shap_val) in enumerate(
            top_drivers_from_row(explainer, X.iloc[[i]], code), start=1
        ):
            driver_rows.append({
                "ts_utc": ts,
                "symbol": symbol,
                "timeframe": timeframe,
                "rank": rank,
                "feature_name": feat,
                "feature_value": value,
                "shap_value": shap_val,
            })

    n_pred = db.upsert_predictions(pred_rows)
    n_drv = db.upsert_prediction_drivers(driver_rows)
    log.info(
        "predict (%s, model %s): wrote %d predictions, %d drivers for %d coins",
        timeframe, model_version, n_pred, n_drv, len(latest),
    )
    for r in pred_rows:
        log.info(
            "  %-5s %-4s  UP %.2f  FLAT %.2f  DOWN %.2f",
            r["symbol"], r["pred_class"], r["prob_up"], r["prob_flat"], r["prob_down"],
        )
    return {"predictions": n_pred, "drivers": n_drv}


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    run_predict()


if __name__ == "__main__":
    main()
