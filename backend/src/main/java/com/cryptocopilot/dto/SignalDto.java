package com.cryptocopilot.dto;

import java.time.Instant;
import java.util.List;

/**
 * The fused per-coin signal: the ML forecast (class + calibrated confidence + probabilities +
 * top-3 drivers) alongside the independent deterministic {@link TAVerdict}.
 *
 * <p>{@code mlConfidence} is the calibrated probability of {@code mlClass} (the stored,
 * validation-tuned label) — never re-derived from the probabilities.
 */
public record SignalDto(String symbol, Instant ts, String mlClass, Double mlConfidence,
                        Double probUp, Double probDown, Double probFlat, String modelVersion,
                        List<DriverDto> drivers, TAVerdict ta) {
}
