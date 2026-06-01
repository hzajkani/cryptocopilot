package com.cryptocopilot.analyst;

/**
 * One scored Analyst input on the −2..+2 scale (PROJECT.md Stage 5 §4), with a human-readable
 * rationale. The four inputs are {@code ml}, {@code ta}, {@code fundamental}, {@code news}.
 */
public record InputScore(String name, int score, String rationale) {
}
