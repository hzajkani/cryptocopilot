package com.cryptocopilot.rag;

import java.util.List;

/**
 * The result of {@code POST /api/chat}: the grounded answer (or an exact refusal phrase), the
 * sources it cited, the full set of retrieved chunks (for transparency/debugging), how long
 * generation took, how the query was classified by the rule-based {@link QueryClassifier}, and the
 * {@link LlmProvider} that actually answered ({@code "ollama"} / {@code "openai"} — the effective
 * provider, which may differ from the one requested if OpenAI was unavailable and we fell back).
 */
public record AnswerWithCitations(
        String answer,
        List<Citation> citations,
        List<RetrievedChunk> retrievedChunks,
        long latencyMs,
        String queryClassification,
        String provider) {
}
