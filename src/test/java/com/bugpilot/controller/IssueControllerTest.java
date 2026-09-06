package com.bugpilot.controller;

import com.bugpilot.dto.IssueResponse;
import com.bugpilot.enums.IssueState;
import com.bugpilot.exception.GlobalExceptionHandler;
import com.bugpilot.service.IssueService;
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
class IssueControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IssueService issueService;

    @InjectMocks
    private IssueController issueController;

    private Principal ownerPrincipal;
    private Principal nonOwnerPrincipal;
    private Principal adminPrincipal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(issueController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        ownerPrincipal = () -> "owner@bugpilot.com";
        nonOwnerPrincipal = () -> "seconddev@bugpilot.com";
        adminPrincipal = () -> "admin@bugpilot.com";
    }

    @Test
    void getIssueById_whenOwner_returns200Ok() throws Exception {
        IssueResponse response = new IssueResponse();
        response.setId(10L);
        response.setNumber(42);
        response.setTitle("NullPointerException in LoginController");
        response.setState(IssueState.OPEN);
        response.setRepositoryId(1L);

        when(issueService.getIssueById(10L, "owner@bugpilot.com")).thenReturn(response);

        mockMvc.perform(get("/issues/10")
                        .principal(ownerPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.number").value(42))
                .andExpect(jsonPath("$.title").value("NullPointerException in LoginController"))
                .andExpect(jsonPath("$.repositoryId").value(1));

        verify(issueService, times(1)).getIssueById(10L, "owner@bugpilot.com");
    }

    @Test
    void getIssueById_whenNonOwner_returns403Forbidden() throws Exception {
        when(issueService.getIssueById(10L, "seconddev@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/issues/10")
                        .principal(nonOwnerPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));

        verify(issueService, times(1)).getIssueById(10L, "seconddev@bugpilot.com");
    }

    @Test
    void getIssueById_whenAdminDoesNotOwnRepository_returns403Forbidden() throws Exception {
        when(issueService.getIssueById(10L, "admin@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        mockMvc.perform(get("/issues/10")
                        .principal(adminPrincipal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));

        verify(issueService, times(1)).getIssueById(10L, "admin@bugpilot.com");
    }

    @Test
    void getIssueById_whenUnauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/issues/10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(issueService);
    }
}
