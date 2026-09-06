package com.bugpilot.service;

import com.bugpilot.dto.PullRequestResponse;
import com.bugpilot.entity.PullRequest;
import com.bugpilot.entity.PullRequestAnalysis;
import com.bugpilot.entity.Repository;
import com.bugpilot.entity.User;
import com.bugpilot.enums.PullRequestState;
import com.bugpilot.enums.RiskLevel;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.PullRequestRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PullRequestServiceTest {

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private UserRepository userRepository;

    private PullRequestService pullRequestService;

    private User owner;
    private User nonOwner;
    private Repository repo;
    private PullRequest pr;

    @BeforeEach
    void setUp() {
        pullRequestService = new PullRequestService(pullRequestRepository, userRepository);

        owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@bugpilot.com");

        nonOwner = new User();
        nonOwner.setId(2L);
        nonOwner.setEmail("seconddev@bugpilot.com");

        repo = new Repository();
        repo.setId(10L);
        repo.setUser(owner);

        pr = new PullRequest();
        pr.setId(20L);
        pr.setNumber(7);
        pr.setTitle("Add OAuth");
        pr.setState(PullRequestState.OPEN);
        pr.setRepository(repo);
    }

    @Test
    void getPullRequestById_whenOwner_returnsResponse() {
        when(pullRequestRepository.findById(20L)).thenReturn(Optional.of(pr));
        when(userRepository.findFirstByEmailOrderByIdDesc("owner@bugpilot.com")).thenReturn(Optional.of(owner));

        PullRequestResponse response = pullRequestService.getPullRequestById(20L, "owner@bugpilot.com");

        assertNotNull(response);
        assertEquals(20L, response.getId());
        assertEquals(7, response.getNumber());
        assertEquals(10L, response.getRepositoryId());
    }

    @Test
    void getPullRequestById_whenNonOwner_throwsAccessDeniedException() {
        when(pullRequestRepository.findById(20L)).thenReturn(Optional.of(pr));
        when(userRepository.findFirstByEmailOrderByIdDesc("seconddev@bugpilot.com")).thenReturn(Optional.of(nonOwner));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> pullRequestService.getPullRequestById(20L, "seconddev@bugpilot.com"));

        assertTrue(ex.getMessage().contains("Access denied: You do not own this repository"));
    }

    @Test
    void getPullRequestById_whenAdminDoesNotOwnRepository_throwsAccessDeniedException() {
        User admin = new User();
        admin.setId(99L);
        admin.setEmail("admin@bugpilot.com");

        when(pullRequestRepository.findById(20L)).thenReturn(Optional.of(pr));
        when(userRepository.findFirstByEmailOrderByIdDesc("admin@bugpilot.com")).thenReturn(Optional.of(admin));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> pullRequestService.getPullRequestById(20L, "admin@bugpilot.com"));

        assertTrue(ex.getMessage().contains("Access denied: You do not own this repository"));
    }

    @Test
    void getPullRequestById_whenUnauthenticated_throwsAccessDeniedException() {
        when(pullRequestRepository.findById(20L)).thenReturn(Optional.of(pr));

        assertThrows(AccessDeniedException.class,
                () -> pullRequestService.getPullRequestById(20L, null));
    }

    @Test
    void getPullRequestById_whenNotFound_throwsResourceNotFoundException() {
        when(pullRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> pullRequestService.getPullRequestById(999L, "owner@bugpilot.com"));
    }

    @Test
    void getPullRequestsByRepository_returnsMappedResponses() {
        PullRequest pr2 = new PullRequest();
        pr2.setId(21L);
        pr2.setNumber(8);
        pr2.setRepository(repo);

        when(pullRequestRepository.findByRepositoryId(10L)).thenReturn(List.of(pr, pr2));

        List<PullRequestResponse> list = pullRequestService.getPullRequestsByRepository(10L);

        assertEquals(2, list.size());
        assertEquals(20L, list.get(0).getId());
        assertEquals(21L, list.get(1).getId());
    }
}
