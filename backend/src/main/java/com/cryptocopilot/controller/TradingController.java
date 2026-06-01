package com.cryptocopilot.controller;

import com.cryptocopilot.trading.FillResult;
import com.cryptocopilot.trading.OrderRequest;
import com.cryptocopilot.trading.PaperTradingEngine;
import com.cryptocopilot.trading.PerformanceReport;
import com.cryptocopilot.trading.domain.AccountState;
import com.cryptocopilot.trading.domain.Order;
import com.cryptocopilot.trading.domain.Position;
import com.cryptocopilot.trading.domain.Trade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The paper-trading API (PROJECT.md Stage 5 §6). Submit orders, inspect positions / trades /
 * account, read the performance report, and reset. No real money, ever — long-only, no leverage.
 */
@RestController
@Tag(name = "Paper trading", description = "Long-only paper orders, positions, trades, performance")
public class TradingController {

    private final PaperTradingEngine engine;

    public TradingController(PaperTradingEngine engine) {
        this.engine = engine;
    }

    @Operation(summary = "Submit a paper order",
            description = "MARKET fills at the next 1h bar's open + slippage; LIMIT fills when a "
                    + "later 1h bar covers the limit, else stays PENDING. Returns the fill + fees. "
                    + "A SELL larger than the held quantity is rejected (long-only).")
    @PostMapping("/api/orders")
    public FillResult submit(@RequestBody OrderRequest request) {
        return engine.submitOrder(request);
    }

    @Operation(summary = "Open positions")
    @GetMapping("/api/positions")
    public List<Position> positions() {
        return engine.positions();
    }

    @Operation(summary = "Trade blotter (newest first)")
    @GetMapping("/api/trades")
    public List<Trade> trades() {
        return engine.trades();
    }

    @Operation(summary = "Order history (newest first)")
    @GetMapping("/api/orders")
    public List<Order> orders() {
        return engine.orders();
    }

    @Operation(summary = "Current account snapshot (cash + equity)")
    @GetMapping("/api/account")
    public AccountState account() {
        return engine.account();
    }

    @Operation(summary = "Performance report",
            description = "Equity curve + metrics: Sharpe, Sortino, max drawdown, win rate, "
                    + "avg win/loss, total trades, total fees.")
    @GetMapping("/api/performance")
    public PerformanceReport performance() {
        return engine.performance();
    }

    @Operation(summary = "Reset the paper account",
            description = "Wipes positions/trades/orders and re-seeds cash. Defaults to 10,000 USD.")
    @PostMapping("/api/account/reset")
    public AccountState reset(
            @RequestParam(name = "startingBalance", required = false, defaultValue = "0")
            double startingBalance) {
        return engine.resetAccount(startingBalance);
    }
}
