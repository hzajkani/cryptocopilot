package com.cryptocopilot.controller;

import com.cryptocopilot.analyst.AnalystResponse;
import com.cryptocopilot.analyst.AnalystService;
import com.cryptocopilot.rag.LlmProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Analyst: one fused, deterministic, explainable opinion per coin (ML + TA + fundamental +
 * news), each with a guarded summary, the scored inputs, citations, {@code healthSource} and a
 * persistent disclaimer (PROJECT.md Stage 5 §4). Decision-support, not financial advice.
 */
@RestController
@Tag(name = "Analyst", description = "Fused ML + TA + fundamental + news opinion per coin")
public class AnalystController {

    private final AnalystService analystService;

    public AnalystController(AnalystService analystService) {
        this.analystService = analystService;
    }

    @Operation(summary = "Analyst opinions for all 10 coins",
            description = "Deterministic direction + conviction + agreement, a guarded summary, the "
                    + "scored inputs, citations and healthSource for every coin in the universe. "
                    + "Missing-data coins still return NEUTRAL/LOW without failing. Optional "
                    + "'provider' (\"ollama\" default / \"openai\") selects the summary model.")
    @GetMapping("/api/analyst")
    public List<AnalystResponse> all(
            @Parameter(description = "Summary LLM: \"ollama\" (default) or \"openai\"", example = "ollama")
            @RequestParam(required = false) String provider) {
        return analystService.opinions(LlmProvider.fromLabel(provider));
    }

    @Operation(summary = "Analyst opinion for one coin",
            description = "The fused opinion with inputs, citations, healthSource and disclaimer. "
                    + "Optional 'provider' (\"ollama\" default / \"openai\") selects the summary model.")
    @GetMapping("/api/analyst/{symbol}")
    public AnalystResponse one(
            @Parameter(description = "Coin symbol, e.g. BTC", example = "BTC")
            @PathVariable String symbol,
            @Parameter(description = "Summary LLM: \"ollama\" (default) or \"openai\"", example = "ollama")
            @RequestParam(required = false) String provider) {
        return analystService.opinion(symbol, LlmProvider.fromLabel(provider));
    }
}
