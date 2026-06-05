package com.cryptocopilot.analyst;

import com.cryptocopilot.analyst.AnalystScorer.ScoreResult;
import com.cryptocopilot.dto.SignalDto;
import com.cryptocopilot.entity.News;
import com.cryptocopilot.rag.LlmProvider;
import com.cryptocopilot.repository.NewsRepository;
import com.cryptocopilot.service.SignalService;
import com.cryptocopilot.util.Symbols;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Analyst aggregator (PROJECT.md Stage 5 §4): fuses the ML signal, the ta4j TA verdict, the
 * two-tier {@link FundamentalSnapshot} and recent news into one deterministic, explainable
 * {@link AnalystOpinion} per coin, with an LLM summary guarded against hallucination. Scoring is
 * 100% deterministic ({@link AnalystScorer}); only the {@code summary} wording uses the LLM, and it
 * can add no fact not in the inputs.
 */
@Service
public class AnalystService {

    /** Persistent disclaimer on every Analyst response (PROJECT.md Stage 5 §4 / §9). */
    public static final String DISCLAIMER =
            "This is decision-support, not financial advice. The Analyst combines ML, technical, "
                    + "fundamental, and news inputs deterministically. You are responsible for your decisions.";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);
    private static final int MAX_CITATIONS = 3;

    private final SignalService signalService;
    private final FundamentalSnapshotService fundamentalSnapshotService;
    private final AnalystSummarizer summarizer;
    private final NewsRepository newsRepository;
    private final AnalystProperties props;

    public AnalystService(SignalService signalService,
                          FundamentalSnapshotService fundamentalSnapshotService,
                          AnalystSummarizer summarizer,
                          NewsRepository newsRepository,
                          AnalystProperties props) {
        this.signalService = signalService;
        this.fundamentalSnapshotService = fundamentalSnapshotService;
        this.summarizer = summarizer;
        this.newsRepository = newsRepository;
        this.props = props;
    }

    /** Opinions for all coins, summaries on the default provider ({@link LlmProvider#OLLAMA}). */
    @Transactional(readOnly = true)
    public List<AnalystResponse> opinions() {
        return opinions(LlmProvider.OLLAMA);
    }

    @Transactional(readOnly = true)
    public List<AnalystResponse> opinions(LlmProvider provider) {
        List<AnalystResponse> out = new ArrayList<>(Symbols.UNIVERSE.size());
        for (String symbol : Symbols.UNIVERSE) {
            out.add(opinion(symbol, provider));
        }
        return out;
    }

    /** One coin's opinion, summary on the default provider ({@link LlmProvider#OLLAMA}). */
    @Transactional(readOnly = true)
    public AnalystResponse opinion(String symbol) {
        return opinion(symbol, LlmProvider.OLLAMA);
    }

    @Transactional(readOnly = true)
    public AnalystResponse opinion(String symbol, LlmProvider provider) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        Instant now = Instant.now();

        SignalDto signal = signalService.signal(sym);
        FundamentalSnapshot fundamental = fundamentalSnapshotService.snapshot(sym, now);
        List<String> citations = recentHeadlines(sym, now);

        double tau = props.mlConfidenceThreshold();
        String taDirection = signal.ta() == null ? null : signal.ta().direction();
        String taConfidence = signal.ta() == null ? null : signal.ta().confidence();
        ScoreResult score = AnalystScorer.score(signal.mlClass(), signal.mlConfidence(),
                taDirection, taConfidence, fundamental.health(), fundamental.newsSentiment7d(), tau);

        AnalystSummarizer.Facts facts = new AnalystSummarizer.Facts(sym, score.direction(),
                score.conviction(), score.agreementScore(), score.combined(), score.inputs(),
                fundamental, signal.mlClass(), signal.mlConfidence(), signal.ta(),
                fundamental.newsSentimentScore());
        String summary = summarizer.summarize(facts, provider);

        Map<String, Object> inputs = buildInputs(signal, fundamental, score, tau);
        AnalystOpinion opinion = new AnalystOpinion(sym, now, score.direction(), score.conviction(),
                summary, score.agreementScore(), inputs, citations);
        return new AnalystResponse(opinion, fundamental.healthSource(), DISCLAIMER);
    }

    /** The transparent {@code inputs} map: each perspective's detail + the score breakdown. */
    private Map<String, Object> buildInputs(SignalDto signal, FundamentalSnapshot fundamental,
                                            ScoreResult score, double tau) {
        Map<String, Object> ml = new LinkedHashMap<>();
        ml.put("class", signal.mlClass());
        ml.put("confidence", signal.mlConfidence());
        ml.put("score", scoreOf(score, "ml"));

        Map<String, Object> ta = new LinkedHashMap<>();
        ta.put("direction", signal.ta() == null ? null : signal.ta().direction());
        ta.put("confidence", signal.ta() == null ? null : signal.ta().confidence());
        ta.put("score", scoreOf(score, "ta"));
        ta.put("signals", signal.ta() == null ? List.of() : signal.ta().signals());

        Map<String, Object> news = new LinkedHashMap<>();
        news.put("label", fundamental.newsSentiment7d());
        news.put("score", fundamental.newsSentimentScore());

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("ml", ml);
        inputs.put("ta", ta);
        inputs.put("fundamental", fundamental);
        inputs.put("news", news);
        inputs.put("scoreBreakdown", score.inputs());
        inputs.put("combined", score.combined());
        inputs.put("agreementScore", score.agreementScore());
        inputs.put("mlConfidenceThreshold", tau);
        return inputs;
    }

    private static int scoreOf(ScoreResult score, String name) {
        return score.inputs().stream()
                .filter(i -> i.name().equals(name))
                .mapToInt(InputScore::score)
                .findFirst().orElse(0);
    }

    /** Up to three recent (≤7d) headlines tagged with the coin, formatted as numbered citations. */
    private List<String> recentHeadlines(String symbol, Instant now) {
        Instant since = now.minus(7, ChronoUnit.DAYS);
        List<News> items = newsRepository
                .findByCurrenciesContainingAndTsUtcAfterOrderByTsUtcDesc(symbol, since);
        List<String> citations = new ArrayList<>();
        for (News n : items) {
            if (n.getTitle() == null || n.getTitle().isBlank()) {
                continue;
            }
            String source = n.getSource() == null ? "news" : n.getSource();
            String date = n.getTsUtc() == null ? "" : " (" + DATE.format(n.getTsUtc()) + ")";
            citations.add("[%d] %s — %s%s".formatted(citations.size() + 1, source, n.getTitle().trim(), date));
            if (citations.size() >= MAX_CITATIONS) {
                break;
            }
        }
        return citations;
    }
}
