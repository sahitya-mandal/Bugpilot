package com.bugpilot.service;

import com.bugpilot.entity.PullRequest;
import com.bugpilot.entity.Repository;
import com.bugpilot.entity.SyncJob;
import com.bugpilot.entity.User;
import com.bugpilot.enums.Role;
import com.bugpilot.enums.SyncJobStep;
import com.bugpilot.exception.GitHubApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepositorySyncWorkerTest {

    @Mock
    private GitHubService gitHubService;

    @Mock
    private RepositorySyncPersistenceService persistenceService;

    @InjectMocks
    private RepositorySyncWorker worker;

    private SyncJob job;
    private Repository repository;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(10L);
        user.setName("Dev User");
        user.setEmail("dev@bugpilot.com");
        user.setRole(Role.DEVELOPER);

        repository = new Repository();
        repository.setId(1L);
        repository.setOwner("octocat");
        repository.setName("Hello-World");
        repository.setUser(user);

        job = new SyncJob(repository, user);
        job.setId(100L);
        lenient().when(gitHubService.shouldFetchPullRequestDetails(any(), any())).thenCallRealMethod();
        lenient().when(gitHubService.parseDateTime(anyString())).thenCallRealMethod();
    }

    @Test
    void executeSync_successfulLifecycle_fetchesOutsideTxnAndPersistsInShortTxns() {
        when(persistenceService.findJobByIdWithAssociations(100L)).thenReturn(job);
        when(gitHubService.fetchRepositoryMetadata("octocat", "Hello-World"))
                .thenReturn(Map.of("full_name", "octocat/Hello-World", "id", 12345));
        when(gitHubService.getPageSize()).thenReturn(100);

        when(gitHubService.fetchIssuesPage(eq("octocat"), eq("Hello-World"), eq(1), eq(100)))
                .thenReturn(List.of(Map.of("number", 1, "title", "Issue 1")));
        when(persistenceService.persistIssues(eq(1L), anyList())).thenReturn(1);

        Map<String, Object> prMap = new java.util.HashMap<>();
        prMap.put("number", 10);
        prMap.put("title", "PR 10");
        when(gitHubService.fetchPullRequestsPage(eq("octocat"), eq("Hello-World"), eq(1), eq(100)))
                .thenReturn(List.of(prMap));
        when(gitHubService.fetchPullRequestDetails("octocat", "Hello-World", 10))
                .thenReturn(Map.of("additions", 50, "deletions", 20, "changed_files", 3));
        when(persistenceService.persistPullRequests(eq(1L), anyList())).thenReturn(1);

        when(gitHubService.fetchCommitsPage(eq("octocat"), eq("Hello-World"), eq(1), eq(100)))
                .thenReturn(List.of(Map.of("sha", "abc12345")));
        when(persistenceService.persistCommits(eq(1L), anyList())).thenReturn(1);

        worker.executeSync(100L);

        verify(persistenceService).markJobInProgress(100L);
        verify(persistenceService).updateJobStep(100L, SyncJobStep.FETCHING_METADATA);
        verify(persistenceService).persistRepositoryMetadata(eq(1L), anyMap(), eq("dev@bugpilot.com"));

        verify(persistenceService).updateJobStep(100L, SyncJobStep.FETCHING_ISSUES);
        verify(persistenceService).persistIssues(eq(1L), anyList());

        verify(persistenceService).updateJobStep(100L, SyncJobStep.FETCHING_PULL_REQUESTS);
        verify(gitHubService).fetchPullRequestDetails("octocat", "Hello-World", 10);
        verify(persistenceService).persistPullRequests(eq(1L), anyList());

        verify(persistenceService).updateJobStep(100L, SyncJobStep.FETCHING_COMMITS);
        verify(persistenceService).persistCommits(eq(1L), anyList());

        verify(persistenceService).markJobCompleted(100L, 1L, "Dev User");
        verify(persistenceService, never()).markJobFailed(anyLong(), anyString(), any(), any());
    }

    @Test
    void executeSync_whenGitHub401_marksJobFailedWithoutExposingSecret() {
        when(persistenceService.findJobByIdWithAssociations(100L)).thenReturn(job);
        when(gitHubService.fetchRepositoryMetadata("octocat", "Hello-World"))
                .thenThrow(new GitHubApiException("GitHub API authentication failed: Invalid or expired GitHub credentials", 401));
        when(gitHubService.sanitize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        worker.executeSync(100L);

        verify(persistenceService).markJobFailed(
                eq(100L),
                contains("authentication failed"),
                eq(401),
                isNull()
        );
        verify(persistenceService, never()).markJobCompleted(anyLong(), anyLong(), anyString());
    }

    @Test
    void executeSync_whenGitHub429RateLimit_marksJobFailedWithResetTime() {
        when(persistenceService.findJobByIdWithAssociations(100L)).thenReturn(job);
        GitHubApiException rateLimitEx = new GitHubApiException("GitHub API rate limit exceeded", 429, true, 0, 1741234567L, 60L);
        when(gitHubService.fetchRepositoryMetadata("octocat", "Hello-World")).thenThrow(rateLimitEx);
        when(gitHubService.sanitize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        worker.executeSync(100L);

        verify(persistenceService).markJobFailed(
                eq(100L),
                contains("rate limit exceeded"),
                eq(429),
                eq(1741234567L)
        );
    }

    @Test
    void executeSync_whenPRDetailFails_marksJobFailed() {
        when(persistenceService.findJobByIdWithAssociations(100L)).thenReturn(job);
        when(gitHubService.fetchRepositoryMetadata("octocat", "Hello-World")).thenReturn(Map.of("full_name", "octocat/Hello-World"));
        when(gitHubService.getPageSize()).thenReturn(100);
        when(gitHubService.fetchIssuesPage(anyString(), anyString(), eq(1), eq(100))).thenReturn(List.of());

        Map<String, Object> prMap = new java.util.HashMap<>();
        prMap.put("number", 42);
        when(gitHubService.fetchPullRequestsPage(anyString(), anyString(), eq(1), eq(100)))
                .thenReturn(List.of(prMap));
        when(gitHubService.fetchPullRequestDetails("octocat", "Hello-World", 42))
                .thenThrow(new GitHubApiException("GitHub repository or resource not found: /repos/octocat/Hello-World/pulls/42", 404));
        when(gitHubService.sanitize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        worker.executeSync(100L);

        verify(persistenceService).markJobFailed(
                eq(100L),
                contains("not found"),
                eq(404),
                isNull()
        );
        verify(persistenceService, never()).markJobCompleted(anyLong(), anyLong(), anyString());
    }

    @Test
    void executeSync_sanitizesTokenInErrorMessage() {
        when(persistenceService.findJobByIdWithAssociations(100L)).thenReturn(job);
        when(gitHubService.fetchRepositoryMetadata("octocat", "Hello-World"))
                .thenThrow(new RuntimeException("Error with token Bearer ghp_SuperSecret12345ABCDE"));
        when(gitHubService.sanitize(anyString()))
                .thenReturn("Error with token Bearer [REDACTED]");

        worker.executeSync(100L);

        verify(persistenceService).markJobFailed(
                eq(100L),
                eq("Error with token Bearer [REDACTED]"),
                eq(502),
                isNull()
        );
    }

    @Test
    void executeSync_whenPRUnchanged_skipsDetailEndpoint() {
        when(persistenceService.findJobByIdWithAssociations(100L)).thenReturn(job);
        when(gitHubService.fetchRepositoryMetadata("octocat", "Hello-World"))
                .thenReturn(Map.of("full_name", "octocat/Hello-World", "id", 12345));
        when(gitHubService.getPageSize()).thenReturn(100);
        when(gitHubService.fetchIssuesPage(eq("octocat"), eq("Hello-World"), eq(1), eq(100)))
                .thenReturn(List.of());

        PullRequest existingPr = new PullRequest();
        existingPr.setId(10L);
        existingPr.setNumber(10);
        existingPr.setAdditions(50);
        existingPr.setDeletions(20);
        existingPr.setChangedFiles(3);
        LocalDateTime date = gitHubService.parseDateTime("2026-01-01T10:00:00Z");
        existingPr.setGithubUpdatedAt(date);

        when(persistenceService.getExistingPullRequestsMap(1L)).thenReturn(Map.of(10, existingPr));

        Map<String, Object> prMap = new java.util.HashMap<>();
        prMap.put("number", 10);
        prMap.put("title", "PR 10");
        prMap.put("updated_at", "2026-01-01T10:00:00Z");

        when(gitHubService.fetchPullRequestsPage(eq("octocat"), eq("Hello-World"), eq(1), eq(100)))
                .thenReturn(List.of(prMap));
        when(gitHubService.fetchCommitsPage(eq("octocat"), eq("Hello-World"), eq(1), eq(100)))
                .thenReturn(List.of());

        worker.executeSync(100L);

        verify(gitHubService, never()).fetchPullRequestDetails(anyString(), anyString(), anyInt());
        verify(persistenceService).persistPullRequests(eq(1L), anyList());
        verify(persistenceService).markJobCompleted(100L, 1L, "Dev User");
    }

    @Test
    void executeSync_whenUnexpectedExceptionDuringLoading_marksJobFailed() {
        when(persistenceService.findJobByIdWithAssociations(100L))
                .thenThrow(new RuntimeException("Simulated lazy loading exception"));
        when(gitHubService.sanitize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        worker.executeSync(100L);

        verify(persistenceService).markJobFailed(
                eq(100L),
                contains("Simulated lazy loading exception"),
                eq(502),
                isNull()
        );
        verify(persistenceService, never()).markJobCompleted(anyLong(), anyLong(), anyString());
    }

    @Test
    void executeSync_whenJobNotFound_abortsExecutionCleanly() {
        when(persistenceService.findJobByIdWithAssociations(999L)).thenReturn(null);

        worker.executeSync(999L);

        verify(persistenceService, never()).markJobInProgress(anyLong());
        verify(persistenceService, never()).markJobFailed(anyLong(), anyString(), any(), any());
        verify(persistenceService, never()).markJobCompleted(anyLong(), anyLong(), anyString());
    }
}
