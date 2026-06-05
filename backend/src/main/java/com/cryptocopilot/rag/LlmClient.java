package com.cryptocopilot.rag;

/**
 * Minimal seam over the chat LLM: one system prompt + one user message in, the assistant text out.
 * Keeps {@link Generator} free of Spring AI types so its grounding/citation logic is unit-testable
 * with a trivial fake. The production implementation is {@link SpringAiLlmClient}.
 *
 * <p>The {@link LlmProvider}-aware overload lets a caller pick Ollama (default, free) or OpenAI at
 * runtime. The provider methods are {@code default} so existing single-provider fakes (which only
 * implement {@link #complete(String, String)}) keep working: their provider call collapses to the
 * default, and they report supporting Ollama only.
 */
public interface LlmClient {

    /** Complete on the default provider ({@link LlmProvider#OLLAMA}). */
    String complete(String systemPrompt, String userPrompt);

    /** Complete on a specific provider, falling back to the default behaviour if unspecialised. */
    default String complete(String systemPrompt, String userPrompt, LlmProvider provider) {
        return complete(systemPrompt, userPrompt);
    }

    /** Whether this client can actually serve {@code provider} (e.g. OpenAI needs a real key). */
    default boolean supports(LlmProvider provider) {
        return provider == LlmProvider.OLLAMA;
    }
}
