package com.bugpilot.controller;

import com.bugpilot.dto.IssueResponse;
import com.bugpilot.service.IssueService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @GetMapping("/{id}")
    public IssueResponse getIssueById(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Access denied: unauthenticated");
        }
        return issueService.getIssueById(id, principal.getName());
    }
}
