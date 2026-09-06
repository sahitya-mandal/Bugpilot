package com.bugpilot.dto;

import com.bugpilot.enums.PullRequestState;

import java.time.LocalDateTime;

public class PullRequestResponse {

    private Long id;
    private Long githubId;
    private Integer number;
    private String title;
    private String body;
    private PullRequestState state;
    private Boolean draft;
    private String author;
    private String htmlUrl;
    private String sourceBranch;
    private String targetBranch;
    private Integer additions;
    private Integer deletions;
    private Integer changedFiles;
    private LocalDateTime githubCreatedAt;
    private LocalDateTime githubUpdatedAt;
    private LocalDateTime githubClosedAt;
    private LocalDateTime githubMergedAt;
    private Long repositoryId;
    private boolean hasAiAnalysis;

    public PullRequestResponse() {
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

    public PullRequestState getState() {
        return state;
    }

    public void setState(PullRequestState state) {
        this.state = state;
    }

    public Boolean getDraft() {
        return draft;
    }

    public void setDraft(Boolean draft) {
        this.draft = draft;
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

    public String getSourceBranch() {
        return sourceBranch;
    }

    public void setSourceBranch(String sourceBranch) {
        this.sourceBranch = sourceBranch;
    }

    public String getTargetBranch() {
        return targetBranch;
    }

    public void setTargetBranch(String targetBranch) {
        this.targetBranch = targetBranch;
    }

    public Integer getAdditions() {
        return additions;
    }

    public void setAdditions(Integer additions) {
        this.additions = additions;
    }

    public Integer getDeletions() {
        return deletions;
    }

    public void setDeletions(Integer deletions) {
        this.deletions = deletions;
    }

    public Integer getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(Integer changedFiles) {
        this.changedFiles = changedFiles;
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

    public LocalDateTime getGithubMergedAt() {
        return githubMergedAt;
    }

    public void setGithubMergedAt(LocalDateTime githubMergedAt) {
        this.githubMergedAt = githubMergedAt;
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
}
