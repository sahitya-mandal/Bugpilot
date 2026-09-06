package com.bugpilot.controller;

import com.bugpilot.dto.PullRequestResponse;
import com.bugpilot.service.PullRequestService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/pull-requests")
public class PullRequestController {

    private final PullRequestService pullRequestService;

    public PullRequestController(PullRequestService pullRequestService) {
        this.pullRequestService = pullRequestService;
    }

    @GetMapping("/{id}")
    public PullRequestResponse getPullRequestById(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Access denied: unauthenticated");
        }
        return pullRequestService.getPullRequestById(id, principal.getName());
    }
}
