package com.bugpilot.entity;

import com.bugpilot.enums.AnalysisSeverity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bug_analyses")
public class BugAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false, unique = true)
    private Issue issue;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "probable_root_cause", columnDefinition = "TEXT")
    private String probableRootCause;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisSeverity severity = AnalysisSeverity.MEDIUM;

    @Column(name = "suggested_fix", columnDefinition = "TEXT")
    private String suggestedFix;

    @Column(name = "affected_area")
    private String affectedArea;

    @Column(name = "recommended_next_steps", columnDefinition = "TEXT")
    private String recommendedNextSteps;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    public BugAnalysis() {
        this.analyzedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Issue getIssue() {
        return issue;
    }

    public void setIssue(Issue issue) {
        this.issue = issue;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getProbableRootCause() {
        return probableRootCause;
    }

    public void setProbableRootCause(String probableRootCause) {
        this.probableRootCause = probableRootCause;
    }

    public AnalysisSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AnalysisSeverity severity) {
        this.severity = severity;
    }

    public String getSuggestedFix() {
        return suggestedFix;
    }

    public void setSuggestedFix(String suggestedFix) {
        this.suggestedFix = suggestedFix;
    }

    public String getAffectedArea() {
        return affectedArea;
    }

    public void setAffectedArea(String affectedArea) {
        this.affectedArea = affectedArea;
    }

    public String getRecommendedNextSteps() {
        return recommendedNextSteps;
    }

    public void setRecommendedNextSteps(String recommendedNextSteps) {
        this.recommendedNextSteps = recommendedNextSteps;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }
}
