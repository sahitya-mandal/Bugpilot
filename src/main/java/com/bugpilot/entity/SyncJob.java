package com.bugpilot.entity;

import com.bugpilot.enums.SyncJobStatus;
import com.bugpilot.enums.SyncJobStep;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sync_jobs")
public class SyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by_id", nullable = false)
    private User triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SyncJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 50)
    private SyncJobStep currentStep;

    @Column(name = "issues_imported")
    private Integer issuesImported = 0;

    @Column(name = "pull_requests_imported")
    private Integer pullRequestsImported = 0;

    @Column(name = "commits_imported")
    private Integer commitsImported = 0;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "github_status")
    private Integer githubStatus;

    @Column(name = "rate_limit_reset")
    private Long rateLimitReset;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public SyncJob() {
    }

    public SyncJob(Repository repository, User triggeredBy) {
        this.repository = repository;
        this.triggeredBy = triggeredBy;
        this.status = SyncJobStatus.QUEUED;
        this.currentStep = SyncJobStep.QUEUED;
        this.issuesImported = 0;
        this.pullRequestsImported = 0;
        this.commitsImported = 0;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = SyncJobStatus.QUEUED;
        }
        if (this.currentStep == null) {
            this.currentStep = SyncJobStep.QUEUED;
        }
        if (this.issuesImported == null) {
            this.issuesImported = 0;
        }
        if (this.pullRequestsImported == null) {
            this.pullRequestsImported = 0;
        }
        if (this.commitsImported == null) {
            this.commitsImported = 0;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public User getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(User triggeredBy) {
        this.triggeredBy = triggeredBy;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
}
