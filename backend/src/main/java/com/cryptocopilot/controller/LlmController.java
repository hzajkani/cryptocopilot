package com.cryptocopilot.controller;

import com.cryptocopilot.rag.LlmClient;
import com.cryptocopilot.rag.LlmProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the UI which chat/generation providers are available so the sidebar toggle can render
 * correctly — Ollama is always on (free, local); OpenAI is on only when {@code OPENAI_API_KEY} is
 * configured (see {@code LlmConfig}). The default is always Ollama.
 */
@RestController
@Tag(name = "LLM", description = "Available chat/generation providers for the Ollama/OpenAI toggle")
public class LlmController {

    private final LlmClient llm;

    public LlmController(LlmClient llm) {
        this.llm = llm;
    }

    @Operation(summary = "Available LLM providers",
            description = "{ default, ollama, openai } — whether each provider can answer right now. "
                    + "openai is false unless OPENAI_API_KEY is set, so the UI disables that side.")
    @GetMapping("/api/llm/providers")
    public Map<String, Object> providers() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("default", LlmProvider.OLLAMA.label());
        out.put("ollama", llm.supports(LlmProvider.OLLAMA));
        out.put("openai", llm.supports(LlmProvider.OPENAI));
        return out;
    }
}
