package com.bugpilot.repository;

import com.bugpilot.entity.BugAnalysis;
import com.bugpilot.enums.AnalysisSeverity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BugAnalysisRepository extends JpaRepository<BugAnalysis, Long> {

    Optional<BugAnalysis> findByIssueId(Long issueId);

    long countByIssueRepositoryId(Long repositoryId);

    long countByIssueRepositoryIdAndSeverity(Long repositoryId, AnalysisSeverity severity);
}
