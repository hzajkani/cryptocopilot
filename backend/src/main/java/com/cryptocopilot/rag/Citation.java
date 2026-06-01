package com.cryptocopilot.rag;

import java.time.Instant;

/**
 * A source actually cited by the answer (i.e. a {@code [number]} marker the model emitted, mapped
 * back to its {@link RetrievedChunk}). The frontend renders these as footnotes.
 */
public record Citation(
        int number,
        String sourceType,
        String symbol,
        String source,
        String url,
        Instant tsUtc,
        String snippet) {
}
