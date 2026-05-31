"""RSS news ingestion -> ``news``.

For each of the 5 feeds: parse entries, tag currencies by ticker + name match,
score sentiment locally with VADER, dedupe by a URL hash, and keep only the last
``NEWS_WINDOW_DAYS`` days (rolling window — older rows are deleted).
"""

from __future__ import annotations

import calendar
import hashlib
import html
import logging
import re
from datetime import datetime, timedelta, timezone

import feedparser
from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer

from ..config import (
    ASSETS,
    COIN_NAMES,
    NEWS_WINDOW_DAYS,
    RSS_SOURCES,
    SENTIMENT_NEG_THRESHOLD,
    SENTIMENT_POS_THRESHOLD,
)

log = logging.getLogger(__name__)


def _build_currency_patterns() -> dict[str, list[re.Pattern]]:
    """One list of compiled word-boundary patterns per asset (ticker + names)."""
    patterns: dict[str, list[re.Pattern]] = {}
    for sym in ASSETS:
        terms = [sym] + COIN_NAMES.get(sym, [])
        patterns[sym] = [re.compile(rf"\b{re.escape(t)}\b", re.IGNORECASE) for t in terms]
    return patterns


_CURRENCY_PATTERNS = _build_currency_patterns()


def _clean_html(value: str) -> str:
    return html.unescape(re.sub(r"<[^>]+>", "", value or "")).strip()


def _tag_currencies(text: str) -> str:
    matched = [sym for sym, pats in _CURRENCY_PATTERNS.items() if any(p.search(text) for p in pats)]
    return ",".join(sorted(matched, key=ASSETS.index))


def _entry_ts(entry) -> datetime | None:
    for key in ("published_parsed", "updated_parsed"):
        parsed = entry.get(key)
        if parsed:
            return datetime.fromtimestamp(calendar.timegm(parsed), tz=timezone.utc)
    return None


def _classify(score: float) -> str:
    if score > SENTIMENT_POS_THRESHOLD:
        return "POSITIVE"
    if score < SENTIMENT_NEG_THRESHOLD:
        return "NEGATIVE"
    return "NEUTRAL"


def _entry_to_row(entry, source: str, analyzer: SentimentIntensityAnalyzer) -> dict | None:
    url = entry.get("link")
    if not url:
        return None
    ts = _entry_ts(entry)
    if ts is None:
        return None
    title = _clean_html(entry.get("title", ""))
    summary = _clean_html(entry.get("summary", ""))
    score = analyzer.polarity_scores(f"{title} {summary}")["compound"]
    return {
        "id": hashlib.sha256(url.encode("utf-8")).hexdigest(),
        "ts_utc": ts,
        "title": title or None,
        "summary": summary or None,
        "source": source,
        "url": url,
        "currencies": _tag_currencies(f"{title} {summary}") or None,
        "sentiment": _classify(score),
        "sentiment_score": score,
    }


def ingest() -> int:
    """Parse all feeds, upsert recent items, prune the rolling window."""
    from ..db import delete_news_older_than, upsert_news

    analyzer = SentimentIntensityAnalyzer()
    cutoff = datetime.now(timezone.utc) - timedelta(days=NEWS_WINDOW_DAYS)
    seen: set[str] = set()
    rows: list[dict] = []
    per_source: dict[str, int] = {}

    for src in RSS_SOURCES:
        name, url = src["name"], src["url"]
        try:
            feed = feedparser.parse(url)
        except Exception as exc:  # never block the pipeline on one feed
            log.warning("RSS parse failed for %s: %s", name, exc)
            per_source[name] = 0
            continue
        if getattr(feed, "bozo", 0) and not feed.entries:
            log.warning("RSS feed %s returned no usable entries (%s)", name, getattr(feed, "bozo_exception", ""))
        kept = 0
        for entry in feed.entries:
            row = _entry_to_row(entry, name, analyzer)
            if row is None or row["ts_utc"] < cutoff or row["id"] in seen:
                continue
            seen.add(row["id"])
            rows.append(row)
            kept += 1
        per_source[name] = kept
        log.info("news %-18s: %d entries within %dd window", name, kept, NEWS_WINDOW_DAYS)

    n = upsert_news(rows) if rows else 0
    deleted = delete_news_older_than(NEWS_WINDOW_DAYS)
    log.info("news upserted: %d (per source: %s); pruned %d older than %dd",
             n, per_source, deleted, NEWS_WINDOW_DAYS)
    return n


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    ingest()
