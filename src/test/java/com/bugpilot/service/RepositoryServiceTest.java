package com.bugpilot.service;

import com.bugpilot.dto.RepositoryRequest;
import com.bugpilot.dto.RepositoryResponse;
import com.bugpilot.entity.BugAnalysis;
import com.bugpilot.entity.Commit;
import com.bugpilot.entity.Issue;
import com.bugpilot.entity.PullRequest;
import com.bugpilot.entity.PullRequestAnalysis;
import com.bugpilot.entity.Repository;
import com.bugpilot.entity.SyncJob;
import com.bugpilot.entity.User;
import com.bugpilot.enums.Role;
import com.bugpilot.enums.SyncJobStatus;
import com.bugpilot.exception.ConcurrentSyncException;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.ActivityRepository;
import com.bugpilot.repository.RepoRepository;
import com.bugpilot.repository.SyncJobRepository;
import com.bugpilot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepositoryServiceTest {

    @Mock
    private RepoRepository repoRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private SyncJobRepository syncJobRepository;

    private RepositoryService repositoryService;

    @BeforeEach
    void setUp() {
        repositoryService = new RepositoryService(repoRepository, userRepository, activityRepository, syncJobRepository);
    }

    @Test
    void createRepository_createsSuccessfully() {
        RepositoryRequest request = new RepositoryRequest("octocat", "Hello-World");
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());

        Repository saved = new Repository();
        saved.setId(100L);
        saved.setOwner("octocat");
        saved.setName("Hello-World");
        saved.setFullName("octocat/Hello-World");
        saved.setUser(user);

        when(repoRepository.save(any(Repository.class))).thenReturn(saved);

        RepositoryResponse response = repositoryService.createRepository(request, "user@example.com");

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("octocat/Hello-World", response.getFullName());
    }

    @Test
    void getRepositoryById_found_returnsResponse() {
        Repository repo = new Repository();
        repo.setId(5L);
        repo.setOwner("owner");
        repo.setName("repo");
        repo.setFullName("owner/repo");

        when(repoRepository.findById(5L)).thenReturn(Optional.of(repo));

        RepositoryResponse response = repositoryService.getRepositoryById(5L);

        assertNotNull(response);
        assertEquals(5L, response.getId());
    }

    @Test
    void getRepositoryById_notFound_throwsResourceNotFoundException() {
        when(repoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> repositoryService.getRepositoryById(99L));
    }

    @Test
    void getRepositoriesForUser_whenUserExists_returnsUserRepositories() {
        User user = new User();
        user.setId(3L);
        user.setEmail("testdev@bugpilot.com");

        Repository repo = new Repository();
        repo.setId(1L);
        repo.setName("BugPilot Demo Repository");
        repo.setOwner("sahitya-mandal");
        repo.setUser(user);

        when(userRepository.findFirstByEmailOrderByIdDesc("testdev@bugpilot.com")).thenReturn(Optional.of(user));
        when(repoRepository.findByUserId(3L)).thenReturn(List.of(repo));

        List<RepositoryResponse> results = repositoryService.getRepositoriesForUser("testdev@bugpilot.com");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getId());
        assertEquals(3L, results.get(0).getUserId());
    }

    @Test
    void getRepositoriesForUser_whenUserHasNoRepositories_returnsEmptyList() {
        User user = new User();
        user.setId(4L);
        user.setEmail("seconddev@bugpilot.com");

        when(userRepository.findFirstByEmailOrderByIdDesc("seconddev@bugpilot.com")).thenReturn(Optional.of(user));
        when(repoRepository.findByUserId(4L)).thenReturn(List.of());

        List<RepositoryResponse> results = repositoryService.getRepositoriesForUser("seconddev@bugpilot.com");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void getRepositoriesForUser_whenEmailNull_returnsEmptyList() {
        List<RepositoryResponse> results = repositoryService.getRepositoriesForUser(null);

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verifyNoInteractions(repoRepository);
    }

    @Test
    void getRepositoryById_whenOwner_returnsRepository() {
        User owner = new User();
        owner.setId(3L);
        owner.setEmail("testdev@bugpilot.com");

        Repository repo = new Repository();
        repo.setId(1L);
        repo.setName("BugPilot Demo Repository");
        repo.setOwner("sahitya-mandal");
        repo.setUser(owner);

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));
        when(userRepository.findFirstByEmailOrderByIdDesc("testdev@bugpilot.com")).thenReturn(Optional.of(owner));

        RepositoryResponse response = repositoryService.getRepositoryById(1L, "testdev@bugpilot.com");

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(3L, response.getUserId());
    }

    @Test
    void getRepositoryById_whenNonOwner_throwsAccessDeniedException() {
        User owner = new User();
        owner.setId(3L);
        owner.setEmail("testdev@bugpilot.com");

        User otherUser = new User();
        otherUser.setId(4L);
        otherUser.setEmail("seconddev@bugpilot.com");

        Repository repo = new Repository();
        repo.setId(1L);
        repo.setName("BugPilot Demo Repository");
        repo.setUser(owner);

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));
        when(userRepository.findFirstByEmailOrderByIdDesc("seconddev@bugpilot.com")).thenReturn(Optional.of(otherUser));

        assertThrows(AccessDeniedException.class, () ->
                repositoryService.getRepositoryById(1L, "seconddev@bugpilot.com"));
    }

    @Test
    void getRepositoryById_whenUnauthenticated_throwsAccessDeniedException() {
        Repository repo = new Repository();
        repo.setId(1L);
        repo.setName("BugPilot Demo Repository");

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));

        assertThrows(AccessDeniedException.class, () ->
                repositoryService.getRepositoryById(1L, null));
    }

    @Test
    void deleteRepository_whenOwner_deletesSuccessfully() {
        User owner = new User();
        owner.setId(3L);
        owner.setEmail("testdev@bugpilot.com");
        owner.setRole(Role.DEVELOPER);

        Repository repo = new Repository();
        repo.setId(1L);
        repo.setUser(owner);

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));
        when(userRepository.findFirstByEmailOrderByIdDesc("testdev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(syncJobRepository.findFirstByRepositoryIdAndStatusOrderByCreatedAtDesc(1L, SyncJobStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(syncJobRepository.findByRepositoryIdAndStatus(1L, SyncJobStatus.QUEUED))
                .thenReturn(Collections.emptyList());

        repositoryService.deleteRepository(1L, "testdev@bugpilot.com");

        InOrder inOrder = inOrder(activityRepository, syncJobRepository, repoRepository);
        inOrder.verify(activityRepository, times(1)).deleteByRepositoryId(1L);
        inOrder.verify(syncJobRepository, times(1)).deleteByRepositoryId(1L);
        inOrder.verify(repoRepository, times(1)).delete(repo);
    }

    @Test
    void deleteRepository_whenAdmin_deletesSuccessfully() {
        User owner = new User();
        owner.setId(3L);
        owner.setEmail("testdev@bugpilot.com");
        owner.setRole(Role.DEVELOPER);

        User admin = new User();
        admin.setId(99L);
        admin.setEmail("admin@bugpilot.com");
        admin.setRole(Role.ADMIN);

        Repository repo = new Repository();
        repo.setId(1L);
        repo.setUser(owner);

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));
        when(userRepository.findFirstByEmailOrderByIdDesc("admin@bugpilot.com")).thenReturn(Optional.of(admin));
        when(syncJobRepository.findFirstByRepositoryIdAndStatusOrderByCreatedAtDesc(1L, SyncJobStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(syncJobRepository.findByRepositoryIdAndStatus(1L, SyncJobStatus.QUEUED))
                .thenReturn(Collections.emptyList());

        repositoryService.deleteRepository(1L, "admin@bugpilot.com");

        InOrder inOrder = inOrder(activityRepository, syncJobRepository, repoRepository);
        inOrder.verify(activityRepository, times(1)).deleteByRepositoryId(1L);
        inOrder.verify(syncJobRepository, times(1)).deleteByRepositoryId(1L);
        inOrder.verify(repoRepository, times(1)).delete(repo);
    }

    @Test
    void deleteRepository_whenNonOwner_throwsAccessDeniedException() {
        User owner = new User();
        owner.setId(3L);
        owner.setEmail("testdev@bugpilot.com");
        owner.setRole(Role.DEVELOPER);

        User otherUser = new User();
        otherUser.setId(4L);
        otherUser.setEmail("seconddev@bugpilot.com");
        otherUser.setRole(Role.DEVELOPER);

        Repository repo = new Repository();
        repo.setId(1L);
        repo.setUser(owner);

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));
        when(userRepository.findFirstByEmailOrderByIdDesc("seconddev@bugpilot.com")).thenReturn(Optional.of(otherUser));

        assertThrows(AccessDeniedException.class, () ->
                repositoryService.deleteRepository(1L, "seconddev@bugpilot.com"));

        verify(activityRepository, never()).deleteByRepositoryId(anyLong());
        verify(syncJobRepository, never()).deleteByRepositoryId(anyLong());
        verify(repoRepository, never()).delete(any(Repository.class));
    }

    @Test
    void deleteRepository_whenUnauthenticated_throwsAccessDeniedException() {
        Repository repo = new Repository();
        repo.setId(1L);

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));

        assertThrows(AccessDeniedException.class, () ->
                repositoryService.deleteRepository(1L, null));

        verify(activityRepository, never()).deleteByRepositoryId(anyLong());
        verify(syncJobRepository, never()).deleteByRepositoryId(anyLong());
        verify(repoRepository, never()).delete(any(Repository.class));
    }

    @Test
    void deleteRepository_whenRepoNotFound_throwsResourceNotFoundException() {
        when(repoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                repositoryService.deleteRepository(999L, "testdev@bugpilot.com"));

        verify(activityRepository, never()).deleteByRepositoryId(anyLong());
        verify(syncJobRepository, never()).deleteByRepositoryId(anyLong());
        verify(repoRepository, never()).delete(any(Repository.class));
    }

    @Test
    void deleteRepository_whenSyncInProgress_throwsConcurrentSyncException() {
        User owner = new User();
        owner.setId(3L);
        owner.setEmail("testdev@bugpilot.com");

        Repository repo = new Repository();
        repo.setId(1L);
        repo.setUser(owner);

        SyncJob inProgressJob = new SyncJob();
        inProgressJob.setId(42L);
        inProgressJob.setStatus(SyncJobStatus.IN_PROGRESS);

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));
        when(userRepository.findFirstByEmailOrderByIdDesc("testdev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(syncJobRepository.findFirstByRepositoryIdAndStatusOrderByCreatedAtDesc(1L, SyncJobStatus.IN_PROGRESS))
                .thenReturn(Optional.of(inProgressJob));

        ConcurrentSyncException ex = assertThrows(ConcurrentSyncException.class, () ->
                repositoryService.deleteRepository(1L, "testdev@bugpilot.com"));

        assertEquals("Cannot delete repository while synchronization is in progress. Please wait for sync to complete.", ex.getMessage());
        assertEquals(42L, ex.getActiveJobId());

        verify(activityRepository, never()).deleteByRepositoryId(anyLong());
        verify(syncJobRepository, never()).deleteByRepositoryId(anyLong());
        verify(repoRepository, never()).delete(any(Repository.class));
    }

    @Test
    void deleteRepository_whenQueuedJobsExist_cancelsQueuedJobsBeforeDeletion() {
        User owner = new User();
        owner.setId(3L);
        owner.setEmail("testdev@bugpilot.com");

        Repository repo = new Repository();
        repo.setId(1L);
        repo.setUser(owner);

        SyncJob queuedJob = new SyncJob();
        queuedJob.setId(101L);
        queuedJob.setStatus(SyncJobStatus.QUEUED);

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));
        when(userRepository.findFirstByEmailOrderByIdDesc("testdev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(syncJobRepository.findFirstByRepositoryIdAndStatusOrderByCreatedAtDesc(1L, SyncJobStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(syncJobRepository.findByRepositoryIdAndStatus(1L, SyncJobStatus.QUEUED))
                .thenReturn(List.of(queuedJob));

        repositoryService.deleteRepository(1L, "testdev@bugpilot.com");

        assertEquals(SyncJobStatus.CANCELLED, queuedJob.getStatus());
        assertEquals("Repository deleted before sync started.", queuedJob.getErrorMessage());
        verify(syncJobRepository, times(1)).saveAll(List.of(queuedJob));

        InOrder inOrder = inOrder(activityRepository, syncJobRepository, repoRepository);
        inOrder.verify(activityRepository, times(1)).deleteByRepositoryId(1L);
        inOrder.verify(syncJobRepository, times(1)).deleteByRepositoryId(1L);
        inOrder.verify(repoRepository, times(1)).delete(repo);
    }

    @Test
    void deleteRepository_idorAttemptUsingAnotherRepositoryId_fails() {
        User userA = new User();
        userA.setId(10L);
        userA.setEmail("usera@bugpilot.com");
        userA.setRole(Role.DEVELOPER);

        User userB = new User();
        userB.setId(20L);
        userB.setEmail("userb@bugpilot.com");
        userB.setRole(Role.DEVELOPER);

        Repository targetRepoOwnedByUserB = new Repository();
        targetRepoOwnedByUserB.setId(200L);
        targetRepoOwnedByUserB.setUser(userB);

        when(repoRepository.findById(200L)).thenReturn(Optional.of(targetRepoOwnedByUserB));
        when(userRepository.findFirstByEmailOrderByIdDesc("usera@bugpilot.com")).thenReturn(Optional.of(userA));

        assertThrows(AccessDeniedException.class, () ->
                repositoryService.deleteRepository(200L, "usera@bugpilot.com"));

        verify(activityRepository, never()).deleteByRepositoryId(anyLong());
        verify(syncJobRepository, never()).deleteByRepositoryId(anyLong());
        verify(repoRepository, never()).delete(any(Repository.class));
    }

    @Test
    void deleteRepository_preservesExistingCascadeEntities_passesPopulatedAggregateToRepoRepositoryDelete() {
        User owner = new User();
        owner.setId(3L);
        owner.setEmail("testdev@bugpilot.com");
        owner.setRole(Role.DEVELOPER);

        Repository repo = new Repository();
        repo.setId(1L);
        repo.setUser(owner);

        Issue issue = new Issue();
        issue.setId(10L);
        issue.setTitle("Crash on null pointer");
        issue.setRepository(repo);

        BugAnalysis bugAnalysis = new BugAnalysis();
        bugAnalysis.setId(20L);
        bugAnalysis.setIssue(issue);
        issue.setBugAnalysis(bugAnalysis);
        repo.getIssues().add(issue);

        PullRequest pr = new PullRequest();
        pr.setId(30L);
        pr.setTitle("Fix null pointer");
        pr.setRepository(repo);

        PullRequestAnalysis prAnalysis = new PullRequestAnalysis();
        prAnalysis.setId(40L);
        prAnalysis.setPullRequest(pr);
        pr.setPrAnalysis(prAnalysis);
        repo.getPullRequests().add(pr);

        Commit commit = new Commit();
        commit.setId(50L);
        commit.setSha("a1b2c3d4e5");
        commit.setRepository(repo);
        repo.getCommits().add(commit);

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));
        when(userRepository.findFirstByEmailOrderByIdDesc("testdev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(syncJobRepository.findFirstByRepositoryIdAndStatusOrderByCreatedAtDesc(1L, SyncJobStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(syncJobRepository.findByRepositoryIdAndStatus(1L, SyncJobStatus.QUEUED))
                .thenReturn(Collections.emptyList());

        repositoryService.deleteRepository(1L, "testdev@bugpilot.com");

        InOrder inOrder = inOrder(activityRepository, syncJobRepository, repoRepository);
        inOrder.verify(activityRepository, times(1)).deleteByRepositoryId(1L);
        inOrder.verify(syncJobRepository, times(1)).deleteByRepositoryId(1L);
        inOrder.verify(repoRepository, times(1)).delete(argThat(r ->
                r.getId().equals(1L) &&
                r.getIssues().size() == 1 &&
                r.getIssues().get(0).getBugAnalysis() != null &&
                r.getPullRequests().size() == 1 &&
                r.getPullRequests().get(0).getPrAnalysis() != null &&
                r.getCommits().size() == 1
        ));
    }

    @Test
    void createRepository_whenExistingRepoOwnedByAnotherUser_throwsAccessDeniedException() {
        User owner = new User();
        owner.setId(3L);
        owner.setEmail("testdev@bugpilot.com");

        User otherUser = new User();
        otherUser.setId(4L);
        otherUser.setEmail("seconddev@bugpilot.com");

        Repository existingRepo = new Repository();
        existingRepo.setId(1L);
        existingRepo.setOwner("sahitya-mandal");
        existingRepo.setName("demo");
        existingRepo.setUser(owner);

        RepositoryRequest request = new RepositoryRequest("sahitya-mandal", "demo");

        when(userRepository.findFirstByEmailOrderByIdDesc("seconddev@bugpilot.com")).thenReturn(Optional.of(otherUser));
        when(repoRepository.findByOwnerAndName("sahitya-mandal", "demo")).thenReturn(Optional.of(existingRepo));

        assertThrows(AccessDeniedException.class, () ->
                repositoryService.createRepository(request, "seconddev@bugpilot.com"));

        verify(repoRepository, never()).save(any(Repository.class));
    }

    @Test
    void createRepository_whenExistingRepoOwnedBySameUser_updatesSuccessfully() {
        User owner = new User();
        owner.setId(3L);
        owner.setEmail("testdev@bugpilot.com");

        Repository existingRepo = new Repository();
        existingRepo.setId(1L);
        existingRepo.setOwner("sahitya-mandal");
        existingRepo.setName("demo");
        existingRepo.setUser(owner);

        RepositoryRequest request = new RepositoryRequest("sahitya-mandal", "demo");

        when(userRepository.findFirstByEmailOrderByIdDesc("testdev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByOwnerAndName("sahitya-mandal", "demo")).thenReturn(Optional.of(existingRepo));
        when(repoRepository.save(any(Repository.class))).thenReturn(existingRepo);

        RepositoryResponse response = repositoryService.createRepository(request, "testdev@bugpilot.com");

        assertNotNull(response);
        verify(repoRepository, times(1)).save(existingRepo);
    }

    @Test
    void createRepository_withDescriptionAndGithubUrl_persistsAndMapsCorrectly() {
        RepositoryRequest request = new RepositoryRequest(
                "sahitya-mandal",
                "Bugpilot",
                "https://github.com/sahitya-mandal/Bugpilot",
                "AI-Powered Engineering Intelligence Platform"
        );
        User user = new User();
        user.setId(3L);
        user.setEmail("testdev@bugpilot.com");

        when(userRepository.findFirstByEmailOrderByIdDesc("testdev@bugpilot.com")).thenReturn(Optional.of(user));
        when(repoRepository.findByOwnerAndName("sahitya-mandal", "Bugpilot")).thenReturn(Optional.empty());

        org.mockito.ArgumentCaptor<Repository> captor = org.mockito.ArgumentCaptor.forClass(Repository.class);
        when(repoRepository.save(captor.capture())).thenAnswer(invocation -> {
            Repository repo = invocation.getArgument(0);
            repo.setId(10L);
            return repo;
        });

        RepositoryResponse response = repositoryService.createRepository(request, "testdev@bugpilot.com");

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Bugpilot", response.getName());
        assertEquals("sahitya-mandal", response.getOwner());
        assertEquals("sahitya-mandal/Bugpilot", response.getFullName());
        assertEquals("AI-Powered Engineering Intelligence Platform", response.getDescription());
        assertEquals("https://github.com/sahitya-mandal/Bugpilot", response.getHtmlUrl());
        assertEquals("https://github.com/sahitya-mandal/Bugpilot", response.getGithubUrl());
        assertEquals(3L, response.getUserId());

        Repository savedRepo = captor.getValue();
        assertEquals("AI-Powered Engineering Intelligence Platform", savedRepo.getDescription());
        assertEquals("https://github.com/sahitya-mandal/Bugpilot", savedRepo.getHtmlUrl());
    }
}
