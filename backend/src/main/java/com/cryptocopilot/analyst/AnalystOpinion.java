package com.cryptocopilot.analyst;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The Analyst's fused, deterministic opinion on one coin (PROJECT.md Stage 5 §4) — the synthesis of
 * four perspectives (ML, TA, fundamental, news). Explainable: {@code inputs} carries every input
 * and its ±2 contribution; {@code summary} is an LLM phrasing of those facts, guarded so it can add
 * no claim not present in the inputs (else a deterministic template is used).
 *
 * @param symbol         coin symbol
 * @param tsUtc          when the opinion was produced
 * @param direction      LEAN_BULLISH / LEAN_BEARISH / NEUTRAL / CONFLICTED
 * @param conviction     HIGH / MEDIUM / LOW
 * @param summary        2–3 sentence synthesis (LLM if grounded; deterministic template otherwise)
 * @param agreementScore 1 − normalised variance of the four input scores (1 = full agreement)
 * @param inputs         the four scored inputs + the combined score (full transparency)
 * @param citations      up to three recent headline references for the coin (last 7d)
 */
public record AnalystOpinion(String symbol, Instant tsUtc, String direction, String conviction,
                             String summary, double agreementScore, Map<String, Object> inputs,
                             List<String> citations) {
}
