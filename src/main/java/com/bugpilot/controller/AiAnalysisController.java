package com.bugpilot.controller;

import com.bugpilot.dto.BugAnalysisResponse;
import com.bugpilot.dto.PullRequestAnalysisResponse;
import com.bugpilot.service.AIAnalysisService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class AiAnalysisController {

    private final AIAnalysisService aiAnalysisService;

    public AiAnalysisController(AIAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @PostMapping("/issues/{id}/analyze")
    public BugAnalysisResponse analyzeIssue(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Access denied: unauthenticated");
        }
        return aiAnalysisService.analyzeIssue(id, principal.getName());
    }

    @GetMapping("/issues/{id}/analysis")
    public BugAnalysisResponse getIssueAnalysis(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Access denied: unauthenticated");
        }
        return aiAnalysisService.getIssueAnalysis(id, principal.getName());
    }

    @PostMapping("/pull-requests/{id}/analyze")
    public PullRequestAnalysisResponse analyzePullRequest(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Access denied: unauthenticated");
        }
        return aiAnalysisService.analyzePullRequest(id, principal.getName());
    }

    @GetMapping("/pull-requests/{id}/analysis")
    public PullRequestAnalysisResponse getPullRequestAnalysis(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Access denied: unauthenticated");
        }
        return aiAnalysisService.getPullRequestAnalysis(id, principal.getName());
    }
}
