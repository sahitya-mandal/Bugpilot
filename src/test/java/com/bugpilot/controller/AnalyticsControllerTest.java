package com.bugpilot.controller;

import com.bugpilot.dto.ActivityResponse;
import com.bugpilot.dto.AnalyticsResponse;
import com.bugpilot.exception.GlobalExceptionHandler;
import com.bugpilot.service.ActivityService;
import com.bugpilot.service.AnalyticsService;
import com.bugpilot.service.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private ActivityService activityService;

    @Mock
    private RepositoryService repositoryService;

    @InjectMocks
    private AnalyticsController analyticsController;

    private Principal userBPrincipal;
    private Principal userAPrincipal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(analyticsController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        userAPrincipal = () -> "testdev@bugpilot.com";
        userBPrincipal = () -> "seconddev@bugpilot.com";
    }

    @Test
    void getRepositoryAnalytics_whenOwner_returns200Ok() throws Exception {
        AnalyticsResponse res = new AnalyticsResponse();
        res.setRepositoryId(1L);
        res.setAnalyzedIssuesCount(7L);

        when(analyticsService.getRepositoryAnalytics(1L)).thenReturn(res);

        mockMvc.perform(get("/repositories/1/analytics")
                        .principal(userAPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryId").value(1))
                .andExpect(jsonPath("$.analyzedIssuesCount").value(7));

        verify(repositoryService).getRepositoryEntityForUser(1L, "testdev@bugpilot.com");
    }

    @Test
    void getRepositoryAnalytics_whenNonOwner_returns403Forbidden() throws Exception {
        when(repositoryService.getRepositoryEntityForUser(1L, "seconddev@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/repositories/1/analytics")
                        .principal(userBPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(analyticsService);
    }

    @Test
    void getRepositoryActivities_whenNonOwner_returns403Forbidden() throws Exception {
        when(repositoryService.getRepositoryEntityForUser(1L, "seconddev@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/repositories/1/activities")
                        .principal(userBPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(activityService);
    }
}
