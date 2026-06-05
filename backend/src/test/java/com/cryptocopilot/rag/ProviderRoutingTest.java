package com.cryptocopilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The Ollama/OpenAI toggle routing in {@link Generator} + the lenient {@link LlmProvider#fromLabel}
 * parse. A fake {@link LlmClient} records the provider it was asked for and can declare OpenAI
 * unavailable, so we can assert: the requested provider is threaded through and echoed back; an
 * unavailable OpenAI transparently falls back to Ollama; and the answer cache is keyed per-provider.
 */
class ProviderRoutingTest {

    /** Fake LLM: records the provider of the last call; can refuse to support OpenAI. */
    private static final class FakeLlm implements LlmClient {
        boolean openAiAvailable = true;
        LlmProvider lastProvider;
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            return complete(systemPrompt, userPrompt, LlmProvider.OLLAMA);
        }

        @Override
        public String complete(String systemPrompt, String userPrompt, LlmProvider provider) {
            calls.incrementAndGet();
            this.lastProvider = provider;
            return "Bitcoin uses Proof of Work [1].";
        }

        @Override
        public boolean supports(LlmProvider provider) {
            return provider != LlmProvider.OPENAI || openAiAvailable;
        }
    }

    private static RetrievalResult oneChunk() {
        return new RetrievalResult(List.of(new RetrievedChunk(1, "kb1",
                "Bitcoin uses Nakamoto consensus with Proof of Work.", "kb", "BTC", "src", null,
                Instant.parse("2026-05-30T00:00:00Z"), null, 0.9, 0.9)), "kb");
    }

    @Test
    void requestedProviderIsThreadedThroughAndEchoed() {
        FakeLlm llm = new FakeLlm();
        Generator generator = new Generator(llm);

        AnswerWithCitations a = generator.generate("How does Bitcoin work?", oneChunk(), LlmProvider.OPENAI);

        assertThat(llm.lastProvider).isEqualTo(LlmProvider.OPENAI);
        assertThat(a.provider()).isEqualTo("openai");
    }

    @Test
    void unavailableOpenAiFallsBackToOllama() {
        FakeLlm llm = new FakeLlm();
        llm.openAiAvailable = false;
        Generator generator = new Generator(llm);

        AnswerWithCitations a = generator.generate("How does Bitcoin work?", oneChunk(), LlmProvider.OPENAI);

        assertThat(llm.lastProvider).isEqualTo(LlmProvider.OLLAMA);   // routed to the free default
        assertThat(a.provider()).isEqualTo("ollama");                  // and reported truthfully
    }

    @Test
    void cacheIsKeyedPerProvider() {
        FakeLlm llm = new FakeLlm();
        Generator generator = new Generator(llm);
        RetrievalResult r = oneChunk();

        generator.generate("How does Bitcoin work?", r, LlmProvider.OLLAMA);
        generator.generate("How does Bitcoin work?", r, LlmProvider.OLLAMA);   // cache hit
        generator.generate("How does Bitcoin work?", r, LlmProvider.OPENAI);   // different provider → miss

        assertThat(llm.calls.get()).isEqualTo(2);
    }

    @Test
    void defaultGenerateUsesOllama() {
        FakeLlm llm = new FakeLlm();
        Generator generator = new Generator(llm);

        AnswerWithCitations a = generator.generate("How does Bitcoin work?", oneChunk());

        assertThat(a.provider()).isEqualTo("ollama");
    }

    @Test
    void fromLabelIsLenientAndDefaultsToOllama() {
        assertThat(LlmProvider.fromLabel("openai")).isEqualTo(LlmProvider.OPENAI);
        assertThat(LlmProvider.fromLabel("OpenAI")).isEqualTo(LlmProvider.OPENAI);
        assertThat(LlmProvider.fromLabel("  openai ")).isEqualTo(LlmProvider.OPENAI);
        assertThat(LlmProvider.fromLabel("ollama")).isEqualTo(LlmProvider.OLLAMA);
        assertThat(LlmProvider.fromLabel(null)).isEqualTo(LlmProvider.OLLAMA);
        assertThat(LlmProvider.fromLabel("")).isEqualTo(LlmProvider.OLLAMA);
        assertThat(LlmProvider.fromLabel("gpt-9")).isEqualTo(LlmProvider.OLLAMA);   // unknown → safe default
    }
}
