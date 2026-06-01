package com.cryptocopilot.trading;

import java.util.List;

/** The {@code GET /api/performance} payload: the full equity curve plus the summary metrics. */
public record PerformanceReport(List<EquityPoint> equityCurve, TradingMetrics metrics) {
}
