"""Database access for the ML/data service.

A single SQLAlchemy engine (built from ``DATABASE_URL``) plus idempotent upsert
helpers for every Python-owned table. The schema itself is owned by
``db/init.sql`` — **nothing here creates tables**. Upserts use Postgres
``INSERT ... ON CONFLICT ... DO UPDATE`` so re-running ingestion is safe.
"""

from __future__ import annotations

import logging
import math
import os
from functools import lru_cache
from typing import Any, Iterable

from psycopg2.extras import execute_values
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

log = logging.getLogger(__name__)


@lru_cache(maxsize=1)
def get_engine() -> Engine:
    """Return a cached SQLAlchemy engine built from ``DATABASE_URL``."""
    url = os.getenv("DATABASE_URL")
    if not url:
        raise RuntimeError(
            "DATABASE_URL is not set. Inside Docker it is provided by compose; "
            "locally, export it or put it in .env "
            "(postgresql+psycopg2://cc:ccpass@localhost:5432/cryptocopilot)."
        )
    return create_engine(url, pool_pre_ping=True, future=True)


# --------------------------------------------------------------------------- #
# Row coercion helpers
# --------------------------------------------------------------------------- #
def _py(value: Any) -> Any:
    """Coerce numpy scalars / NaN / NaT to native Python (psycopg2-friendly)."""
    if value is None:
        return None
    # numpy scalar -> python scalar
    if hasattr(value, "item") and not isinstance(value, (str, bytes)):
        try:
            value = value.item()
        except (ValueError, AttributeError):
            pass
    if isinstance(value, float) and math.isnan(value):
        return None
    return value


def _to_rows(data: Any) -> list[dict]:
    """Normalise input (pandas DataFrame or iterable of dicts) to a list of dicts."""
    # Avoid importing pandas at module load; detect duck-typed DataFrame.
    if hasattr(data, "to_dict") and hasattr(data, "where") and hasattr(data, "columns"):
        import pandas as pd

        df = data.where(data.notnull(), None)
        return [{k: _py(v) for k, v in rec.items()} for rec in df.to_dict("records")]
    return [{k: _py(v) for k, v in dict(r).items()} for r in data]


def _bulk_upsert(
    table: str,
    columns: list[str],
    pk_cols: list[str],
    data: Any,
) -> int:
    """Generic ``INSERT ... ON CONFLICT DO UPDATE`` over the non-PK columns.

    Uses psycopg2's ``execute_values`` for fast multi-row inserts. Returns the
    number of rows sent (inserted or updated).
    """
    rows = _to_rows(data)
    if not rows:
        return 0

    update_cols = [c for c in columns if c not in pk_cols]
    cols_sql = ", ".join(columns)
    conflict_sql = ", ".join(pk_cols)
    if update_cols:
        set_sql = ", ".join(f"{c} = EXCLUDED.{c}" for c in update_cols)
        sql = (
            f"INSERT INTO {table} ({cols_sql}) VALUES %s "
            f"ON CONFLICT ({conflict_sql}) DO UPDATE SET {set_sql}"
        )
    else:
        sql = (
            f"INSERT INTO {table} ({cols_sql}) VALUES %s "
            f"ON CONFLICT ({conflict_sql}) DO NOTHING"
        )

    values = [tuple(r.get(c) for c in columns) for r in rows]
    raw = get_engine().raw_connection()
    try:
        with raw.cursor() as cur:
            execute_values(cur, sql, values, page_size=1000)
        raw.commit()
    except Exception:
        raw.rollback()
        raise
    finally:
        raw.close()
    return len(values)


# --------------------------------------------------------------------------- #
# Per-table upserts (column order matches db/init.sql)
# --------------------------------------------------------------------------- #
def upsert_ohlcv(data: Any) -> int:
    return _bulk_upsert(
        "ohlcv",
        ["ts_utc", "symbol", "timeframe", "open", "high", "low", "close", "volume"],
        ["ts_utc", "symbol", "timeframe"],
        data,
    )


def upsert_market_meta(data: Any) -> int:
    return _bulk_upsert(
        "market_meta",
        ["ts_utc", "symbol", "market_cap_usd", "circulating_supply", "total_supply"],
        ["ts_utc", "symbol"],
        data,
    )


def upsert_news(data: Any) -> int:
    return _bulk_upsert(
        "news",
        ["id", "ts_utc", "title", "summary", "source", "url", "currencies",
         "sentiment", "sentiment_score"],
        ["id"],
        data,
    )


def upsert_onchain(data: Any) -> int:
    return _bulk_upsert(
        "onchain",
        ["ts_utc", "symbol", "metric", "value", "source"],
        ["ts_utc", "symbol", "metric"],
        data,
    )


def upsert_fundamentals(data: Any) -> int:
    return _bulk_upsert(
        "fundamentals",
        ["ts_utc", "symbol", "price_change_pct_24h", "price_change_pct_7d",
         "price_change_pct_30d", "total_volume_usd", "market_cap_change_pct_24h",
         "reddit_subscribers", "reddit_active_48h", "reddit_avg_posts_48h",
         "twitter_followers", "github_commit_count_4w", "github_prs_merged",
         "github_code_additions_4w", "github_code_deletions_4w"],
        ["ts_utc", "symbol"],
        data,
    )


# --------------------------------------------------------------------------- #
# Small query helpers (used by run_all for reporting + the news window)
# --------------------------------------------------------------------------- #
def delete_news_older_than(days: int) -> int:
    """Delete news rows older than ``days``; return number of rows removed."""
    with get_engine().begin() as conn:
        res = conn.execute(
            text("DELETE FROM news WHERE ts_utc < now() - make_interval(days => :d)"),
            {"d": days},
        )
        return res.rowcount or 0


def count_rows(table: str) -> int:
    with get_engine().connect() as conn:
        return int(conn.execute(text(f"SELECT count(*) FROM {table}")).scalar_one())


def fetch_all(sql: str, **params: Any) -> list[dict]:
    """Run a read query and return rows as dicts."""
    with get_engine().connect() as conn:
        result = conn.execute(text(sql), params)
        return [dict(row) for row in result.mappings()]
