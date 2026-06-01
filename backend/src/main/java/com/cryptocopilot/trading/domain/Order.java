package com.cryptocopilot.trading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A submitted paper order (Java-owned, read-write — PROJECT.md §3). Maps to {@code orders}.
 *
 * <p>Lifecycle: a MARKET order fills immediately against the next 1h bar's open; a LIMIT order
 * fills when a later 1h bar's range covers the limit, otherwise it stays {@code PENDING}. A
 * rejected order (e.g. a SELL larger than the held quantity, or insufficient cash) is stored
 * {@code CANCELLED} with the reason in {@code notes}-equivalent fields left null.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "ts_submitted")
    private Instant tsSubmitted;

    @Column(name = "ts_filled")
    private Instant tsFilled;

    @Column(name = "symbol")
    private String symbol;

    @Column(name = "side")
    private String side;

    @Column(name = "type")
    private String type;

    @Column(name = "quantity")
    private double quantity;

    @Column(name = "limit_price")
    private Double limitPrice;

    @Column(name = "status")
    private String status;

    @Column(name = "filled_price")
    private Double filledPrice;

    @Column(name = "fees")
    private Double fees;

    protected Order() {
    }

    public Order(String id, Instant tsSubmitted, String symbol, String side, String type,
                 double quantity, Double limitPrice, String status) {
        this.id = id;
        this.tsSubmitted = tsSubmitted;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.status = status;
    }

    /** Record a fill: stamp the fill price, fees, time and flip the status to {@code FILLED}. */
    public void markFilled(Instant tsFilled, double filledPrice, double fees) {
        this.tsFilled = tsFilled;
        this.filledPrice = filledPrice;
        this.fees = fees;
        this.status = "FILLED";
    }

    public String getId() {
        return id;
    }

    public Instant getTsSubmitted() {
        return tsSubmitted;
    }

    public Instant getTsFilled() {
        return tsFilled;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSide() {
        return side;
    }

    public String getType() {
        return type;
    }

    public double getQuantity() {
        return quantity;
    }

    public Double getLimitPrice() {
        return limitPrice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getFilledPrice() {
        return filledPrice;
    }

    public Double getFees() {
        return fees;
    }
}
