package com.cryptocopilot.rag;

import java.util.List;

/**
 * Body of {@code POST /api/chat}. {@code symbols} is optional — when present it restricts
 * retrieval to chunks tagged with one of those coins (e.g. the user is viewing BTC).
 */
public record ChatRequest(String query, List<String> symbols) {
}
