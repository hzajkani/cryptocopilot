package com.cryptocopilot.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/** On-chain metric (long format). Python-owned, read-only. Maps to {@code onchain}. */
@Entity
@Table(name = "onchain")
@IdClass(OnchainId.class)
public class Onchain {

    @Id
    @Column(name = "ts_utc", nullable = false)
    private Instant tsUtc;

    @Id
    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Id
    @Column(name = "metric", nullable = false)
    private String metric;

    @Column(name = "value")
    private Double value;

    @Column(name = "source")
    private String source;

    protected Onchain() {
    }

    public Instant getTsUtc() {
        return tsUtc;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getMetric() {
        return metric;
    }

    public Double getValue() {
        return value;
    }

    public String getSource() {
        return source;
    }
}
