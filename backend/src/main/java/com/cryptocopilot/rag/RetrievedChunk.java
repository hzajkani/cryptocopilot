package com.cryptocopilot.rag;

import java.time.Instant;

/**
 * One chunk returned by the {@link Retriever}, numbered for citation as {@code [number]} in the
 * generated answer. {@code similarity} is the raw cosine score from pgvector; {@code score} is the
 * final value after the news/onchain recency re-rank (equal to {@code similarity} for kb/fundamental).
 */
public record RetrievedChunk(
        int number,
        String id,
        String content,
        String sourceType,
        String symbol,
        String source,
        String url,
        Instant tsUtc,
        String section,
        double similarity,
        double score) {
}
