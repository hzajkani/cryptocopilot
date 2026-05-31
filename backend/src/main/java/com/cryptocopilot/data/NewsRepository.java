package com.cryptocopilot.data;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-only access to {@code news}. */
public interface NewsRepository extends JpaRepository<News, String> {

    /**
     * Recent news tagged with a symbol. {@code currencies} is a CSV, so this matches
     * {@code currencies LIKE %SYM%} for items newer than {@code since}, newest first.
     */
    List<News> findByCurrenciesContainingAndTsUtcAfterOrderByTsUtcDesc(String currency, Instant since);
}
