package com.cryptocopilot.data;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Composite key for {@link Fundamentals}: {@code (ts_utc, symbol)}. */
public class FundamentalsId implements Serializable {

    private Instant tsUtc;
    private String symbol;

    public FundamentalsId() {
    }

    public FundamentalsId(Instant tsUtc, String symbol) {
        this.tsUtc = tsUtc;
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FundamentalsId that)) {
            return false;
        }
        return Objects.equals(tsUtc, that.tsUtc) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tsUtc, symbol);
    }
}
