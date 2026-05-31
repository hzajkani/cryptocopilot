"""On-chain ingestion -> ``onchain`` (long format: ts, symbol, metric, value, source).

- **BTC** via Blockchain.com Charts (daily series, last 1 year).
- **ETH** via Etherscan free ``stats`` endpoints (point-in-time snapshots). Most
  *daily* Etherscan endpoints are Pro-only; if any endpoint is unavailable we
  log and skip it rather than crashing.
"""

from __future__ import annotations

import logging
import time
from datetime import datetime, timezone

import requests

from ..config import (
    BLOCKCHAIN_CHARTS,
    BLOCKCHAIN_CHARTS_BASE_URL,
    ETHERSCAN_API_KEY,
    ETHERSCAN_BASE_URL,
    ETHERSCAN_CHAIN_ID,
)

log = logging.getLogger(__name__)

ONCHAIN_COLUMNS = ["ts_utc", "symbol", "metric", "value", "source"]
_WEI = 1e18


def _btc_rows() -> list[dict]:
    rows: list[dict] = []
    for chart in BLOCKCHAIN_CHARTS:
        url = f"{BLOCKCHAIN_CHARTS_BASE_URL}/{chart}"
        try:
            resp = requests.get(url, params={"timespan": "1year", "format": "json"}, timeout=30)
            resp.raise_for_status()
            values = resp.json().get("values", [])
        except (requests.RequestException, ValueError) as exc:
            log.warning("blockchain.com %s failed — skipping: %s", chart, exc)
            continue
        for point in values:
            ts = point.get("x")
            if ts is None:
                continue
            rows.append(
                {
                    "ts_utc": datetime.fromtimestamp(ts, tz=timezone.utc),
                    "symbol": "BTC",
                    "metric": chart,
                    "value": point.get("y"),
                    "source": "blockchain_com",
                }
            )
        log.info("onchain BTC %-32s: %d points", chart, len(values))
        time.sleep(0.5)
    return rows


def _etherscan_stat(action: str, extra: dict | None = None):
    """Call an Etherscan ``stats`` endpoint; raise on a non-OK response."""
    params = {
        "chainid": ETHERSCAN_CHAIN_ID,
        "module": "stats",
        "action": action,
        "apikey": ETHERSCAN_API_KEY,
    }
    if extra:
        params.update(extra)
    resp = requests.get(ETHERSCAN_BASE_URL, params=params, timeout=30)
    resp.raise_for_status()
    payload = resp.json()
    if str(payload.get("status")) != "1":
        # Pro-only / rate-limited / error — surface so the caller logs and skips.
        raise RuntimeError(f"{action}: {payload.get('message')} ({payload.get('result')})")
    return payload["result"]


def _eth_rows() -> list[dict]:
    if not ETHERSCAN_API_KEY:
        log.warning("ETHERSCAN_API_KEY not set — skipping ETH on-chain")
        return []

    now = datetime.now(timezone.utc)
    rows: list[dict] = []

    def add(metric: str, value):
        rows.append({"ts_utc": now, "symbol": "ETH", "metric": metric, "value": value, "source": "etherscan"})

    # Total ETH supply (wei -> ETH).
    try:
        add("eth_supply", float(_etherscan_stat("ethsupply")) / _WEI)
    except Exception as exc:
        log.warning("etherscan ethsupply skipped: %s", exc)

    # Extended supply breakdown (dict of wei strings).
    try:
        result = _etherscan_stat("ethsupply2")
        for key, metric in (("Eth2Staking", "eth2_staking"), ("BurntFees", "burnt_fees")):
            if isinstance(result, dict) and result.get(key) is not None:
                add(metric, float(result[key]) / _WEI)
    except Exception as exc:
        log.warning("etherscan ethsupply2 skipped: %s", exc)

    # Total node count.
    try:
        result = _etherscan_stat("nodecount")
        if isinstance(result, dict) and result.get("TotalNodeCount") is not None:
            add("node_count", float(result["TotalNodeCount"]))
    except Exception as exc:
        log.warning("etherscan nodecount skipped: %s", exc)

    log.info("onchain ETH (etherscan): %d snapshot metrics", len(rows))
    return rows


def ingest() -> int:
    """Fetch + upsert on-chain metrics for BTC and ETH. Returns rows upserted."""
    from ..db import upsert_onchain

    rows = _btc_rows() + _eth_rows()
    n = upsert_onchain(rows) if rows else 0
    log.info("onchain total upserted: %d", n)
    return n


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    ingest()
