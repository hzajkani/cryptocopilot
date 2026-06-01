package com.cryptocopilot.trading.backtest;

/**
 * The spec default strategy (PROJECT.md Stage 5 §5): <b>"ML confirmed by TA"</b>.
 *
 * <ul>
 *   <li><b>Enter</b> when the ML label is {@code UP} with {@code prob_up > probUpThreshold}
 *       (default 0.55) <i>and</i> the TA verdict is {@code BULLISH}.</li>
 *   <li><b>Exit</b> when ML flips away from {@code UP}, or TA turns {@code BEARISH}.</li>
 * </ul>
 *
 * The stored {@code pred_class} is trusted as the ML label (never re-argmaxed from probabilities —
 * PROJECT.md Stage 5 "What NOT to do").
 */
public record MlConfirmedByTaStrategy(double probUpThreshold) implements Strategy {

    public MlConfirmedByTaStrategy() {
        this(0.55);
    }

    @Override
    public Action decide(SignalRow row, boolean hasPosition) {
        boolean mlUp = "UP".equalsIgnoreCase(row.mlClass())
                && row.probUp() != null && row.probUp() > probUpThreshold;
        boolean taBullish = row.ta() != null && "BULLISH".equalsIgnoreCase(row.ta().direction());
        boolean taBearish = row.ta() != null && "BEARISH".equalsIgnoreCase(row.ta().direction());

        if (!hasPosition) {
            return (mlUp && taBullish) ? Action.ENTER : Action.HOLD;
        }
        // Hold while the thesis stands; close when ML is no longer UP or TA turns bearish.
        return (!mlUp || taBearish) ? Action.EXIT : Action.HOLD;
    }

    @Override
    public String label() {
        return String.format(java.util.Locale.US, "ML-confirmed-by-TA (prob_up>%.2f & TA BULLISH)", probUpThreshold);
    }
}
