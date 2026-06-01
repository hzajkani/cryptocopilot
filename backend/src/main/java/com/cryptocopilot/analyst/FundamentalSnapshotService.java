package com.cryptocopilot.analyst;

import com.cryptocopilot.entity.Fundamentals;
import com.cryptocopilot.entity.MarketMeta;
import com.cryptocopilot.entity.News;
import com.cryptocopilot.entity.Onchain;
import com.cryptocopilot.repository.FundamentalsRepository;
import com.cryptocopilot.repository.MarketMetaRepository;
import com.cryptocopilot.repository.NewsRepository;
import com.cryptocopilot.repository.OnchainRepository;
import com.cryptocopilot.util.Symbols;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the deterministic, rule-based {@link FundamentalSnapshot} for a coin (PROJECT.md Stage 5
 * §3). Pure rules over the data already in Postgres — no ML, no LLM. The tier-health decisions are
 * exposed as static functions so they are unit-tested directly (golden scenarios), and the service
 * just wires them to the repositories.
 */
@Service
public class FundamentalSnapshotService {

    /** Two 7-day windows are needed to compare on-chain MAs. */
    static final int TIER1_MIN_POINTS = 14;
    static final double MOMENTUM_PCT = 5.0;          // |price_change_pct_7d| band
    static final int DEV_FLOOR = 20;                 // github_commit_count_4w above this = healthy
    static final int DEV_NEAR_ZERO = 5;              // ...at or below this = unhealthy
    static final double VOLCAP_PCT = 3.0;            // |market_cap_change_pct_24h| band
    static final int NEWS_MIN_ITEMS = 3;
    static final double NEWS_TAU_DAYS = 3.0;
    static final double NEWS_LABEL_BAND = 0.10;      // |weighted mean| above this leans pos/neg
    static final double DOMINANCE_TOLERANCE_PP = 0.05;

    private final OnchainRepository onchainRepository;
    private final FundamentalsRepository fundamentalsRepository;
    private final MarketMetaRepository marketMetaRepository;
    private final NewsRepository newsRepository;

    public FundamentalSnapshotService(OnchainRepository onchainRepository,
                                      FundamentalsRepository fundamentalsRepository,
                                      MarketMetaRepository marketMetaRepository,
                                      NewsRepository newsRepository) {
        this.onchainRepository = onchainRepository;
        this.fundamentalsRepository = fundamentalsRepository;
        this.marketMetaRepository = marketMetaRepository;
        this.newsRepository = newsRepository;
    }

    @Transactional(readOnly = true)
    public FundamentalSnapshot snapshot(String symbol) {
        return snapshot(symbol, Instant.now());
    }

    /** Snapshot as of an explicit {@code now} (lets tests pin recency windows). */
    @Transactional(readOnly = true)
    public FundamentalSnapshot snapshot(String symbol, Instant now) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        HealthResult health = computeHealth(sym);
        Dominance dominance = computeDominance(sym, now);
        NewsResult news = computeNews(sym, now);
        return new FundamentalSnapshot(sym, now, health.health(), health.source(), health.reasons(),
                dominance.pct(), dominance.trend(), news.label(), news.score());
    }

    // ---- health (two-tier) ---------------------------------------------------------------------

    private HealthResult computeHealth(String symbol) {
        List<Onchain> oc = onchainRepository.findBySymbolOrderByTsUtc(symbol);
        double[] addresses = longestSeries(oc, FundamentalSnapshotService::isAddressMetric);
        double[] volume = longestSeries(oc, FundamentalSnapshotService::isVolumeMetric);
        Optional<HealthResult> tier1 = tier1Health(addresses, volume);
        if (tier1.isPresent()) {
            return tier1.get();
        }
        Optional<Fundamentals> f = fundamentalsRepository.findFirstBySymbolOrderByTsUtcDesc(symbol);
        if (f.isPresent()) {
            Fundamentals fu = f.get();
            return tier2Health(fu.getPriceChangePct7d(), fu.getGithubCommitCount4w(),
                    fu.getMarketCapChangePct24h());
        }
        return new HealthResult("UNKNOWN", "unknown",
                List.of("No on-chain series and no CoinGecko snapshot for " + symbol));
    }

    /**
     * Tier 1 (on-chain): 7-day MA of active addresses and transfer volume, recent window vs the
     * prior window. Both rising → IMPROVING; both falling → DETERIORATING; else STABLE. Empty if
     * either series lacks two full weeks (then the caller falls through to Tier 2).
     */
    static Optional<HealthResult> tier1Health(double[] addresses, double[] volume) {
        if (addresses.length < TIER1_MIN_POINTS || volume.length < TIER1_MIN_POINTS) {
            return Optional.empty();
        }
        boolean addrUp = recentMa(addresses) > priorMa(addresses);
        boolean addrDown = recentMa(addresses) < priorMa(addresses);
        boolean volUp = recentMa(volume) > priorMa(volume);
        boolean volDown = recentMa(volume) < priorMa(volume);

        String health;
        if (addrUp && volUp) {
            health = "IMPROVING";
        } else if (addrDown && volDown) {
            health = "DETERIORATING";
        } else {
            health = "STABLE";
        }
        List<String> reasons = List.of(
                "7d-MA active addresses " + trendWord(addrUp, addrDown),
                "7d-MA transfer volume " + trendWord(volUp, volDown));
        return Optional.of(new HealthResult(health, "onchain", reasons));
    }

    /**
     * Tier 2 (CoinGecko, single snapshot): score 7d momentum, 4-week dev activity and 24h
     * market-cap change at ±1 each; ≥2 positive & 0 negative → IMPROVING, ≥2 negative & 0 positive
     * → DETERIORATING, else STABLE (PROJECT.md Stage 5 §3).
     */
    static HealthResult tier2Health(Double priceChange7d, Integer commits4w, Double marketCapChange24h) {
        List<String> reasons = new ArrayList<>();
        int pos = 0;
        int neg = 0;

        if (priceChange7d != null) {
            if (priceChange7d > MOMENTUM_PCT) {
                pos++;
                reasons.add(String.format(Locale.US, "momentum +1: 7d price %+.1f%%", priceChange7d));
            } else if (priceChange7d < -MOMENTUM_PCT) {
                neg++;
                reasons.add(String.format(Locale.US, "momentum -1: 7d price %+.1f%%", priceChange7d));
            } else {
                reasons.add(String.format(Locale.US, "momentum 0: 7d price %+.1f%%", priceChange7d));
            }
        }
        if (commits4w != null) {
            if (commits4w > DEV_FLOOR) {
                pos++;
                reasons.add(String.format(Locale.US, "dev +1: %d GitHub commits/4w", commits4w));
            } else if (commits4w <= DEV_NEAR_ZERO) {
                neg++;
                reasons.add(String.format(Locale.US, "dev -1: %d GitHub commits/4w (near zero)", commits4w));
            } else {
                reasons.add(String.format(Locale.US, "dev 0: %d GitHub commits/4w", commits4w));
            }
        }
        if (marketCapChange24h != null) {
            if (marketCapChange24h > VOLCAP_PCT) {
                pos++;
                reasons.add(String.format(Locale.US, "vol/cap +1: market cap 24h %+.1f%%", marketCapChange24h));
            } else if (marketCapChange24h < -VOLCAP_PCT) {
                neg++;
                reasons.add(String.format(Locale.US, "vol/cap -1: market cap 24h %+.1f%%", marketCapChange24h));
            } else {
                reasons.add(String.format(Locale.US, "vol/cap 0: market cap 24h %+.1f%%", marketCapChange24h));
            }
        }

        String health;
        if (pos >= 2 && neg == 0) {
            health = "IMPROVING";
        } else if (neg >= 2 && pos == 0) {
            health = "DETERIORATING";
        } else {
            health = "STABLE";
        }
        if (reasons.isEmpty()) {
            reasons.add("No CoinGecko signals available");
        }
        return new HealthResult(health, "coingecko", List.copyOf(reasons));
    }

    // ---- market dominance ----------------------------------------------------------------------

    private Dominance computeDominance(String symbol, Instant now) {
        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);
        double totalNow = 0;
        double totalThen = 0;
        Double mineNow = null;
        Double mineThen = null;
        for (String sym : Symbols.UNIVERSE) {
            Double capNow = marketMetaRepository.findFirstBySymbolOrderByTsUtcDesc(sym)
                    .map(MarketMeta::getMarketCapUsd).orElse(null);
            Double capThen = marketMetaRepository
                    .findFirstBySymbolAndTsUtcLessThanEqualOrderByTsUtcDesc(sym, weekAgo)
                    .map(MarketMeta::getMarketCapUsd).orElse(null);
            if (capNow != null) {
                totalNow += capNow;
                if (sym.equals(symbol)) {
                    mineNow = capNow;
                }
            }
            if (capThen != null) {
                totalThen += capThen;
                if (sym.equals(symbol)) {
                    mineThen = capThen;
                }
            }
        }
        Double domNow = (mineNow != null && totalNow > 0) ? mineNow / totalNow * 100.0 : null;
        Double domThen = (mineThen != null && totalThen > 0) ? mineThen / totalThen * 100.0 : null;
        String trend = null;
        if (domNow != null && domThen != null) {
            double delta = domNow - domThen;
            trend = delta > DOMINANCE_TOLERANCE_PP ? "RISING"
                    : delta < -DOMINANCE_TOLERANCE_PP ? "FALLING" : "STABLE";
        }
        return new Dominance(domNow, trend);
    }

    // ---- news sentiment ------------------------------------------------------------------------

    private NewsResult computeNews(String symbol, Instant now) {
        Instant since = now.minus(7, ChronoUnit.DAYS);
        List<News> items = newsRepository
                .findByCurrenciesContainingAndTsUtcAfterOrderByTsUtcDesc(symbol, since);
        double wsum = 0;
        double sum = 0;
        int n = 0;
        for (News item : items) {
            if (item.getSentimentScore() == null || item.getTsUtc() == null) {
                continue;
            }
            double ageDays = Math.max(0.0, (now.toEpochMilli() - item.getTsUtc().toEpochMilli()) / 86_400_000.0);
            double w = Math.exp(-ageDays / NEWS_TAU_DAYS);
            wsum += w;
            sum += w * item.getSentimentScore();
            n++;
        }
        if (n < NEWS_MIN_ITEMS) {
            return new NewsResult("INSUFFICIENT_DATA", 0.0);
        }
        double mean = wsum > 0 ? sum / wsum : 0.0;
        String label = mean > NEWS_LABEL_BAND ? "POSITIVE"
                : mean < -NEWS_LABEL_BAND ? "NEGATIVE" : "MIXED";
        return new NewsResult(label, mean);
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** The longest value series among on-chain metrics matching {@code predicate}, in ts order. */
    private static double[] longestSeries(List<Onchain> ascending, java.util.function.Predicate<String> predicate) {
        Map<String, List<Double>> byMetric = new LinkedHashMap<>();
        for (Onchain o : ascending) {
            if (o.getMetric() != null && o.getValue() != null && predicate.test(o.getMetric())) {
                byMetric.computeIfAbsent(o.getMetric(), k -> new ArrayList<>()).add(o.getValue());
            }
        }
        List<Double> best = List.of();
        for (List<Double> series : byMetric.values()) {
            if (series.size() > best.size()) {
                best = series;
            }
        }
        return best.stream().mapToDouble(Double::doubleValue).toArray();
    }

    static boolean isAddressMetric(String metric) {
        return metric.toLowerCase(Locale.ROOT).contains("address");
    }

    static boolean isVolumeMetric(String metric) {
        String m = metric.toLowerCase(Locale.ROOT);
        return m.contains("volume") || m.contains("transfer");
    }

    /** Mean of the most-recent 7 values. */
    private static double recentMa(double[] series) {
        return mean(series, series.length - 7, series.length);
    }

    /** Mean of the 7 values just before the recent window. */
    private static double priorMa(double[] series) {
        return mean(series, series.length - 14, series.length - 7);
    }

    private static double mean(double[] a, int fromInclusive, int toExclusive) {
        double s = 0;
        for (int i = fromInclusive; i < toExclusive; i++) {
            s += a[i];
        }
        return s / (toExclusive - fromInclusive);
    }

    private static String trendWord(boolean up, boolean down) {
        return up ? "rising" : down ? "falling" : "flat";
    }

    /** Internal health verdict (health label + source + the contributing reasons). */
    record HealthResult(String health, String source, List<String> reasons) {
    }

    private record Dominance(Double pct, String trend) {
    }

    private record NewsResult(String label, double score) {
    }
}
