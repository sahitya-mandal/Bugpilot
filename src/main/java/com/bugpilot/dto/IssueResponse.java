package com.bugpilot.dto;

import com.bugpilot.enums.AnalysisSeverity;
import com.bugpilot.enums.IssueState;

import java.time.LocalDateTime;

public class IssueResponse {

    private Long id;
    private Long githubId;
    private Integer number;
    private String title;
    private String body;
    private IssueState state;
    private String author;
    private String htmlUrl;
    private LocalDateTime githubCreatedAt;
    private LocalDateTime githubUpdatedAt;
    private LocalDateTime githubClosedAt;
    private Long repositoryId;
    private boolean hasAiAnalysis;
    private AnalysisSeverity aiSeverity;

    public IssueResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGithubId() {
        return githubId;
    }

    public void setGithubId(Long githubId) {
        this.githubId = githubId;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public IssueState getState() {
        return state;
    }

    public void setState(IssueState state) {
        this.state = state;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public LocalDateTime getGithubCreatedAt() {
        return githubCreatedAt;
    }

    public void setGithubCreatedAt(LocalDateTime githubCreatedAt) {
        this.githubCreatedAt = githubCreatedAt;
    }

    public LocalDateTime getGithubUpdatedAt() {
        return githubUpdatedAt;
    }

    public void setGithubUpdatedAt(LocalDateTime githubUpdatedAt) {
        this.githubUpdatedAt = githubUpdatedAt;
    }

    public LocalDateTime getGithubClosedAt() {
        return githubClosedAt;
    }

    public void setGithubClosedAt(LocalDateTime githubClosedAt) {
        this.githubClosedAt = githubClosedAt;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(Long repositoryId) {
        this.repositoryId = repositoryId;
    }

    public boolean isHasAiAnalysis() {
        return hasAiAnalysis;
    }

    public void setHasAiAnalysis(boolean hasAiAnalysis) {
        this.hasAiAnalysis = hasAiAnalysis;
    }

    public AnalysisSeverity getAiSeverity() {
        return aiSeverity;
    }

    public void setAiSeverity(AnalysisSeverity aiSeverity) {
        this.aiSeverity = aiSeverity;
    }
}
