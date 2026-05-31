package com.cryptocopilot.api;

import com.cryptocopilot.data.Prediction;
import com.cryptocopilot.data.PredictionDriverRepository;
import com.cryptocopilot.data.PredictionRepository;
import com.cryptocopilot.ta.TAVerdict;
import com.cryptocopilot.ta.TaVerdictService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the per-coin {@link SignalDto}: the latest ML forecast + its top-3 drivers, fused
 * with the independent {@link TAVerdict}. One row per coin in the universe.
 */
@Service
public class SignalService {

    private static final String TIMEFRAME = "4h";

    private final PredictionRepository predictionRepository;
    private final PredictionDriverRepository driverRepository;
    private final TaVerdictService taVerdictService;

    public SignalService(PredictionRepository predictionRepository,
                         PredictionDriverRepository driverRepository,
                         TaVerdictService taVerdictService) {
        this.predictionRepository = predictionRepository;
        this.driverRepository = driverRepository;
        this.taVerdictService = taVerdictService;
    }

    @Transactional(readOnly = true)
    public List<SignalDto> signals() {
        List<SignalDto> out = new ArrayList<>(Symbols.UNIVERSE.size());
        for (String symbol : Symbols.UNIVERSE) {
            out.add(signal(symbol));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public SignalDto signal(String symbol) {
        TAVerdict ta = taVerdictService.verdict(symbol);
        Optional<Prediction> latest =
                predictionRepository.findFirstBySymbolAndTimeframeOrderByTsUtcDesc(symbol, TIMEFRAME);
        if (latest.isEmpty()) {
            // Defensive: every coin has a prediction, but never drop a coin from the list.
            return new SignalDto(symbol, null, null, null, null, null, null, null, List.of(), ta);
        }
        Prediction p = latest.get();
        List<DriverDto> drivers = driverRepository
                .findBySymbolAndTimeframeAndTsUtcOrderByRank(symbol, TIMEFRAME, p.getTsUtc())
                .stream()
                .map(d -> new DriverDto(d.getRank(), d.getFeatureName(), d.getFeatureValue(), d.getShapValue()))
                .toList();
        // mlConfidence = calibrated prob of the stored label (never recomputed from probs).
        return new SignalDto(symbol, p.getTsUtc(), p.getPredClass(), p.confidence(),
                p.getProbUp(), p.getProbDown(), p.getProbFlat(), p.getModelVersion(), drivers, ta);
    }
}
