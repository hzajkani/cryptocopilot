package com.cryptocopilot.trading.backtest;

/**
 * A long-only backtest strategy (PROJECT.md Stage 5 §5). Given the current {@link SignalRow} for a
 * coin and whether a position is already open, it decides to enter, exit, or hold. The
 * {@link com.cryptocopilot.trading.backtest.PortfolioSimulator} translates {@code ENTER} into a
 * fixed-notional MARKET BUY and {@code EXIT} into a MARKET SELL of the whole position.
 */
@FunctionalInterface
public interface Strategy {

    enum Action {ENTER, EXIT, HOLD}

    Action decide(SignalRow row, boolean hasPosition);

    /** A short identifier used in the backtest report. */
    default String label() {
        return getClass().getSimpleName();
    }
}
