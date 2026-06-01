package com.cryptocopilot.trading.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptocopilot.dto.TAVerdict;
import com.cryptocopilot.trading.EquityPoint;
import com.cryptocopilot.trading.TradingProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure {@link PortfolioSimulator} with the spec-default {@link MlConfirmedByTaStrategy}
 * over a hand-built one-month daily fixture (PROJECT.md Stage 5 §7). No Spring, no DB, no ta4j.
 */
class BacktestTest {

    private static final TradingProperties PROPS = new TradingProperties(10_000, 5, 10, "1h");
    private static final Instant T0 = Instant.parse("2026-03-01T00:00:00Z");

    @Test
    void defaultStrategyEntersOnConfirmationThenExitsOnTaFlip() {
        // 30 days, single coin. Day 0 NEUTRAL (no entry); days 1–19 BULLISH + ML UP@0.60 (enter,
        // hold while price ramps 101→119); day 20+ BEARISH (exit, then flat).
        List<DayBars> days = new ArrayList<>();
        for (int d = 0; d < 30; d++) {
            String dir = d == 0 ? "NEUTRAL" : (d >= 20 ? "BEARISH" : "BULLISH");
            days.add(day(d, dir, "UP", 0.60));
        }

        BacktestResult r = PortfolioSimulator.simulate(days, new MlConfirmedByTaStrategy(),
                BacktestParams.defaults(PROPS));

        assertThat(r.equityCurve()).hasSize(30);
        // Before entry the account is all cash at the starting balance.
        assertThat(r.equityCurve().get(0).equity()).isEqualTo(10_000.0);
        // One round trip: an entry and an exit.
        assertThat(r.trades()).isEqualTo(2);
        assertThat(r.openPositionsAtEnd()).isZero();
        // The position was held through a rising market → the single closed trade is a win.
        assertThat(r.metrics().winRate()).isEqualTo(1.0);
        assertThat(r.metrics().totalReturnPct()).isGreaterThan(0.0);
        // Equity peaked above the start while holding, and ends in all cash above the start.
        double peak = r.equityCurve().stream().mapToDouble(EquityPoint::equity).max().orElseThrow();
        assertThat(peak).isGreaterThan(10_000.0);
        assertThat(r.metrics().finalEquity()).isGreaterThan(10_000.0);
    }

    @Test
    void defaultStrategyMakesNoTradesWhenMlNeverSaysUp() {
        // The live reality: with no coin at UP (calm/down regime), the ML-confirmed default never
        // enters — zero trades, flat equity. Documented honest result (PROJECT.md Stage 5 §5).
        List<DayBars> days = new ArrayList<>();
        for (int d = 0; d < 30; d++) {
            days.add(day(d, "BULLISH", "FLAT", 0.49));   // TA bullish, but ML is FLAT
        }

        BacktestResult r = PortfolioSimulator.simulate(days, new MlConfirmedByTaStrategy(),
                BacktestParams.defaults(PROPS));

        assertThat(r.trades()).isZero();
        assertThat(r.openPositionsAtEnd()).isZero();
        assertThat(r.metrics().finalEquity()).isEqualTo(10_000.0);
        assertThat(r.metrics().totalReturnPct()).isZero();
        assertThat(r.metrics().sharpe()).isZero();   // no variance in a flat curve
    }

    /** One day's bars for a single coin: price ramps 100 + d; fill bar brackets the close. */
    private static DayBars day(int d, String taDirection, String mlClass, double probUp) {
        Instant t = T0.plus(d, ChronoUnit.DAYS);
        double price = 100.0 + d;
        double score = "BULLISH".equals(taDirection) ? 3.0 : "BEARISH".equals(taDirection) ? -3.0 : 0.0;
        TAVerdict ta = new TAVerdict("BTC", t, taDirection, "STRONG", List.of(), score);
        DecisionBar bar = new DecisionBar("BTC", ta, mlClass, probUp, probUp,
                price, price + 1.0, price - 1.0, price);
        return new DayBars(t, List.of(bar));
    }
}
