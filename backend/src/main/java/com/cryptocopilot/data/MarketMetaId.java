package com.cryptocopilot.data;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Composite key for {@link MarketMeta}: {@code (ts_utc, symbol)}. */
public class MarketMetaId implements Serializable {

    private Instant tsUtc;
    private String symbol;

    public MarketMetaId() {
    }

    public MarketMetaId(Instant tsUtc, String symbol) {
        this.tsUtc = tsUtc;
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MarketMetaId that)) {
            return false;
        }
        return Objects.equals(tsUtc, that.tsUtc) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tsUtc, symbol);
    }
}
