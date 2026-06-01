package com.cryptocopilot.analyst;

import java.time.Instant;
import java.util.List;

/**
 * A deterministic, rule-based fundamental read on one coin (PROJECT.md Stage 5 §3). No ML, no LLM.
 *
 * <p><b>Two-tier health.</b> {@code health} is {@code IMPROVING}/{@code STABLE}/{@code
 * DETERIORATING}/{@code UNKNOWN}, derived from the best source available for the coin:
 * <ul>
 *   <li><b>Tier 1 — on-chain</b> ({@code healthSource="onchain"}): the 7-day moving averages of
 *       active addresses and transfer volume both rising → IMPROVING, both falling → DETERIORATING,
 *       else STABLE. Needs a real daily on-chain series (BTC has one; ETH's snapshot does not, so
 *       ETH falls to Tier 2).</li>
 *   <li><b>Tier 2 — CoinGecko</b> ({@code healthSource="coingecko"}): three within-snapshot signals
 *       (7d momentum, 4-week dev activity, 24h market-cap change) scored ±1; ≥2 positive & 0
 *       negative → IMPROVING, ≥2 negative & 0 positive → DETERIORATING, else STABLE. Used because
 *       {@code fundamentals} is a single snapshot per coin (no history — PROJECT.md Stage 5
 *       "Reality").</li>
 *   <li><b>Tier 3</b> ({@code healthSource="unknown"}): neither source has data → UNKNOWN.</li>
 * </ul>
 *
 * @param symbol               coin symbol
 * @param tsUtc                when the snapshot was computed
 * @param health               IMPROVING / STABLE / DETERIORATING / UNKNOWN
 * @param healthSource         onchain / coingecko / unknown (a transparency requirement — never hidden)
 * @param reasons              human-readable contributions behind the health verdict
 * @param marketDominancePct   this coin's share of the 10-coin universe market cap, in % (nullable)
 * @param marketDominanceTrend RISING / FALLING / STABLE over the last 7 days (nullable)
 * @param newsSentiment7d      POSITIVE / MIXED / NEGATIVE / INSUFFICIENT_DATA
 * @param newsSentimentScore   recency-weighted mean of stored news sentiment over 7d (0 if insufficient)
 */
public record FundamentalSnapshot(String symbol, Instant tsUtc, String health,
                                  String healthSource, List<String> reasons,
                                  Double marketDominancePct, String marketDominanceTrend,
                                  String newsSentiment7d, double newsSentimentScore) {
}
