package com.bugpilot.controller;

import com.bugpilot.dto.SyncJobResponse;
import com.bugpilot.enums.SyncJobStatus;
import com.bugpilot.enums.SyncJobStep;
import com.bugpilot.exception.GlobalExceptionHandler;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.service.RepositorySyncService;
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
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SyncJobControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RepositorySyncService repositorySyncService;

    @InjectMocks
    private SyncJobController syncJobController;

    private Principal userAPrincipal;
    private Principal userBPrincipal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(syncJobController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        userAPrincipal = () -> "testdev@bugpilot.com";
        userBPrincipal = () -> "seconddev@bugpilot.com";
    }

    @Test
    void getSyncJob_whenOwner_returns200OkWithJobDetails() throws Exception {
        SyncJobResponse response = new SyncJobResponse();
        response.setJobId(101L);
        response.setRepositoryId(1L);
        response.setRepositoryName("BugPilot");
        response.setStatus(SyncJobStatus.IN_PROGRESS);
        response.setCurrentStep(SyncJobStep.FETCHING_ISSUES);
        response.setIssuesImported(15);

        when(repositorySyncService.getJobByIdForUser(101L, "testdev@bugpilot.com")).thenReturn(response);

        mockMvc.perform(get("/sync-jobs/101")
                        .principal(userAPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(101))
                .andExpect(jsonPath("$.repositoryId").value(1))
                .andExpect(jsonPath("$.repositoryName").value("BugPilot"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentStep").value("FETCHING_ISSUES"))
                .andExpect(jsonPath("$.issuesImported").value(15));
    }

    @Test
    void getSyncJob_whenNonOwner_returns403Forbidden() throws Exception {
        when(repositorySyncService.getJobByIdForUser(101L, "seconddev@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/sync-jobs/101")
                        .principal(userBPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));
    }

    @Test
    void getSyncJob_whenNotFound_returns404NotFound() throws Exception {
        when(repositorySyncService.getJobByIdForUser(999L, "testdev@bugpilot.com"))
                .thenThrow(new ResourceNotFoundException("SyncJob", 999L));

        mockMvc.perform(get("/sync-jobs/999")
                        .principal(userAPrincipal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getRepositorySyncJobs_whenOwner_returns200OkWithList() throws Exception {
        SyncJobResponse job1 = new SyncJobResponse();
        job1.setJobId(101L);
        job1.setStatus(SyncJobStatus.COMPLETED);

        SyncJobResponse job2 = new SyncJobResponse();
        job2.setJobId(102L);
        job2.setStatus(SyncJobStatus.FAILED);

        when(repositorySyncService.getJobsForRepository(1L, "testdev@bugpilot.com"))
                .thenReturn(List.of(job2, job1));

        mockMvc.perform(get("/repositories/1/sync-jobs")
                        .principal(userAPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].jobId").value(102))
                .andExpect(jsonPath("$[1].jobId").value(101));
    }

    @Test
    void getRepositorySyncJobs_whenNonOwner_returns403Forbidden() throws Exception {
        when(repositorySyncService.getJobsForRepository(1L, "seconddev@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/repositories/1/sync-jobs")
                        .principal(userBPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getActiveSyncJob_whenPresent_returns200OkWithJob() throws Exception {
        SyncJobResponse activeJob = new SyncJobResponse();
        activeJob.setJobId(103L);
        activeJob.setStatus(SyncJobStatus.IN_PROGRESS);

        when(repositorySyncService.getActiveJobForRepository(1L, "testdev@bugpilot.com"))
                .thenReturn(Optional.of(activeJob));

        mockMvc.perform(get("/repositories/1/sync-jobs/active")
                        .principal(userAPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(103))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void getActiveSyncJob_whenNoneActive_returns204NoContent() throws Exception {
        when(repositorySyncService.getActiveJobForRepository(1L, "testdev@bugpilot.com"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/repositories/1/sync-jobs/active")
                        .principal(userAPrincipal))
                .andExpect(status().isNoContent());
    }
}
