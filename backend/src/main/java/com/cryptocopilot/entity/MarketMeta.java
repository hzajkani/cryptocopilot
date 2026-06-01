package com.cryptocopilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Market-cap / supply snapshot. Python-owned, read-only. Maps to {@code market_meta}.
 */
@Entity
@Table(name = "market_meta")
@IdClass(MarketMetaId.class)
public class MarketMeta {

    @Id
    @Column(name = "ts_utc", nullable = false)
    private Instant tsUtc;

    @Id
    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "market_cap_usd")
    private Double marketCapUsd;

    @Column(name = "circulating_supply")
    private Double circulatingSupply;

    @Column(name = "total_supply")
    private Double totalSupply;

    protected MarketMeta() {
    }

    public Instant getTsUtc() {
        return tsUtc;
    }

    public String getSymbol() {
        return symbol;
    }

    public Double getMarketCapUsd() {
        return marketCapUsd;
    }

    public Double getCirculatingSupply() {
        return circulatingSupply;
    }

    public Double getTotalSupply() {
        return totalSupply;
    }
}
