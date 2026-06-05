package com.cryptocopilot.rag;

import java.util.Locale;

/**
 * Which chat/generation LLM answers a request. The user picks this at runtime via the UI toggle
 * (default {@link #OLLAMA}); it is threaded through {@code /api/chat} and {@code /api/analyst}.
 *
 * <p><b>Chat/generation only.</b> This never changes the embedding model: retrieval stays on the
 * Ollama {@code nomic-embed-text} (768-dim) vectors already in the pgvector store, so toggling needs
 * no reindex and no column change. Only the model that <em>writes the answer/summary</em> switches.
 */
public enum LlmProvider {
    /** Local, free, default — Ollama {@code llama3.2:3b}. */
    OLLAMA,
    /** OpenAI {@code gpt-4o-mini} (needs {@code OPENAI_API_KEY}). */
    OPENAI;

    /** Lower-case wire label used in JSON ({@code "ollama"} / {@code "openai"}). */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parse a wire label leniently. {@code null}, blank or anything unrecognised falls back to
     * {@link #OLLAMA} — the safe, free default — so a bad value never selects the paid provider.
     */
    public static LlmProvider fromLabel(String label) {
        if (label == null) {
            return OLLAMA;
        }
        return "openai".equals(label.trim().toLowerCase(Locale.ROOT)) ? OPENAI : OLLAMA;
    }
}
