package com.bugpilot.service;

import com.bugpilot.dto.AnalyticsResponse;
import com.bugpilot.entity.Repository;
import com.bugpilot.enums.AnalysisSeverity;
import com.bugpilot.enums.IssueState;
import com.bugpilot.enums.PullRequestState;
import com.bugpilot.enums.RiskLevel;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final RepoRepository repoRepository;
    private final IssueRepository issueRepository;
    private final PullRequestRepository pullRequestRepository;
    private final CommitRepository commitRepository;
    private final BugAnalysisRepository bugAnalysisRepository;
    private final PullRequestAnalysisRepository pullRequestAnalysisRepository;

    public AnalyticsService(RepoRepository repoRepository,
                            IssueRepository issueRepository,
                            PullRequestRepository pullRequestRepository,
                            CommitRepository commitRepository,
                            BugAnalysisRepository bugAnalysisRepository,
                            PullRequestAnalysisRepository pullRequestAnalysisRepository) {
        this.repoRepository = repoRepository;
        this.issueRepository = issueRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.commitRepository = commitRepository;
        this.bugAnalysisRepository = bugAnalysisRepository;
        this.pullRequestAnalysisRepository = pullRequestAnalysisRepository;
    }

    public AnalyticsResponse getRepositoryAnalytics(Long repositoryId) {
        Repository repo = repoRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", repositoryId));

        AnalyticsResponse res = new AnalyticsResponse();
        res.setRepositoryId(repo.getId());
        res.setRepositoryName(repo.getFullName());

        res.setTotalIssues(issueRepository.countByRepositoryId(repositoryId));
        res.setOpenIssues(issueRepository.countByRepositoryIdAndState(repositoryId, IssueState.OPEN));
        res.setClosedIssues(issueRepository.countByRepositoryIdAndState(repositoryId, IssueState.CLOSED));

        res.setTotalPullRequests(pullRequestRepository.countByRepositoryId(repositoryId));
        res.setOpenPullRequests(pullRequestRepository.countByRepositoryIdAndState(repositoryId, PullRequestState.OPEN));
        res.setMergedPullRequests(pullRequestRepository.countByRepositoryIdAndState(repositoryId, PullRequestState.MERGED));

        res.setTotalCommits(commitRepository.countByRepositoryId(repositoryId));
        res.setTotalContributors(commitRepository.countDistinctAuthorsByRepositoryId(repositoryId));
        res.setAnalyzedIssuesCount(bugAnalysisRepository.countByIssueRepositoryId(repositoryId));

        // Severity distribution
        Map<String, Long> severityDist = new HashMap<>();
        for (AnalysisSeverity s : AnalysisSeverity.values()) {
            long count = bugAnalysisRepository.countByIssueRepositoryIdAndSeverity(repositoryId, s);
            severityDist.put(s.name(), count);
        }
        res.setIssueSeverityDistribution(severityDist);

        // PR risk distribution
        Map<String, Long> riskDist = new HashMap<>();
        for (RiskLevel r : RiskLevel.values()) {
            long count = pullRequestAnalysisRepository.countByPullRequestRepositoryIdAndRiskLevel(repositoryId, r);
            riskDist.put(r.name(), count);
        }
        res.setPrRiskDistribution(riskDist);

        return res;
    }
}
