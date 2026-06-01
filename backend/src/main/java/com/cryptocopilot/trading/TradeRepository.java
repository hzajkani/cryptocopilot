package com.cryptocopilot.trading;

import com.cryptocopilot.trading.domain.Trade;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-write access to {@code trades} (one row per fill). */
public interface TradeRepository extends JpaRepository<Trade, String> {

    /** Trade blotter, newest first. */
    List<Trade> findAllByOrderByTsUtcDesc();
}
