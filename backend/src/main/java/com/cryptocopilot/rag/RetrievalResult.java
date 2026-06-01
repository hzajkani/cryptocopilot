package com.cryptocopilot.rag;

import java.util.List;

/**
 * Output of {@link Retriever#retrieve}: the re-ranked, numbered chunks plus the query
 * classification ({@code kb}/{@code news}/{@code onchain}/{@code fundamental}/{@code all}) that
 * biased the {@code source_type} filter.
 */
public record RetrievalResult(List<RetrievedChunk> chunks, String classification) {
}
