package com.bugpilot.controller;

import com.bugpilot.dto.PullRequestResponse;
import com.bugpilot.enums.PullRequestState;
import com.bugpilot.exception.GlobalExceptionHandler;
import com.bugpilot.service.PullRequestService;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PullRequestControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PullRequestService pullRequestService;

    @InjectMocks
    private PullRequestController pullRequestController;

    private Principal ownerPrincipal;
    private Principal nonOwnerPrincipal;
    private Principal adminPrincipal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(pullRequestController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        ownerPrincipal = () -> "owner@bugpilot.com";
        nonOwnerPrincipal = () -> "seconddev@bugpilot.com";
        adminPrincipal = () -> "admin@bugpilot.com";
    }

    @Test
    void getPullRequestById_whenOwner_returns200Ok() throws Exception {
        PullRequestResponse response = new PullRequestResponse();
        response.setId(20L);
        response.setNumber(7);
        response.setTitle("Add OAuth authentication");
        response.setState(PullRequestState.OPEN);
        response.setRepositoryId(1L);

        when(pullRequestService.getPullRequestById(20L, "owner@bugpilot.com")).thenReturn(response);

        mockMvc.perform(get("/pull-requests/20")
                        .principal(ownerPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.number").value(7))
                .andExpect(jsonPath("$.title").value("Add OAuth authentication"))
                .andExpect(jsonPath("$.repositoryId").value(1));

        verify(pullRequestService, times(1)).getPullRequestById(20L, "owner@bugpilot.com");
    }

    @Test
    void getPullRequestById_whenNonOwner_returns403Forbidden() throws Exception {
        when(pullRequestService.getPullRequestById(20L, "seconddev@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/pull-requests/20")
                        .principal(nonOwnerPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));

        verify(pullRequestService, times(1)).getPullRequestById(20L, "seconddev@bugpilot.com");
    }

    @Test
    void getPullRequestById_whenAdminDoesNotOwnRepository_returns403Forbidden() throws Exception {
        when(pullRequestService.getPullRequestById(20L, "admin@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/pull-requests/20")
                        .principal(adminPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));

        verify(pullRequestService, times(1)).getPullRequestById(20L, "admin@bugpilot.com");
    }

    @Test
    void getPullRequestById_whenUnauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/pull-requests/20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(pullRequestService);
    }
}
