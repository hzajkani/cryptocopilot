package com.cryptocopilot.repository;

import com.cryptocopilot.entity.Fundamentals;
import com.cryptocopilot.entity.FundamentalsId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-only access to {@code fundamentals} (per-coin snapshots). */
public interface FundamentalsRepository extends JpaRepository<Fundamentals, FundamentalsId> {

    /** The latest snapshot for a coin — the basis of its fundamental-synthesis chunk. */
    Optional<Fundamentals> findFirstBySymbolOrderByTsUtcDesc(String symbol);
}
