package com.cryptocopilot.rag;

import com.cryptocopilot.entity.Fundamentals;
import com.cryptocopilot.entity.News;
import com.cryptocopilot.entity.Onchain;
import com.cryptocopilot.repository.FundamentalsRepository;
import com.cryptocopilot.repository.NewsRepository;
import com.cryptocopilot.repository.OnchainRepository;
import com.cryptocopilot.util.Symbols;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds the RAG corpus from four source types and (re)loads it into the Spring AI
 * {@link VectorStore} (pgvector). Idempotent: every {@code Document} gets a deterministic UUID
 * derived from a stable key, and {@link #reindex()} first clears all chunks it owns (by
 * {@code source_type} filter) before re-adding — so it is safe to run repeatedly and must NOT run
 * on boot (it is triggered by {@code POST /api/rag/reindex}).
 *
 * <p>Sources (PROJECT.md Stage 4 §3): one chunk per {@code news} row; a weekly synthesis per
 * {@code onchain} {@code (symbol, ISO-week)}; one fundamental synthesis per coin from its latest
 * snapshot; and the curated Knowledge Base split by {@code ##} section.
 */
@Component
public class CorpusIndexer {

    private static final Logger log = LoggerFactory.getLogger(CorpusIndexer.class);
    private static final String KB_GLOB = "classpath:kb/*.md";

    private final VectorStore vectorStore;
    private final NewsRepository newsRepository;
    private final OnchainRepository onchainRepository;
    private final FundamentalsRepository fundamentalsRepository;
    private final PathMatchingResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();

    public CorpusIndexer(VectorStore vectorStore,
                         NewsRepository newsRepository,
                         OnchainRepository onchainRepository,
                         FundamentalsRepository fundamentalsRepository) {
        this.vectorStore = vectorStore;
        this.newsRepository = newsRepository;
        this.onchainRepository = onchainRepository;
        this.fundamentalsRepository = fundamentalsRepository;
    }

    /** Clear-and-rebuild the whole corpus. Returns chunk counts per {@code source_type}. */
    public synchronized Map<String, Integer> reindex() {
        log.info("RAG reindex: building corpus…");
        List<Document> docs = new ArrayList<>();
        docs.addAll(newsDocuments());
        docs.addAll(onchainDocuments());
        docs.addAll(fundamentalDocuments());
        docs.addAll(knowledgeBaseDocuments());

        // Drop everything we own, then add the freshly built set (deterministic ids also upsert).
        vectorStore.delete(new FilterExpressionBuilder()
                .in(RagMetadata.SOURCE_TYPE,
                        SourceType.NEWS.value(), SourceType.ONCHAIN.value(),
                        SourceType.FUNDAMENTAL.value(), SourceType.KB.value())
                .build());
        if (!docs.isEmpty()) {
            vectorStore.add(docs);
        }

        Map<String, Integer> counts = countBySourceType(docs);
        log.info("RAG reindex complete: {} chunks total — news={}, onchain={}, fundamental={}, kb={}",
                docs.size(), counts.get(SourceType.NEWS.value()), counts.get(SourceType.ONCHAIN.value()),
                counts.get(SourceType.FUNDAMENTAL.value()), counts.get(SourceType.KB.value()));
        return counts;
    }

    // ----------------------------------------------------------------- news

    private List<Document> newsDocuments() {
        List<Document> out = new ArrayList<>();
        for (News n : newsRepository.findAllByOrderByTsUtcDesc()) {
            String title = n.getTitle() == null ? "" : n.getTitle().trim();
            String summary = n.getSummary() == null ? "" : n.getSummary().trim();
            String content = (title + "\n" + summary).trim();
            if (content.isBlank()) {
                continue;
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(RagMetadata.SOURCE_TYPE, SourceType.NEWS.value());
            putIfText(meta, RagMetadata.TITLE, title);
            putIfText(meta, RagMetadata.SOURCE, n.getSource());
            putIfText(meta, RagMetadata.URL, n.getUrl());
            putIfText(meta, RagMetadata.SENTIMENT, n.getSentiment());
            if (n.getTsUtc() != null) {
                meta.put(RagMetadata.TS_UTC, n.getTsUtc().toString());
            }
            String currencies = n.getCurrencies();
            if (StringUtils.hasText(currencies)) {
                meta.put(RagMetadata.SYMBOLS, currencies);
                String primary = currencies.split(",")[0].trim();
                putIfText(meta, RagMetadata.SYMBOL, primary);
            }
            out.add(document("news:" + n.getId(), content, meta));
        }
        return out;
    }

    // -------------------------------------------------------------- onchain

    private List<Document> onchainDocuments() {
        List<Document> out = new ArrayList<>();
        for (String symbol : onchainRepository.findDistinctSymbols()) {
            // (weekStart -> (metric -> [sum, count])) for a per-week mean, ascending by week.
            Map<LocalDate, Map<String, double[]>> byWeek = new TreeMap<>();
            String source = "blockchain_com";
            for (Onchain row : onchainRepository.findBySymbolOrderByTsUtc(symbol)) {
                if (row.getTsUtc() == null || row.getValue() == null) {
                    continue;
                }
                if (StringUtils.hasText(row.getSource())) {
                    source = row.getSource();
                }
                LocalDate week = row.getTsUtc().atZone(ZoneOffset.UTC).toLocalDate()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                double[] acc = byWeek
                        .computeIfAbsent(week, w -> new java.util.HashMap<>())
                        .computeIfAbsent(row.getMetric(), m -> new double[2]);
                acc[0] += row.getValue();
                acc[1] += 1;
            }
            for (Map.Entry<LocalDate, Map<String, double[]>> e : byWeek.entrySet()) {
                LocalDate week = e.getKey();
                Map<String, double[]> metrics = e.getValue();
                List<String> parts = new ArrayList<>();
                addOnchainPart(parts, metrics, "n-unique-addresses", "unique addresses", false);
                addOnchainPart(parts, metrics, "n-transactions", "transactions", false);
                addOnchainPart(parts, metrics, "estimated-transaction-volume-usd",
                        "est. transfer volume", true);
                if (parts.isEmpty()) {
                    continue;
                }
                String content = "Week of " + week + ": " + symbol + " " + String.join(", ", parts) + ".";
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put(RagMetadata.SOURCE_TYPE, SourceType.ONCHAIN.value());
                meta.put(RagMetadata.SYMBOL, symbol);
                meta.put(RagMetadata.SOURCE, source);
                meta.put(RagMetadata.TS_UTC, week.atStartOfDay(ZoneOffset.UTC).toInstant().toString());
                out.add(document("onchain:" + symbol + ":" + week, content, meta));
            }
        }
        return out;
    }

    private static void addOnchainPart(List<String> parts, Map<String, double[]> metrics,
                                       String metric, String label, boolean usd) {
        double[] acc = metrics.get(metric);
        if (acc == null || acc[1] == 0) {
            return;
        }
        double mean = acc[0] / acc[1];
        parts.add(label + " " + (usd ? formatUsd(mean) : formatCount(mean)));
    }

    // ---------------------------------------------------------- fundamentals

    private List<Document> fundamentalDocuments() {
        List<Document> out = new ArrayList<>();
        for (String symbol : Symbols.UNIVERSE) {
            Fundamentals f = fundamentalsRepository.findFirstBySymbolOrderByTsUtcDesc(symbol).orElse(null);
            if (f == null) {
                continue;
            }
            List<String> parts = new ArrayList<>();
            List<String> price = new ArrayList<>();
            addPct(price, "24h", f.getPriceChangePct24h());
            addPct(price, "7d", f.getPriceChangePct7d());
            addPct(price, "30d", f.getPriceChangePct30d());
            if (!price.isEmpty()) {
                parts.add("price " + String.join(", ", price));
            }
            if (f.getMarketCapChangePct24h() != null) {
                parts.add("market cap 24h " + formatPct(f.getMarketCapChangePct24h()));
            }
            if (f.getTotalVolumeUsd() != null) {
                parts.add("total volume " + formatUsd(f.getTotalVolumeUsd()));
            }
            addCount(parts, "GitHub commits (4w)", f.getGithubCommitCount4w());
            addCount(parts, "PRs merged", f.getGithubPrsMerged());
            if (positive(f.getGithubCodeAdditions4w()) || positive(f.getGithubCodeDeletions4w())) {
                parts.add("code +" + nz(f.getGithubCodeAdditions4w()) + "/-"
                        + nz(f.getGithubCodeDeletions4w()) + " lines (4w)");
            }
            addCount(parts, "Reddit subscribers", f.getRedditSubscribers());
            addCount(parts, "Reddit active (48h)", f.getRedditActive48h());
            addCount(parts, "Twitter followers", f.getTwitterFollowers());
            if (parts.isEmpty()) {
                continue;
            }
            String asOf = f.getTsUtc() == null ? "" : " (as of "
                    + f.getTsUtc().atZone(ZoneOffset.UTC).toLocalDate() + ")";
            String content = symbol + " fundamentals" + asOf + ": " + String.join("; ", parts) + ".";
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(RagMetadata.SOURCE_TYPE, SourceType.FUNDAMENTAL.value());
            meta.put(RagMetadata.SYMBOL, symbol);
            meta.put(RagMetadata.SOURCE, "coingecko");
            if (f.getTsUtc() != null) {
                meta.put(RagMetadata.TS_UTC, f.getTsUtc().toString());
            }
            out.add(document("fundamental:" + symbol, content, meta));
        }
        return out;
    }

    // --------------------------------------------------------- knowledge base

    private List<Document> knowledgeBaseDocuments() {
        List<Document> out = new ArrayList<>();
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(KB_GLOB);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read KB resources at " + KB_GLOB, ex);
        }
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || !filename.endsWith(".md")) {
                continue;
            }
            String symbol = filename.substring(0, filename.length() - 3).toUpperCase();
            String text;
            try {
                text = resource.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new UncheckedIOException("Cannot read KB file " + filename, ex);
            }
            out.addAll(splitKb(symbol, text));
        }
        return out;
    }

    /** Split a coin KB markdown into one chunk per {@code ##} section, prefixed with the H1 title. */
    private List<Document> splitKb(String symbol, String text) {
        List<Document> out = new ArrayList<>();
        String title = "";
        String heading = null;
        StringBuilder body = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("## ")) {
                flushKb(out, symbol, title, heading, body);
                heading = line.substring(3).trim();
                body = new StringBuilder();
            } else if (line.startsWith("# ")) {
                title = line.substring(2).trim();
            } else if (heading != null) {
                body.append(line).append('\n');
            }
        }
        flushKb(out, symbol, title, heading, body);
        return out;
    }

    private void flushKb(List<Document> out, String symbol, String title, String heading, StringBuilder body) {
        if (heading == null) {
            return;
        }
        String text = body.toString().trim();
        if (text.isBlank()) {
            return;
        }
        String content = (title.isBlank() ? symbol : title) + "\n## " + heading + "\n" + text;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(RagMetadata.SOURCE_TYPE, SourceType.KB.value());
        meta.put(RagMetadata.SYMBOL, symbol);
        meta.put(RagMetadata.SECTION, heading);
        meta.put(RagMetadata.SOURCE, "kb");
        out.add(document("kb:" + symbol + ":" + heading, content, meta));
    }

    // -------------------------------------------------------------- helpers

    private static Document document(String stableId, String content, Map<String, Object> metadata) {
        String id = UUID.nameUUIDFromBytes(stableId.getBytes(StandardCharsets.UTF_8)).toString();
        return Document.builder().id(id).text(content).metadata(metadata).build();
    }

    private static Map<String, Integer> countBySourceType(List<Document> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SourceType st : SourceType.values()) {
            counts.put(st.value(), 0);
        }
        for (Document d : docs) {
            String st = (String) d.getMetadata().get(RagMetadata.SOURCE_TYPE);
            counts.merge(st, 1, Integer::sum);
        }
        return counts;
    }

    private static void putIfText(Map<String, Object> meta, String key, String value) {
        if (StringUtils.hasText(value)) {
            meta.put(key, value.trim());
        }
    }

    private static void addPct(List<String> parts, String label, Double value) {
        if (value != null) {
            parts.add(label + " " + formatPct(value));
        }
    }

    private static void addCount(List<String> parts, String label, Integer value) {
        if (positive(value)) {
            parts.add(label + " " + formatCount(value));
        }
    }

    private static boolean positive(Integer v) {
        return v != null && v > 0;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String formatPct(double v) {
        return String.format("%+.1f%%", v);
    }

    /** Human-friendly magnitude: 518522 → "519k", 8.7e9 → "8.7B". */
    static String formatCount(double v) {
        double a = Math.abs(v);
        if (a >= 1e9) {
            return String.format("%.1fB", v / 1e9);
        }
        if (a >= 1e6) {
            return String.format("%.1fM", v / 1e6);
        }
        if (a >= 1e3) {
            return String.format("%.0fk", v / 1e3);
        }
        return String.format("%.0f", v);
    }

    static String formatUsd(double v) {
        return "$" + formatCount(v);
    }
}
