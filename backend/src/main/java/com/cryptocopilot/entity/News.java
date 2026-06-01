package com.cryptocopilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A news item (RSS). Python-owned, read-only. Maps to {@code news}.
 * {@code currencies} is a CSV of tagged symbols (e.g. {@code "BTC,ETH"}).
 */
@Entity
@Table(name = "news")
public class News {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "ts_utc", nullable = false)
    private Instant tsUtc;

    @Column(name = "title")
    private String title;

    @Column(name = "summary")
    private String summary;

    @Column(name = "source")
    private String source;

    @Column(name = "url")
    private String url;

    @Column(name = "currencies")
    private String currencies;

    @Column(name = "sentiment")
    private String sentiment;

    @Column(name = "sentiment_score")
    private Double sentimentScore;

    protected News() {
    }

    public String getId() {
        return id;
    }

    public Instant getTsUtc() {
        return tsUtc;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getSource() {
        return source;
    }

    public String getUrl() {
        return url;
    }

    public String getCurrencies() {
        return currencies;
    }

    public String getSentiment() {
        return sentiment;
    }

    public Double getSentimentScore() {
        return sentimentScore;
    }
}
