package com.cryptocopilot.service;

import com.cryptocopilot.dto.CandleDto;
import com.cryptocopilot.dto.MarketDto;
import com.cryptocopilot.entity.MarketMeta;
import com.cryptocopilot.entity.Ohlcv;
import com.cryptocopilot.repository.MarketMetaRepository;
import com.cryptocopilot.repository.OhlcvRepository;
import com.cryptocopilot.util.Symbols;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Market-data read API: the markets overview and chart candles, from {@code ohlcv}/{@code market_meta}. */
@Service
public class MarketService {

    /** 24h ago on the 4h timeframe is 6 bars back; fetch 7 so index 0 is latest, index 6 is -24h. */
    private static final String OVERVIEW_TF = "4h";
    private static final int BARS_24H = 6;

    private final OhlcvRepository ohlcvRepository;
    private final MarketMetaRepository marketMetaRepository;

    public MarketService(OhlcvRepository ohlcvRepository, MarketMetaRepository marketMetaRepository) {
        this.ohlcvRepository = ohlcvRepository;
        this.marketMetaRepository = marketMetaRepository;
    }

    @Transactional(readOnly = true)
    public List<MarketDto> markets() {
        List<MarketDto> out = new ArrayList<>(Symbols.UNIVERSE.size());
        for (String symbol : Symbols.UNIVERSE) {
            List<Ohlcv> recent = ohlcvRepository.findBySymbolAndTimeframeOrderByTsUtcDesc(
                    symbol, OVERVIEW_TF, PageRequest.of(0, BARS_24H + 1));
            if (recent.isEmpty()) {
                continue;
            }
            Double price = recent.get(0).getClose();
            Double change24hPct = null;
            if (recent.size() > BARS_24H) {
                Double prior = recent.get(BARS_24H).getClose();
                if (price != null && prior != null && prior != 0.0) {
                    change24hPct = (price - prior) / prior * 100.0;
                }
            }
            Double marketCap = marketMetaRepository.findFirstBySymbolOrderByTsUtcDesc(symbol)
                    .map(MarketMeta::getMarketCapUsd)
                    .orElse(null);
            out.add(new MarketDto(symbol, price, change24hPct, marketCap));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<CandleDto> candles(String symbol, String timeframe, Instant from, Instant to) {
        return ohlcvRepository
                .findBySymbolAndTimeframeAndTsUtcBetweenOrderByTsUtc(symbol, timeframe, from, to)
                .stream()
                .map(o -> new CandleDto(o.getTsUtc(), o.getOpen(), o.getHigh(),
                        o.getLow(), o.getClose(), o.getVolume()))
                .toList();
    }
}
