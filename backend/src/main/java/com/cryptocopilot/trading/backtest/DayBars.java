package com.cryptocopilot.trading.backtest;

import java.time.Instant;
import java.util.List;

/** All coins' {@link DecisionBar}s at one decision timestamp (one step of the daily backtest grid). */
public record DayBars(Instant ts, List<DecisionBar> bars) {
}
