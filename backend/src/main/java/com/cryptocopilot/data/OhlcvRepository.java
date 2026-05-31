package com.cryptocopilot.data;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-only access to {@code ohlcv}. */
public interface OhlcvRepository extends JpaRepository<Ohlcv, OhlcvId> {

    /** Candles for charting: ascending by time within an inclusive range. */
    List<Ohlcv> findBySymbolAndTimeframeAndTsUtcBetweenOrderByTsUtc(
            String symbol, String timeframe, Instant from, Instant to);

    /** Most recent candles first; pass a {@code Pageable} to cap the count (e.g. last N bars). */
    List<Ohlcv> findBySymbolAndTimeframeOrderByTsUtcDesc(
            String symbol, String timeframe, Pageable pageable);

    /** The single latest candle for a symbol/timeframe. */
    Optional<Ohlcv> findFirstBySymbolAndTimeframeOrderByTsUtcDesc(String symbol, String timeframe);
}
