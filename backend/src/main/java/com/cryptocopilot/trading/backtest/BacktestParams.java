package com.cryptocopilot.trading.backtest;

import com.cryptocopilot.trading.TradingProperties;

/**
 * Knobs for one backtest run.
 *
 * @param startingCash   portfolio starting cash (USD)
 * @param tradeSizeUsd   notional bought per ENTER (USD) — the spec's "$1000"
 * @param periodsPerYear annualisation factor for Sharpe/Sortino (365 for a daily-marked curve)
 * @param props          the shared slippage/fee model
 */
public record BacktestParams(double startingCash, double tradeSizeUsd, double periodsPerYear,
                             TradingProperties props) {

    public static BacktestParams defaults(TradingProperties props) {
        return new BacktestParams(10_000.0, 1_000.0, 365.0, props);
    }
}
