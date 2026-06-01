package com.cryptocopilot.trading.backtest;

import com.cryptocopilot.dto.TAVerdict;
import java.time.Instant;

/**
 * The per-coin signal a {@link Strategy} sees at one decision step: the ML view (stored
 * {@code pred_class} + {@code prob_up} + calibrated confidence) and the deterministic {@link
 * TAVerdict}, plus the mark price (close) at the decision time. Mirrors the live
 * {@link com.cryptocopilot.dto.SignalDto}, narrowed to what a strategy needs.
 */
public record SignalRow(String symbol, Instant ts, double close, TAVerdict ta,
                        String mlClass, Double probUp, Double mlConfidence) {
}
