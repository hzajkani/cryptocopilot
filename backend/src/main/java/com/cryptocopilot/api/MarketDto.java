package com.cryptocopilot.api;

/**
 * A markets-overview row. {@code marketCapUsd} may be {@code null} — not every coin has a
 * {@code market_meta} snapshot (log-and-skip ingestion, PROJECT.md §9).
 */
public record MarketDto(String symbol, Double price, Double change24hPct, Double marketCapUsd) {
}
