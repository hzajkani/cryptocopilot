package com.cryptocopilot.api;

import com.cryptocopilot.ta.TAVerdict;
import com.cryptocopilot.ta.TaVerdictService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The deterministic ta4j TA verdict for a single coin. */
@RestController
public class TaController {

    private final TaVerdictService taVerdictService;

    public TaController(TaVerdictService taVerdictService) {
        this.taVerdictService = taVerdictService;
    }

    @GetMapping("/api/ta/{symbol}")
    public TAVerdict ta(@PathVariable String symbol) {
        return taVerdictService.verdict(symbol.toUpperCase());
    }
}
