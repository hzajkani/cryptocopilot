package com.cryptocopilot.trading.backtest;

/**
 * A purely TA-driven long-only strategy: enter on {@code BULLISH}, exit on {@code BEARISH}.
 *
 * <p><b>Why this exists alongside the spec default.</b> The {@code predictions} table holds a
 * single <i>latest</i> ML snapshot per coin (PROJECT.md §2: the ML batch job writes only the
 * current forecast), so there is no historical ML series to backtest the "ML-confirmed-by-TA"
 * rule bar-by-bar. The TA verdict, by contrast, is fully reconstructable at every historical bar
 * from {@code ohlcv}. This strategy is therefore the substantive, reproducible real-window
 * backtest; the ML-confirmed default is also run, and (in the current calm/down regime where no
 * coin is {@code UP} with {@code prob_up>0.55}) it correctly produces zero trades — documented in
 * the report and STATE.md.
 */
public final class TaLongOnlyStrategy implements Strategy {

    @Override
    public Action decide(SignalRow row, boolean hasPosition) {
        if (row.ta() == null) {
            return Action.HOLD;
        }
        String dir = row.ta().direction();
        if (!hasPosition) {
            return "BULLISH".equalsIgnoreCase(dir) ? Action.ENTER : Action.HOLD;
        }
        return "BEARISH".equalsIgnoreCase(dir) ? Action.EXIT : Action.HOLD;
    }

    @Override
    public String label() {
        return "TA-long-only (enter BULLISH, exit BEARISH)";
    }
}
