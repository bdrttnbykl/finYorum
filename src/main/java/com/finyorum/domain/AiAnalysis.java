package com.finyorum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ai_analyses")
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String recommendation;

    @Column(nullable = false, length = 4000)
    private String summary;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(length = 32)
    private String provider;

    @Column(length = 128)
    private String model;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AiAnalysis() {
    }

    public AiAnalysis(String symbol, String recommendation, String summary) {
        this.symbol = symbol;
        this.recommendation = recommendation;
        this.summary = summary;
    }

    public AiAnalysis(Long snapshotId,
                      String symbol,
                      String provider,
                      String model,
                      String recommendation,
                      String summary) {
        this.snapshotId = snapshotId;
        this.symbol = symbol;
        this.provider = provider;
        this.model = model;
        this.recommendation = recommendation;
        this.summary = summary;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public String getSummary() {
        return summary;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
