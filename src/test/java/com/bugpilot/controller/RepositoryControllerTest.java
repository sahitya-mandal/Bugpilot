package com.bugpilot.controller;

import com.bugpilot.dto.*;
import com.bugpilot.enums.SyncJobStatus;
import com.bugpilot.enums.SyncJobStep;
import com.bugpilot.exception.ConcurrentSyncException;
import com.bugpilot.exception.GitHubApiException;
import com.bugpilot.exception.GlobalExceptionHandler;
import com.bugpilot.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RepositoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private IssueService issueService;

    @Mock
    private PullRequestService pullRequestService;

    @Mock
    private CommitService commitService;

    @Mock
    private RepositorySyncService repositorySyncService;

    @InjectMocks
    private RepositoryController repositoryController;

    private Principal userBPrincipal;
    private Principal userAPrincipal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(repositoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        userAPrincipal = () -> "testdev@bugpilot.com";
        userBPrincipal = () -> "seconddev@bugpilot.com";
    }

    @Test
    void getRepositoryById_whenOwner_returns200Ok() throws Exception {
        RepositoryResponse repo = new RepositoryResponse();
        repo.setId(1L);
        repo.setName("BugPilot Demo Repository");
        repo.setUserId(3L);

        when(repositoryService.getRepositoryById(1L, "testdev@bugpilot.com")).thenReturn(repo);

        mockMvc.perform(get("/repositories/1")
                        .principal(userAPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("BugPilot Demo Repository"))
                .andExpect(jsonPath("$.userId").value(3));
    }

    @Test
    void getRepositoryById_whenNonOwner_returns403Forbidden() throws Exception {
        when(repositoryService.getRepositoryById(1L, "seconddev@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/repositories/1")
                        .principal(userBPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));
    }

    @Test
    void deleteRepository_whenNonOwner_returns403Forbidden() throws Exception {
        doThrow(new AccessDeniedException("Access denied: You do not own this repository"))
                .when(repositoryService).deleteRepository(1L, "seconddev@bugpilot.com");

        mockMvc.perform(delete("/repositories/1")
                        .principal(userBPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deleteRepository_whenOwner_returns200Ok() throws Exception {
        doNothing().when(repositoryService).deleteRepository(1L, "testdev@bugpilot.com");

        mockMvc.perform(delete("/repositories/1")
                        .principal(userAPrincipal))
                .andExpect(status().isOk())
                .andExpect(content().string("Repository deleted successfully."));
    }

    @Test
    void getRepositoryIssues_whenNonOwner_returns403Forbidden() throws Exception {
        when(repositoryService.getRepositoryEntityForUser(1L, "seconddev@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/repositories/1/issues")
                        .principal(userBPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(issueService);
    }

    @Test
    void getRepositoryPullRequests_whenNonOwner_returns403Forbidden() throws Exception {
        when(repositoryService.getRepositoryEntityForUser(1L, "seconddev@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/repositories/1/pull-requests")
                        .principal(userBPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(pullRequestService);
    }

    @Test
    void getRepositoryCommits_whenNonOwner_returns403Forbidden() throws Exception {
        when(repositoryService.getRepositoryEntityForUser(1L, "seconddev@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/repositories/1/commits")
                        .principal(userBPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(commitService);
    }

    @Test
    void syncRepository_whenNonOwner_returns403Forbidden() throws Exception {
        when(repositorySyncService.queueSync(1L, "seconddev@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(post("/repositories/1/sync")
                        .principal(userBPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void syncRepository_whenOwner_returns202AcceptedWithSyncJobResponse() throws Exception {
        SyncJobResponse response = new SyncJobResponse();
        response.setJobId(104L);
        response.setRepositoryId(1L);
        response.setRepositoryName("BugPilot");
        response.setStatus(SyncJobStatus.QUEUED);
        response.setCurrentStep(SyncJobStep.QUEUED);
        response.setMessage("Repository synchronization has been queued.");

        when(repositorySyncService.queueSync(1L, "testdev@bugpilot.com")).thenReturn(response);

        mockMvc.perform(post("/repositories/1/sync")
                        .principal(userAPrincipal))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(104))
                .andExpect(jsonPath("$.repositoryId").value(1))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.currentStep").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("Repository synchronization has been queued."));
    }

    @Test
    void syncRepository_whenAlreadyActive_returns409Conflict() throws Exception {
        when(repositorySyncService.queueSync(1L, "testdev@bugpilot.com"))
                .thenThrow(new ConcurrentSyncException(103L));

        mockMvc.perform(post("/repositories/1/sync")
                        .principal(userAPrincipal))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.activeJobId").value(103))
                .andExpect(jsonPath("$.message").value("A sync job is already in progress for this repository."));
    }

    @Test
    void createRepository_withDescriptionAndGithubUrl_returns200WithAllFields() throws Exception {
        RepositoryResponse response = new RepositoryResponse();
        response.setId(10L);
        response.setName("Bugpilot");
        response.setOwner("sahitya-mandal");
        response.setFullName("sahitya-mandal/Bugpilot");
        response.setDescription("AI-Powered Engineering Intelligence Platform");
        response.setHtmlUrl("https://github.com/sahitya-mandal/Bugpilot");
        response.setUserId(3L);

        when(repositoryService.createRepository(any(), eq("testdev@bugpilot.com"))).thenReturn(response);

        String jsonPayload = """
                {
                  "name": "Bugpilot",
                  "owner": "sahitya-mandal",
                  "githubUrl": "https://github.com/sahitya-mandal/Bugpilot",
                  "description": "AI-Powered Engineering Intelligence Platform"
                }
                """;

        mockMvc.perform(post("/repositories")
                        .principal(userAPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Bugpilot"))
                .andExpect(jsonPath("$.owner").value("sahitya-mandal"))
                .andExpect(jsonPath("$.description").value("AI-Powered Engineering Intelligence Platform"))
                .andExpect(jsonPath("$.htmlUrl").value("https://github.com/sahitya-mandal/Bugpilot"))
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/sahitya-mandal/Bugpilot"))
                .andExpect(jsonPath("$.userId").value(3));
    }

    @Test
    void importRepository_returns202AcceptedWithSyncJobResponse() throws Exception {
        SyncJobResponse response = new SyncJobResponse();
        response.setJobId(105L);
        response.setRepositoryId(2L);
        response.setRepositoryName("Hello-World");
        response.setStatus(SyncJobStatus.QUEUED);
        response.setCurrentStep(SyncJobStep.QUEUED);
        response.setMessage("Repository import has been queued.");

        when(repositorySyncService.queueImport(any(RepositoryRequest.class), eq("testdev@bugpilot.com")))
                .thenReturn(response);

        String jsonPayload = """
                {
                  "owner": "octocat",
                  "name": "Hello-World"
                }
                """;

        mockMvc.perform(post("/repositories/import")
                        .principal(userAPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(105))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("Repository import has been queued."));
    }

    @Test
    void importRepository_whenAlreadyRegisteredByAnotherUser_returns403Forbidden() throws Exception {
        when(repositorySyncService.queueImport(any(RepositoryRequest.class), eq("seconddev@bugpilot.com")))
                .thenThrow(new AccessDeniedException("Access denied: Repository is already registered by another user"));

        String jsonPayload = """
                {
                  "owner": "octocat",
                  "name": "Hello-World"
                }
                """;

        mockMvc.perform(post("/repositories/import")
                        .principal(userBPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
