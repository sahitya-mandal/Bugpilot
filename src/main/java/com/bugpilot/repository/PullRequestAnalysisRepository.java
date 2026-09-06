package com.bugpilot.repository;

import com.bugpilot.entity.PullRequestAnalysis;
import com.bugpilot.enums.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PullRequestAnalysisRepository extends JpaRepository<PullRequestAnalysis, Long> {

    Optional<PullRequestAnalysis> findByPullRequestId(Long pullRequestId);

    long countByPullRequestRepositoryId(Long repositoryId);

    long countByPullRequestRepositoryIdAndRiskLevel(Long repositoryId, RiskLevel riskLevel);
}
