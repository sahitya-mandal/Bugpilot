package com.bugpilot.service;

import com.bugpilot.dto.AnalyticsResponse;
import com.bugpilot.entity.Repository;
import com.bugpilot.enums.AnalysisSeverity;
import com.bugpilot.enums.IssueState;
import com.bugpilot.enums.PullRequestState;
import com.bugpilot.enums.RiskLevel;
import com.bugpilot.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private RepoRepository repoRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private BugAnalysisRepository bugAnalysisRepository;

    @Mock
    private PullRequestAnalysisRepository pullRequestAnalysisRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(
                repoRepository,
                issueRepository,
                pullRequestRepository,
                commitRepository,
                bugAnalysisRepository,
                pullRequestAnalysisRepository
        );
    }

    @Test
    void getRepositoryAnalytics_computesCorrectCounts() {
        Repository repo = new Repository();
        repo.setId(1L);
        repo.setFullName("sahitya/bugpilot");

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));
        when(issueRepository.countByRepositoryId(1L)).thenReturn(10L);
        when(issueRepository.countByRepositoryIdAndState(1L, IssueState.OPEN)).thenReturn(6L);
        when(issueRepository.countByRepositoryIdAndState(1L, IssueState.CLOSED)).thenReturn(4L);

        when(pullRequestRepository.countByRepositoryId(1L)).thenReturn(5L);
        when(pullRequestRepository.countByRepositoryIdAndState(1L, PullRequestState.OPEN)).thenReturn(2L);
        when(pullRequestRepository.countByRepositoryIdAndState(1L, PullRequestState.MERGED)).thenReturn(3L);

        when(commitRepository.countByRepositoryId(1L)).thenReturn(50L);
        when(commitRepository.countDistinctAuthorsByRepositoryId(1L)).thenReturn(3L);

        when(bugAnalysisRepository.countByIssueRepositoryId(1L)).thenReturn(4L);
        when(bugAnalysisRepository.countByIssueRepositoryIdAndSeverity(eq(1L), any(AnalysisSeverity.class))).thenReturn(1L);
        when(pullRequestAnalysisRepository.countByPullRequestRepositoryIdAndRiskLevel(eq(1L), any(RiskLevel.class))).thenReturn(1L);

        AnalyticsResponse response = analyticsService.getRepositoryAnalytics(1L);

        assertNotNull(response);
        assertEquals(1L, response.getRepositoryId());
        assertEquals("sahitya/bugpilot", response.getRepositoryName());
        assertEquals(10L, response.getTotalIssues());
        assertEquals(6L, response.getOpenIssues());
        assertEquals(4L, response.getClosedIssues());
        assertEquals(5L, response.getTotalPullRequests());
        assertEquals(50L, response.getTotalCommits());
        assertEquals(3L, response.getTotalContributors());
        assertEquals(4L, response.getAnalyzedIssuesCount());
    }
}
