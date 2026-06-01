package com.cryptocopilot.trading.backtest;

import com.cryptocopilot.dto.TAVerdict;

/**
 * Everything the simulator needs for one coin at one decision step: the signal inputs, the OHLC of
 * the bar an order would fill against (the next bar after the decision), and the mark price (close)
 * at the decision time. Pre-baked by {@link BacktestRunner} so {@link PortfolioSimulator} stays a
 * pure function (no DB, no ta4j) and is unit-testable from a hand-built fixture.
 *
 * @param symbol        coin symbol
 * @param ta            TA verdict computed from bars up to the decision time (leakage-safe)
 * @param mlClass       stored ML {@code pred_class} (UP/DOWN/FLAT)
 * @param probUp        calibrated P(UP)
 * @param mlConfidence  calibrated prob of {@code mlClass}
 * @param fillOpen      open of the fill bar (next bar after the decision)
 * @param fillHigh      high of the fill bar
 * @param fillLow       low of the fill bar
 * @param markClose     close at the decision time, used to mark equity
 */
public record DecisionBar(String symbol, TAVerdict ta, String mlClass, Double probUp,
                          Double mlConfidence, double fillOpen, double fillHigh, double fillLow,
                          double markClose) {
}
