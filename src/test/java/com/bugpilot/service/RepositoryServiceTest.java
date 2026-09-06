package com.bugpilot.service;

import com.bugpilot.dto.RepositoryRequest;
import com.bugpilot.dto.RepositoryResponse;
import com.bugpilot.entity.Repository;
import com.bugpilot.entity.User;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.RepoRepository;
import com.bugpilot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

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

    private RepositoryService repositoryService;

    @BeforeEach
    void setUp() {
        repositoryService = new RepositoryService(repoRepository, userRepository);
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

        Repository repo = new Repository();
        repo.setId(1L);
        repo.setUser(owner);

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));
        when(userRepository.findFirstByEmailOrderByIdDesc("testdev@bugpilot.com")).thenReturn(Optional.of(owner));

        repositoryService.deleteRepository(1L, "testdev@bugpilot.com");

        verify(repoRepository, times(1)).delete(repo);
    }

    @Test
    void deleteRepository_whenNonOwner_throwsAccessDeniedException() {
        User owner = new User();
        owner.setId(3L);
        owner.setEmail("testdev@bugpilot.com");

        User otherUser = new User();
        otherUser.setId(4L);
        otherUser.setEmail("seconddev@bugpilot.com");

        Repository repo = new Repository();
        repo.setId(1L);
        repo.setUser(owner);

        when(repoRepository.findById(1L)).thenReturn(Optional.of(repo));
        when(userRepository.findFirstByEmailOrderByIdDesc("seconddev@bugpilot.com")).thenReturn(Optional.of(otherUser));

        assertThrows(AccessDeniedException.class, () ->
                repositoryService.deleteRepository(1L, "seconddev@bugpilot.com"));

        verify(repoRepository, never()).delete(any(Repository.class));
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
