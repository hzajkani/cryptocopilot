package com.cryptocopilot.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Composite key for {@link Ohlcv}: {@code (ts_utc, symbol, timeframe)}. */
public class OhlcvId implements Serializable {

    private Instant tsUtc;
    private String symbol;
    private String timeframe;

    public OhlcvId() {
    }

    public OhlcvId(Instant tsUtc, String symbol, String timeframe) {
        this.tsUtc = tsUtc;
        this.symbol = symbol;
        this.timeframe = timeframe;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OhlcvId that)) {
            return false;
        }
        return Objects.equals(tsUtc, that.tsUtc)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(timeframe, that.timeframe);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tsUtc, symbol, timeframe);
    }
}
