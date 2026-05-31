package com.cryptocopilot.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Repository slice test against the running compose {@code db} (Postgres + pgvector), read-only.
 *
 * <p>{@code @DataJpaTest} wraps each test in a transaction that is rolled back, and these tests
 * only read — the real ingested data is never mutated. Running with {@code ddl-auto: validate}
 * also proves the JPA entities match the contract schema in {@code db/init.sql} (the DoD's
 * validate check). It exercises the OHLCV-range and latest-prediction queries against real data.
 *
 * <p>Requires {@code docker compose up -d db} (the DoD assumes the stack is up). Testcontainers
 * was the first choice, but this host's docker-java ↔ Docker Desktop socket returns HTTP 400 on
 * the client ping, so the slice targets the live {@code db} instead.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OhlcvRepositoryTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:5432/cryptocopilot");
        registry.add("spring.datasource.username", () -> "cc");
        registry.add("spring.datasource.password", () -> "ccpass");
        // entities must validate against the real init.sql schema, not be generated
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private static final String BTC = "BTC";
    private static final String TF = "4h";

    @Autowired
    private OhlcvRepository ohlcvRepository;

    @Autowired
    private PredictionRepository predictionRepository;

    @Test
    void ohlcvRangeQueryIsAscendingAndWithinBounds() {
        Instant to = Instant.now();
        Instant from = to.minus(30, ChronoUnit.DAYS);
        List<Ohlcv> rows = ohlcvRepository
                .findBySymbolAndTimeframeAndTsUtcBetweenOrderByTsUtc(BTC, TF, from, to);

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(o -> {
            assertThat(o.getSymbol()).isEqualTo(BTC);
            assertThat(o.getTimeframe()).isEqualTo(TF);
            assertThat(o.getTsUtc()).isBetween(from, to);
            assertThat(o.getClose()).isNotNull();
        });
        // strictly ascending by timestamp
        assertThat(rows).extracting(Ohlcv::getTsUtc).isSorted();
    }

    @Test
    void latestCandleIsReturned() {
        Ohlcv latest = ohlcvRepository
                .findFirstBySymbolAndTimeframeOrderByTsUtcDesc(BTC, TF).orElseThrow();
        assertThat(latest.getClose()).isNotNull();
        assertThat(latest.getClose()).isPositive();
    }

    @Test
    void latestPredictionIsTheModelV1Forecast() {
        Prediction latest = predictionRepository
                .findFirstBySymbolAndTimeframeOrderByTsUtcDesc(BTC, TF).orElseThrow();

        assertThat(latest.getModelVersion()).isEqualTo("v1");
        assertThat(latest.getPredClass()).isIn("UP", "DOWN", "FLAT");
        // ML confidence = calibrated prob of the stored class; a valid probability
        assertThat(latest.confidence()).isNotNull();
        assertThat(latest.confidence()).isBetween(0.0, 1.0);
    }
}
