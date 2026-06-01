package com.cryptocopilot.service;

import com.cryptocopilot.dto.TAVerdict;
import com.cryptocopilot.entity.Ohlcv;
import com.cryptocopilot.repository.OhlcvRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

/**
 * Loads a coin's recent 4h candles and runs {@link TaVerdictEngine}. The TA verdict is
 * computed from raw {@code ohlcv} only (PROJECT.md §3) — never from Python features.
 */
@Service
public class TaVerdictService {

    /** TA runs on the 4h timeframe (the signal timeframe the predictions also use). */
    static final String TIMEFRAME = "4h";
    /** Enough bars to warm up Ichimoku (52 + 26) plus MACD/RSI headroom; ~50 days of 4h. */
    static final int LOOKBACK_BARS = 300;

    private final OhlcvRepository ohlcvRepository;

    public TaVerdictService(OhlcvRepository ohlcvRepository) {
        this.ohlcvRepository = ohlcvRepository;
    }

    @Transactional(readOnly = true)
    public TAVerdict verdict(String symbol) {
        List<Ohlcv> descending = ohlcvRepository.findBySymbolAndTimeframeOrderByTsUtcDesc(
                symbol, TIMEFRAME, PageRequest.of(0, LOOKBACK_BARS));
        if (descending.isEmpty()) {
            return new TAVerdict(symbol, Instant.EPOCH, "NEUTRAL", "WEAK",
                    List.of("No OHLCV data for " + symbol), 0.0);
        }
        List<Ohlcv> ascending = new ArrayList<>(descending);
        Collections.reverse(ascending);
        return TaVerdictEngine.compute(symbol, toSeries(symbol, ascending));
    }

    /** Build a ta4j {@link BarSeries} from time-ascending candles. */
    static BarSeries toSeries(String symbol, List<Ohlcv> ascending) {
        BarSeries series = new BaseBarSeriesBuilder().withName(symbol).build();
        for (Ohlcv o : ascending) {
            ZonedDateTime end = o.getTsUtc().atZone(ZoneOffset.UTC);
            series.addBar(end,
                    series.numOf(nz(o.getOpen())),
                    series.numOf(nz(o.getHigh())),
                    series.numOf(nz(o.getLow())),
                    series.numOf(nz(o.getClose())),
                    series.numOf(nz(o.getVolume())));
        }
        return series;
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }
}
