package com.cryptocopilot.trading;

/**
 * The {@code POST /api/orders} request body. {@code limitPrice} is required only for
 * {@code type=LIMIT}. Long-only: {@code side} is {@code BUY} or {@code SELL} (a SELL larger than
 * the held quantity is rejected — no shorts).
 *
 * @param symbol     coin symbol (e.g. {@code BTC})
 * @param side       {@code BUY} / {@code SELL}
 * @param type       {@code MARKET} / {@code LIMIT}
 * @param quantity   units of the coin to trade
 * @param limitPrice limit price (LIMIT only; ignored for MARKET)
 */
public record OrderRequest(String symbol, String side, String type, double quantity,
                           Double limitPrice) {
}
