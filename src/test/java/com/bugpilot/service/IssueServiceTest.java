package com.bugpilot.service;

import com.bugpilot.dto.IssueResponse;
import com.bugpilot.entity.BugAnalysis;
import com.bugpilot.entity.Issue;
import com.bugpilot.entity.Repository;
import com.bugpilot.enums.AnalysisSeverity;
import com.bugpilot.enums.IssueState;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.IssueRepository;
import com.bugpilot.entity.User;
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
class IssueServiceTest {

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private UserRepository userRepository;

    private IssueService issueService;

    @BeforeEach
    void setUp() {
        issueService = new IssueService(issueRepository, userRepository);
    }

    @Test
    void mapToResponse_whenBugAnalysisPresent_populatesAiSeverity() {
        Issue issue = new Issue();
        issue.setId(10L);
        issue.setNumber(42);
        issue.setTitle("Null pointer in auth");
        issue.setState(IssueState.OPEN);

        BugAnalysis analysis = new BugAnalysis();
        analysis.setSeverity(AnalysisSeverity.CRITICAL);
        issue.setBugAnalysis(analysis);

        IssueResponse response = issueService.mapToResponse(issue);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(42, response.getNumber());
        assertTrue(response.isHasAiAnalysis());
        assertEquals(AnalysisSeverity.CRITICAL, response.getAiSeverity());
    }

    @Test
    void mapToResponse_whenBugAnalysisNull_aiSeverityIsNull() {
        Issue issue = new Issue();
        issue.setId(11L);
        issue.setNumber(43);
        issue.setTitle("Docs typo");
        issue.setState(IssueState.OPEN);
        issue.setBugAnalysis(null);

        IssueResponse response = issueService.mapToResponse(issue);

        assertNotNull(response);
        assertEquals(11L, response.getId());
        assertFalse(response.isHasAiAnalysis());
        assertNull(response.getAiSeverity());
    }

    @Test
    void getIssuesByRepository_returnsMappedResponses() {
        Repository repo = new Repository();
        repo.setId(1L);

        Issue issue1 = new Issue();
        issue1.setId(1L);
        issue1.setRepository(repo);
        BugAnalysis ba = new BugAnalysis();
        ba.setSeverity(AnalysisSeverity.HIGH);
        issue1.setBugAnalysis(ba);

        Issue issue2 = new Issue();
        issue2.setId(2L);
        issue2.setRepository(repo);

        when(issueRepository.findByRepositoryId(1L)).thenReturn(List.of(issue1, issue2));

        List<IssueResponse> results = issueService.getIssuesByRepository(1L);

        assertEquals(2, results.size());
        assertTrue(results.get(0).isHasAiAnalysis());
        assertEquals(AnalysisSeverity.HIGH, results.get(0).getAiSeverity());
        assertFalse(results.get(1).isHasAiAnalysis());
        assertNull(results.get(1).getAiSeverity());
    }

    @Test
    void getIssueById_whenExists_returnsResponse() {
        Issue issue = new Issue();
        issue.setId(1L);
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));

        IssueResponse response = issueService.getIssueById(1L);
        assertNotNull(response);
        assertEquals(1L, response.getId());
    }

    @Test
    void getIssueById_whenNotFound_throwsException() {
        when(issueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> issueService.getIssueById(99L));
    }

    @Test
    void getIssueById_withUserEmail_whenOwner_returnsResponse() {
        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@bugpilot.com");

        Repository repo = new Repository();
        repo.setId(10L);
        repo.setUser(owner);

        Issue issue = new Issue();
        issue.setId(100L);
        issue.setNumber(42);
        issue.setTitle("Null pointer");
        issue.setRepository(repo);

        when(issueRepository.findById(100L)).thenReturn(Optional.of(issue));
        when(userRepository.findFirstByEmailOrderByIdDesc("owner@bugpilot.com")).thenReturn(Optional.of(owner));

        IssueResponse response = issueService.getIssueById(100L, "owner@bugpilot.com");

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(10L, response.getRepositoryId());
    }

    @Test
    void getIssueById_withUserEmail_whenNonOwner_throwsAccessDeniedException() {
        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@bugpilot.com");

        User nonOwner = new User();
        nonOwner.setId(2L);
        nonOwner.setEmail("seconddev@bugpilot.com");

        Repository repo = new Repository();
        repo.setId(10L);
        repo.setUser(owner);

        Issue issue = new Issue();
        issue.setId(100L);
        issue.setRepository(repo);

        when(issueRepository.findById(100L)).thenReturn(Optional.of(issue));
        when(userRepository.findFirstByEmailOrderByIdDesc("seconddev@bugpilot.com")).thenReturn(Optional.of(nonOwner));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> issueService.getIssueById(100L, "seconddev@bugpilot.com"));

        assertTrue(ex.getMessage().contains("Access denied: You do not own this repository"));
    }

    @Test
    void getIssueById_withUserEmail_whenAdminDoesNotOwnRepository_throwsAccessDeniedException() {
        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@bugpilot.com");

        User admin = new User();
        admin.setId(99L);
        admin.setEmail("admin@bugpilot.com");

        Repository repo = new Repository();
        repo.setId(10L);
        repo.setUser(owner);

        Issue issue = new Issue();
        issue.setId(100L);
        issue.setRepository(repo);

        when(issueRepository.findById(100L)).thenReturn(Optional.of(issue));
        when(userRepository.findFirstByEmailOrderByIdDesc("admin@bugpilot.com")).thenReturn(Optional.of(admin));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> issueService.getIssueById(100L, "admin@bugpilot.com"));

        assertTrue(ex.getMessage().contains("Access denied: You do not own this repository"));
    }

    @Test
    void getIssueById_withUserEmail_whenUnauthenticated_throwsAccessDeniedException() {
        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@bugpilot.com");

        Repository repo = new Repository();
        repo.setId(10L);
        repo.setUser(owner);

        Issue issue = new Issue();
        issue.setId(100L);
        issue.setRepository(repo);

        when(issueRepository.findById(100L)).thenReturn(Optional.of(issue));

        assertThrows(AccessDeniedException.class,
                () -> issueService.getIssueById(100L, null));
    }
}
