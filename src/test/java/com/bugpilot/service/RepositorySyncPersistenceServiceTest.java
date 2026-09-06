package com.bugpilot.service;

import com.bugpilot.entity.Commit;
import com.bugpilot.entity.Issue;
import com.bugpilot.entity.PullRequest;
import com.bugpilot.entity.Repository;
import com.bugpilot.enums.IssueState;
import com.bugpilot.enums.PullRequestState;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepositorySyncPersistenceServiceTest {

    @Mock
    private SyncJobRepository syncJobRepository;

    @Mock
    private RepoRepository repoRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RepositorySyncPersistenceService persistenceService;

    private Repository repository;

    @BeforeEach
    void setUp() {
        repository = new Repository();
        repository.setId(1L);
        repository.setOwner("octocat");
        repository.setName("Hello-World");
        repository.setFullName("octocat/Hello-World");
    }

    // ==========================================
    // 1. ISSUES BATCH PERSISTENCE & N+1 REMOVAL
    // ==========================================

    @Test
    void persistIssues_whenNewAndExistingIssues_executesSingleBulkLookupAndSaveAll() {
        when(repoRepository.findById(1L)).thenReturn(Optional.of(repository));

        Issue existingIssue = new Issue();
        existingIssue.setId(101L);
        existingIssue.setRepository(repository);
        existingIssue.setNumber(1);
        existingIssue.setTitle("Old Title");

        when(issueRepository.findByRepositoryIdAndNumberIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingIssue));

        Map<String, Object> item1 = Map.of(
                "number", 1,
                "id", 1111L,
                "title", "Updated Issue 1",
                "body", "Updated description",
                "state", "closed",
                "created_at", "2026-01-01T10:00:00Z",
                "updated_at", "2026-01-02T10:00:00Z"
        );

        Map<String, Object> item2 = Map.of(
                "number", 2,
                "id", 2222L,
                "title", "New Issue 2",
                "body", "New issue body",
                "state", "open",
                "created_at", "2026-01-03T10:00:00Z"
        );

        Map<String, Object> prItem = Map.of(
                "number", 3,
                "title", "PR disguised as issue",
                "pull_request", Map.of("url", "https://api.github.com/repos/octocat/Hello-World/pulls/3")
        );

        List<Map<String, Object>> items = List.of(item1, item2, prItem);

        int count = persistenceService.persistIssues(1L, items);

        assertEquals(2, count);

        // Verify single bulk lookup was invoked with numbers [1, 2]
        ArgumentCaptor<Collection<Integer>> numbersCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(issueRepository, times(1)).findByRepositoryIdAndNumberIn(eq(1L), numbersCaptor.capture());
        Collection<Integer> capturedNumbers = numbersCaptor.getValue();
        assertEquals(2, capturedNumbers.size());
        assertTrue(capturedNumbers.contains(1));
        assertTrue(capturedNumbers.contains(2));

        // CRITICAL: verify zero per-item individual lookups were executed
        verify(issueRepository, never()).findByRepositoryIdAndNumber(anyLong(), anyInt());

        // Verify saveAll was invoked once with both issues
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Issue>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(issueRepository, times(1)).saveAll(saveCaptor.capture());
        List<Issue> saved = saveCaptor.getValue();
        assertEquals(2, saved.size());

        // Check issue 1 was updated in-place
        Issue saved1 = saved.stream().filter(i -> i.getNumber() == 1).findFirst().orElseThrow();
        assertEquals(101L, saved1.getId());
        assertEquals("Updated Issue 1", saved1.getTitle());
        assertEquals(IssueState.CLOSED, saved1.getState());

        // Check issue 2 was created new
        Issue saved2 = saved.stream().filter(i -> i.getNumber() == 2).findFirst().orElseThrow();
        assertNull(saved2.getId());
        assertEquals("New Issue 2", saved2.getTitle());
        assertEquals(IssueState.OPEN, saved2.getState());
        assertEquals(repository, saved2.getRepository());
    }

    @Test
    void persistIssues_whenEmptyList_returnsZeroWithoutQueryingIssues() {
        when(repoRepository.findById(1L)).thenReturn(Optional.of(repository));

        int count = persistenceService.persistIssues(1L, Collections.emptyList());

        assertEquals(0, count);
        verify(issueRepository, never()).findByRepositoryIdAndNumberIn(anyLong(), anyCollection());
        verify(issueRepository, never()).findByRepositoryIdAndNumber(anyLong(), anyInt());
        verify(issueRepository, never()).saveAll(any());
    }

    @Test
    void persistIssues_whenRepoNotFound_throwsResourceNotFoundException() {
        when(repoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                persistenceService.persistIssues(999L, List.of(Map.of("number", 1))));
    }

    // ==========================================
    // 2. PULL REQUESTS BATCH PERSISTENCE & N+1 REMOVAL
    // ==========================================

    @Test
    void persistPullRequests_whenNewAndExistingPrs_executesSingleBulkLookupAndSaveAll() {
        when(repoRepository.findById(1L)).thenReturn(Optional.of(repository));

        PullRequest existingPr = new PullRequest();
        existingPr.setId(201L);
        existingPr.setRepository(repository);
        existingPr.setNumber(10);
        existingPr.setTitle("Old PR Title");

        when(pullRequestRepository.findByRepositoryIdAndNumberIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingPr));

        Map<String, Object> prItem1 = new HashMap<>();
        prItem1.put("number", 10);
        prItem1.put("id", 1010L);
        prItem1.put("title", "Updated PR 10");
        prItem1.put("body", "Updated PR body");
        prItem1.put("state", "closed");
        prItem1.put("merged_at", "2026-01-02T10:00:00Z");
        prItem1.put("draft", false);
        prItem1.put("_additions", 150);
        prItem1.put("_deletions", 25);
        prItem1.put("_changed_files", 5);

        Map<String, Object> prItem2 = new HashMap<>();
        prItem2.put("number", 20);
        prItem2.put("id", 2020L);
        prItem2.put("title", "New PR 20");
        prItem2.put("state", "open");
        prItem2.put("draft", true);
        prItem2.put("_additions", 10);
        prItem2.put("_deletions", 2);
        prItem2.put("_changed_files", 1);

        List<Map<String, Object>> items = List.of(prItem1, prItem2);

        int count = persistenceService.persistPullRequests(1L, items);

        assertEquals(2, count);

        // Verify single bulk lookup was invoked with numbers [10, 20]
        ArgumentCaptor<Collection<Integer>> numbersCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(pullRequestRepository, times(1)).findByRepositoryIdAndNumberIn(eq(1L), numbersCaptor.capture());
        Collection<Integer> capturedNumbers = numbersCaptor.getValue();
        assertEquals(2, capturedNumbers.size());
        assertTrue(capturedNumbers.contains(10));
        assertTrue(capturedNumbers.contains(20));

        // CRITICAL: verify zero per-item individual lookups were executed
        verify(pullRequestRepository, never()).findByRepositoryIdAndNumber(anyLong(), anyInt());

        // Verify saveAll was invoked once
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PullRequest>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(pullRequestRepository, times(1)).saveAll(saveCaptor.capture());
        List<PullRequest> saved = saveCaptor.getValue();
        assertEquals(2, saved.size());

        // Check PR 10 updated
        PullRequest saved1 = saved.stream().filter(p -> p.getNumber() == 10).findFirst().orElseThrow();
        assertEquals(201L, saved1.getId());
        assertEquals("Updated PR 10", saved1.getTitle());
        assertEquals(PullRequestState.MERGED, saved1.getState());
        assertEquals(150, saved1.getAdditions());
        assertEquals(25, saved1.getDeletions());
        assertEquals(5, saved1.getChangedFiles());

        // Check PR 20 created
        PullRequest saved2 = saved.stream().filter(p -> p.getNumber() == 20).findFirst().orElseThrow();
        assertNull(saved2.getId());
        assertEquals("New PR 20", saved2.getTitle());
        assertEquals(PullRequestState.OPEN, saved2.getState());
        assertTrue(saved2.getDraft());
        assertEquals(10, saved2.getAdditions());
    }

    @Test
    void persistPullRequests_whenEmptyList_returnsZeroWithoutQueryingPrs() {
        when(repoRepository.findById(1L)).thenReturn(Optional.of(repository));

        int count = persistenceService.persistPullRequests(1L, Collections.emptyList());

        assertEquals(0, count);
        verify(pullRequestRepository, never()).findByRepositoryIdAndNumberIn(anyLong(), anyCollection());
        verify(pullRequestRepository, never()).findByRepositoryIdAndNumber(anyLong(), anyInt());
        verify(pullRequestRepository, never()).saveAll(any());
    }

    // ==========================================
    // 3. COMMITS BATCH PERSISTENCE & N+1 REMOVAL
    // ==========================================

    @Test
    void persistCommits_whenNewAndExistingCommits_executesSingleBulkLookupAndSaveAll() {
        when(repoRepository.findById(1L)).thenReturn(Optional.of(repository));

        Commit existingCommit = new Commit();
        existingCommit.setId(301L);
        existingCommit.setRepository(repository);
        existingCommit.setSha("sha-aaa");
        existingCommit.setMessage("Old message");

        when(commitRepository.findByRepositoryIdAndShaIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingCommit));

        Map<String, Object> commitItem1 = Map.of(
                "sha", "sha-aaa",
                "html_url", "https://github.com/octocat/Hello-World/commit/sha-aaa",
                "commit", Map.of(
                        "message", "Updated commit message",
                        "author", Map.of(
                                "name", "Alice",
                                "email", "alice@example.com",
                                "date", "2026-01-01T12:00:00Z"
                        )
                )
        );

        Map<String, Object> commitItem2 = Map.of(
                "sha", "sha-bbb",
                "html_url", "https://github.com/octocat/Hello-World/commit/sha-bbb",
                "commit", Map.of(
                        "message", "New commit message",
                        "author", Map.of(
                                "name", "Bob",
                                "email", "bob@example.com",
                                "date", "2026-01-02T12:00:00Z"
                        )
                )
        );

        List<Map<String, Object>> items = List.of(commitItem1, commitItem2);

        int count = persistenceService.persistCommits(1L, items);

        assertEquals(2, count);

        // Verify single bulk lookup was invoked with SHAs ["sha-aaa", "sha-bbb"]
        ArgumentCaptor<Collection<String>> shasCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(commitRepository, times(1)).findByRepositoryIdAndShaIn(eq(1L), shasCaptor.capture());
        Collection<String> capturedShas = shasCaptor.getValue();
        assertEquals(2, capturedShas.size());
        assertTrue(capturedShas.contains("sha-aaa"));
        assertTrue(capturedShas.contains("sha-bbb"));

        // CRITICAL: verify zero per-item individual lookups were executed
        verify(commitRepository, never()).findByRepositoryIdAndSha(anyLong(), anyString());

        // Verify saveAll was invoked once
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Commit>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(commitRepository, times(1)).saveAll(saveCaptor.capture());
        List<Commit> saved = saveCaptor.getValue();
        assertEquals(2, saved.size());

        // Check commit aaa updated
        Commit saved1 = saved.stream().filter(c -> "sha-aaa".equals(c.getSha())).findFirst().orElseThrow();
        assertEquals(301L, saved1.getId());
        assertEquals("Updated commit message", saved1.getMessage());
        assertEquals("Alice", saved1.getAuthorName());

        // Check commit bbb created
        Commit saved2 = saved.stream().filter(c -> "sha-bbb".equals(c.getSha())).findFirst().orElseThrow();
        assertNull(saved2.getId());
        assertEquals("New commit message", saved2.getMessage());
        assertEquals("Bob", saved2.getAuthorName());
        assertEquals(repository, saved2.getRepository());
    }

    @Test
    void persistCommits_whenEmptyList_returnsZeroWithoutQueryingCommits() {
        when(repoRepository.findById(1L)).thenReturn(Optional.of(repository));

        int count = persistenceService.persistCommits(1L, Collections.emptyList());

        assertEquals(0, count);
        verify(commitRepository, never()).findByRepositoryIdAndShaIn(anyLong(), anyCollection());
        verify(commitRepository, never()).findByRepositoryIdAndSha(anyLong(), anyString());
        verify(commitRepository, never()).saveAll(any());
    }
}
