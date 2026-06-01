package com.cryptocopilot.trading;

/**
 * Performance metrics over an equity curve + trade blotter (PROJECT.md Stage 5 §2).
 *
 * @param sharpe         annualised Sharpe ratio (mean/stddev of period returns × √periodsPerYear)
 * @param sortino        annualised Sortino ratio (downside-deviation denominator)
 * @param maxDrawdownPct worst peak-to-trough decline of equity, as a positive fraction (0.12 = 12%)
 * @param winRate        fraction of closing (SELL) trades with positive realized P&L
 * @param avgWin         mean realized P&L of winning closes (USD)
 * @param avgLoss        mean realized P&L of losing closes (USD, negative)
 * @param totalTrades    number of fills executed
 * @param totalFees      total taker fees paid across all fills (the "fee drag", USD)
 * @param finalEquity    last equity value on the curve (USD)
 * @param totalReturnPct total return from first to last equity point, as a fraction
 */
public record TradingMetrics(double sharpe, double sortino, double maxDrawdownPct, double winRate,
                             double avgWin, double avgLoss, int totalTrades, double totalFees,
                             double finalEquity, double totalReturnPct) {

    /** All-zero metrics for an account that never traded / has no curve. */
    public static TradingMetrics empty(double startingEquity) {
        return new TradingMetrics(0, 0, 0, 0, 0, 0, 0, 0, startingEquity, 0);
    }
}
