package com.cryptocopilot.analyst;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptocopilot.dto.TAVerdict;
import com.cryptocopilot.rag.LlmClient;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests the Analyst's hallucination guard (PROJECT.md Stage 5 §7): a summary with a number that is
 * not in the input objects fails the guard and falls back to the deterministic template; an LLM
 * error does the same; a grounded summary is returned verbatim. Uses a fake {@link LlmClient} — no
 * Spring, no model.
 */
class HallucinationGuardTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    /** Fake LLM returning a fixed reply, or throwing if asked. */
    private static final class FakeLlm implements LlmClient {
        String reply = "";
        boolean explode = false;

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            if (explode) {
                throw new RuntimeException("model offline");
            }
            return reply;
        }
    }

    private static AnalystSummarizer.Facts facts() {
        List<InputScore> inputs = List.of(
                new InputScore("ml", 0, "ML FLAT → 0"),
                new InputScore("ta", 0, "TA NEUTRAL → 0"),
                new InputScore("fundamental", 0, "Fundamentals STABLE → 0"),
                new InputScore("news", 0, "News INSUFFICIENT_DATA → 0"));
        FundamentalSnapshot fundamental = new FundamentalSnapshot("BTC", NOW, "STABLE", "coingecko",
                List.of("momentum 0: 7d price -7.6%", "dev +1: 108 GitHub commits/4w",
                        "vol/cap 0: market cap 24h -2.7%"),
                38.2, "FALLING", "INSUFFICIENT_DATA", 0.0);
        TAVerdict ta = new TAVerdict("BTC", NOW, "NEUTRAL", "WEAK", List.of(), 0.0);
        return new AnalystSummarizer.Facts("BTC", "NEUTRAL", "LOW", 1.0, 0, inputs, fundamental,
                "FLAT", 0.49, ta, 0.0);
    }

    @Test
    void summaryWithUnknownNumberFailsGuardAndFallsBack() {
        FakeLlm llm = new FakeLlm();
        llm.reply = "BTC will rally 42% this quarter on strong demand.";   // 42 is not in the inputs
        AnalystSummarizer summarizer = new AnalystSummarizer(llm);

        AnalystSummarizer.Facts facts = facts();
        String summary = summarizer.summarize(facts);

        assertThat(summary).isEqualTo(AnalystSummarizer.template(facts));
        assertThat(summary).doesNotContain("42");
    }

    @Test
    void groundedSummaryIsReturnedVerbatim() {
        FakeLlm llm = new FakeLlm();
        // Only numbers are the agreement (1.0) and ML confidence (0.49) — both present in the inputs.
        llm.reply = "BTC looks balanced across signals; input agreement is 1.0 and ML confidence sits at 0.49.";
        AnalystSummarizer summarizer = new AnalystSummarizer(llm);

        String summary = summarizer.summarize(facts());

        assertThat(summary).isEqualTo(llm.reply);
    }

    @Test
    void llmErrorFallsBackToTemplate() {
        FakeLlm llm = new FakeLlm();
        llm.explode = true;
        AnalystSummarizer summarizer = new AnalystSummarizer(llm);

        AnalystSummarizer.Facts facts = facts();
        assertThat(summarizer.summarize(facts)).isEqualTo(AnalystSummarizer.template(facts));
    }

    @Test
    void isGroundedDetectsUnsupportedNumbers() {
        assertThat(AnalystSummarizer.isGrounded("up about 5% today", List.of(5.0))).isTrue();
        assertThat(AnalystSummarizer.isGrounded("up about 5% today", List.of(2.0))).isFalse();
        assertThat(AnalystSummarizer.isGrounded("no figures at all here", List.of())).isTrue();
    }
}
