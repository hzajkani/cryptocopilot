package com.cryptocopilot.trading.backtest;

import com.cryptocopilot.trading.EquityPoint;
import com.cryptocopilot.trading.Fill;
import com.cryptocopilot.trading.FillModel;
import com.cryptocopilot.trading.MetricsCalculator;
import com.cryptocopilot.trading.TradingMetrics;
import com.cryptocopilot.trading.TradingProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure long-only portfolio backtest (PROJECT.md Stage 5 §5). Walks a daily grid of {@link DayBars};
 * for each coin it asks the {@link Strategy} to ENTER / EXIT / HOLD, fills via the shared
 * {@link FillModel} (same slippage + fees as the live engine), and marks the whole portfolio to
 * market at each day's close. No Spring, no DB, no ta4j — a deterministic function of its inputs,
 * so the fixture test ({@code BacktestTest}) drives it directly.
 *
 * <p><b>Sizing.</b> Each ENTER buys {@code tradeSizeUsd} notional ({@code qty = tradeSizeUsd /
 * fillOpen}); an ENTER is skipped if cash is below {@code tradeSizeUsd} or a position already
 * exists for the coin. Each EXIT sells the whole position. Positions still open at the end are
 * counted into final equity at the last mark (their P&L is unrealised, so not in win/loss stats).
 */
public final class PortfolioSimulator {

    private static final double EPS = 1e-9;

    private PortfolioSimulator() {
    }

    public static BacktestResult simulate(List<DayBars> days, Strategy strategy, BacktestParams params) {
        TradingProperties props = params.props();
        double cash = params.startingCash();
        // symbol -> [size, avgEntryPrice]
        Map<String, double[]> positions = new LinkedHashMap<>();
        List<EquityPoint> curve = new ArrayList<>(days.size());
        List<Double> realizedPnls = new ArrayList<>();
        double totalFees = 0.0;
        int trades = 0;

        for (DayBars day : days) {
            for (DecisionBar db : day.bars()) {
                boolean has = positions.containsKey(db.symbol());
                SignalRow row = new SignalRow(db.symbol(), day.ts(), db.markClose(), db.ta(),
                        db.mlClass(), db.probUp(), db.mlConfidence());
                Strategy.Action action = strategy.decide(row, has);

                if (action == Strategy.Action.ENTER && !has
                        && cash >= params.tradeSizeUsd() && db.fillOpen() > 0) {
                    double qty = params.tradeSizeUsd() / db.fillOpen();
                    Fill f = FillModel.fill("BUY", "MARKET", qty, null,
                            db.fillOpen(), db.fillHigh(), db.fillLow(), props).orElseThrow();
                    double cost = f.price() * qty + f.fees();
                    if (cost <= cash + EPS) {
                        cash -= cost;
                        positions.put(db.symbol(), new double[]{qty, f.price()});
                        totalFees += f.fees();
                        trades++;
                    }
                } else if (action == Strategy.Action.EXIT && has) {
                    double[] pos = positions.get(db.symbol());
                    double qty = pos[0];
                    double avg = pos[1];
                    Fill f = FillModel.fill("SELL", "MARKET", qty, null,
                            db.fillOpen(), db.fillHigh(), db.fillLow(), props).orElseThrow();
                    cash += f.price() * qty - f.fees();
                    realizedPnls.add((f.price() - avg) * qty - f.fees());
                    totalFees += f.fees();
                    trades++;
                    positions.remove(db.symbol());
                }
            }

            // Mark the portfolio at this day's closes.
            double holdings = 0.0;
            for (DecisionBar db : day.bars()) {
                double[] pos = positions.get(db.symbol());
                if (pos != null) {
                    holdings += pos[0] * db.markClose();
                }
            }
            curve.add(new EquityPoint(day.ts(), cash + holdings, cash));
        }

        TradingMetrics metrics = MetricsCalculator.compute(curve, realizedPnls, totalFees, trades,
                params.periodsPerYear());
        Instant from = days.isEmpty() ? Instant.EPOCH : days.get(0).ts();
        Instant to = days.isEmpty() ? Instant.EPOCH : days.get(days.size() - 1).ts();
        return new BacktestResult(strategy.label(), from, to, params.startingCash(),
                params.tradeSizeUsd(), trades, positions.size(), curve, metrics);
    }
}
