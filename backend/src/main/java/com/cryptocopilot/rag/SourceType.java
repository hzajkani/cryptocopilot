package com.cryptocopilot.rag;

/**
 * The four kinds of chunk in the RAG corpus. The {@link #value()} is what is stored in the
 * {@code source_type} metadata of every Spring AI {@code Document} and used in retrieval filters.
 */
public enum SourceType {
    NEWS("news"),
    ONCHAIN("onchain"),
    FUNDAMENTAL("fundamental"),
    KB("kb");

    private final String value;

    SourceType(String value) {
        this.value = value;
    }

    /** The lowercase metadata token (e.g. {@code "news"}). */
    public String value() {
        return value;
    }

    /** Parse a metadata token back to the enum, or {@code null} if unknown. */
    public static SourceType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SourceType s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
