package com.cryptocopilot.rag;

import java.util.List;

/**
 * Body of {@code POST /api/chat}. {@code symbols} is optional — when present it restricts
 * retrieval to chunks tagged with one of those coins (e.g. the user is viewing BTC).
 * {@code provider} is the optional LLM toggle ({@code "ollama"} default / {@code "openai"}); an
 * absent or unknown value resolves to Ollama (see {@link LlmProvider#fromLabel}).
 */
public record ChatRequest(String query, List<String> symbols, String provider) {
}
