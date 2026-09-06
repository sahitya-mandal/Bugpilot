package com.bugpilot.entity;

import com.bugpilot.enums.RiskLevel;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pull_request_analyses")
public class PullRequestAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false, unique = true)
    private PullRequest pullRequest;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "potential_bugs", columnDefinition = "TEXT")
    private String potentialBugs;

    @Column(name = "code_quality_concerns", columnDefinition = "TEXT")
    private String codeQualityConcerns;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel = RiskLevel.MEDIUM;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    public PullRequestAnalysis() {
        this.analyzedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PullRequest getPullRequest() {
        return pullRequest;
    }

    public void setPullRequest(PullRequest pullRequest) {
        this.pullRequest = pullRequest;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPotentialBugs() {
        return potentialBugs;
    }

    public void setPotentialBugs(String potentialBugs) {
        this.potentialBugs = potentialBugs;
    }

    public String getCodeQualityConcerns() {
        return codeQualityConcerns;
    }

    public void setCodeQualityConcerns(String codeQualityConcerns) {
        this.codeQualityConcerns = codeQualityConcerns;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }
}
