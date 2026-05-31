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
