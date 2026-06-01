package com.cryptocopilot.trading;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable knobs for the paper-trading engine (PROJECT.md Stage 5 §2). Bound from
 * {@code cryptocopilot.trading.*} in {@code application.yml}; sensible defaults match the spec
 * (10,000 USD start, 0.05% slippage, 0.1% taker fee, fills on the 1h OHLCV grid).
 *
 * @param startingBalance starting cash in USD ({@code resetAccount} default)
 * @param slippageBps     MARKET slippage in basis points applied against the fill (5 = 0.05%)
 * @param feeBps          taker fee in basis points on every fill notional (10 = 0.1%)
 * @param timeframe       the OHLCV timeframe orders fill against (the 1h grid)
 */
@ConfigurationProperties(prefix = "cryptocopilot.trading")
public record TradingProperties(
        double startingBalance,
        double slippageBps,
        double feeBps,
        String timeframe) {

    public TradingProperties {
        if (startingBalance <= 0) {
            startingBalance = 10_000.0;
        }
        if (slippageBps < 0) {
            slippageBps = 5.0;
        }
        if (feeBps < 0) {
            feeBps = 10.0;
        }
        if (timeframe == null || timeframe.isBlank()) {
            timeframe = "1h";
        }
    }

    /** Slippage as a fraction (5 bps -> 0.0005). */
    public double slippageFraction() {
        return slippageBps / 10_000.0;
    }

    /** Fee as a fraction (10 bps -> 0.001). */
    public double feeFraction() {
        return feeBps / 10_000.0;
    }
}
