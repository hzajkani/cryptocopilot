package com.cryptocopilot.trading.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptocopilot.trading.TradingMetrics;
import com.cryptocopilot.trading.TradingProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Live backtest over the real available window (PROJECT.md Stage 5 §5), against the running compose
 * {@code db}. Gated on {@code BACKTEST_LIVE} so it is SKIPPED (not failed) in the default offline
 * {@code mvn test}; it needs the DB but <b>not</b> Ollama. Run it:
 * <pre>BACKTEST_LIVE=1 mvn -Dtest=BacktestLiveIT test</pre>
 *
 * <p>It runs the spec-default {@link MlConfirmedByTaStrategy} and the reconstructable
 * {@link TaLongOnlyStrategy} from {@code 2025-09-01 → latest bar}, writes
 * {@code reports/backtest_strategy_v1.md}, and asserts the engine produced a non-trivial equity
 * curve. Per the brief, a Sharpe ≤ 0 is an accepted, documented result (fees + a calm regime + a
 * single-snapshot ML view), so the sign is reported, not asserted.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "BACKTEST_LIVE", matches = "(?i)1|true|yes")
class BacktestLiveIT {

    private static final Logger log = LoggerFactory.getLogger(BacktestLiveIT.class);

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/cryptocopilot");
        registry.add("spring.datasource.username", () -> "cc");
        registry.add("spring.datasource.password", () -> "ccpass");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private BacktestRunner runner;
    @Autowired
    private TradingProperties props;

    @Test
    void runsDefaultAndProxyStrategiesOverRealWindowAndWritesReport() throws IOException {
        BacktestResult mlConfirmed = runner.run(new MlConfirmedByTaStrategy());
        BacktestResult taProxy = runner.run(new TaLongOnlyStrategy());
        List<BacktestResult> results = List.of(mlConfirmed, taProxy);

        log.info("ML-confirmed-by-TA: {} trades, Sharpe {}, final {}", mlConfirmed.trades(),
                mlConfirmed.metrics().sharpe(), mlConfirmed.metrics().finalEquity());
        log.info("TA-long-only: {} trades, Sharpe {}, final {}", taProxy.trades(),
                taProxy.metrics().sharpe(), taProxy.metrics().finalEquity());

        BacktestReportWriter.write(reportFile(), results, props, notes(mlConfirmed, taProxy));

        // The engine produced a real daily equity curve over the window.
        assertThat(taProxy.equityCurve()).isNotEmpty();
        assertThat(mlConfirmed.equityCurve()).hasSameSizeAs(taProxy.equityCurve());
        assertThat(Files.exists(reportFile())).isTrue();
        // Sharpe sign is intentionally NOT asserted (≤0 is an accepted honest result per the brief).
    }

    private static String notes(BacktestResult mlConfirmed, BacktestResult taProxy) {
        TradingMetrics ml = mlConfirmed.metrics();
        TradingMetrics ta = taProxy.metrics();
        StringBuilder sb = new StringBuilder();
        sb.append("- **ML-confirmed-by-TA** is the spec default. The `predictions` table holds a single ")
                .append("*latest* ML snapshot per coin (the ML batch job stores only the current forecast — ")
                .append("PROJECT.md §2), so there is no historical ML series to drive it bar-by-bar; the latest ")
                .append("snapshot is held constant. In the current calm/down regime no coin is `UP` with ")
                .append("`prob_up>0.55`, so it makes **").append(mlConfirmed.trades())
                .append(" trades** — a correct, documented outcome, not a defect.\n");
        sb.append("- **TA-long-only** (enter BULLISH / exit BEARISH) is fully reconstructable from `ohlcv` at ")
                .append("every historical bar, so it carries the substantive curve: ")
                .append(taProxy.trades()).append(" trades, Sharpe ").append(String.format(Locale.US, "%.3f", ta.sharpe()))
                .append(", max drawdown ").append(String.format(Locale.US, "%.2f%%", ta.maxDrawdownPct() * 100))
                .append(", win rate ").append(String.format(Locale.US, "%.1f%%", ta.winRate() * 100))
                .append(", fee drag $").append(String.format(Locale.US, "%,.2f", ta.totalFees()))
                .append(", final equity $").append(String.format(Locale.US, "%,.2f", ta.finalEquity())).append(".\n");
        sb.append("- Fees + a calm regime mean a Sharpe ≤ 0 is an accepted, honest result (PROJECT.md Stage 5 ")
                .append("§5 / DoD). The point of this stage is a correct, single-sourced fill + metrics engine, ")
                .append("not alpha. Daily marking → Sharpe/Sortino annualised ×√365.\n");
        if (ml.totalFees() == 0.0) {
            sb.append("- (The ML-confirmed line shows $0.00 fees / 0.00 Sharpe precisely because it never traded.)\n");
        }
        return sb.toString();
    }

    private static Path reportFile() {
        Path parent = Path.of("..", "reports");
        return Files.exists(parent) ? parent.resolve("backtest_strategy_v1.md")
                : Path.of("reports", "backtest_strategy_v1.md");
    }
}
