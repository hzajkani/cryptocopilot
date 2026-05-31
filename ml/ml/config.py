"""Central configuration for the CryptoCopilot ML/data service.

Hard-coded constants (asset universe, timeframes, RSS sources, CoinGecko ids)
plus a few values pulled from the environment (API keys, DB URL). All of this is
read straight from PROJECT.md §5/§6 — the frozen spec.
"""

from __future__ import annotations

import os

from dotenv import load_dotenv

# Load a local .env when running outside Docker (no-op inside the container,
# where the environment is provided via docker-compose `env_file`).
load_dotenv()

# --------------------------------------------------------------------------- #
# Asset universe (PROJECT.md §6)
# --------------------------------------------------------------------------- #
ASSETS = ["BTC", "ETH", "SOL", "BNB", "XRP", "ADA", "AVAX", "DOT", "LINK", "MATIC"]
QUOTE = "USDT"
TIMEFRAMES = ["1h", "4h", "1d"]
HISTORY_DAYS = 730  # ~2 years

# CoinGecko coin ids (PROJECT.md §6). MATIC was rebranded POL; use the POL token
# id with a fallback to the legacy id.
COIN_IDS = {
    "BTC": "bitcoin",
    "ETH": "ethereum",
    "SOL": "solana",
    "BNB": "binancecoin",
    "XRP": "ripple",
    "ADA": "cardano",
    "AVAX": "avalanche-2",
    "DOT": "polkadot",
    "LINK": "chainlink",
    "MATIC": "polygon-ecosystem-token",
}
COIN_ID_FALLBACKS = {"MATIC": "matic-network"}

# Human-readable name aliases for tagging news by name (in addition to ticker).
COIN_NAMES = {
    "BTC": ["Bitcoin"],
    "ETH": ["Ethereum", "Ether"],
    "SOL": ["Solana"],
    "BNB": ["Binance Coin", "Binance"],
    "XRP": ["Ripple"],
    "ADA": ["Cardano"],
    "AVAX": ["Avalanche"],
    "DOT": ["Polkadot"],
    "LINK": ["Chainlink"],
    "MATIC": ["Polygon", "Matic", "POL"],
}

# --------------------------------------------------------------------------- #
# News (PROJECT.md §6)
# --------------------------------------------------------------------------- #
RSS_SOURCES = [
    {"name": "CoinDesk", "url": "https://www.coindesk.com/arc/outboundfeeds/rss/"},
    {"name": "Cointelegraph", "url": "https://cointelegraph.com/rss"},
    {"name": "Decrypt", "url": "https://decrypt.co/feed"},
    {"name": "The Block", "url": "https://www.theblock.co/rss.xml"},
    {"name": "Bitcoin Magazine", "url": "https://bitcoinmagazine.com/.rss/full/"},
]
NEWS_WINDOW_DAYS = 180  # rolling retention window for the `news` table

# VADER sentiment thresholds on the compound score.
SENTIMENT_POS_THRESHOLD = 0.2
SENTIMENT_NEG_THRESHOLD = -0.2

# --------------------------------------------------------------------------- #
# External APIs
# --------------------------------------------------------------------------- #
COINGECKO_API_KEY = os.getenv("COINGECKO_API_KEY", "")
COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3"
COINGECKO_SLEEP_SEC = 4.0  # demo tier: 30 req/min — pace at ~15/min for margin
# The Demo/free plan limits market_chart history to the past 365 days; asking
# for more returns HTTP 401. (OHLCV history still comes from Binance at 730d.)
COINGECKO_MARKET_CHART_DAYS = 365

ETHERSCAN_API_KEY = os.getenv("ETHERSCAN_API_KEY", "")
# Etherscan migrated to a unified multichain V2 API; pass chainid=1 for mainnet.
ETHERSCAN_BASE_URL = "https://api.etherscan.io/v2/api"
ETHERSCAN_CHAIN_ID = 1

BLOCKCHAIN_CHARTS_BASE_URL = "https://api.blockchain.info/charts"
BLOCKCHAIN_CHARTS = [
    "n-unique-addresses",
    "n-transactions",
    "estimated-transaction-volume-usd",
]

# --------------------------------------------------------------------------- #
# Database
# --------------------------------------------------------------------------- #
DATABASE_URL = os.getenv("DATABASE_URL", "")

# --------------------------------------------------------------------------- #
# ML service (Stage 2)
# --------------------------------------------------------------------------- #
from pathlib import Path  # noqa: E402

# Artifact roots. In the container these resolve to /app/{data,models,reports}
# (bind-mounted in docker-compose); locally they sit under ml/. ``parents[1]`` is
# the package-root dir (``/app`` in the image, ``ml/`` on the host).
_BASE_DIR = Path(os.getenv("ML_HOME", str(Path(__file__).resolve().parents[1])))
DATA_DIR = Path(os.getenv("ML_DATA_DIR", str(_BASE_DIR / "data")))
MODELS_DIR = Path(os.getenv("ML_MODELS_DIR", str(_BASE_DIR / "models")))
REPORTS_DIR = Path(os.getenv("ML_REPORTS_DIR", str(_BASE_DIR / "reports")))
PROCESSED_DIR = DATA_DIR / "processed"

# The model version written to ``predictions.model_version`` and the dir under
# ``models/`` that holds the calibrated bundle (``models/v1/``).
MODEL_VERSION = "v1"

# Feature timeframe used for signals (PROJECT.md §6: 1h and 4h for signals).
DEFAULT_TIMEFRAME = "4h"
# Hours per bar — used to convert horizons (1h/24h/7d) into bar counts so the
# same feature code works on any timeframe.
TIMEFRAME_HOURS = {"1h": 1, "4h": 4, "1d": 24}

# Return-horizon lookbacks emitted as features (in hours). On a 4h frame these
# map to {1, 1, 6, 42} bars (sub-bar horizons clamp to 1 bar).
RETURN_HORIZONS_H = [1, 4, 24, 168]

# --- Target (PROJECT.md / Stage 2 §3) ---------------------------------------
# r_24h = close[t+24h]/close[t] - 1 ; UP > +2%, DOWN < -2%, else FLAT.
# Kept at the spec's ±2% band: it is *more* learnable than a tighter band (a 2%
# 24h move trends; a 1% move is mostly noise — tightening to ±1% lowered AUC and
# macro F1 in testing). Class imbalance is handled at decision time via the
# validation-selected prior-corrected rule, not by redefining the target.
# Overridable via env for experiments.
TARGET_HORIZON_H = 24
TARGET_UP_THRESHOLD = float(os.getenv("ML_TARGET_UP", "0.02"))
TARGET_DOWN_THRESHOLD = float(os.getenv("ML_TARGET_DOWN", "-0.02"))
# Fixed class order -> integer codes (xgboost needs 0..K-1). Probability columns
# in `predictions` are filled from this order.
CLASSES = ["DOWN", "FLAT", "UP"]
CLASS_TO_CODE = {c: i for i, c in enumerate(CLASSES)}
CODE_TO_CLASS = {i: c for i, c in enumerate(CLASSES)}

# --- Time-based splits (no shuffle) -----------------------------------------
# NOTE (deviation from the Stage 2 prompt, documented in STATE.md): the prompt
# specifies Train 2023-01-01→2024-06-30 etc., but Binance only returned ~2 years
# of OHLCV (2024-05-31 → 2026-05-31). Those literal dates would leave ~1 month of
# training data. We keep the *methodology* (chronological, no shuffle, train ≫
# val, embargo gap) and anchor the boundaries inside the real span:
#   train 12mo / val 3mo / test 9mo→present.
# The test window deliberately spans ~9 months across multiple volatility regimes
# (a robust out-of-sample estimate, and faithful to the prompt's "test → present"
# large-test intent) rather than a short, single-regime tail. Override via env.
TRAIN_START = os.getenv("ML_TRAIN_START", "2024-06-01")
TRAIN_END = os.getenv("ML_TRAIN_END", "2025-05-31")
VAL_START = os.getenv("ML_VAL_START", "2025-06-01")
VAL_END = os.getenv("ML_VAL_END", "2025-08-31")
TEST_START = os.getenv("ML_TEST_START", "2025-09-01")
# Expanding-window CV folds on the training span (for Optuna).
CV_FOLDS = 6

# --- Tuning ------------------------------------------------------------------
OPTUNA_TRIALS = int(os.getenv("ML_OPTUNA_TRIALS", "40"))
RANDOM_STATE = 42
