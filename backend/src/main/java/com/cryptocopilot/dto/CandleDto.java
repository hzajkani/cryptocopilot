package com.cryptocopilot.dto;

import java.time.Instant;

/** One OHLCV candle for charting. */
public record CandleDto(Instant ts, Double open, Double high, Double low,
                        Double close, Double volume) {
}
