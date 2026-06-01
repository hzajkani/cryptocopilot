package com.cryptocopilot.repository;

import com.cryptocopilot.entity.Onchain;
import com.cryptocopilot.entity.OnchainId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Read-only access to {@code onchain} (long-format metrics). */
public interface OnchainRepository extends JpaRepository<Onchain, OnchainId> {

    /** All metrics for a coin, time-ascending — fed into the weekly synthesis. */
    List<Onchain> findBySymbolOrderByTsUtc(String symbol);

    /** Which coins actually have on-chain coverage (BTC only, in the current corpus). */
    @Query("select distinct o.symbol from Onchain o order by o.symbol")
    List<String> findDistinctSymbols();
}
