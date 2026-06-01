package com.cryptocopilot.trading;

import com.cryptocopilot.trading.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-write access to {@code positions} (one row per open long position, keyed by symbol). */
public interface PositionRepository extends JpaRepository<Position, String> {
}
