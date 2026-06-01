package com.cryptocopilot.trading;

import com.cryptocopilot.trading.domain.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-write access to {@code orders}. */
public interface OrderRepository extends JpaRepository<Order, String> {

    /** Orders newest first (by submission time). */
    List<Order> findAllByOrderByTsSubmittedDesc();
}
