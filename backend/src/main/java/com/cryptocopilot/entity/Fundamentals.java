package com.cryptocopilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Per-coin fundamental snapshot (CoinGecko community + developer + market data).
 * Python-owned, read-only. Maps to {@code fundamentals}.
 */
@Entity
@Table(name = "fundamentals")
@IdClass(FundamentalsId.class)
public class Fundamentals {

    @Id
    @Column(name = "ts_utc", nullable = false)
    private Instant tsUtc;

    @Id
    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "price_change_pct_24h")
    private Double priceChangePct24h;

    @Column(name = "price_change_pct_7d")
    private Double priceChangePct7d;

    @Column(name = "price_change_pct_30d")
    private Double priceChangePct30d;

    @Column(name = "total_volume_usd")
    private Double totalVolumeUsd;

    @Column(name = "market_cap_change_pct_24h")
    private Double marketCapChangePct24h;

    @Column(name = "reddit_subscribers")
    private Integer redditSubscribers;

    @Column(name = "reddit_active_48h")
    private Integer redditActive48h;

    @Column(name = "reddit_avg_posts_48h")
    private Double redditAvgPosts48h;

    @Column(name = "twitter_followers")
    private Integer twitterFollowers;

    @Column(name = "github_commit_count_4w")
    private Integer githubCommitCount4w;

    @Column(name = "github_prs_merged")
    private Integer githubPrsMerged;

    @Column(name = "github_code_additions_4w")
    private Integer githubCodeAdditions4w;

    @Column(name = "github_code_deletions_4w")
    private Integer githubCodeDeletions4w;

    protected Fundamentals() {
    }

    public Instant getTsUtc() {
        return tsUtc;
    }

    public String getSymbol() {
        return symbol;
    }

    public Double getPriceChangePct24h() {
        return priceChangePct24h;
    }

    public Double getPriceChangePct7d() {
        return priceChangePct7d;
    }

    public Double getPriceChangePct30d() {
        return priceChangePct30d;
    }

    public Double getTotalVolumeUsd() {
        return totalVolumeUsd;
    }

    public Double getMarketCapChangePct24h() {
        return marketCapChangePct24h;
    }

    public Integer getRedditSubscribers() {
        return redditSubscribers;
    }

    public Integer getRedditActive48h() {
        return redditActive48h;
    }

    public Double getRedditAvgPosts48h() {
        return redditAvgPosts48h;
    }

    public Integer getTwitterFollowers() {
        return twitterFollowers;
    }

    public Integer getGithubCommitCount4w() {
        return githubCommitCount4w;
    }

    public Integer getGithubPrsMerged() {
        return githubPrsMerged;
    }

    public Integer getGithubCodeAdditions4w() {
        return githubCodeAdditions4w;
    }

    public Integer getGithubCodeDeletions4w() {
        return githubCodeDeletions4w;
    }
}
