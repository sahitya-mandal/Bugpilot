package com.bugpilot.dto;

import com.bugpilot.entity.SyncJob;
import com.bugpilot.enums.SyncJobStatus;
import com.bugpilot.enums.SyncJobStep;

import java.time.LocalDateTime;

public class SyncJobResponse {

    private Long jobId;
    private Long repositoryId;
    private String repositoryName;
    private SyncJobStatus status;
    private SyncJobStep currentStep;
    private Integer issuesImported;
    private Integer pullRequestsImported;
    private Integer commitsImported;
    private Integer githubStatus;
    private Long rateLimitReset;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String message;

    public SyncJobResponse() {
    }

    public static SyncJobResponse fromEntity(SyncJob job, String customMessage) {
        if (job == null) {
            return null;
        }
        SyncJobResponse response = new SyncJobResponse();
        response.setJobId(job.getId());
        if (job.getRepository() != null) {
            response.setRepositoryId(job.getRepository().getId());
            response.setRepositoryName(job.getRepository().getName());
        }
        response.setStatus(job.getStatus());
        response.setCurrentStep(job.getCurrentStep());
        response.setIssuesImported(job.getIssuesImported());
        response.setPullRequestsImported(job.getPullRequestsImported());
        response.setCommitsImported(job.getCommitsImported());
        response.setGithubStatus(job.getGithubStatus());
        response.setRateLimitReset(job.getRateLimitReset());
        response.setErrorMessage(job.getErrorMessage());
        response.setCreatedAt(job.getCreatedAt());
        response.setStartedAt(job.getStartedAt());
        response.setCompletedAt(job.getCompletedAt());
        response.setMessage(customMessage);
        return response;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
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

    public SyncJobStatus getStatus() {
        return status;
    }

    public void setStatus(SyncJobStatus status) {
        this.status = status;
    }

    public SyncJobStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(SyncJobStep currentStep) {
        this.currentStep = currentStep;
    }

    public Integer getIssuesImported() {
        return issuesImported;
    }

    public void setIssuesImported(Integer issuesImported) {
        this.issuesImported = issuesImported;
    }

    public Integer getPullRequestsImported() {
        return pullRequestsImported;
    }

    public void setPullRequestsImported(Integer pullRequestsImported) {
        this.pullRequestsImported = pullRequestsImported;
    }

    public Integer getCommitsImported() {
        return commitsImported;
    }

    public void setCommitsImported(Integer commitsImported) {
        this.commitsImported = commitsImported;
    }

    public Integer getGithubStatus() {
        return githubStatus;
    }

    public void setGithubStatus(Integer githubStatus) {
        this.githubStatus = githubStatus;
    }

    public Long getRateLimitReset() {
        return rateLimitReset;
    }

    public void setRateLimitReset(Long rateLimitReset) {
        this.rateLimitReset = rateLimitReset;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
