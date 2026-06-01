package com.cryptocopilot.rag;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Rule-based query classifier (PROJECT.md Stage 4 §4). Maps a free-text question to a
 * {@link QueryClass} that biases the {@code source_type} retrieval filter. Deterministic and
 * cheap — no LLM call.
 *
 * <p>Priority order is most-specific-first so that a domain term wins over a generic phrase:
 * <b>onchain → fundamental → news → kb → all</b>. For example "what is Bitcoin's <i>recent</i>
 * <i>on-chain</i> <i>transaction</i> activity" classifies as ONCHAIN (not NEWS via "recent" nor
 * KB via "what is"), and "what is the current <i>sentiment</i>" classifies as NEWS (not KB).
 *
 * <p>Deviation from the prompt's literal cue list: "supply" is routed to KB, not
 * fundamental/onchain — supply schedules live only in the Knowledge Base (the {@code fundamentals}
 * table has no supply field), so KB is the only source that can answer them.
 */
@Component
public class QueryClassifier {

    // Multi-word / hyphenated cues — matched as substrings of the lowercased query.
    private static final List<String> ONCHAIN_PHRASES = List.of(
            "on-chain", "on chain", "active addresses", "unique addresses", "transfer volume",
            "network activity", "transaction count", "transaction volume");
    private static final List<String> FUNDAMENTAL_PHRASES = List.of(
            "pull request", "trading volume", "30 day", "7 day", "24 hour", "past 30", "past 7",
            "past month", "code additions", "developer activity", "social metrics");
    private static final List<String> NEWS_PHRASES = List.of(
            "this week", "right now", "why is", "why did", "why are", "selling pressure",
            "sell-off", "price action", "what's happening", "this month", "in the news");
    private static final List<String> KB_PHRASES = List.of(
            "how does", "how do", "what is", "what are", "proof of", "use case", "maximum supply",
            "max supply", "total supply", "supply schedule", "supply cap", "designed to",
            "how it works");

    // Single-word cues — matched against the tokenized query (so "sec" ≠ "second", "work" ≠ "network").
    private static final Set<String> ONCHAIN_TOKENS = Set.of(
            "onchain", "transactions", "transaction", "addresses", "address", "hashrate");
    private static final Set<String> FUNDAMENTAL_TOKENS = Set.of(
            "github", "commit", "commits", "developer", "developers", "reddit", "followers",
            "performed", "performance");
    private static final Set<String> NEWS_TOKENS = Set.of(
            "news", "today", "latest", "recent", "recently", "sentiment", "exploit", "exploits",
            "hack", "hacks", "hacked", "seizure", "seized", "regulatory", "regulation", "sec",
            "rally", "crash", "surge", "plunge", "headlines", "announcement", "announced");
    private static final Set<String> KB_TOKENS = Set.of(
            "consensus", "mechanism", "staking", "stake", "validator", "validators", "halving",
            "tokenomics", "governance", "parachain", "parachains", "architecture", "works", "work",
            "supply", "inflation", "decentralized", "decentralization", "whitepaper", "risks",
            "risk", "mint", "burn");

    public QueryClass classify(String query) {
        if (query == null || query.isBlank()) {
            return QueryClass.ALL;
        }
        String q = query.toLowerCase();
        Set<String> tokens = new HashSet<>(Arrays.asList(q.split("[^a-z0-9]+")));

        if (matches(q, tokens, ONCHAIN_PHRASES, ONCHAIN_TOKENS)) {
            return QueryClass.ONCHAIN;
        }
        if (matches(q, tokens, FUNDAMENTAL_PHRASES, FUNDAMENTAL_TOKENS)) {
            return QueryClass.FUNDAMENTAL;
        }
        if (matches(q, tokens, NEWS_PHRASES, NEWS_TOKENS)) {
            return QueryClass.NEWS;
        }
        if (matches(q, tokens, KB_PHRASES, KB_TOKENS)) {
            return QueryClass.KB;
        }
        return QueryClass.ALL;
    }

    private static boolean matches(String q, Set<String> tokens, List<String> phrases, Set<String> words) {
        for (String phrase : phrases) {
            if (q.contains(phrase)) {
                return true;
            }
        }
        for (String word : words) {
            if (tokens.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
