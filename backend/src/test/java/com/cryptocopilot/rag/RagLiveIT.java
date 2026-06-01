package com.cryptocopilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.yaml.snakeyaml.Yaml;

/**
 * Live RAG integration test + retrieval eval. Requires the running compose {@code db} and a local
 * {@code Ollama} with the chat + embedding models pulled (see {@code docs/OLLAMA_SETUP.md}), so it
 * is gated on the {@code RAG_LIVE} env var — cleanly SKIPPED (not failed) otherwise, keeping
 * {@code mvn test} green by default with no model server.
 *
 * <p>Run it (db up, Ollama up with models pulled):
 * <pre>RAG_LIVE=1 mvn -Dtest=RagLiveIT test</pre>
 *
 * <p>It (1) reindexes the corpus, (2) checks the DoD behaviours — mechanism question cites a KB
 * chunk, out-of-corpus and trading-advice are refused with the exact phrases, a zero-news coin
 * refuses cleanly — and (3) runs {@code evals/retrieval_eval.yaml}, writing
 * {@code reports/retrieval_eval.md} and asserting the recall targets.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "RAG_LIVE", matches = "(?i)1|true|yes")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RagLiveIT {

    private static final Logger log = LoggerFactory.getLogger(RagLiveIT.class);

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/cryptocopilot");
        registry.add("spring.datasource.username", () -> "cc");
        registry.add("spring.datasource.password", () -> "ccpass");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private CorpusIndexer indexer;
    @Autowired
    private Retriever retriever;
    @Autowired
    private RagService ragService;

    private static Map<String, Integer> corpusCounts;

    @Test
    @Order(1)
    void reindexPopulatesAllSourceTypes() {
        corpusCounts = indexer.reindex();
        log.info("RAG corpus counts: {}", corpusCounts);
        assertThat(corpusCounts.get("kb")).as("kb chunks").isGreaterThan(0);
        assertThat(corpusCounts.get("news")).as("news chunks").isGreaterThan(0);
        assertThat(corpusCounts.get("fundamental")).as("fundamental chunks").isGreaterThan(0);
        assertThat(corpusCounts.get("onchain")).as("onchain chunks").isGreaterThan(0);
    }

    @Test
    @Order(2)
    void mechanismQuestionRetrievesCoinKbChunk() {
        RetrievalResult r = retriever.retrieve("How does Solana achieve consensus?", 8, null, null);
        assertThat(r.classification()).isEqualTo("kb");
        assertThat(r.chunks()).anySatisfy(c -> {
            assertThat(c.sourceType()).isEqualTo("kb");
            assertThat(c.symbol()).isEqualTo("SOL");
            assertThat(c.content().toLowerCase(Locale.ROOT)).contains("proof of history");
        });
    }

    @Test
    @Order(3)
    void chatAnswersMechanismWithKbCitations() {
        AnswerWithCitations a = ragService.chat("How does Solana achieve consensus?", null);
        log.info("mechanism answer: {}", a.answer());
        assertThat(a.answer()).doesNotStartWith("The available sources do not answer");
        assertThat(a.answer()).contains("[");
        assertThat(a.citations()).isNotEmpty();
        assertThat(a.citations()).anyMatch(c -> "kb".equals(c.sourceType()));
    }

    @Test
    @Order(4)
    void outOfCorpusQuestionIsRefusedExactly() {
        AnswerWithCitations a = ragService.chat("What will BTC be worth in 2030?", null);
        assertThat(a.answer()).isEqualTo(Generator.REFUSAL_NO_CONTEXT);
        assertThat(a.citations()).isEmpty();
    }

    @Test
    @Order(5)
    void tradingAdviceIsRefusedExactly() {
        AnswerWithCitations a = ragService.chat("Should I buy ETH now?", null);
        assertThat(a.answer()).isEqualTo(Generator.REFUSAL_ADVICE);
    }

    @Test
    @Order(6)
    void coinWithNoRecentNewsRefusesCleanly() {
        // LINK has no symbol-tagged news in the corpus — must refuse, not hallucinate or crash.
        AnswerWithCitations a = ragService.chat("What's the latest news on LINK?", List.of("LINK"));
        assertThat(a.answer()).isEqualTo(Generator.REFUSAL_NO_CONTEXT);
    }

    @Test
    @Order(7)
    @SuppressWarnings("unchecked")
    void retrievalEvalMeetsTargets() throws IOException {
        List<Map<String, Object>> questions;
        try (InputStream in = Files.newInputStream(evalFile())) {
            Map<String, Object> root = new Yaml().load(in);
            questions = (List<Map<String, Object>>) root.get("questions");
        }

        Map<String, int[]> byCategory = new LinkedHashMap<>();   // category -> [hits, total]
        Map<String, int[]> classAcc = new LinkedHashMap<>();     // category -> [correct, total]
        List<String> rows = new ArrayList<>();
        Instant now = Instant.now();

        for (Map<String, Object> q : questions) {
            String id = str(q.get("id"));
            String category = str(q.get("category"));
            String query = str(q.get("query"));
            List<String> keywords = lower((List<String>) q.getOrDefault("expected_keywords", List.of()));
            List<String> symbols = (List<String>) q.getOrDefault("expected_symbols", List.of());
            List<String> sourceTypes = (List<String>) q.getOrDefault("expected_source_types", List.of());
            String expectedClass = str(q.get("expected_query_classification"));
            Integer maxAge = q.get("max_age_days") == null ? null : ((Number) q.get("max_age_days")).intValue();

            RetrievalResult r = retriever.retrieve(query, 8, null, null);
            boolean hit = false;
            int matchedAgeDays = -1;
            for (RetrievedChunk c : r.chunks()) {
                if (isRelevant(c, keywords, symbols, sourceTypes)) {
                    hit = true;
                    if (c.tsUtc() != null) {
                        matchedAgeDays = (int) Duration.between(c.tsUtc(), now).toDays();
                    }
                    break;
                }
            }
            boolean ageOk = (maxAge == null || matchedAgeDays < 0 || matchedAgeDays <= maxAge);

            byCategory.computeIfAbsent(category, k -> new int[2]);
            byCategory.get(category)[0] += hit ? 1 : 0;
            byCategory.get(category)[1] += 1;
            boolean classOk = expectedClass != null && expectedClass.equalsIgnoreCase(r.classification());
            classAcc.computeIfAbsent(category, k -> new int[2]);
            classAcc.get(category)[0] += classOk ? 1 : 0;
            classAcc.get(category)[1] += 1;

            rows.add(String.format("| %s | %s | %s | %s | %s | %s |", id, category,
                    hit ? "✅" : "❌", classOk ? r.classification() : r.classification() + " (≠" + expectedClass + ")",
                    matchedAgeDays < 0 ? "—" : matchedAgeDays + "d", ageOk ? "ok" : "STALE"));
        }

        double overall = ratio(byCategory.values().stream().mapToInt(a -> a[0]).sum(),
                byCategory.values().stream().mapToInt(a -> a[1]).sum());
        writeReport(byCategory, classAcc, rows, overall, now);

        log.info("recall@8 overall={} perCategory={}", overall, recallMap(byCategory));
        // Mechanism (KB) and fundamental are deterministic/stable; news is corpus-dependent (caveat).
        assertThat(recall(byCategory, "mechanism")).as("mechanism recall@8").isGreaterThanOrEqualTo(0.75);
        assertThat(recall(byCategory, "fundamental")).as("fundamental recall@8").isGreaterThanOrEqualTo(0.75);
        assertThat(overall).as("overall recall@8").isGreaterThanOrEqualTo(0.70);
    }

    // ----------------------------------------------------------------- helpers

    private static boolean isRelevant(RetrievedChunk c, List<String> keywords,
                                      List<String> symbols, List<String> sourceTypes) {
        if (!sourceTypes.isEmpty() && !sourceTypes.contains(c.sourceType())) {
            return false;
        }
        String content = c.content() == null ? "" : c.content().toLowerCase(Locale.ROOT);
        if (!symbols.isEmpty()) {
            boolean symbolOk = (c.symbol() != null && symbols.contains(c.symbol()))
                    || symbols.stream().anyMatch(s -> content.contains(s.toLowerCase(Locale.ROOT)));
            if (!symbolOk) {
                return false;
            }
        }
        if (!keywords.isEmpty()) {
            return keywords.stream().anyMatch(content::contains);
        }
        return true;
    }

    private void writeReport(Map<String, int[]> byCategory, Map<String, int[]> classAcc,
                             List<String> rows, double overall, Instant now) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Retrieval eval — CryptoCopilot Stage 4 (RAG)\n\n");
        sb.append("Generated: ").append(now).append("  \n");
        sb.append("Embedding: Ollama `nomic-embed-text` (768-dim) · chat: Ollama `llama3.2:3b` · ");
        sb.append("top-k = 8 · score = 0.7·similarity + 0.3·exp(-ageDays/14) for news/onchain.\n\n");
        if (corpusCounts != null) {
            sb.append("Corpus: ").append(new TreeMap<>(corpusCounts)).append("\n\n");
        }
        sb.append("**recall@8** = fraction of questions for which ≥1 of the top-8 chunks matches the ")
                .append("expected source_type, symbol and a keyword. News is corpus-dependent ")
                .append("(~124 rows, ~4-day window) and grows with ingestion.\n\n");
        sb.append("| category | n | recall@8 | classifier accuracy |\n");
        sb.append("|---|---|---|---|\n");
        for (var e : byCategory.entrySet()) {
            String cat = e.getKey();
            sb.append(String.format("| %s | %d | %.2f | %.2f |%n", cat, e.getValue()[1],
                    recall(byCategory, cat), ratio(classAcc.get(cat)[0], classAcc.get(cat)[1])));
        }
        sb.append(String.format("| **overall** | %d | **%.2f** | %.2f |%n",
                byCategory.values().stream().mapToInt(a -> a[1]).sum(), overall,
                ratio(classAcc.values().stream().mapToInt(a -> a[0]).sum(),
                        classAcc.values().stream().mapToInt(a -> a[1]).sum())));
        sb.append("\n## Per-question\n\n");
        sb.append("| id | category | hit@8 | classification | matched age | age gate |\n");
        sb.append("|---|---|---|---|---|---|\n");
        rows.forEach(r -> sb.append(r).append('\n'));

        Path out = reportFile();
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        log.info("wrote retrieval eval report to {}", out.toAbsolutePath());
    }

    /** Resolve repo-root/evals/retrieval_eval.yaml from the backend working dir (or its parent). */
    private static Path evalFile() {
        Path direct = Path.of("..", "evals", "retrieval_eval.yaml");
        return Files.exists(direct) ? direct : Path.of("evals", "retrieval_eval.yaml");
    }

    private static Path reportFile() {
        Path parent = Path.of("..", "reports");
        return Files.exists(Path.of("..", "evals")) ? parent.resolve("retrieval_eval.md")
                : Path.of("reports", "retrieval_eval.md");
    }

    private static double recall(Map<String, int[]> byCategory, String cat) {
        int[] a = byCategory.getOrDefault(cat, new int[]{0, 0});
        return ratio(a[0], a[1]);
    }

    private static Map<String, String> recallMap(Map<String, int[]> byCategory) {
        Map<String, String> m = new LinkedHashMap<>();
        byCategory.forEach((k, v) -> m.put(k, String.format("%.2f", ratio(v[0], v[1]))));
        return m;
    }

    private static double ratio(int num, int den) {
        return den == 0 ? 0.0 : (double) num / den;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) {
            out.add(s.toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
