package com.cryptocopilot.analyst;

import java.util.List;
import java.util.Locale;

/**
 * The deterministic Analyst scoring rules (PROJECT.md Stage 5 §4). Pure functions of the four
 * inputs — no Spring, no DB, no LLM — so the golden scenarios are unit-tested directly.
 *
 * <p>Each input scores on −2..+2; the combined sum (−6..+6) drives {@code direction} and
 * {@code conviction}, and {@code agreementScore = 1 − normalisedVariance} measures how much the
 * four inputs agree.
 */
public final class AnalystScorer {

    private AnalystScorer() {
    }

    /** The scored result: overall direction/conviction/agreement plus each input's contribution. */
    public record ScoreResult(String direction, String conviction, double agreementScore,
                              int combined, List<InputScore> inputs) {
    }

    public static ScoreResult score(String mlClass, Double mlConfidence, String taDirection,
                                    String taConfidence, String fundamentalHealth,
                                    String newsLabel, double tau) {
        InputScore ml = mlInput(mlClass, mlConfidence, tau);
        InputScore ta = taInput(taDirection, taConfidence);
        InputScore fundamental = fundamentalInput(fundamentalHealth);
        InputScore news = newsInput(newsLabel);
        List<InputScore> inputs = List.of(ml, ta, fundamental, news);

        int combined = ml.score() + ta.score() + fundamental.score() + news.score();
        String direction = direction(combined, inputs);
        String conviction = conviction(combined);
        double agreement = agreementScore(inputs);
        return new ScoreResult(direction, conviction, agreement, combined, inputs);
    }

    // ---- per-input scoring ---------------------------------------------------------------------

    static InputScore mlInput(String mlClass, Double confidence, double tau) {
        String cls = mlClass == null ? "" : mlClass.toUpperCase(Locale.ROOT);
        double conf = confidence == null ? 0.0 : confidence;
        boolean strong = conf >= tau;
        return switch (cls) {
            case "UP" -> new InputScore("ml", strong ? 2 : 1,
                    String.format(Locale.US, "ML UP @ %.2f %s τ %.2f → %+d", conf, strong ? "≥" : "<", tau, strong ? 2 : 1));
            case "DOWN" -> new InputScore("ml", strong ? -2 : -1,
                    String.format(Locale.US, "ML DOWN @ %.2f %s τ %.2f → %+d", conf, strong ? "≥" : "<", tau, strong ? -2 : -1));
            case "FLAT" -> new InputScore("ml", 0, "ML FLAT → 0");
            default -> new InputScore("ml", 0, "ML unavailable → 0");
        };
    }

    static InputScore taInput(String direction, String confidence) {
        String dir = direction == null ? "" : direction.toUpperCase(Locale.ROOT);
        boolean strong = "STRONG".equalsIgnoreCase(confidence);
        return switch (dir) {
            case "BULLISH" -> new InputScore("ta", strong ? 2 : 1,
                    String.format(Locale.US, "TA BULLISH/%s → %+d", label(confidence), strong ? 2 : 1));
            case "BEARISH" -> new InputScore("ta", strong ? -2 : -1,
                    String.format(Locale.US, "TA BEARISH/%s → %+d", label(confidence), strong ? -2 : -1));
            default -> new InputScore("ta", 0, "TA NEUTRAL → 0");
        };
    }

    static InputScore fundamentalInput(String health) {
        String h = health == null ? "UNKNOWN" : health.toUpperCase(Locale.ROOT);
        return switch (h) {
            case "IMPROVING" -> new InputScore("fundamental", 1, "Fundamentals IMPROVING → +1");
            case "DETERIORATING" -> new InputScore("fundamental", -1, "Fundamentals DETERIORATING → -1");
            case "STABLE" -> new InputScore("fundamental", 0, "Fundamentals STABLE → 0");
            default -> new InputScore("fundamental", 0, "Fundamentals UNKNOWN → 0");
        };
    }

    static InputScore newsInput(String label) {
        String l = label == null ? "INSUFFICIENT_DATA" : label.toUpperCase(Locale.ROOT);
        return switch (l) {
            case "POSITIVE" -> new InputScore("news", 1, "News POSITIVE → +1");
            case "NEGATIVE" -> new InputScore("news", -1, "News NEGATIVE → -1");
            case "MIXED" -> new InputScore("news", 0, "News MIXED → 0");
            default -> new InputScore("news", 0, "News INSUFFICIENT_DATA → 0");
        };
    }

    // ---- aggregation ---------------------------------------------------------------------------

    /**
     * {@code ≥+3} LEAN_BULLISH; {@code ≤−3} LEAN_BEARISH; otherwise CONFLICTED when the inputs hold
     * genuinely opposite signs with no directional consensus, else NEUTRAL (PROJECT.md Stage 5 §4).
     */
    static String direction(int combined, List<InputScore> inputs) {
        if (combined >= 3) {
            return "LEAN_BULLISH";
        }
        if (combined <= -3) {
            return "LEAN_BEARISH";
        }
        boolean hasPositive = inputs.stream().anyMatch(i -> i.score() > 0);
        boolean hasNegative = inputs.stream().anyMatch(i -> i.score() < 0);
        return (hasPositive && hasNegative) ? "CONFLICTED" : "NEUTRAL";
    }

    /** {@code |sum|≥4} HIGH, {@code 2..3} MEDIUM, else LOW. */
    static String conviction(int combined) {
        int mag = Math.abs(combined);
        if (mag >= 4) {
            return "HIGH";
        }
        if (mag >= 2) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /**
     * {@code 1 − variance/maxVariance} of the four scores. Max variance for values in [−2,+2] is 4
     * (two at −2, two at +2), so a fully-split panel scores 0 and a unanimous panel scores 1.
     */
    static double agreementScore(List<InputScore> inputs) {
        double mean = inputs.stream().mapToInt(InputScore::score).average().orElse(0.0);
        double variance = inputs.stream()
                .mapToDouble(i -> {
                    double d = i.score() - mean;
                    return d * d;
                })
                .average().orElse(0.0);
        double agreement = 1.0 - variance / 4.0;
        agreement = Math.max(0.0, Math.min(1.0, agreement));
        return Math.round(agreement * 1000.0) / 1000.0;
    }

    private static String label(String confidence) {
        return confidence == null ? "MODERATE" : confidence.toUpperCase(Locale.ROOT);
    }
}
