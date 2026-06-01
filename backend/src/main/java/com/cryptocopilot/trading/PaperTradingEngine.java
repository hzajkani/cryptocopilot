package com.cryptocopilot.trading;

import com.cryptocopilot.entity.Ohlcv;
import com.cryptocopilot.repository.OhlcvRepository;
import com.cryptocopilot.trading.domain.AccountState;
import com.cryptocopilot.trading.domain.Order;
import com.cryptocopilot.trading.domain.Position;
import com.cryptocopilot.trading.domain.Trade;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The long-only paper-trading engine (PROJECT.md Stage 5 §2). Simulates orders against the 1h
 * OHLCV grid with realistic slippage + taker fees and persists orders / trades / positions /
 * account_state — the only tables the backend writes to (PROJECT.md §3). No real money, ever
 * (PROJECT.md §9).
 *
 * <p><b>Fill timing.</b> A MARKET order fills at the <i>next</i> 1h bar's open (after submission)
 * moved by slippage; a LIMIT order fills at the limit when a later bar's range covers it, else it
 * stays {@code PENDING}. At the live edge there is no future bar, so a live order fills against the
 * latest available 1h bar — the deterministic present-time proxy for "the next bar" (a historical
 * backtest always has a true next bar). All fill economics live in {@link FillModel}.
 *
 * <p><b>Long-only.</b> A SELL larger than the held quantity is rejected ({@code CANCELLED}); no
 * shorts, no leverage. A BUY that exceeds available cash is rejected.
 */
@Service
public class PaperTradingEngine {

    private static final Logger log = LoggerFactory.getLogger(PaperTradingEngine.class);
    private static final double EPS = 1e-9;
    /** Daily-equivalent annualisation for the live equity curve's Sharpe (documented approximation). */
    private static final double PERIODS_PER_YEAR = 365.0;

    private final OhlcvRepository ohlcvRepository;
    private final AccountStateRepository accountRepository;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final OrderRepository orderRepository;
    private final TradingProperties props;

    public PaperTradingEngine(OhlcvRepository ohlcvRepository,
                              AccountStateRepository accountRepository,
                              PositionRepository positionRepository,
                              TradeRepository tradeRepository,
                              OrderRepository orderRepository,
                              TradingProperties props) {
        this.ohlcvRepository = ohlcvRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.tradeRepository = tradeRepository;
        this.orderRepository = orderRepository;
        this.props = props;
    }

    /** Wipe all paper state and re-seed cash = equity = {@code startingBalance} (default 10,000). */
    @Transactional
    public AccountState resetAccount(double startingBalance) {
        double balance = startingBalance > 0 ? startingBalance : props.startingBalance();
        orderRepository.deleteAllInBatch();
        tradeRepository.deleteAllInBatch();
        positionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        AccountState seed = new AccountState(Instant.now(), balance, balance);
        accountRepository.save(seed);
        log.info("paper account reset to {} USD", balance);
        return seed;
    }

    /** Submit an order; fills it (or leaves it PENDING / rejects it) and persists everything. */
    @Transactional
    public FillResult submitOrder(OrderRequest req) {
        String symbol = req.symbol() == null ? "" : req.symbol().trim().toUpperCase(Locale.ROOT);
        String side = req.side() == null ? "" : req.side().trim().toUpperCase(Locale.ROOT);
        String type = req.type() == null ? "MARKET" : req.type().trim().toUpperCase(Locale.ROOT);

        String id = UUID.randomUUID().toString();
        Instant tsSubmitted = Instant.now();

        String validation = validate(symbol, side, type, req.quantity(), req.limitPrice());
        if (validation != null) {
            return reject(id, tsSubmitted, symbol, side, type, req.quantity(), req.limitPrice(), validation);
        }

        Optional<Ohlcv> bar = fillBar(symbol, tsSubmitted);
        if (bar.isEmpty()) {
            return reject(id, tsSubmitted, symbol, side, type, req.quantity(), req.limitPrice(),
                    "no market data for " + symbol);
        }
        Ohlcv b = bar.get();

        double held = positionRepository.findById(symbol).map(Position::getSize).orElse(0.0);
        if ("SELL".equals(side) && req.quantity() > held + EPS) {
            return reject(id, tsSubmitted, symbol, side, type, req.quantity(), req.limitPrice(),
                    String.format(Locale.US, "SELL quantity %.8f exceeds held %.8f (long-only, no shorts)",
                            req.quantity(), held));
        }

        Optional<Fill> maybeFill = FillModel.fill(side, type, req.quantity(), req.limitPrice(),
                nz(b.getOpen()), nz(b.getHigh()), nz(b.getLow()), props);
        if (maybeFill.isEmpty()) {
            // LIMIT not reachable on the reference bar -> stays PENDING.
            Order pending = new Order(id, tsSubmitted, symbol, side, type, req.quantity(),
                    req.limitPrice(), "PENDING");
            orderRepository.save(pending);
            return new FillResult(id, symbol, side, type, "PENDING", null, null, null, null,
                    String.format(Locale.US, "limit %.4f not reached on bar [%.4f, %.4f]",
                            req.limitPrice(), nz(b.getLow()), nz(b.getHigh())));
        }
        Fill fill = maybeFill.get();

        double cash = cash();
        if ("BUY".equals(side)) {
            double cost = fill.price() * req.quantity() + fill.fees();
            if (cost > cash + EPS) {
                return reject(id, tsSubmitted, symbol, side, type, req.quantity(), req.limitPrice(),
                        String.format(Locale.US, "insufficient cash: cost %.2f > cash %.2f", cost, cash));
            }
        }

        Instant fillTs = b.getTsUtc();
        FillBookkeeping bk = applyFill(symbol, side, req.quantity(), fill, fillTs);

        Order order = new Order(id, tsSubmitted, symbol, side, type, req.quantity(),
                req.limitPrice(), "PENDING");
        order.markFilled(fillTs, fill.price(), fill.fees());
        orderRepository.save(order);

        Trade trade = new Trade(UUID.randomUUID().toString(), fillTs, symbol, side,
                req.quantity(), fill.price(), fill.fees(), bk.realizedPnl(),
                "%s %s via %s order".formatted(side, symbol, type));
        tradeRepository.save(trade);

        // One clean account_state snapshot: post-fill cash + freshly marked equity.
        snapshot(Instant.now(), bk.newCash());

        return new FillResult(id, symbol, side, type, "FILLED", fill.price(), fill.fees(),
                bk.realizedPnl(), fillTs, "filled");
    }

    /** Outcome of a fill applied to the books: realized P&L and the resulting cash level. */
    private record FillBookkeeping(double realizedPnl, double newCash) {
    }

    /**
     * Apply a fill to cash + position (long-only bookkeeping) and return the realized P&L (0 for a
     * BUY; for a SELL the realized gain on the closed quantity, net of the exit fee) and the new
     * cash level. Persists the position change but does <b>not</b> write account_state — the caller
     * writes a single {@link #snapshot} so the equity curve never records a half-applied state.
     */
    private FillBookkeeping applyFill(String symbol, String side, double qty, Fill fill, Instant fillTs) {
        double cash = cash();
        Optional<Position> existing = positionRepository.findById(symbol);
        if ("BUY".equals(side)) {
            if (existing.isPresent()) {
                Position p = existing.get();
                double newSize = p.getSize() + qty;
                double newAvg = (p.getSize() * p.getAvgEntryPrice() + qty * fill.price()) / newSize;
                p.setSize(newSize);
                p.setAvgEntryPrice(newAvg);
                positionRepository.save(p);
            } else {
                positionRepository.save(new Position(symbol, qty, fill.price(), fillTs));
            }
            return new FillBookkeeping(0.0, cash - (fill.price() * qty + fill.fees()));
        }
        // SELL: reduce the position, realize P&L on the closed quantity.
        Position p = existing.orElseThrow();   // guaranteed by the held-quantity check
        double realized = (fill.price() - p.getAvgEntryPrice()) * qty - fill.fees();
        double newSize = p.getSize() - qty;
        if (newSize <= EPS) {
            positionRepository.delete(p);
        } else {
            p.setSize(newSize);
            positionRepository.save(p);
        }
        return new FillBookkeeping(realized, cash + (fill.price() * qty - fill.fees()));
    }

    /** Snapshot equity = cash + Σ(position size × mark price at {@code ts}) into account_state. */
    @Transactional
    public AccountState markToMarket(Instant ts) {
        return snapshot(ts, cash());
    }

    /** Write one account_state row: the given cash plus holdings marked at {@code ts}. */
    private AccountState snapshot(Instant ts, double cash) {
        double holdings = 0.0;
        for (Position p : positionRepository.findAll()) {
            double price = markPrice(p.getSymbol(), ts).orElse(p.getAvgEntryPrice());
            holdings += p.getSize() * price;
        }
        AccountState snapshot = new AccountState(ts, cash, cash + holdings);
        accountRepository.save(snapshot);   // upsert by ts
        return snapshot;
    }

    @Transactional(readOnly = true)
    public List<Position> positions() {
        return positionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Trade> trades() {
        return tradeRepository.findAllByOrderByTsUtcDesc();
    }

    @Transactional(readOnly = true)
    public List<Order> orders() {
        return orderRepository.findAllByOrderByTsSubmittedDesc();
    }

    /** Current account snapshot, initialising a fresh account to the starting balance if needed. */
    @Transactional
    public AccountState account() {
        ensureInitialised();
        return accountRepository.findFirstByOrderByTsUtcDesc().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<EquityPoint> equityCurve() {
        return accountRepository.findAllByOrderByTsUtc().stream()
                .map(a -> new EquityPoint(a.getTsUtc(), a.getTotalEquityUsd(), a.getCashUsd()))
                .toList();
    }

    /** The {@code GET /api/performance} payload: equity curve + Sharpe/Sortino/maxDD/win-rate/fees. */
    @Transactional
    public PerformanceReport performance() {
        ensureInitialised();
        List<EquityPoint> curve = equityCurve();
        List<Trade> allTrades = tradeRepository.findAll();
        List<Double> realized = new ArrayList<>();
        double totalFees = 0.0;
        for (Trade t : allTrades) {
            totalFees += t.getFees();
            if ("SELL".equalsIgnoreCase(t.getSide())) {
                realized.add(t.getRealizedPnl());
            }
        }
        TradingMetrics metrics = MetricsCalculator.compute(curve, realized, totalFees,
                allTrades.size(), PERIODS_PER_YEAR);
        return new PerformanceReport(curve, metrics);
    }

    // ---- internals -----------------------------------------------------------------------------

    private String validate(String symbol, String side, String type, double qty, Double limit) {
        if (symbol.isEmpty()) {
            return "symbol is required";
        }
        if (!"BUY".equals(side) && !"SELL".equals(side)) {
            return "side must be BUY or SELL";
        }
        if (!"MARKET".equals(type) && !"LIMIT".equals(type)) {
            return "type must be MARKET or LIMIT";
        }
        if (qty <= 0) {
            return "quantity must be positive";
        }
        if ("LIMIT".equals(type) && (limit == null || limit <= 0)) {
            return "LIMIT order requires a positive limitPrice";
        }
        return null;
    }

    private FillResult reject(String id, Instant ts, String symbol, String side, String type,
                              double qty, Double limit, String reason) {
        Order cancelled = new Order(id, ts, symbol, side, type, qty, limit, "CANCELLED");
        orderRepository.save(cancelled);
        log.info("order {} rejected: {}", id, reason);
        return new FillResult(id, symbol, side, type, "CANCELLED", null, null, null, null, reason);
    }

    /** The bar an order fills against: the next 1h bar after submission, else the latest one. */
    private Optional<Ohlcv> fillBar(String symbol, Instant after) {
        Optional<Ohlcv> next = ohlcvRepository
                .findFirstBySymbolAndTimeframeAndTsUtcGreaterThanOrderByTsUtc(symbol, props.timeframe(), after);
        if (next.isPresent()) {
            return next;
        }
        return ohlcvRepository.findFirstBySymbolAndTimeframeOrderByTsUtcDesc(symbol, props.timeframe());
    }

    private Optional<Double> markPrice(String symbol, Instant ts) {
        return ohlcvRepository
                .findFirstBySymbolAndTimeframeAndTsUtcLessThanEqualOrderByTsUtcDesc(symbol, props.timeframe(), ts)
                .or(() -> ohlcvRepository.findFirstBySymbolAndTimeframeOrderByTsUtcDesc(symbol, props.timeframe()))
                .map(o -> nz(o.getClose()));
    }

    private void ensureInitialised() {
        if (accountRepository.count() == 0) {
            accountRepository.save(new AccountState(Instant.now(),
                    props.startingBalance(), props.startingBalance()));
        }
    }

    private double cash() {
        ensureInitialised();
        return accountRepository.findFirstByOrderByTsUtcDesc().map(AccountState::getCashUsd).orElse(0.0);
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }
}
