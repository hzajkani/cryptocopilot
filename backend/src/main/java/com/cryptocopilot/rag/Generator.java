package com.cryptocopilot.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Generates the grounded answer from retrieved chunks (PROJECT.md Stage 4 §5) using the
 * {@link LlmClient}. Strictly grounded and advice-free, enforced by deterministic guards around
 * the model call so the exact refusal phrases never depend on the LLM cooperating:
 *
 * <ol>
 *   <li>a trading-advice question is refused before any LLM call;</li>
 *   <li>empty retrieval is refused before any LLM call;</li>
 *   <li>after generation, an answer with no valid {@code [N]} citation is treated as ungrounded
 *       and replaced with the exact "sources do not answer" refusal.</li>
 * </ol>
 *
 * Responses are cached in-memory keyed by {@code (query, retrievedChunkIds)}.
 */
@Component
public class Generator {

    static final String REFUSAL_NO_CONTEXT = "The available sources do not answer this question.";
    static final String REFUSAL_ADVICE =
            "I can summarise what sources are saying, but I cannot give trading advice.";

    /** Verbatim intent from the Stage 4 prompt §5. */
    static final String SYSTEM_PROMPT = """
            You are a precise crypto market research assistant. Answer ONLY from the provided context.
            - Every factual claim must end with a citation [N] (the chunk number).
            - If the context does not answer the question, reply EXACTLY: "The available sources do not answer this question." Do not guess.
            - Be concise — 5 sentences max unless asked for detail.
            - If asked for trading advice, refuse EXACTLY: "I can summarise what sources are saying, but I cannot give trading advice."
            - Distinguish news sentiment (what people say) from on-chain signal (what they do).
            - Prefer KB chunks for mechanism questions, recent news for "what's happening", on-chain/fundamental chunks for fundamentals.""";

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)\\]");
    private static final int SNIPPET_LEN = 160;

    /** Phrases that mark a request for a buy/sell/hold/timing recommendation. */
    private static final List<String> ADVICE_CUES = List.of(
            "should i buy", "should i sell", "should i invest", "should i hold", "should i get into",
            "should i put", "is it a good time to buy", "is it a good time to sell", "good time to buy",
            "good time to sell", "worth buying", "worth selling", "good investment", "is it a buy",
            "is it a sell", "buy or sell", "do you recommend", "what should i do", "should i ape");

    private final LlmClient llm;
    private final Map<String, AnswerWithCitations> cache = new ConcurrentHashMap<>();

    public Generator(LlmClient llm) {
        this.llm = llm;
    }

    public AnswerWithCitations generate(String query, RetrievalResult retrieval) {
        long start = System.nanoTime();
        List<RetrievedChunk> chunks = retrieval.chunks();

        if (isAdviceRequest(query)) {
            return new AnswerWithCitations(REFUSAL_ADVICE, List.of(), chunks,
                    elapsedMs(start), retrieval.classification());
        }
        if (chunks.isEmpty()) {
            return new AnswerWithCitations(REFUSAL_NO_CONTEXT, List.of(), chunks,
                    elapsedMs(start), retrieval.classification());
        }

        String key = cacheKey(query, chunks);
        AnswerWithCitations cached = cache.get(key);
        if (cached != null) {
            return new AnswerWithCitations(cached.answer(), cached.citations(),
                    cached.retrievedChunks(), elapsedMs(start), cached.queryClassification());
        }

        String raw = llm.complete(SYSTEM_PROMPT, buildUserMessage(query, chunks)).trim();
        List<Citation> citations = extractCitations(raw, chunks);
        // Grounding guard: an answer with no verifiable citation is not grounded — refuse cleanly.
        String answer = citations.isEmpty() ? REFUSAL_NO_CONTEXT : raw;

        AnswerWithCitations result = new AnswerWithCitations(answer, citations, chunks,
                elapsedMs(start), retrieval.classification());
        cache.put(key, result);
        return result;
    }

    static boolean isAdviceRequest(String query) {
        if (query == null) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        for (String cue : ADVICE_CUES) {
            if (q.contains(cue)) {
                return true;
            }
        }
        return false;
    }

    static String buildUserMessage(String query, List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Question: ").append(query).append("\n\nContext:\n");
        for (RetrievedChunk c : chunks) {
            sb.append('[').append(c.number()).append("] (").append(c.sourceType());
            if (c.symbol() != null) {
                sb.append(", ").append(c.symbol());
            }
            if (c.section() != null) {
                sb.append(", ").append(c.section());
            }
            if (c.source() != null) {
                sb.append(", ").append(c.source());
            }
            if (c.tsUtc() != null) {
                sb.append(", ").append(c.tsUtc());
            }
            sb.append(")\n").append(c.content()).append("\n\n");
        }
        sb.append("Answer the question using ONLY the context above, citing chunks as [N].");
        return sb.toString();
    }

    private static List<Citation> extractCitations(String answer, List<RetrievedChunk> chunks) {
        Map<Integer, Citation> byNumber = new LinkedHashMap<>();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n < 1 || n > chunks.size() || byNumber.containsKey(n)) {
                continue;
            }
            RetrievedChunk c = chunks.get(n - 1);
            byNumber.put(n, new Citation(n, c.sourceType(), c.symbol(), c.source(),
                    c.url(), c.tsUtc(), snippet(c.content())));
        }
        return new ArrayList<>(byNumber.values());
    }

    private static String snippet(String content) {
        String oneLine = content.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= SNIPPET_LEN ? oneLine : oneLine.substring(0, SNIPPET_LEN) + "…";
    }

    private static String cacheKey(String query, List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder(query.trim().toLowerCase(Locale.ROOT)).append('|');
        for (RetrievedChunk c : chunks) {
            sb.append(c.id()).append(',');
        }
        return sb.toString();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
