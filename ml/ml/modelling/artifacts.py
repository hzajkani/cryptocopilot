"""Persist / load the trained model bundle under ``models/{version}/``.

A bundle is a single dict holding both the calibrated classifier (used for
probabilities at predict time) and the raw fitted XGBoost (used by SHAP's
TreeExplainer), plus the exact feature-column order and metadata. ``predict`` and
``explain`` reload it; nothing here trains.
"""

from __future__ import annotations

import json
import logging
from functools import lru_cache
from pathlib import Path
from typing import Any

import joblib

from ..config import MODEL_VERSION, MODELS_DIR

log = logging.getLogger(__name__)

BUNDLE_NAME = "bundle.joblib"
META_NAME = "meta.json"


def version_dir(version: str = MODEL_VERSION) -> Path:
    return MODELS_DIR / version


def bundle_path(version: str = MODEL_VERSION) -> Path:
    return version_dir(version) / BUNDLE_NAME


def save_bundle(bundle: dict[str, Any], version: str = MODEL_VERSION) -> Path:
    """Write the bundle (joblib) + a human-readable meta.json. Returns the path."""
    vdir = version_dir(version)
    vdir.mkdir(parents=True, exist_ok=True)
    path = vdir / BUNDLE_NAME
    joblib.dump(bundle, path)

    meta = {k: bundle[k] for k in ("model_version", "timeframe", "classes",
                                   "feature_cols", "metrics", "trained_at", "splits")
            if k in bundle}
    (vdir / META_NAME).write_text(json.dumps(meta, indent=2, default=str))
    log.info("saved model bundle -> %s (%d features)", path, len(bundle.get("feature_cols", [])))
    return path


@lru_cache(maxsize=2)
def load_bundle(version: str = MODEL_VERSION) -> dict[str, Any]:
    """Load (and cache) the bundle for ``version``."""
    path = bundle_path(version)
    if not path.exists():
        raise FileNotFoundError(
            f"no model bundle at {path}. Train first: "
            "docker compose run --rm ml python -m ml.train"
        )
    return joblib.load(path)
