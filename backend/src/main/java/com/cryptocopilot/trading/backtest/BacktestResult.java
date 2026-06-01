package com.cryptocopilot.trading.backtest;

import com.cryptocopilot.trading.EquityPoint;
import com.cryptocopilot.trading.TradingMetrics;
import java.time.Instant;
import java.util.List;

/**
 * The outcome of one backtest run: the daily equity curve + summary metrics + bookkeeping.
 *
 * @param label              the strategy's label
 * @param from               first decision day
 * @param to                 last decision day
 * @param startingCash       starting cash (USD)
 * @param tradeSizeUsd       notional per ENTER (USD)
 * @param trades             number of fills (entries + exits)
 * @param openPositionsAtEnd positions still open at the final mark (counted into final equity)
 * @param equityCurve        daily mark-to-market equity
 * @param metrics            Sharpe / Sortino / maxDD / win-rate / fees / total return
 */
public record BacktestResult(String label, Instant from, Instant to, double startingCash,
                             double tradeSizeUsd, int trades, int openPositionsAtEnd,
                             List<EquityPoint> equityCurve, TradingMetrics metrics) {
}
