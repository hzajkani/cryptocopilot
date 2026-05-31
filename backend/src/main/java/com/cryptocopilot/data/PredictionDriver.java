package com.cryptocopilot.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One of the top-3 SHAP drivers behind a {@link Prediction} (rank 1..3). Python-owned,
 * read-only. Maps to {@code prediction_drivers}. The coin one-hot is deliberately
 * excluded upstream, so {@code featureName} is always a market-state feature.
 */
@Entity
@Table(name = "prediction_drivers")
@IdClass(PredictionDriverId.class)
public class PredictionDriver {

    @Id
    @Column(name = "ts_utc", nullable = false)
    private Instant tsUtc;

    @Id
    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Id
    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    // "rank" is a SQL reserved word; the column is unquoted lowercase in init.sql.
    @Id
    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "feature_name")
    private String featureName;

    @Column(name = "feature_value")
    private Double featureValue;

    @Column(name = "shap_value")
    private Double shapValue;

    protected PredictionDriver() {
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

    public Integer getRank() {
        return rank;
    }

    public String getFeatureName() {
        return featureName;
    }

    public Double getFeatureValue() {
        return featureValue;
    }

    public Double getShapValue() {
        return shapValue;
    }
}
