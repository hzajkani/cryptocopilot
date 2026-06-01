package com.cryptocopilot.service;

import com.cryptocopilot.dto.TAVerdict;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.bollinger.PercentBIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;

/**
 * The deterministic Ichimoku-centric TA verdict, computed with ta4j (PROJECT.md §3:
 * Java recomputes the indicators from raw {@code ohlcv} — it never reads Python features).
 *
 * <p>This is a pure function of a {@link BarSeries}: no Spring, no DB. That makes the
 * scoring rules unit-testable with a hand-built series (the golden test).
 *
 * <p><b>Cloud displacement.</b> Ichimoku's Senkou spans are normally plotted 26 bars
 * ahead. We instantiate them with {@code offset = 0} (raw spans) and read them at
 * {@code endIndex - 26} ourselves, so the cloud sitting under the current price was
 * derived from data 26 bars back — past-only, leakage-safe, identical to the Python
 * {@code shift(26)} convention. {@code chikou-clear} is likewise the leakage-safe
 * definition (today's close above the close 26 bars ago); ta4j's
 * {@code IchimokuChikouSpanIndicator} is the forward-plotted lagging line and is NaN at
 * the live edge, so it is intentionally not used for a current-bar verdict.
 */
public final class TaVerdictEngine {

    static final int TENKAN = 9;
    static final int KIJUN = 26;
    static final int SENKOU_B = 52;
    static final int DISPLACEMENT = 26;
    static final int RSI_PERIOD = 14;
    static final int MACD_SHORT = 12;
    static final int MACD_LONG = 26;
    static final int MACD_SIGNAL = 9;
    static final int BB_PERIOD = 20;
    static final double BB_K = 2.0;

    /** Need 52 bars for Senkou B plus 26 for displacement before the math is defined. */
    static final int MIN_BARS = SENKOU_B + DISPLACEMENT;

    private TaVerdictEngine() {
    }

    /** Compute the verdict for {@code symbol} from a ready ta4j {@link BarSeries} (ascending). */
    public static TAVerdict compute(String symbol, BarSeries series) {
        if (series.getBarCount() == 0) {
            throw new IllegalArgumentException("empty bar series for " + symbol);
        }
        int last = series.getEndIndex();
        Instant ts = series.getBar(last).getEndTime().toInstant();

        if (last < MIN_BARS) {
            return new TAVerdict(symbol, ts, "NEUTRAL", "WEAK",
                    List.of("Insufficient history for a TA verdict"), 0.0);
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        IchimokuTenkanSenIndicator tenkan = new IchimokuTenkanSenIndicator(series, TENKAN);
        IchimokuKijunSenIndicator kijun = new IchimokuKijunSenIndicator(series, KIJUN);
        // offset 0 => raw spans; the +26 displacement is applied by reading at (last - 26).
        IchimokuSenkouSpanAIndicator senkouA = new IchimokuSenkouSpanAIndicator(series, tenkan, kijun, 0);
        IchimokuSenkouSpanBIndicator senkouB = new IchimokuSenkouSpanBIndicator(series, SENKOU_B, 0);
        RSIIndicator rsi = new RSIIndicator(close, RSI_PERIOD);
        MACDIndicator macd = new MACDIndicator(close, MACD_SHORT, MACD_LONG);
        EMAIndicator macdSignal = new EMAIndicator(macd, MACD_SIGNAL);
        PercentBIndicator bbPct = new PercentBIndicator(close, BB_PERIOD, BB_K);

        int cloudIdx = last - DISPLACEMENT;
        double c = close.getValue(last).doubleValue();
        double senA = senkouA.getValue(cloudIdx).doubleValue();
        double senB = senkouB.getValue(cloudIdx).doubleValue();
        double cloudTop = Math.max(senA, senB);
        double cloudBottom = Math.min(senA, senB);

        double tkNow = tenkan.getValue(last).doubleValue() - kijun.getValue(last).doubleValue();
        double tkPrev = tenkan.getValue(last - 1).doubleValue() - kijun.getValue(last - 1).doubleValue();

        double closePrev = close.getValue(cloudIdx).doubleValue();   // close 26 bars ago

        double histNow = macd.getValue(last).doubleValue() - macdSignal.getValue(last).doubleValue();
        double histPrev = macd.getValue(last - 1).doubleValue() - macdSignal.getValue(last - 1).doubleValue();

        double r = rsi.getValue(last).doubleValue();
        double bb = bbPct.getValue(last).doubleValue();

        double score = 0.0;
        List<String> signals = new ArrayList<>();

        // 1) Price vs cloud (+/-2) — the dominant rule.
        if (c > cloudTop) {
            score += 2;
            signals.add("Price above the Ichimoku cloud (+2.0)");
        } else if (c < cloudBottom) {
            score -= 2;
            signals.add("Price below the Ichimoku cloud (-2.0)");
        }

        // 2) Tenkan/Kijun cross today (+/-1).
        if (tkNow > 0 && tkPrev <= 0) {
            score += 1;
            signals.add("Tenkan crossed above Kijun (+1.0)");
        } else if (tkNow < 0 && tkPrev >= 0) {
            score -= 1;
            signals.add("Tenkan crossed below Kijun (-1.0)");
        }

        // 3) Chikou clear (+1 only) — today's close above the close 26 bars ago.
        if (c > closePrev) {
            score += 1;
            signals.add("Chikou above price " + DISPLACEMENT + " bars ago (+1.0)");
        }

        // 4) Cloud thickness sign (+/-0.5).
        double thickness = senA - senB;
        if (thickness > 0) {
            score += 0.5;
            signals.add("Bullish cloud: Senkou A above Senkou B (+0.5)");
        } else if (thickness < 0) {
            score -= 0.5;
            signals.add("Bearish cloud: Senkou A below Senkou B (-0.5)");
        }

        // 5) MACD histogram (+/-1).
        if (histNow > 0 && histNow > histPrev) {
            score += 1;
            signals.add("MACD histogram positive and rising (+1.0)");
        } else if (histNow < 0 && histNow < histPrev) {
            score -= 1;
            signals.add("MACD histogram negative and falling (-1.0)");
        }

        // 6) RSI extremes (+/-1).
        if (r > 70) {
            score -= 1;
            signals.add("RSI overbought >70 (-1.0)");
        } else if (r < 30) {
            score += 1;
            signals.add("RSI oversold <30 (+1.0)");
        }

        // 7) Bollinger %B extremes (+/-0.5).
        if (bb > 0.95) {
            score -= 0.5;
            signals.add("Price at upper Bollinger band (-0.5)");
        } else if (bb < 0.05) {
            score += 0.5;
            signals.add("Price at lower Bollinger band (+0.5)");
        }

        return new TAVerdict(symbol, ts, direction(score), confidence(score),
                List.copyOf(signals), score);
    }

    /** {@code score >= +2} BULLISH, {@code <= -2} BEARISH, else NEUTRAL (spec §6). */
    static String direction(double score) {
        if (score >= 2) {
            return "BULLISH";
        }
        if (score <= -2) {
            return "BEARISH";
        }
        return "NEUTRAL";
    }

    /**
     * {@code |score| >= 3} STRONG, {@code >= 2} MODERATE, else WEAK. The spec writes the
     * middle band as "==2"; it is generalised to {@code >= 2} so the half-point scores the
     * rules can produce (e.g. 2.5) still read as a directional, non-WEAK call.
     */
    static String confidence(double score) {
        double mag = Math.abs(score);
        if (mag >= 3) {
            return "STRONG";
        }
        if (mag >= 2) {
            return "MODERATE";
        }
        return "WEAK";
    }
}
