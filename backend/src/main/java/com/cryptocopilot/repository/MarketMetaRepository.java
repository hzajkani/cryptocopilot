package com.cryptocopilot.repository;

import com.cryptocopilot.entity.MarketMeta;
import com.cryptocopilot.entity.MarketMetaId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-only access to {@code market_meta}. */
public interface MarketMetaRepository extends JpaRepository<MarketMeta, MarketMetaId> {

    /** The latest market-cap / supply snapshot for a coin. */
    Optional<MarketMeta> findFirstBySymbolOrderByTsUtcDesc(String symbol);

    /** The market-cap / supply snapshot as of {@code ts} (latest at or before it) — for 7d trends. */
    Optional<MarketMeta> findFirstBySymbolAndTsUtcLessThanEqualOrderByTsUtcDesc(String symbol, Instant ts);
}
