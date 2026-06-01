package com.cryptocopilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One OHLCV candle. Python-owned (PROJECT.md §3) — read-only here; no setters.
 * Maps 1:1 to the {@code ohlcv} table in {@code db/init.sql}.
 */
@Entity
@Table(name = "ohlcv")
@IdClass(OhlcvId.class)
public class Ohlcv {

    @Id
    @Column(name = "ts_utc", nullable = false)
    private Instant tsUtc;

    @Id
    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Id
    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    @Column(name = "open")
    private Double open;

    @Column(name = "high")
    private Double high;

    @Column(name = "low")
    private Double low;

    @Column(name = "close")
    private Double close;

    @Column(name = "volume")
    private Double volume;

    protected Ohlcv() {
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

    public Double getOpen() {
        return open;
    }

    public Double getHigh() {
        return high;
    }

    public Double getLow() {
        return low;
    }

    public Double getClose() {
        return close;
    }

    public Double getVolume() {
        return volume;
    }
}
