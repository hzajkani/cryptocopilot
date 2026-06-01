package com.cryptocopilot.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Composite key for {@link Onchain}: {@code (ts_utc, symbol, metric)}. */
public class OnchainId implements Serializable {

    private Instant tsUtc;
    private String symbol;
    private String metric;

    public OnchainId() {
    }

    public OnchainId(Instant tsUtc, String symbol, String metric) {
        this.tsUtc = tsUtc;
        this.symbol = symbol;
        this.metric = metric;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OnchainId that)) {
            return false;
        }
        return Objects.equals(tsUtc, that.tsUtc)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(metric, that.metric);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tsUtc, symbol, metric);
    }
}
