package com.cryptocopilot.analyst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cryptocopilot.analyst.AnalystScorer.ScoreResult;
import com.cryptocopilot.analyst.FundamentalSnapshotService.HealthResult;
import com.cryptocopilot.entity.Fundamentals;
import com.cryptocopilot.entity.Onchain;
import com.cryptocopilot.repository.FundamentalsRepository;
import com.cryptocopilot.repository.MarketMetaRepository;
import com.cryptocopilot.repository.NewsRepository;
import com.cryptocopilot.repository.OnchainRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Golden tests for the deterministic Analyst (PROJECT.md Stage 5 §7): the {@link AnalystScorer}
 * fusion scenarios, the two-tier {@link FundamentalSnapshotService} health helpers, and the
 * {@code healthSource} routing (on-chain vs CoinGecko vs unknown). All pure / mocked — no live DB.
 */
class AnalystTest {

    private static final double TAU = 0.50;

    // ---- scorer golden scenarios ---------------------------------------------------------------

    @Test
    void allAlignedBullish() {   // e.g. BTC with on-chain IMPROVING
        ScoreResult r = AnalystScorer.score("UP", 0.70, "BULLISH", "STRONG", "IMPROVING", "POSITIVE", TAU);
        assertThat(r.combined()).isEqualTo(6);            // +2 +2 +1 +1
        assertThat(r.direction()).isEqualTo("LEAN_BULLISH");
        assertThat(r.conviction()).isEqualTo("HIGH");
        assertThat(r.agreementScore()).isGreaterThan(0.9);
    }

    @Test
    void allAlignedBearish() {
        ScoreResult r = AnalystScorer.score("DOWN", 0.70, "BEARISH", "STRONG", "DETERIORATING", "NEGATIVE", TAU);
        assertThat(r.combined()).isEqualTo(-6);
        assertThat(r.direction()).isEqualTo("LEAN_BEARISH");
        assertThat(r.conviction()).isEqualTo("HIGH");
    }

    @Test
    void conflictedWhenMlAndTaOppose() {
        ScoreResult r = AnalystScorer.score("UP", 0.70, "BEARISH", "STRONG", "STABLE", "INSUFFICIENT_DATA", TAU);
        assertThat(r.combined()).isEqualTo(0);            // +2 -2 0 0
        assertThat(r.direction()).isEqualTo("CONFLICTED");
        assertThat(r.conviction()).isEqualTo("LOW");
    }

    @Test
    void neutralWhenAllFlat() {
        ScoreResult r = AnalystScorer.score("FLAT", 0.49, "NEUTRAL", "WEAK", "STABLE", "MIXED", TAU);
        assertThat(r.combined()).isEqualTo(0);
        assertThat(r.direction()).isEqualTo("NEUTRAL");
        assertThat(r.conviction()).isEqualTo("LOW");
    }

    @Test
    void missingEverythingIsNeutralLowWithoutCrashing() {
        ScoreResult r = AnalystScorer.score(null, null, null, null, "UNKNOWN", "INSUFFICIENT_DATA", TAU);
        assertThat(r.combined()).isEqualTo(0);
        assertThat(r.direction()).isEqualTo("NEUTRAL");
        assertThat(r.conviction()).isEqualTo("LOW");
        assertThat(r.inputs()).extracting(InputScore::score).containsExactly(0, 0, 0, 0);
    }

    @Test
    void lowMlConfidenceScoresOneNotTwo() {
        // DOWN below τ → −1 (not −2); the calm-regime reality the brief calls out.
        ScoreResult r = AnalystScorer.score("DOWN", 0.40, "NEUTRAL", "WEAK", "STABLE", "INSUFFICIENT_DATA", TAU);
        assertThat(r.inputs().get(0).score()).isEqualTo(-1);
        assertThat(r.direction()).isEqualTo("NEUTRAL");   // sum −1, no opposing inputs
        assertThat(r.conviction()).isEqualTo("LOW");
    }

    @Test
    void mediumConvictionAtLeanBoundary() {
        // −2 (DOWN strong) −1 (TA bearish moderate) = −3 → LEAN_BEARISH, |3| MEDIUM.
        ScoreResult r = AnalystScorer.score("DOWN", 0.70, "BEARISH", "MODERATE", "STABLE", "INSUFFICIENT_DATA", TAU);
        assertThat(r.combined()).isEqualTo(-3);
        assertThat(r.direction()).isEqualTo("LEAN_BEARISH");
        assertThat(r.conviction()).isEqualTo("MEDIUM");
    }

    // ---- two-tier health helpers (pure) --------------------------------------------------------

    @Test
    void tier1BothRisingIsImproving() {
        HealthResult h = FundamentalSnapshotService.tier1Health(ramp(100, +1, 14), ramp(50, +1, 14)).orElseThrow();
        assertThat(h.health()).isEqualTo("IMPROVING");
        assertThat(h.source()).isEqualTo("onchain");
    }

    @Test
    void tier1BothFallingIsDeteriorating() {
        HealthResult h = FundamentalSnapshotService.tier1Health(ramp(113, -1, 14), ramp(80, -1, 14)).orElseThrow();
        assertThat(h.health()).isEqualTo("DETERIORATING");
    }

    @Test
    void tier1MixedIsStable() {
        HealthResult h = FundamentalSnapshotService.tier1Health(ramp(100, +1, 14), ramp(80, -1, 14)).orElseThrow();
        assertThat(h.health()).isEqualTo("STABLE");
    }

    @Test
    void tier1InsufficientHistoryFallsThrough() {
        assertThat(FundamentalSnapshotService.tier1Health(ramp(100, +1, 10), ramp(50, +1, 10))).isEmpty();
    }

    @Test
    void tier2ThreePositiveIsImproving() {
        HealthResult h = FundamentalSnapshotService.tier2Health(8.0, 200, 5.0);
        assertThat(h.health()).isEqualTo("IMPROVING");
        assertThat(h.source()).isEqualTo("coingecko");
    }

    @Test
    void tier2ThreeNegativeIsDeteriorating() {
        HealthResult h = FundamentalSnapshotService.tier2Health(-8.0, 0, -5.0);
        assertThat(h.health()).isEqualTo("DETERIORATING");
    }

    @Test
    void tier2MixedIsStable() {
        HealthResult h = FundamentalSnapshotService.tier2Health(8.0, 0, -5.0);   // +1, −1, −1
        assertThat(h.health()).isEqualTo("STABLE");
    }

    // ---- healthSource routing (service, mocked repos) ------------------------------------------

    @Test
    void onchainCoinRoutesToOnchain() {   // BTC: a real daily series → Tier 1
        FundamentalSnapshotService svc = service();
        List<Onchain> series = onchainSeries(ramp(100, +1, 14), ramp(50, +1, 14));
        when(onchainRepo.findBySymbolOrderByTsUtc("BTC")).thenReturn(series);

        FundamentalSnapshot snap = svc.snapshot("BTC", Instant.parse("2026-06-01T00:00:00Z"));

        assertThat(snap.healthSource()).isEqualTo("onchain");
        assertThat(snap.health()).isEqualTo("IMPROVING");
    }

    @Test
    void missingOnchainRoutesToCoingecko() {   // SOL: no usable on-chain series → Tier 2
        FundamentalSnapshotService svc = service();
        Fundamentals f = fundamentals(8.0, 200, 5.0);
        when(onchainRepo.findBySymbolOrderByTsUtc("SOL")).thenReturn(List.of());
        when(fundamentalsRepo.findFirstBySymbolOrderByTsUtcDesc("SOL")).thenReturn(Optional.of(f));

        FundamentalSnapshot snap = svc.snapshot("SOL", Instant.parse("2026-06-01T00:00:00Z"));

        assertThat(snap.healthSource()).isEqualTo("coingecko");
        assertThat(snap.health()).isEqualTo("IMPROVING");
    }

    @Test
    void noDataRoutesToUnknown() {
        FundamentalSnapshotService svc = service();
        when(onchainRepo.findBySymbolOrderByTsUtc("XYZ")).thenReturn(List.of());
        when(fundamentalsRepo.findFirstBySymbolOrderByTsUtcDesc("XYZ")).thenReturn(Optional.empty());

        FundamentalSnapshot snap = svc.snapshot("XYZ", Instant.parse("2026-06-01T00:00:00Z"));

        assertThat(snap.healthSource()).isEqualTo("unknown");
        assertThat(snap.health()).isEqualTo("UNKNOWN");
    }

    // ---- fixtures ------------------------------------------------------------------------------

    private OnchainRepository onchainRepo;
    private FundamentalsRepository fundamentalsRepo;
    private MarketMetaRepository marketMetaRepo;
    private NewsRepository newsRepo;

    private FundamentalSnapshotService service() {
        onchainRepo = mock(OnchainRepository.class);
        fundamentalsRepo = mock(FundamentalsRepository.class);
        marketMetaRepo = mock(MarketMetaRepository.class);
        newsRepo = mock(NewsRepository.class);
        return new FundamentalSnapshotService(onchainRepo, fundamentalsRepo, marketMetaRepo, newsRepo);
    }

    private static double[] ramp(double start, double step, int n) {
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = start + step * i;
        }
        return a;
    }

    private static List<Onchain> onchainSeries(double[] addresses, double[] volume) {
        List<Onchain> list = new ArrayList<>();
        for (double v : addresses) {
            list.add(onchain("n-unique-addresses", v));
        }
        for (double v : volume) {
            list.add(onchain("estimated-transaction-volume-usd", v));
        }
        return list;
    }

    private static Onchain onchain(String metric, double value) {
        Onchain o = mock(Onchain.class);
        when(o.getMetric()).thenReturn(metric);
        when(o.getValue()).thenReturn(value);
        return o;
    }

    private static Fundamentals fundamentals(Double priceChange7d, Integer commits4w, Double mcap24h) {
        Fundamentals f = mock(Fundamentals.class);
        when(f.getPriceChangePct7d()).thenReturn(priceChange7d);
        when(f.getGithubCommitCount4w()).thenReturn(commits4w);
        when(f.getMarketCapChangePct24h()).thenReturn(mcap24h);
        return f;
    }
}
