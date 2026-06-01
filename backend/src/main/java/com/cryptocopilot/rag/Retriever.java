package com.cryptocopilot.rag;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

/**
 * Semantic retrieval over the pgvector store (PROJECT.md Stage 4 §4). Classifies the query to
 * bias the {@code source_type} filter, optionally restricts to caller-supplied symbols, then
 * re-ranks news/onchain hits by recency: {@code final = 0.7*similarity + 0.3*exp(-ageDays/14)}.
 * KB and fundamental chunks are ranked by similarity alone (no recency boost).
 */
@Component
public class Retriever {

    private static final Logger log = LoggerFactory.getLogger(Retriever.class);

    static final double SIMILARITY_WEIGHT = 0.7;
    static final double RECENCY_WEIGHT = 0.3;
    static final double RECENCY_TAU_DAYS = 14.0;
    /** Fetch more than k so the recency re-rank can actually reorder before we cut to k. */
    static final int OVERSAMPLE = 4;
    static final int MIN_FETCH = 25;

    private final VectorStore vectorStore;
    private final QueryClassifier classifier;

    public Retriever(VectorStore vectorStore, QueryClassifier classifier) {
        this.vectorStore = vectorStore;
        this.classifier = classifier;
    }

    public RetrievalResult retrieve(String query, int k, List<String> symbols, SourceType sourceOverride) {
        QueryClass queryClass = classifier.classify(query);
        SourceType filter = sourceOverride != null ? sourceOverride : queryClass.sourceType();

        SearchRequest.Builder request = SearchRequest.builder()
                .query(query)
                .topK(Math.max(k * OVERSAMPLE, MIN_FETCH));
        Filter.Expression expression = buildFilter(symbols, filter);
        if (expression != null) {
            request.filterExpression(expression);
        }

        List<Document> hits = vectorStore.similaritySearch(request.build());
        Instant now = Instant.now();
        List<RetrievedChunk> ranked = new ArrayList<>(hits.size());
        for (Document d : hits) {
            ranked.add(toChunk(d, now));
        }
        ranked.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());

        List<RetrievedChunk> top = new ArrayList<>(Math.min(k, ranked.size()));
        for (int i = 0; i < ranked.size() && i < k; i++) {
            RetrievedChunk c = ranked.get(i);
            top.add(new RetrievedChunk(i + 1, c.id(), c.content(), c.sourceType(), c.symbol(),
                    c.source(), c.url(), c.tsUtc(), c.section(), c.similarity(), c.score()));
        }
        log.debug("retrieve('{}') class={} filter={} -> {} chunks", query, queryClass.label(),
                filter == null ? "all" : filter.value(), top.size());
        return new RetrievalResult(top, queryClass.label());
    }

    private RetrievedChunk toChunk(Document d, Instant now) {
        var meta = d.getMetadata();
        String sourceType = (String) meta.get(RagMetadata.SOURCE_TYPE);
        Instant ts = parseInstant((String) meta.get(RagMetadata.TS_UTC));
        Double rawScore = d.getScore();
        double similarity = rawScore == null ? 0.0 : rawScore;
        double score = rerank(similarity, SourceType.fromValue(sourceType), ts, now);
        return new RetrievedChunk(0, d.getId(), d.getText(), sourceType,
                (String) meta.get(RagMetadata.SYMBOL), (String) meta.get(RagMetadata.SOURCE),
                (String) meta.get(RagMetadata.URL), ts, (String) meta.get(RagMetadata.SECTION),
                similarity, score);
    }

    static double rerank(double similarity, SourceType type, Instant ts, Instant now) {
        boolean recencyAware = (type == SourceType.NEWS || type == SourceType.ONCHAIN);
        if (!recencyAware || ts == null) {
            return similarity;
        }
        double ageDays = Math.max(0.0, (now.toEpochMilli() - ts.toEpochMilli()) / 86_400_000.0);
        double recency = Math.exp(-ageDays / RECENCY_TAU_DAYS);
        return SIMILARITY_WEIGHT * similarity + RECENCY_WEIGHT * recency;
    }

    private Filter.Expression buildFilter(List<String> symbols, SourceType filter) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op symbolOp = (symbols != null && !symbols.isEmpty())
                ? b.in(RagMetadata.SYMBOL, symbols.stream().map(String::toUpperCase).toArray())
                : null;
        FilterExpressionBuilder.Op sourceOp = (filter != null)
                ? b.eq(RagMetadata.SOURCE_TYPE, filter.value())
                : null;
        if (symbolOp != null && sourceOp != null) {
            return b.and(symbolOp, sourceOp).build();
        }
        if (sourceOp != null) {
            return sourceOp.build();
        }
        if (symbolOp != null) {
            return symbolOp.build();
        }
        return null;
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
