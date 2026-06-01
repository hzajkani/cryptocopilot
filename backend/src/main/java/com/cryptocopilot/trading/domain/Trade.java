package com.cryptocopilot.trading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An executed fill (Java-owned, read-write — PROJECT.md §3). Maps to {@code trades}.
 * One row per fill: a BUY has {@code realizedPnl = 0}; a SELL carries the realized P&L of the
 * closed quantity ({@code (exitPrice - avgEntry) * qty - fees}). {@code price} is the fill price
 * (after slippage); {@code fees} the taker fee paid on that fill.
 */
@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "ts_utc")
    private Instant tsUtc;

    @Column(name = "symbol")
    private String symbol;

    @Column(name = "side")
    private String side;

    @Column(name = "quantity")
    private double quantity;

    @Column(name = "price")
    private double price;

    @Column(name = "fees")
    private double fees;

    @Column(name = "realized_pnl")
    private double realizedPnl;

    @Column(name = "notes")
    private String notes;

    protected Trade() {
    }

    public Trade(String id, Instant tsUtc, String symbol, String side, double quantity,
                 double price, double fees, double realizedPnl, String notes) {
        this.id = id;
        this.tsUtc = tsUtc;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.fees = fees;
        this.realizedPnl = realizedPnl;
        this.notes = notes;
    }

    public String getId() {
        return id;
    }

    public Instant getTsUtc() {
        return tsUtc;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSide() {
        return side;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public double getFees() {
        return fees;
    }

    public double getRealizedPnl() {
        return realizedPnl;
    }

    public String getNotes() {
        return notes;
    }
}
