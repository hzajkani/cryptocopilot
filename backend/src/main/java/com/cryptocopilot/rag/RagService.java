package com.cryptocopilot.rag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Facade wiring retrieval + generation for {@code /api/chat}, plus reindex and status for the
 * {@code /api/rag/*} endpoints. The Researcher (PROJECT.md Stage 4).
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    static final int TOP_K = 8;

    private final Retriever retriever;
    private final Generator generator;
    private final CorpusIndexer indexer;
    private final JdbcTemplate jdbcTemplate;

    public RagService(Retriever retriever, Generator generator, CorpusIndexer indexer,
                      JdbcTemplate jdbcTemplate) {
        this.retriever = retriever;
        this.generator = generator;
        this.indexer = indexer;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Answer a grounded, cited question. A blank query is refused without touching the LLM. */
    public AnswerWithCitations chat(String query, List<String> symbols) {
        if (query == null || query.isBlank()) {
            return new AnswerWithCitations(Generator.REFUSAL_NO_CONTEXT, List.of(), List.of(), 0,
                    QueryClass.ALL.label());
        }
        RetrievalResult retrieval = retriever.retrieve(query, TOP_K, symbols, null);
        return generator.generate(query, retrieval);
    }

    /** Clear-and-rebuild the pgvector corpus. Returns chunk counts per source type. */
    public Map<String, Integer> reindex() {
        return indexer.reindex();
    }

    /** Live chunk counts per source type, read straight from the Spring AI {@code vector_store}. */
    public Map<String, Integer> status() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SourceType st : SourceType.values()) {
            counts.put(st.value(), 0);
        }
        try {
            jdbcTemplate.query(
                    "SELECT metadata->>'source_type' AS st, count(*) AS n FROM vector_store "
                            + "GROUP BY metadata->>'source_type'",
                    rs -> {
                        String st = rs.getString("st");
                        if (st != null) {
                            counts.merge(st, rs.getInt("n"), Integer::sum);
                        }
                    });
        } catch (Exception ex) {
            // Table not initialised / DB hiccup — report zeros rather than failing the endpoint.
            log.warn("rag status query failed ({}); returning zero counts", ex.getMessage());
        }
        return counts;
    }
}
