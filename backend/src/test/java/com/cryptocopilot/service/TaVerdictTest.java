package com.cryptocopilot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptocopilot.dto.TAVerdict;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

/**
 * Golden tests for {@link TaVerdictEngine} on hand-built bar series — pure, no Spring/DB.
 * Mirrors the capstone's {@code test_ta_verdict}: a known-bullish series must score BULLISH
 * with the expected Ichimoku-driven signals.
 */
class TaVerdictTest {

    /** Degenerate "line" series (open=high=low=close=value) — exact and hand-computable. */
    private static BarSeries lineSeries(String name, double[] closes) {
        BarSeries s = new BaseBarSeriesBuilder().withName(name).build();
        ZonedDateTime t0 = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        for (int i = 0; i < closes.length; i++) {
            double c = closes[i];
            s.addBar(t0.plusHours(4L * i),
                    s.numOf(c), s.numOf(c), s.numOf(c), s.numOf(c), s.numOf(1000));
        }
        return s;
    }

    private static double[] ramp(double start, double step, int n) {
        double[] c = new double[n];
        for (int i = 0; i < n; i++) {
            c[i] = start + step * i;
        }
        return c;
    }

    @Test
    void knownBullishSeriesScoresBullish() {
        // A clean 120-bar uptrend: price far above the (lagged) cloud, chikou clear, bullish
        // cloud; RSI is pinned overbought (-1) by the pure up-move. Net = +2.5 -> BULLISH.
        TAVerdict v = TaVerdictEngine.compute("BULL", lineSeries("BULL", ramp(100.0, 1.5, 120)));

        assertThat(v.symbol()).isEqualTo("BULL");
        assertThat(v.direction()).isEqualTo("BULLISH");
        assertThat(v.confidence()).isEqualTo("MODERATE");
        assertThat(v.score()).isEqualTo(2.5);
        assertThat(v.signals()).containsExactly(
                "Price above the Ichimoku cloud (+2.0)",
                "Chikou above price 26 bars ago (+1.0)",
                "Bullish cloud: Senkou A above Senkou B (+0.5)",
                "RSI overbought >70 (-1.0)");
    }

    @Test
    void downtrendFiresBearishCloudSignals() {
        // A pure downtrend fires the bearish cloud rules (price below cloud, Senkou A below B),
        // but the oversold mean-reversion guard (+1) hedges it to a net-negative NEUTRAL — an
        // intended property: a clean BEARISH needs a fresh TK cross / MACD turn (real data).
        TAVerdict v = TaVerdictEngine.compute("BEAR", lineSeries("BEAR", ramp(300.0, -1.5, 120)));

        assertThat(v.score()).isEqualTo(-1.5);
        assertThat(v.direction()).isEqualTo("NEUTRAL");
        assertThat(v.signals()).containsExactly(
                "Price below the Ichimoku cloud (-2.0)",
                "Bearish cloud: Senkou A below Senkou B (-0.5)",
                "RSI oversold <30 (+1.0)");
    }

    @Test
    void classificationThresholds() {
        // direction: >=+2 BULLISH, <=-2 BEARISH, else NEUTRAL
        assertThat(TaVerdictEngine.direction(2.0)).isEqualTo("BULLISH");
        assertThat(TaVerdictEngine.direction(1.5)).isEqualTo("NEUTRAL");
        assertThat(TaVerdictEngine.direction(0.0)).isEqualTo("NEUTRAL");
        assertThat(TaVerdictEngine.direction(-1.5)).isEqualTo("NEUTRAL");
        assertThat(TaVerdictEngine.direction(-2.0)).isEqualTo("BEARISH");
        assertThat(TaVerdictEngine.direction(-3.5)).isEqualTo("BEARISH");

        // confidence: |s|>=3 STRONG, >=2 MODERATE, else WEAK
        assertThat(TaVerdictEngine.confidence(3.0)).isEqualTo("STRONG");
        assertThat(TaVerdictEngine.confidence(-3.5)).isEqualTo("STRONG");
        assertThat(TaVerdictEngine.confidence(2.5)).isEqualTo("MODERATE");
        assertThat(TaVerdictEngine.confidence(2.0)).isEqualTo("MODERATE");
        assertThat(TaVerdictEngine.confidence(1.5)).isEqualTo("WEAK");
        assertThat(TaVerdictEngine.confidence(0.0)).isEqualTo("WEAK");
    }

    @Test
    void insufficientHistoryIsNeutralWeak() {
        TAVerdict v = TaVerdictEngine.compute("SHORT", lineSeries("SHORT", ramp(100.0, 1.0, 50)));

        assertThat(v.direction()).isEqualTo("NEUTRAL");
        assertThat(v.confidence()).isEqualTo("WEAK");
        assertThat(v.signals()).containsExactly("Insufficient history for a TA verdict");
    }
}
