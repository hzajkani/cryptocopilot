package com.cryptocopilot.repository;

import com.cryptocopilot.entity.PredictionDriver;
import com.cryptocopilot.entity.PredictionDriverId;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-only access to {@code prediction_drivers}. */
public interface PredictionDriverRepository extends JpaRepository<PredictionDriver, PredictionDriverId> {

    /** The 3 drivers (rank 1..3) for a given prediction, ordered by rank. */
    List<PredictionDriver> findBySymbolAndTimeframeAndTsUtcOrderByRank(
            String symbol, String timeframe, Instant tsUtc);
}
