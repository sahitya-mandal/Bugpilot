package com.bugpilot.dto;

import java.util.Map;

public class AnalyticsResponse {

    private Long repositoryId;
    private String repositoryName;
    private long totalIssues;
    private long openIssues;
    private long closedIssues;
    private long totalPullRequests;
    private long openPullRequests;
    private long mergedPullRequests;
    private long totalCommits;
    private long totalContributors;
    private long analyzedIssuesCount;
    private Map<String, Long> issueSeverityDistribution;
    private Map<String, Long> prRiskDistribution;

    public AnalyticsResponse() {
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(Long repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public long getTotalIssues() {
        return totalIssues;
    }

    public void setTotalIssues(long totalIssues) {
        this.totalIssues = totalIssues;
    }

    public long getOpenIssues() {
        return openIssues;
    }

    public void setOpenIssues(long openIssues) {
        this.openIssues = openIssues;
    }

    public long getClosedIssues() {
        return closedIssues;
    }

    public void setClosedIssues(long closedIssues) {
        this.closedIssues = closedIssues;
    }

    public long getTotalPullRequests() {
        return totalPullRequests;
    }

    public void setTotalPullRequests(long totalPullRequests) {
        this.totalPullRequests = totalPullRequests;
    }

    public long getOpenPullRequests() {
        return openPullRequests;
    }

    public void setOpenPullRequests(long openPullRequests) {
        this.openPullRequests = openPullRequests;
    }

    public long getMergedPullRequests() {
        return mergedPullRequests;
    }

    public void setMergedPullRequests(long mergedPullRequests) {
        this.mergedPullRequests = mergedPullRequests;
    }

    public long getTotalCommits() {
        return totalCommits;
    }

    public void setTotalCommits(long totalCommits) {
        this.totalCommits = totalCommits;
    }

    public long getTotalContributors() {
        return totalContributors;
    }

    public void setTotalContributors(long totalContributors) {
        this.totalContributors = totalContributors;
    }

    public Map<String, Long> getIssueSeverityDistribution() {
        return issueSeverityDistribution;
    }

    public void setIssueSeverityDistribution(Map<String, Long> issueSeverityDistribution) {
        this.issueSeverityDistribution = issueSeverityDistribution;
    }

    public Map<String, Long> getPrRiskDistribution() {
        return prRiskDistribution;
    }

    public void setPrRiskDistribution(Map<String, Long> prRiskDistribution) {
        this.prRiskDistribution = prRiskDistribution;
    }

    public long getAnalyzedIssuesCount() {
        return analyzedIssuesCount;
    }

    public void setAnalyzedIssuesCount(long analyzedIssuesCount) {
        this.analyzedIssuesCount = analyzedIssuesCount;
    }
}
