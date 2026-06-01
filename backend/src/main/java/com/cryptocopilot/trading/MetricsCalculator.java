package com.cryptocopilot.trading;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure performance statistics over an equity curve and the realized-P&L blotter (PROJECT.md Stage
 * 5 §2). No Spring, no DB — directly unit-tested, and reused by both the live engine and the
 * backtest.
 *
 * <p><b>Sharpe / Sortino</b> are annualised: {@code mean(r)/σ(r) · √periodsPerYear} over the
 * period-to-period equity returns. The caller chooses {@code periodsPerYear} to match the curve
 * cadence (e.g. 365 for a daily-marked backtest). Sortino swaps σ for the downside deviation.
 * <b>Max drawdown</b> is the worst peak-to-trough decline as a positive fraction. Win-rate /
 * average win / average loss are over the closing (SELL) trades' realized P&L.
 */
public final class MetricsCalculator {

    private MetricsCalculator() {
    }

    public static TradingMetrics compute(List<EquityPoint> curve, List<Double> realizedPnls,
                                         double totalFees, int totalTrades, double periodsPerYear) {
        if (curve == null || curve.size() < 2) {
            double last = (curve == null || curve.isEmpty()) ? 0.0 : curve.get(curve.size() - 1).equity();
            return new TradingMetrics(0, 0, 0,
                    winRate(realizedPnls), avgWin(realizedPnls), avgLoss(realizedPnls),
                    totalTrades, totalFees, last, 0);
        }

        double[] equity = curve.stream().mapToDouble(EquityPoint::equity).toArray();
        List<Double> returns = new ArrayList<>(equity.length - 1);
        for (int i = 1; i < equity.length; i++) {
            if (equity[i - 1] > 0) {
                returns.add(equity[i] / equity[i - 1] - 1.0);
            }
        }

        double mean = mean(returns);
        double std = std(returns, mean);
        double downside = downsideDeviation(returns);
        double annual = Math.sqrt(periodsPerYear);

        double sharpe = std > 0 ? (mean / std) * annual : 0.0;
        double sortino = downside > 0 ? (mean / downside) * annual : 0.0;
        double maxDd = maxDrawdown(equity);
        double finalEquity = equity[equity.length - 1];
        double totalReturn = equity[0] > 0 ? finalEquity / equity[0] - 1.0 : 0.0;

        return new TradingMetrics(sharpe, sortino, maxDd,
                winRate(realizedPnls), avgWin(realizedPnls), avgLoss(realizedPnls),
                totalTrades, totalFees, finalEquity, totalReturn);
    }

    static double maxDrawdown(double[] equity) {
        double peak = equity.length == 0 ? 0.0 : equity[0];
        double maxDd = 0.0;
        for (double e : equity) {
            if (e > peak) {
                peak = e;
            }
            if (peak > 0) {
                maxDd = Math.max(maxDd, (peak - e) / peak);
            }
        }
        return maxDd;
    }

    private static double mean(List<Double> xs) {
        if (xs.isEmpty()) {
            return 0.0;
        }
        double s = 0.0;
        for (double x : xs) {
            s += x;
        }
        return s / xs.size();
    }

    /** Sample standard deviation (n−1); 0 for fewer than two observations. */
    private static double std(List<Double> xs, double mean) {
        if (xs.size() < 2) {
            return 0.0;
        }
        double s = 0.0;
        for (double x : xs) {
            double d = x - mean;
            s += d * d;
        }
        return Math.sqrt(s / (xs.size() - 1));
    }

    /** Root-mean-square of the negative returns (downside deviation about zero). */
    private static double downsideDeviation(List<Double> xs) {
        if (xs.size() < 2) {
            return 0.0;
        }
        double s = 0.0;
        int n = 0;
        for (double x : xs) {
            if (x < 0) {
                s += x * x;
            }
            n++;
        }
        return n > 0 ? Math.sqrt(s / n) : 0.0;
    }

    private static double winRate(List<Double> pnls) {
        if (pnls == null || pnls.isEmpty()) {
            return 0.0;
        }
        long wins = pnls.stream().filter(p -> p > 0).count();
        return (double) wins / pnls.size();
    }

    private static double avgWin(List<Double> pnls) {
        if (pnls == null) {
            return 0.0;
        }
        return pnls.stream().filter(p -> p > 0).mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double avgLoss(List<Double> pnls) {
        if (pnls == null) {
            return 0.0;
        }
        return pnls.stream().filter(p -> p < 0).mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
