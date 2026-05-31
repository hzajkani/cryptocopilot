package com.cryptocopilot.data;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-only access to {@code predictions}. */
public interface PredictionRepository extends JpaRepository<Prediction, PredictionId> {

    /** The current forecast for a coin: the latest row for {@code (symbol, timeframe)}. */
    Optional<Prediction> findFirstBySymbolAndTimeframeOrderByTsUtcDesc(String symbol, String timeframe);
}
