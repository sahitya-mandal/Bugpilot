package com.bugpilot.dto;

import com.bugpilot.enums.RiskLevel;

import java.time.LocalDateTime;

public class PullRequestAnalysisResponse {

    private Long id;
    private Long pullRequestId;
    private Integer pullRequestNumber;
    private String pullRequestTitle;
    private String summary;
    private String potentialBugs;
    private String codeQualityConcerns;
    private RiskLevel riskLevel;
    private String recommendations;
    private LocalDateTime analyzedAt;

    public PullRequestAnalysisResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPullRequestId() {
        return pullRequestId;
    }

    public void setPullRequestId(Long pullRequestId) {
        this.pullRequestId = pullRequestId;
    }

    public Integer getPullRequestNumber() {
        return pullRequestNumber;
    }

    public void setPullRequestNumber(Integer pullRequestNumber) {
        this.pullRequestNumber = pullRequestNumber;
    }

    public String getPullRequestTitle() {
        return pullRequestTitle;
    }

    public void setPullRequestTitle(String pullRequestTitle) {
        this.pullRequestTitle = pullRequestTitle;
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
