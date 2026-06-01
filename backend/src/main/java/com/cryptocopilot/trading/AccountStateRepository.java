package com.cryptocopilot.trading;

import com.cryptocopilot.trading.domain.AccountState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-write access to {@code account_state} — the append-only equity-curve log. */
public interface AccountStateRepository extends JpaRepository<AccountState, Instant> {

    /** The latest account snapshot (current cash + equity). */
    Optional<AccountState> findFirstByOrderByTsUtcDesc();

    /** The full equity curve, time-ascending. */
    List<AccountState> findAllByOrderByTsUtc();
}
