package com.bugpilot.controller;

import com.bugpilot.dto.BugAnalysisResponse;
import com.bugpilot.dto.PullRequestAnalysisResponse;
import com.bugpilot.enums.AnalysisSeverity;
import com.bugpilot.enums.RiskLevel;
import com.bugpilot.exception.GlobalExceptionHandler;
import com.bugpilot.service.AIAnalysisService;
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
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiAnalysisControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AIAnalysisService aiAnalysisService;

    @InjectMocks
    private AiAnalysisController aiAnalysisController;

    private Principal ownerPrincipal;
    private Principal nonOwnerPrincipal;
    private Principal adminPrincipal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(aiAnalysisController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        ownerPrincipal = () -> "owner@bugpilot.com";
        nonOwnerPrincipal = () -> "nonowner@bugpilot.com";
        adminPrincipal = () -> "admin@bugpilot.com";
    }

    @Test
    void analyzeIssue_whenOwner_returns200Ok() throws Exception {
        BugAnalysisResponse response = new BugAnalysisResponse();
        response.setId(100L);
        response.setIssueId(10L);
        response.setIssueNumber(42);
        response.setSummary("NullPointerException in LoginController");
        response.setSeverity(AnalysisSeverity.HIGH);
        response.setAnalyzedAt(LocalDateTime.now());

        when(aiAnalysisService.analyzeIssue(10L, "owner@bugpilot.com")).thenReturn(response);

        mockMvc.perform(post("/issues/10/analyze")
                        .principal(ownerPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.issueId").value(10))
                .andExpect(jsonPath("$.issueNumber").value(42))
                .andExpect(jsonPath("$.summary").value("NullPointerException in LoginController"))
                .andExpect(jsonPath("$.severity").value("HIGH"));

        verify(aiAnalysisService, times(1)).analyzeIssue(10L, "owner@bugpilot.com");
    }

    @Test
    void analyzeIssue_whenNonOwner_returns403Forbidden() throws Exception {
        when(aiAnalysisService.analyzeIssue(10L, "nonowner@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(post("/issues/10/analyze")
                        .principal(nonOwnerPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));

        verify(aiAnalysisService, times(1)).analyzeIssue(10L, "nonowner@bugpilot.com");
    }

    @Test
    void analyzeIssue_whenAdminDoesNotOwnRepository_returns403Forbidden() throws Exception {
        when(aiAnalysisService.analyzeIssue(10L, "admin@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(post("/issues/10/analyze")
                        .principal(adminPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));

        verify(aiAnalysisService, times(1)).analyzeIssue(10L, "admin@bugpilot.com");
    }

    @Test
    void analyzeIssue_whenUnauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(post("/issues/10/analyze")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(aiAnalysisService);
    }

    @Test
    void getIssueAnalysis_whenOwner_returns200Ok() throws Exception {
        BugAnalysisResponse response = new BugAnalysisResponse();
        response.setId(100L);
        response.setIssueId(10L);
        response.setIssueNumber(42);
        response.setSummary("NullPointerException in LoginController");
        response.setSeverity(AnalysisSeverity.MEDIUM);
        response.setAnalyzedAt(LocalDateTime.now());

        when(aiAnalysisService.getIssueAnalysis(10L, "owner@bugpilot.com")).thenReturn(response);

        mockMvc.perform(get("/issues/10/analysis")
                        .principal(ownerPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.issueId").value(10))
                .andExpect(jsonPath("$.issueNumber").value(42))
                .andExpect(jsonPath("$.summary").value("NullPointerException in LoginController"))
                .andExpect(jsonPath("$.severity").value("MEDIUM"));

        verify(aiAnalysisService, times(1)).getIssueAnalysis(10L, "owner@bugpilot.com");
    }

    @Test
    void getIssueAnalysis_whenNonOwner_returns403Forbidden() throws Exception {
        when(aiAnalysisService.getIssueAnalysis(10L, "nonowner@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/issues/10/analysis")
                        .principal(nonOwnerPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));

        verify(aiAnalysisService, times(1)).getIssueAnalysis(10L, "nonowner@bugpilot.com");
    }

    @Test
    void getIssueAnalysis_whenAdminDoesNotOwnRepository_returns403Forbidden() throws Exception {
        when(aiAnalysisService.getIssueAnalysis(10L, "admin@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/issues/10/analysis")
                        .principal(adminPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));

        verify(aiAnalysisService, times(1)).getIssueAnalysis(10L, "admin@bugpilot.com");
    }

    @Test
    void getIssueAnalysis_whenUnauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/issues/10/analysis")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(aiAnalysisService);
    }

    @Test
    void analyzePullRequest_whenOwner_returns200Ok() throws Exception {
        PullRequestAnalysisResponse response = new PullRequestAnalysisResponse();
        response.setId(200L);
        response.setPullRequestId(20L);
        response.setPullRequestNumber(7);
        response.setSummary("PR review summary");
        response.setRiskLevel(RiskLevel.LOW);
        response.setAnalyzedAt(LocalDateTime.now());

        when(aiAnalysisService.analyzePullRequest(20L, "owner@bugpilot.com")).thenReturn(response);

        mockMvc.perform(post("/pull-requests/20/analyze")
                        .principal(ownerPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.pullRequestId").value(20))
                .andExpect(jsonPath("$.pullRequestNumber").value(7))
                .andExpect(jsonPath("$.riskLevel").value("LOW"));

        verify(aiAnalysisService, times(1)).analyzePullRequest(20L, "owner@bugpilot.com");
    }

    @Test
    void analyzePullRequest_whenNonOwner_returns403Forbidden() throws Exception {
        when(aiAnalysisService.analyzePullRequest(20L, "nonowner@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(post("/pull-requests/20/analyze")
                        .principal(nonOwnerPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));

        verify(aiAnalysisService, times(1)).analyzePullRequest(20L, "nonowner@bugpilot.com");
    }

    @Test
    void analyzePullRequest_whenAdminDoesNotOwnRepository_returns403Forbidden() throws Exception {
        when(aiAnalysisService.analyzePullRequest(20L, "admin@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(post("/pull-requests/20/analyze")
                        .principal(adminPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));

        verify(aiAnalysisService, times(1)).analyzePullRequest(20L, "admin@bugpilot.com");
    }

    @Test
    void analyzePullRequest_whenUnauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(post("/pull-requests/20/analyze")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(aiAnalysisService);
    }

    @Test
    void getPullRequestAnalysis_whenOwner_returns200Ok() throws Exception {
        PullRequestAnalysisResponse response = new PullRequestAnalysisResponse();
        response.setId(200L);
        response.setPullRequestId(20L);
        response.setPullRequestNumber(7);
        response.setSummary("PR review summary");
        response.setRiskLevel(RiskLevel.MEDIUM);
        response.setAnalyzedAt(LocalDateTime.now());

        when(aiAnalysisService.getPullRequestAnalysis(20L, "owner@bugpilot.com")).thenReturn(response);

        mockMvc.perform(get("/pull-requests/20/analysis")
                        .principal(ownerPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.pullRequestId").value(20))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"));

        verify(aiAnalysisService, times(1)).getPullRequestAnalysis(20L, "owner@bugpilot.com");
    }

    @Test
    void getPullRequestAnalysis_whenNonOwner_returns403Forbidden() throws Exception {
        when(aiAnalysisService.getPullRequestAnalysis(20L, "nonowner@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/pull-requests/20/analysis")
                        .principal(nonOwnerPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));

        verify(aiAnalysisService, times(1)).getPullRequestAnalysis(20L, "nonowner@bugpilot.com");
    }

    @Test
    void getPullRequestAnalysis_whenAdminDoesNotOwnRepository_returns403Forbidden() throws Exception {
        when(aiAnalysisService.getPullRequestAnalysis(20L, "admin@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/pull-requests/20/analysis")
                        .principal(adminPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));

        verify(aiAnalysisService, times(1)).getPullRequestAnalysis(20L, "admin@bugpilot.com");
    }

    @Test
    void getPullRequestAnalysis_whenUnauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/pull-requests/20/analysis")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(aiAnalysisService);
    }
}
