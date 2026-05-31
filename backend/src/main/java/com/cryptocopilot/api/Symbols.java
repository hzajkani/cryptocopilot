package com.cryptocopilot.api;

import java.util.List;

/** The fixed 10-coin universe (PROJECT.md §6), in canonical display order. */
final class Symbols {

    static final List<String> UNIVERSE =
            List.of("BTC", "ETH", "SOL", "BNB", "XRP", "ADA", "AVAX", "DOT", "LINK", "MATIC");

    private Symbols() {
    }
}
