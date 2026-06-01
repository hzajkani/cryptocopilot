package com.cryptocopilot.trading;

import java.util.Optional;

/**
 * The pure fill math shared by the live {@link PaperTradingEngine} and the
 * {@link com.cryptocopilot.trading.backtest.BacktestRunner} — single-sourced so slippage and fees
 * behave identically live and in backtest (PROJECT.md Stage 5 §2).
 *
 * <ul>
 *   <li><b>MARKET</b> fills at the (next) bar's open, moved against the trader by the slippage
 *       fraction: BUY pays {@code open·(1+slip)}, SELL receives {@code open·(1−slip)}.</li>
 *   <li><b>LIMIT</b> fills at the limit price only if the bar's range covers it
 *       ({@code low ≤ limit ≤ high}); otherwise it does not fill (stays pending).</li>
 * </ul>
 *
 * A taker fee of {@code feeFraction} is charged on the fill notional ({@code price·qty}) on every
 * fill. No Spring, no DB — a pure function of the order and one OHLC bar.
 */
public final class FillModel {

    private FillModel() {
    }

    /**
     * Compute the fill for an order against one OHLC bar. Returns empty for a LIMIT order whose
     * price the bar did not reach (the caller leaves it {@code PENDING}). MARKET always fills.
     */
    public static Optional<Fill> fill(String side, String type, double quantity, Double limitPrice,
                                      double open, double high, double low, TradingProperties props) {
        boolean buy = "BUY".equalsIgnoreCase(side);
        if ("LIMIT".equalsIgnoreCase(type)) {
            if (limitPrice == null) {
                return Optional.empty();
            }
            if (limitPrice < low || limitPrice > high) {
                return Optional.empty();   // unreachable this bar -> pending
            }
            return Optional.of(priced(limitPrice, quantity, props));
        }
        // MARKET: open shifted by slippage, against the trader.
        double slip = props.slippageFraction();
        double price = buy ? open * (1.0 + slip) : open * (1.0 - slip);
        return Optional.of(priced(price, quantity, props));
    }

    private static Fill priced(double price, double quantity, TradingProperties props) {
        double fees = Math.abs(price * quantity) * props.feeFraction();
        return new Fill(price, fees);
    }
}
