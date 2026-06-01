package com.cryptocopilot.analyst;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable knobs for the Analyst (PROJECT.md Stage 5 §4). Bound from
 * {@code cryptocopilot.analyst.*}.
 *
 * @param mlConfidenceThreshold τ — the calibrated-confidence cutoff above which an ML UP/DOWN
 *                              scores ±2 instead of ±1 (default 0.50). In the current calm regime
 *                              most confidences fall below 0.50, so most ML scores are ±1 — the
 *                              honest result the brief calls out.
 */
@ConfigurationProperties(prefix = "cryptocopilot.analyst")
public record AnalystProperties(double mlConfidenceThreshold) {

    public AnalystProperties {
        if (mlConfidenceThreshold <= 0 || mlConfidenceThreshold >= 1) {
            mlConfidenceThreshold = 0.50;
        }
    }
}
