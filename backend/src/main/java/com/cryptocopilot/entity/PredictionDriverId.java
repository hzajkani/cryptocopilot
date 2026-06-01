package com.cryptocopilot.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Composite key for {@link PredictionDriver}: {@code (ts_utc, symbol, timeframe, rank)}. */
public class PredictionDriverId implements Serializable {

    private Instant tsUtc;
    private String symbol;
    private String timeframe;
    private Integer rank;

    public PredictionDriverId() {
    }

    public PredictionDriverId(Instant tsUtc, String symbol, String timeframe, Integer rank) {
        this.tsUtc = tsUtc;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.rank = rank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PredictionDriverId that)) {
            return false;
        }
        return Objects.equals(tsUtc, that.tsUtc)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(timeframe, that.timeframe)
                && Objects.equals(rank, that.rank);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tsUtc, symbol, timeframe, rank);
    }
}
