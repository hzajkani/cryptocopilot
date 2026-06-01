package com.cryptocopilot.rag;

/**
 * Minimal seam over the chat LLM: one system prompt + one user message in, the assistant text out.
 * Keeps {@link Generator} free of Spring AI types so its grounding/citation logic is unit-testable
 * with a trivial fake. The production implementation is {@link SpringAiLlmClient}.
 */
public interface LlmClient {

    String complete(String systemPrompt, String userPrompt);
}
