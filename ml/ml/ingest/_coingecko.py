"""Shared CoinGecko Demo API client (paced + retrying).

Both ``coingecko_market`` and ``coingecko_fundamentals`` go through here so the
demo-tier rate limit (30 req/min) and the ``x-cg-demo-api-key`` header live in
one place.
"""

from __future__ import annotations

import logging
import time

import requests

from ..config import COINGECKO_API_KEY, COINGECKO_BASE_URL, COINGECKO_SLEEP_SEC

log = logging.getLogger(__name__)

_session = requests.Session()


# On HTTP 429 the demo limit is per-minute, so we wait long enough to roll out
# of that window rather than hammering with short backoffs.
_RATE_LIMIT_BACKOFF_SEC = 20.0


def get(path: str, params: dict | None = None, retries: int = 5, timeout: int = 30) -> dict | None:
    """GET ``{base}{path}`` with the demo key header, pacing, and retry.

    Sleeps ``COINGECKO_SLEEP_SEC`` before each attempt to respect the rate limit;
    on HTTP 429 it backs off for ``_RATE_LIMIT_BACKOFF_SEC`` (growing) to clear the
    per-minute window. Returns parsed JSON, or ``None`` if all attempts fail
    (caller logs & skips).
    """
    url = f"{COINGECKO_BASE_URL}{path}"
    headers = {"accept": "application/json"}
    if COINGECKO_API_KEY:
        headers["x-cg-demo-api-key"] = COINGECKO_API_KEY

    rate_backoff = _RATE_LIMIT_BACKOFF_SEC
    err_backoff = COINGECKO_SLEEP_SEC
    for attempt in range(1, retries + 1):
        time.sleep(COINGECKO_SLEEP_SEC)
        try:
            resp = _session.get(url, params=params, headers=headers, timeout=timeout)
            if resp.status_code == 429:  # rate limited — wait out the window
                retry_after = float(resp.headers.get("Retry-After") or rate_backoff)
                wait = min(max(retry_after, rate_backoff), 60.0)
                log.warning("CoinGecko 429 on %s (attempt %d/%d), backing off %.0fs",
                            path, attempt, retries, wait)
                time.sleep(wait)
                rate_backoff *= 1.5
                continue
            resp.raise_for_status()
            return resp.json()
        except requests.RequestException as exc:
            log.warning("CoinGecko GET %s attempt %d/%d failed: %s", path, attempt, retries, exc)
            time.sleep(err_backoff)
            err_backoff *= 2
    log.error("CoinGecko GET %s failed after %d attempts", path, retries)
    return None
