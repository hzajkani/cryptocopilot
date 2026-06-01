package com.cryptocopilot.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptocopilot.entity.Ohlcv;
import com.cryptocopilot.repository.OhlcvRepository;
import com.cryptocopilot.trading.domain.AccountState;
import com.cryptocopilot.trading.domain.Order;
import com.cryptocopilot.trading.domain.Position;
import com.cryptocopilot.trading.domain.Trade;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link PaperTradingEngine} bookkeeping with mocked repositories (no Spring, no DB).
 * The fill economics live in {@link FillModel} (5 bps slippage, 10 bps fee here), so the expected
 * prices/fees are exact and hand-computable — exactly the {@code EngineTest} cases the brief lists.
 */
class EngineTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    private OhlcvRepository ohlcv;
    private AccountStateRepository account;
    private PositionRepository positions;
    private TradeRepository trades;
    private OrderRepository orders;
    private PaperTradingEngine engine;

    @BeforeEach
    void setUp() {
        ohlcv = mock(OhlcvRepository.class);
        account = mock(AccountStateRepository.class);
        positions = mock(PositionRepository.class);
        trades = mock(TradeRepository.class);
        orders = mock(OrderRepository.class);
        // 10,000 start, 5 bps slippage, 10 bps fee, 1h grid.
        TradingProperties props = new TradingProperties(10_000, 5, 10, "1h");
        engine = new PaperTradingEngine(ohlcv, account, positions, trades, orders, props);
    }

    @Test
    void marketBuyCreatesPositionWithCorrectAvgEntryAndFees() {
        nextBar(100.0, 101.0, 99.0);
        seedCash(10_000);
        when(positions.findById("BTC")).thenReturn(Optional.empty());

        FillResult result = engine.submitOrder(new OrderRequest("BTC", "BUY", "MARKET", 1.0, null));

        // open 100 + 5 bps slippage = 100.05; fee = 100.05 * 1 * 0.001 = 0.10005
        assertThat(result.status()).isEqualTo("FILLED");
        assertThat(result.filledPrice()).isEqualTo(100.05);
        assertThat(result.fees()).isCloseTo(0.10005, offset(1e-9));
        assertThat(result.realizedPnl()).isEqualTo(0.0);

        Position saved = capturePosition();
        assertThat(saved.getSymbol()).isEqualTo("BTC");
        assertThat(saved.getSize()).isEqualTo(1.0);
        assertThat(saved.getAvgEntryPrice()).isEqualTo(100.05);

        Trade trade = captureTrade();
        assertThat(trade.getSide()).isEqualTo("BUY");
        assertThat(trade.getPrice()).isEqualTo(100.05);
        assertThat(trade.getFees()).isCloseTo(0.10005, offset(1e-9));
    }

    @Test
    void sellProducesCorrectRealizedPnl() {
        // Hold 2 @ avg 100; sell 1 into a bar opening at 110.
        when(positions.findById("BTC"))
                .thenReturn(Optional.of(new Position("BTC", 2.0, 100.0, NOW)));
        nextBar(110.0, 111.0, 109.0);
        seedCash(5_000);

        FillResult result = engine.submitOrder(new OrderRequest("BTC", "SELL", "MARKET", 1.0, null));

        // sell price 110 - 5 bps = 109.945; fee = 0.109945; pnl = (109.945-100)*1 - fee
        assertThat(result.status()).isEqualTo("FILLED");
        assertThat(result.filledPrice()).isCloseTo(109.945, offset(1e-9));
        assertThat(result.realizedPnl()).isCloseTo(9.835055, offset(1e-6));

        Trade trade = captureTrade();
        assertThat(trade.getSide()).isEqualTo("SELL");
        assertThat(trade.getRealizedPnl()).isCloseTo(9.835055, offset(1e-6));
    }

    @Test
    void limitBelowBarLowStaysPending() {
        nextBar(100.0, 101.0, 99.0);
        when(positions.findById("BTC")).thenReturn(Optional.empty());

        FillResult result = engine.submitOrder(new OrderRequest("BTC", "BUY", "LIMIT", 1.0, 98.0));

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.filledPrice()).isNull();
        Order order = captureOrder();
        assertThat(order.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void limitWithinBarRangeFillsAtLimit() {
        nextBar(100.0, 105.0, 95.0);
        seedCash(10_000);
        when(positions.findById("BTC")).thenReturn(Optional.empty());

        FillResult result = engine.submitOrder(new OrderRequest("BTC", "BUY", "LIMIT", 1.0, 98.0));

        assertThat(result.status()).isEqualTo("FILLED");
        assertThat(result.filledPrice()).isEqualTo(98.0);   // limit, no slippage on a LIMIT
        assertThat(result.fees()).isCloseTo(0.098, offset(1e-9));
    }

    @Test
    void sellLargerThanHeldIsRejected() {
        nextBar(100.0, 101.0, 99.0);
        when(positions.findById("BTC")).thenReturn(Optional.empty());   // held 0

        FillResult result = engine.submitOrder(new OrderRequest("BTC", "SELL", "MARKET", 5.0, null));

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.message()).contains("exceeds held");
        Order order = captureOrder();
        assertThat(order.getStatus()).isEqualTo("CANCELLED");
    }

    // ---- helpers -------------------------------------------------------------------------------

    private void nextBar(double open, double high, double low) {
        Ohlcv bar = mock(Ohlcv.class);
        when(bar.getOpen()).thenReturn(open);
        when(bar.getHigh()).thenReturn(high);
        when(bar.getLow()).thenReturn(low);
        when(bar.getClose()).thenReturn(open);
        when(bar.getTsUtc()).thenReturn(NOW);
        when(ohlcv.findFirstBySymbolAndTimeframeAndTsUtcGreaterThanOrderByTsUtc(eq("BTC"), eq("1h"), any()))
                .thenReturn(Optional.of(bar));
    }

    private void seedCash(double cash) {
        when(account.count()).thenReturn(1L);
        when(account.findFirstByOrderByTsUtcDesc())
                .thenReturn(Optional.of(new AccountState(NOW, cash, cash)));
    }

    private Position capturePosition() {
        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(positions).save(captor.capture());
        return captor.getValue();
    }

    private Trade captureTrade() {
        ArgumentCaptor<Trade> captor = ArgumentCaptor.forClass(Trade.class);
        verify(trades).save(captor.capture());
        return captor.getValue();
    }

    private Order captureOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(captor.capture());
        return captor.getValue();
    }
}
