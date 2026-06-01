package com.cryptocopilot.rag;

/**
 * The rule-based classification of a chat query. Each class biases retrieval toward a
 * {@code source_type} (or none, for {@link #ALL}).
 */
public enum QueryClass {
    KB(SourceType.KB),
    NEWS(SourceType.NEWS),
    ONCHAIN(SourceType.ONCHAIN),
    FUNDAMENTAL(SourceType.FUNDAMENTAL),
    /** No bias — search the whole corpus. */
    ALL(null);

    private final SourceType sourceType;

    QueryClass(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    /** The {@code source_type} filter this class implies, or {@code null} to search everything. */
    public SourceType sourceType() {
        return sourceType;
    }

    /** Lowercase label surfaced in the API response (e.g. {@code "kb"}, {@code "all"}). */
    public String label() {
        return name().toLowerCase();
    }
}
