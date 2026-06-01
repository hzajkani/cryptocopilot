package com.cryptocopilot.trading;

import java.time.Instant;

/** One point on the equity curve: total mark-to-market equity (and the cash slice) at an instant. */
public record EquityPoint(Instant ts, double equity, double cash) {
}
