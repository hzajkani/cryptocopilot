package com.cryptocopilot.trading.backtest;

import com.cryptocopilot.trading.TradingMetrics;
import com.cryptocopilot.trading.TradingProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Renders the Stage 5 backtest report ({@code reports/backtest_strategy_v1.md}). */
public final class BacktestReportWriter {

    private BacktestReportWriter() {
    }

    /** Build the markdown for the given results + notes and return it. */
    public static String render(List<BacktestResult> results, TradingProperties props, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Backtest — CryptoCopilot Stage 5 (paper-trading strategies)\n\n");
        sb.append("Generated: ").append(Instant.now()).append("  \n");
        if (!results.isEmpty()) {
            BacktestResult any = results.get(0);
            sb.append("Window: **").append(any.from()).append(" → ").append(any.to())
                    .append("** (").append(any.equityCurve().size()).append(" daily marks).  \n");
            sb.append("Universe: 10 coins · start $").append(fmt(any.startingCash()))
                    .append(" · trade size $").append(fmt(any.tradeSizeUsd()))
                    .append(" · slippage ").append(fmt(props.slippageBps())).append(" bps")
                    .append(" · taker fee ").append(fmt(props.feeBps())).append(" bps")
                    .append(" · Sharpe/Sortino annualised ×√365.\n\n");
        }

        sb.append("## Results\n\n");
        sb.append("| strategy | trades | final equity | total return | Sharpe | Sortino | max DD | win rate | fees |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|\n");
        for (BacktestResult r : results) {
            TradingMetrics m = r.metrics();
            sb.append("| ").append(r.label())
                    .append(" | ").append(r.trades())
                    .append(" | $").append(fmt(m.finalEquity()))
                    .append(" | ").append(pct(m.totalReturnPct()))
                    .append(" | ").append(fmt3(m.sharpe()))
                    .append(" | ").append(fmt3(m.sortino()))
                    .append(" | ").append(pct(m.maxDrawdownPct()))
                    .append(" | ").append(pct(m.winRate()))
                    .append(" | $").append(fmt(m.totalFees()))
                    .append(" |\n");
        }

        if (notes != null && !notes.isBlank()) {
            sb.append("\n## Notes\n\n").append(notes).append('\n');
        }
        return sb.toString();
    }

    /** Render and write the report to {@code path}, creating parent directories. */
    public static void write(Path path, List<BacktestResult> results, TradingProperties props,
                             String notes) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, render(results, props, notes));
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    private static String fmt3(double v) {
        return String.format(Locale.US, "%.3f", v);
    }

    private static String pct(double fraction) {
        return String.format(Locale.US, "%.2f%%", fraction * 100.0);
    }
}
