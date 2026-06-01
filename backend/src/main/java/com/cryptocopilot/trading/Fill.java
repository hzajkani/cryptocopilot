package com.cryptocopilot.trading;

/** A computed fill: the execution price (after slippage) and the taker fee on that notional. */
public record Fill(double price, double fees) {
}
