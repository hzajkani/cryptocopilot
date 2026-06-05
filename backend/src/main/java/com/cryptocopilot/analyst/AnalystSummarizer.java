package com.cryptocopilot.analyst;

import com.cryptocopilot.dto.TAVerdict;
import com.cryptocopilot.rag.LlmClient;
import com.cryptocopilot.rag.LlmProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phrases the deterministic Analyst opinion into 2–3 sentences with an LLM (Spring AI via the
 * shared {@link LlmClient} seam — PROJECT.md Stage 5 §4), constrained to <b>only synthesise the
 * facts it is given</b>.
 *
 * <p><b>Hallucination guard.</b> Every numeric value in the generated summary must appear in the
 * input objects (within a small tolerance). If a number is invented — or the model is unavailable,
 * errors, or returns nothing — the summary falls back to a deterministic template built directly
 * from the inputs (PROJECT.md §9: "on failure, fall back to a deterministic template"). The
 * fallback is grounded by construction, so {@code /api/analyst} works even with the LLM offline.
 */
@Component
public class AnalystSummarizer {

    private static final Logger log = LoggerFactory.getLogger(AnalystSummarizer.class);
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final double TOLERANCE = 0.05;

    static final String SYSTEM_PROMPT = """
            You are a precise crypto market analyst. Write a 2-3 sentence opinion for a retail trader.
            Use ONLY the facts provided below — do not introduce any number, percentage, price, date
            or claim that is not in the facts. Do not give buy/sell/hold advice. Be concise and neutral.""";

    private final LlmClient llm;

    public AnalystSummarizer(LlmClient llm) {
        this.llm = llm;
    }

    /** The facts the summary may draw on — nothing else may appear in the generated text. */
    public record Facts(String symbol, String direction, String conviction, double agreementScore,
                        int combined, List<InputScore> inputs, FundamentalSnapshot fundamental,
                        String mlClass, Double mlConfidence, TAVerdict ta, double newsScore) {
    }

    /** Summarize on the default provider ({@link LlmProvider#OLLAMA}). */
    public String summarize(Facts facts) {
        return summarize(facts, LlmProvider.OLLAMA);
    }

    /** Generate the (guarded) summary, falling back to a deterministic template on any failure. */
    public String summarize(Facts facts, LlmProvider provider) {
        List<Double> allowed = allowedNumbers(facts);
        try {
            String raw = llm.complete(SYSTEM_PROMPT, userPrompt(facts), provider);
            raw = raw == null ? "" : raw.trim();
            if (!raw.isBlank() && isGrounded(raw, allowed)) {
                return raw;
            }
            if (!raw.isBlank()) {
                log.info("analyst summary for {} failed the hallucination guard; using template", facts.symbol());
            }
        } catch (Exception ex) {
            log.warn("analyst LLM summary failed for {} ({}); using template", facts.symbol(), ex.getMessage());
        }
        return template(facts);
    }

    /** True iff every number in {@code text} matches an allowed input value within tolerance. */
    static boolean isGrounded(String text, List<Double> allowed) {
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            double v = Double.parseDouble(m.group());
            boolean ok = allowed.stream().anyMatch(a -> Math.abs(a - v) <= TOLERANCE + 1e-9);
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** The deterministic, always-grounded fallback summary. */
    static String template(Facts f) {
        StringBuilder sb = new StringBuilder();
        sb.append(f.symbol()).append(" looks ").append(phrase(f.direction()))
                .append(" with ").append(f.conviction().toLowerCase()).append(" conviction");
        sb.append(" (input agreement ").append(round2(f.agreementScore())).append("). ");
        sb.append("Signals — ").append(mlPhrase(f)).append("; TA ")
                .append(safeDir(f.ta())).append('/').append(safeConf(f.ta()))
                .append("; fundamentals ").append(low(f.fundamental().health()))
                .append(" via ").append(f.fundamental().healthSource())
                .append("; news ").append(low(f.fundamental().newsSentiment7d())).append('.');
        return sb.toString();
    }

    // ---- prompt + allowed numbers --------------------------------------------------------------

    static String userPrompt(Facts f) {
        StringBuilder sb = new StringBuilder("Facts:\n");
        sb.append("- Overall: ").append(f.direction()).append(", ").append(f.conviction())
                .append(" conviction, combined score ").append(f.combined())
                .append(", agreement ").append(round2(f.agreementScore())).append('\n');
        for (InputScore i : f.inputs()) {
            sb.append("- ").append(i.rationale()).append('\n');
        }
        FundamentalSnapshot fu = f.fundamental();
        sb.append("- Fundamental health: ").append(fu.health()).append(" (source ")
                .append(fu.healthSource()).append(")\n");
        for (String r : fu.reasons()) {
            sb.append("  - ").append(r).append('\n');
        }
        if (fu.marketDominancePct() != null) {
            sb.append("- Market dominance: ").append(round2(fu.marketDominancePct())).append('%');
            if (fu.marketDominanceTrend() != null) {
                sb.append(" (").append(fu.marketDominanceTrend()).append(" over 7d)");
            }
            sb.append('\n');
        }
        sb.append("- News 7d: ").append(fu.newsSentiment7d())
                .append(" (score ").append(round2(fu.newsSentimentScore())).append(")\n");
        sb.append("\nWrite the 2-3 sentence opinion now, using only these facts.");
        return sb.toString();
    }

    /** All numeric values the summary is allowed to mention. */
    static List<Double> allowedNumbers(Facts f) {
        List<Double> allowed = new ArrayList<>();
        for (int n = 0; n <= 6; n++) {
            allowed.add((double) n);
            allowed.add((double) -n);
        }
        allowed.add(f.agreementScore());
        allowed.add((double) f.combined());
        allowed.add(f.newsScore());
        if (f.mlConfidence() != null) {
            allowed.add(f.mlConfidence());
        }
        for (InputScore i : f.inputs()) {
            allowed.add((double) i.score());
            addNumbersFrom(i.rationale(), allowed);   // captures conf + τ embedded in the rationale
        }
        FundamentalSnapshot fu = f.fundamental();
        if (fu.marketDominancePct() != null) {
            allowed.add(fu.marketDominancePct());
        }
        allowed.add(fu.newsSentimentScore());
        for (String r : fu.reasons()) {
            addNumbersFrom(r, allowed);                // captures 7d %, commit counts, mcap %
        }
        return allowed;
    }

    private static void addNumbersFrom(String text, List<Double> out) {
        if (text == null) {
            return;
        }
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            out.add(Double.parseDouble(m.group()));
        }
    }

    // ---- template helpers ----------------------------------------------------------------------

    private static String mlPhrase(Facts f) {
        String cls = f.mlClass() == null ? "n/a" : f.mlClass();
        if (("UP".equalsIgnoreCase(cls) || "DOWN".equalsIgnoreCase(cls)) && f.mlConfidence() != null) {
            return "ML " + cls + " @ " + round2(f.mlConfidence());
        }
        return "ML " + cls;
    }

    private static String phrase(String direction) {
        return switch (direction) {
            case "LEAN_BULLISH" -> "mildly bullish";
            case "LEAN_BEARISH" -> "mildly bearish";
            case "CONFLICTED" -> "conflicted";
            default -> "broadly neutral";
        };
    }

    private static String safeDir(TAVerdict ta) {
        return ta == null || ta.direction() == null ? "NEUTRAL" : ta.direction();
    }

    private static String safeConf(TAVerdict ta) {
        return ta == null || ta.confidence() == null ? "WEAK" : ta.confidence();
    }

    private static String low(String s) {
        return s == null ? "unknown" : s.toLowerCase();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
