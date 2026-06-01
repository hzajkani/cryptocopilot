package com.cryptocopilot.controller;

import com.cryptocopilot.dto.TAVerdict;
import com.cryptocopilot.service.TaVerdictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The deterministic ta4j TA verdict for a single coin. */
@RestController
@Tag(name = "Technical analysis", description = "Deterministic Ichimoku-centric ta4j verdict")
public class TaController {

    private final TaVerdictService taVerdictService;

    public TaController(TaVerdictService taVerdictService) {
        this.taVerdictService = taVerdictService;
    }

    @Operation(summary = "TA verdict for a coin",
            description = "Ichimoku/RSI/MACD/Bollinger scored into direction + confidence + signals.")
    @GetMapping("/api/ta/{symbol}")
    public TAVerdict ta(@Parameter(description = "Coin symbol, e.g. BTC") @PathVariable String symbol) {
        return taVerdictService.verdict(symbol.toUpperCase());
    }
}
