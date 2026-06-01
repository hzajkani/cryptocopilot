package com.cryptocopilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Pure unit test of the rule-based {@link QueryClassifier} (no Spring, no LLM). */
class QueryClassifierTest {

    private final QueryClassifier classifier = new QueryClassifier();

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
            // mechanism / KB
            "'How does Solana achieve consensus?', KB",
            "'What consensus mechanism does Bitcoin use?', KB",
            "'How does Ethereum''s proof-of-stake work?', KB",
            "'What is Bitcoin''s maximum supply?', KB",
            "'Explain Cardano''s Ouroboros staking model.', KB",
            "'What is the use case for Chainlink?', KB",
            "'How does Polkadot connect parachains?', KB",
            "'What are the key risks of Avalanche?', KB",
            // news (recency / sentiment / events) — beats KB's generic 'what is'
            "'Why is Bitcoin under selling pressure right now?', NEWS",
            "'What is the current sentiment around Bitcoin?', NEWS",
            "'What are people saying about XRP this week?', NEWS",
            "'What recent SEC enforcement news is there in crypto?', NEWS",
            "'Were there any exploits or bridge hacks reported recently?', NEWS",
            "'What''s the latest news involving Trump and crypto legislation?', NEWS",
            // fundamental
            "'How many GitHub commits has Solana had in the last 4 weeks?', FUNDAMENTAL",
            "'How has BNB performed over the past 30 days?', FUNDAMENTAL",
            "'Which coin shows the most developer activity by commits?', FUNDAMENTAL",
            // onchain — beats NEWS's 'recent' and KB's 'what is'
            "'What is Bitcoin''s recent on-chain transaction activity?', ONCHAIN",
            // no cue -> all
            "'What will BTC be worth in 2030?', ALL",
            "'Tell me about crypto.', ALL",
    })
    void classifiesAsExpected(String query, QueryClass expected) {
        assertThat(classifier.classify(query)).isEqualTo(expected);
    }

    @Test
    void blankQueryIsAll() {
        assertThat(classifier.classify("")).isEqualTo(QueryClass.ALL);
        assertThat(classifier.classify(null)).isEqualTo(QueryClass.ALL);
    }

    @Test
    void classToSourceTypeMapping() {
        assertThat(QueryClass.KB.sourceType()).isEqualTo(SourceType.KB);
        assertThat(QueryClass.NEWS.sourceType()).isEqualTo(SourceType.NEWS);
        assertThat(QueryClass.ONCHAIN.sourceType()).isEqualTo(SourceType.ONCHAIN);
        assertThat(QueryClass.FUNDAMENTAL.sourceType()).isEqualTo(SourceType.FUNDAMENTAL);
        assertThat(QueryClass.ALL.sourceType()).isNull();
        assertThat(QueryClass.ALL.label()).isEqualTo("all");
    }
}
