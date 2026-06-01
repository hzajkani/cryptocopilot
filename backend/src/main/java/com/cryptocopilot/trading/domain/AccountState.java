package com.cryptocopilot.trading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A snapshot of the paper-trading account at one instant (Java-owned, read-write — PROJECT.md §3).
 * Maps to {@code account_state}; the {@code ts_utc} primary key makes the table an append-only
 * equity-curve log (one row per {@code markToMarket} / fill).
 */
@Entity
@Table(name = "account_state")
public class AccountState {

    @Id
    @Column(name = "ts_utc", nullable = false)
    private Instant tsUtc;

    @Column(name = "cash_usd")
    private double cashUsd;

    @Column(name = "total_equity_usd")
    private double totalEquityUsd;

    protected AccountState() {
    }

    public AccountState(Instant tsUtc, double cashUsd, double totalEquityUsd) {
        this.tsUtc = tsUtc;
        this.cashUsd = cashUsd;
        this.totalEquityUsd = totalEquityUsd;
    }

    public Instant getTsUtc() {
        return tsUtc;
    }

    public double getCashUsd() {
        return cashUsd;
    }

    public double getTotalEquityUsd() {
        return totalEquityUsd;
    }
}
