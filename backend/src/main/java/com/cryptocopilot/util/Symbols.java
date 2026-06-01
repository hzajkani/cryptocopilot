package com.cryptocopilot.util;

import java.util.List;

/** The fixed 10-coin universe (PROJECT.md §6), in canonical display order. */
public final class Symbols {

    public static final List<String> UNIVERSE =
            List.of("BTC", "ETH", "SOL", "BNB", "XRP", "ADA", "AVAX", "DOT", "LINK", "MATIC");

    private Symbols() {
    }
}
