package com.cryptocopilot.rag;

/**
 * Keys for the metadata stored on every RAG {@code Document} in the pgvector store. All values
 * are stored as strings (clean JSONB filtering with {@code eq}/{@code in}); {@link #TS_UTC} is an
 * ISO-8601 instant string the retriever parses back for the recency re-rank.
 */
public final class RagMetadata {

    /** Always present: {@code news} / {@code onchain} / {@code fundamental} / {@code kb}. */
    public static final String SOURCE_TYPE = "source_type";
    /** Primary coin symbol for the chunk (e.g. {@code BTC}); absent for untagged news. */
    public static final String SYMBOL = "symbol";
    /** News only: the full CSV of tagged symbols (e.g. {@code "BTC,ETH"}); for display. */
    public static final String SYMBOLS = "symbols";
    /** News: feed (CoinDesk/…); onchain: {@code blockchain_com}; kb/fundamental: synthetic. */
    public static final String SOURCE = "source";
    /** News only: article URL. */
    public static final String URL = "url";
    /** News only: VADER sentiment label. */
    public static final String SENTIMENT = "sentiment";
    /** KB only: the {@code ##} section heading the chunk came from. */
    public static final String SECTION = "section";
    /** News/onchain/fundamental: ISO-8601 instant (week start for onchain). KB has none. */
    public static final String TS_UTC = "ts_utc";
    /** News: article title; for richer citation snippets. */
    public static final String TITLE = "title";

    private RagMetadata() {
    }
}
