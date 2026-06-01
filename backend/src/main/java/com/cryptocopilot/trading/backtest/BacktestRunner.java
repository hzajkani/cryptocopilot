package com.cryptocopilot.trading.backtest;

import com.cryptocopilot.dto.TAVerdict;
import com.cryptocopilot.entity.Ohlcv;
import com.cryptocopilot.entity.Prediction;
import com.cryptocopilot.repository.OhlcvRepository;
import com.cryptocopilot.repository.PredictionRepository;
import com.cryptocopilot.service.TaVerdictEngine;
import com.cryptocopilot.trading.TradingProperties;
import com.cryptocopilot.util.Symbols;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

/**
 * Builds the per-day {@link DayBars} from Postgres and runs strategies through the pure
 * {@link PortfolioSimulator} (PROJECT.md Stage 5 §5). Decisions are taken on a daily grid over the
 * real available window (default {@code 2025-09-01 → latest bar}); the TA verdict at each day is
 * recomputed from the 4h {@code ohlcv} series ending at that day (leakage-safe, identical to the
 * live engine), and orders fill against the next 1h bar via the shared slippage/fee model.
 *
 * <p>The ML view comes from the single latest {@code predictions} snapshot per coin (the ML batch
 * job stores only the current forecast — PROJECT.md §2), held constant across the window. That is
 * exactly why the spec-default "ML-confirmed-by-TA" strategy is data-limited here and the
 * reconstructable {@link TaLongOnlyStrategy} carries the substantive curve (see {@link BacktestResult}).
 */
@Service
public class BacktestRunner {

    private static final Logger log = LoggerFactory.getLogger(BacktestRunner.class);
    private static final String SIGNAL_TF = "4h";
    private static final String FILL_TF = "1h";
    private static final int TA_LOOKBACK = 300;
    /** Default backtest start — the ML test window (PROJECT.md Stage 5 "Reality"). Never hardcode 2023. */
    public static final Instant DEFAULT_FROM = Instant.parse("2025-09-01T00:00:00Z");

    private final OhlcvRepository ohlcvRepository;
    private final PredictionRepository predictionRepository;
    private final TradingProperties props;

    public BacktestRunner(OhlcvRepository ohlcvRepository, PredictionRepository predictionRepository,
                          TradingProperties props) {
        this.ohlcvRepository = ohlcvRepository;
        this.predictionRepository = predictionRepository;
        this.props = props;
    }

    /** Run a strategy over the default real window. */
    @Transactional(readOnly = true)
    public BacktestResult run(Strategy strategy) {
        return run(strategy, DEFAULT_FROM, latestBarTs());
    }

    /** Run a strategy over an explicit window. */
    @Transactional(readOnly = true)
    public BacktestResult run(Strategy strategy, Instant from, Instant to) {
        List<DayBars> days = buildDays(from, to);
        BacktestResult result = PortfolioSimulator.simulate(days, strategy, BacktestParams.defaults(props));
        log.info("backtest [{}] {} -> {}: {} trades, final equity {}, Sharpe {}", strategy.label(),
                from, to, result.trades(), result.metrics().finalEquity(), result.metrics().sharpe());
        return result;
    }

    /** The latest 1h bar timestamp (BTC) — the natural window end. */
    public Instant latestBarTs() {
        return ohlcvRepository.findFirstBySymbolAndTimeframeOrderByTsUtcDesc("BTC", FILL_TF)
                .map(Ohlcv::getTsUtc)
                .orElse(Instant.parse("2026-06-01T00:00:00Z"));
    }

    /** Build the daily decision grid with each coin's signal + fill + mark data baked in. */
    private List<DayBars> buildDays(Instant from, Instant to) {
        // Per-symbol 4h (with warm-up for TA) and 1h (fills + marks) series, loaded once.
        Map<String, List<Ohlcv>> series4h = new ConcurrentHashMap<>();
        Map<String, List<Ohlcv>> series1h = new ConcurrentHashMap<>();
        Map<String, MlView> mlViews = new ConcurrentHashMap<>();
        Instant warmupStart = from.minus(80, ChronoUnit.DAYS);
        for (String symbol : Symbols.UNIVERSE) {
            series4h.put(symbol, ohlcvRepository.findBySymbolAndTimeframeAndTsUtcBetweenOrderByTsUtc(
                    symbol, SIGNAL_TF, warmupStart, to));
            series1h.put(symbol, ohlcvRepository.findBySymbolAndTimeframeAndTsUtcBetweenOrderByTsUtc(
                    symbol, FILL_TF, from, to.plus(2, ChronoUnit.DAYS)));
            mlViews.put(symbol, mlView(symbol));
        }

        List<DayBars> days = new ArrayList<>();
        for (Instant t : dailyGrid(from, to)) {
            List<DecisionBar> bars = new ArrayList<>(Symbols.UNIVERSE.size());
            for (String symbol : Symbols.UNIVERSE) {
                List<Ohlcv> bars1h = series1h.get(symbol);
                double markClose = lastCloseAtOrBefore(bars1h, t);
                if (Double.isNaN(markClose)) {
                    continue;   // no 1h data for this coin yet at t
                }
                double[] fill = nextBarOhlc(bars1h, t, markClose);
                TAVerdict ta = taUpTo(symbol, series4h.get(symbol), t);
                MlView ml = mlViews.get(symbol);
                bars.add(new DecisionBar(symbol, ta, ml.predClass(), ml.probUp(), ml.confidence(),
                        fill[0], fill[1], fill[2], markClose));
            }
            if (!bars.isEmpty()) {
                days.add(new DayBars(t, bars));
            }
        }
        return days;
    }

    /** Midnight-UTC timestamps from {@code from} to {@code to}, inclusive. */
    static List<Instant> dailyGrid(Instant from, Instant to) {
        Instant start = from.truncatedTo(ChronoUnit.DAYS);
        List<Instant> grid = new ArrayList<>();
        for (Instant t = start; !t.isAfter(to); t = t.plus(1, ChronoUnit.DAYS)) {
            if (!t.isBefore(from)) {
                grid.add(t);
            }
        }
        return grid;
    }

    /** TA verdict from the 4h bars up to (and including) {@code t}; NEUTRAL/WEAK if too little history. */
    private TAVerdict taUpTo(String symbol, List<Ohlcv> bars4h, Instant t) {
        int hi = lastIndexAtOrBefore(bars4h, t);
        if (hi < 0) {
            return new TAVerdict(symbol, t, "NEUTRAL", "WEAK", List.of("No 4h history at " + t), 0.0);
        }
        int lo = Math.max(0, hi - TA_LOOKBACK + 1);
        BarSeries s = new BaseBarSeriesBuilder().withName(symbol).build();
        for (int i = lo; i <= hi; i++) {
            Ohlcv o = bars4h.get(i);
            s.addBar(o.getTsUtc().atZone(ZoneOffset.UTC),
                    s.numOf(nz(o.getOpen())), s.numOf(nz(o.getHigh())),
                    s.numOf(nz(o.getLow())), s.numOf(nz(o.getClose())), s.numOf(nz(o.getVolume())));
        }
        return TaVerdictEngine.compute(symbol, s);
    }

    private MlView mlView(String symbol) {
        Optional<Prediction> p = predictionRepository
                .findFirstBySymbolAndTimeframeOrderByTsUtcDesc(symbol, SIGNAL_TF);
        return p.map(pr -> new MlView(pr.getPredClass(), pr.getProbUp(), pr.confidence()))
                .orElse(new MlView(null, null, null));
    }

    /** Latest close at or before {@code t}; NaN if none. Bars are ascending by ts. */
    private static double lastCloseAtOrBefore(List<Ohlcv> ascending, Instant t) {
        int i = lastIndexAtOrBefore(ascending, t);
        return i < 0 ? Double.NaN : nz(ascending.get(i).getClose());
    }

    /** OHLC of the first bar strictly after {@code t}; falls back to {@code fallback} at the data edge. */
    private static double[] nextBarOhlc(List<Ohlcv> ascending, Instant t, double fallback) {
        int idx = firstIndexAfter(ascending, t);
        if (idx < 0) {
            return new double[]{fallback, fallback, fallback};
        }
        Ohlcv o = ascending.get(idx);
        return new double[]{nz(o.getOpen()), nz(o.getHigh()), nz(o.getLow())};
    }

    /** Binary search: greatest index with ts <= t, or -1. */
    private static int lastIndexAtOrBefore(List<Ohlcv> ascending, Instant t) {
        int lo = 0;
        int hi = ascending.size() - 1;
        int ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (!ascending.get(mid).getTsUtc().isAfter(t)) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    /** Binary search: smallest index with ts > t, or -1. */
    private static int firstIndexAfter(List<Ohlcv> ascending, Instant t) {
        int lo = 0;
        int hi = ascending.size() - 1;
        int ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (ascending.get(mid).getTsUtc().isAfter(t)) {
                ans = mid;
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        return ans;
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    private record MlView(String predClass, Double probUp, Double confidence) {
    }
}
