package com.cryptocopilot.trading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An open long position (Java-owned, read-write — PROJECT.md §3). Maps to {@code positions}.
 * Long-only: {@code size} is always {@code >= 0}; a fully-closed position row is deleted, not
 * left at size 0. {@code avgEntryPrice} is the size-weighted average fill price of the position.
 */
@Entity
@Table(name = "positions")
public class Position {

    @Id
    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "size")
    private double size;

    @Column(name = "avg_entry_price")
    private double avgEntryPrice;

    @Column(name = "opened_at")
    private Instant openedAt;

    protected Position() {
    }

    public Position(String symbol, double size, double avgEntryPrice, Instant openedAt) {
        this.symbol = symbol;
        this.size = size;
        this.avgEntryPrice = avgEntryPrice;
        this.openedAt = openedAt;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public double getAvgEntryPrice() {
        return avgEntryPrice;
    }

    public void setAvgEntryPrice(double avgEntryPrice) {
        this.avgEntryPrice = avgEntryPrice;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }
}
