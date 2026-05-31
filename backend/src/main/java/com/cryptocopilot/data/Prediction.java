package com.cryptocopilot.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One ML forecast for a coin. Python-owned, read-only. Maps to {@code predictions}.
 *
 * <p><b>{@code predClass} is the ML label — a validation-tuned weighted argmax, NOT
 * {@code argmax(prob_*)}</b> (Stage 3 prompt). Trust it as-is; never recompute the
 * class from the probabilities. ML confidence = the calibrated prob of {@code predClass}.
 */
@Entity
@Table(name = "predictions")
@IdClass(PredictionId.class)
public class Prediction {

    @Id
    @Column(name = "ts_utc", nullable = false)
    private Instant tsUtc;

    @Id
    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Id
    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    @Column(name = "pred_class")
    private String predClass;

    @Column(name = "prob_up")
    private Double probUp;

    @Column(name = "prob_down")
    private Double probDown;

    @Column(name = "prob_flat")
    private Double probFlat;

    @Column(name = "model_version")
    private String modelVersion;

    // DB sets this via DEFAULT now(); never written from Java.
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Prediction() {
    }

    /**
     * Calibrated probability of the stored {@link #predClass} (UP→probUp, DOWN→probDown,
     * FLAT→probFlat). This is the ML confidence. Returns {@code null} for an unknown class.
     */
    public Double confidence() {
        if (predClass == null) {
            return null;
        }
        return switch (predClass) {
            case "UP" -> probUp;
            case "DOWN" -> probDown;
            case "FLAT" -> probFlat;
            default -> null;
        };
    }

    public Instant getTsUtc() {
        return tsUtc;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public String getPredClass() {
        return predClass;
    }

    public Double getProbUp() {
        return probUp;
    }

    public Double getProbDown() {
        return probDown;
    }

    public Double getProbFlat() {
        return probFlat;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
