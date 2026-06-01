package com.cryptocopilot.dto;

import java.time.Instant;
import java.util.List;

/**
 * The deterministic technical-analysis verdict for one coin (Stage 3 headline).
 *
 * @param symbol     coin symbol (e.g. {@code BTC})
 * @param tsUtc      timestamp of the latest 4h candle the verdict was computed on
 * @param direction  {@code BULLISH} / {@code BEARISH} / {@code NEUTRAL}
 * @param confidence {@code STRONG} / {@code MODERATE} / {@code WEAK}
 * @param signals    human-readable list of every non-zero contributing rule
 * @param score      summed Ichimoku-centric score
 */
public record TAVerdict(String symbol, Instant tsUtc, String direction,
                        String confidence, List<String> signals, double score) {
}
