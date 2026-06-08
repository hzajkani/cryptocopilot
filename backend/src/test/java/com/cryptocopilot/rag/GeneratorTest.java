package com.cryptocopilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Unit test of the {@link Generator}'s grounding/citation/refusal logic with a fake {@link LlmClient}.
 * No OpenAI, no Spring. The no-context refusal, the no-citation guard, and grounded signal-based
 * answers must hold deterministically.
 */
class GeneratorTest {

    /** Fake LLM: returns a canned reply and counts calls; can be set to fail if called. */
    private static final class FakeLlm implements LlmClient {
        String reply = "";
        boolean explodeIfCalled = false;
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            calls.incrementAndGet();
            if (explodeIfCalled) {
                throw new AssertionError("LLM must not be called for this case");
            }
            return reply;
        }
    }

    private static RetrievedChunk chunk(int n, String id, String content, String sourceType, String symbol) {
        return new RetrievedChunk(n, id, content, sourceType, symbol, "src", null,
                Instant.parse("2026-05-30T00:00:00Z"), null, 0.9, 0.9);
    }

    private static RetrievalResult withChunks(RetrievedChunk... chunks) {
        return new RetrievalResult(List.of(chunks), "all");
    }

    @Test
    void tradingAdviceIsAnsweredFromContextWithCitations() {
        FakeLlm llm = new FakeLlm();
        llm.reply = "On-chain flows and recent news lean cautiously bullish for ETH [1].\n"
                + "This is educational decision-support for paper trading — not financial advice.";
        Generator generator = new Generator(llm);

        AnswerWithCitations a = generator.generate("Should I buy ETH now?",
                withChunks(chunk(1, "k1", "ETH staking inflows hit a 3-month high.", "onchain", "ETH")));

        assertThat(a.answer()).isEqualTo(llm.reply);
        assertThat(a.citations()).extracting(Citation::number).containsExactly(1);
        assertThat(llm.calls.get()).isEqualTo(1);
    }

    @Test
    void emptyRetrievalRefusedWithoutCallingLlm() {
        FakeLlm llm = new FakeLlm();
        llm.explodeIfCalled = true;
        Generator generator = new Generator(llm);

        AnswerWithCitations a = generator.generate("What will BTC be worth in 2030?",
                new RetrievalResult(List.of(), "all"));

        assertThat(a.answer()).isEqualTo(Generator.REFUSAL_NO_CONTEXT);
        assertThat(a.citations()).isEmpty();
        assertThat(llm.calls.get()).isZero();
    }

    @Test
    void groundedAnswerKeepsCitedChunks() {
        FakeLlm llm = new FakeLlm();
        llm.reply = "Bitcoin uses Proof of Work [1]. Its supply is capped at 21 million [2].";
        Generator generator = new Generator(llm);

        AnswerWithCitations a = generator.generate("How does Bitcoin work and what is its supply?",
                withChunks(
                        chunk(1, "kb1", "Bitcoin uses Nakamoto consensus with Proof of Work.", "kb", "BTC"),
                        chunk(2, "kb2", "Bitcoin has a fixed maximum supply of 21 million.", "kb", "BTC")));

        assertThat(a.answer()).isEqualTo(llm.reply);
        assertThat(a.citations()).extracting(Citation::number).containsExactly(1, 2);
        assertThat(a.citations().get(0).symbol()).isEqualTo("BTC");
        assertThat(a.retrievedChunks()).hasSize(2);
    }

    @Test
    void answerWithoutAnyCitationIsTreatedAsUngroundedAndRefused() {
        FakeLlm llm = new FakeLlm();
        llm.reply = "Bitcoin is a great long-term store of value.";   // no [N]
        Generator generator = new Generator(llm);

        AnswerWithCitations a = generator.generate("Tell me about Bitcoin.",
                withChunks(chunk(1, "kb1", "Bitcoin uses Proof of Work.", "kb", "BTC")));

        assertThat(a.answer()).isEqualTo(Generator.REFUSAL_NO_CONTEXT);
        assertThat(a.citations()).isEmpty();
    }

    @Test
    void outOfRangeCitationIsIgnoredThenRefused() {
        FakeLlm llm = new FakeLlm();
        llm.reply = "According to the data [9], it is bullish.";   // [9] not a real chunk
        Generator generator = new Generator(llm);

        AnswerWithCitations a = generator.generate("Anything?",
                withChunks(chunk(1, "kb1", "Some content.", "kb", "BTC")));

        assertThat(a.answer()).isEqualTo(Generator.REFUSAL_NO_CONTEXT);
        assertThat(a.citations()).isEmpty();
    }

    @Test
    void identicalQueryAndChunksAreCached() {
        FakeLlm llm = new FakeLlm();
        llm.reply = "Solana uses Proof of History [1].";
        Generator generator = new Generator(llm);
        RetrievalResult r = withChunks(chunk(1, "kb1", "Solana uses Proof of History.", "kb", "SOL"));

        AnswerWithCitations first = generator.generate("How does Solana order events?", r);
        AnswerWithCitations second = generator.generate("How does Solana order events?", r);

        assertThat(first.answer()).isEqualTo(second.answer());
        assertThat(llm.calls.get()).isEqualTo(1);   // second served from cache
    }
}
