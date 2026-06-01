package com.cryptocopilot.trading;

import java.time.Instant;

/**
 * The outcome of {@link PaperTradingEngine#submitOrder} — the persisted order's id and terminal
 * (or pending) state, plus the fill economics. {@code status} is {@code FILLED} / {@code PENDING}
 * (a LIMIT not yet reachable) / {@code CANCELLED} (rejected, with the reason in {@code message}).
 *
 * @param orderId      the stored order id (UUID)
 * @param symbol       coin symbol
 * @param side         {@code BUY} / {@code SELL}
 * @param type         {@code MARKET} / {@code LIMIT}
 * @param status       {@code FILLED} / {@code PENDING} / {@code CANCELLED}
 * @param filledPrice  fill price after slippage (null unless FILLED)
 * @param fees         taker fee paid (null unless FILLED)
 * @param realizedPnl  realized P&L for a closing SELL (0 for a BUY; null unless FILLED)
 * @param tsFilled     fill timestamp on the OHLCV grid (null unless FILLED)
 * @param message      human-readable note (e.g. the rejection reason)
 */
public record FillResult(String orderId, String symbol, String side, String type, String status,
                         Double filledPrice, Double fees, Double realizedPnl, Instant tsFilled,
                         String message) {
}
