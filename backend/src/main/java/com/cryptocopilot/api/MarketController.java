package com.cryptocopilot.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Market-data endpoints: the overview grid and per-coin chart candles. */
@RestController
public class MarketController {

    private static final int DEFAULT_RANGE_DAYS = 90;

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    /** 10 rows: price + 24h change (from 4h OHLCV) + market cap (latest snapshot, may be null). */
    @GetMapping("/api/markets")
    public List<MarketDto> markets() {
        return marketService.markets();
    }

    /** Candles for charting. Defaults to the last 90 days on the 4h timeframe. */
    @GetMapping("/api/coins/{symbol}/ohlcv")
    public List<CandleDto> ohlcv(@PathVariable String symbol,
                                 @RequestParam(defaultValue = "4h") String timeframe,
                                 @RequestParam(required = false) String from,
                                 @RequestParam(required = false) String to) {
        Instant toInstant = to == null ? Instant.now() : parseInstant(to);
        Instant fromInstant = from == null
                ? toInstant.minus(DEFAULT_RANGE_DAYS, ChronoUnit.DAYS)
                : parseInstant(from);
        return marketService.candles(symbol.toUpperCase(), timeframe, fromInstant, toInstant);
    }

    /** Accept either an ISO date ({@code 2026-01-31}) or a full ISO-8601 instant. */
    private static Instant parseInstant(String value) {
        if (value.length() == 10) {
            return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        return Instant.parse(value);
    }
}
