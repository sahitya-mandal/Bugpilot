package com.bugpilot.controller;

import com.bugpilot.dto.CommitResponse;
import com.bugpilot.dto.IssueResponse;
import com.bugpilot.dto.PullRequestResponse;
import com.bugpilot.dto.RepositoryRequest;
import com.bugpilot.dto.RepositoryResponse;
import com.bugpilot.dto.SyncJobResponse;
import com.bugpilot.service.CommitService;
import com.bugpilot.service.IssueService;
import com.bugpilot.service.PullRequestService;
import com.bugpilot.service.RepositoryService;
import com.bugpilot.service.RepositorySyncService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final IssueService issueService;
    private final PullRequestService pullRequestService;
    private final CommitService commitService;
    private final RepositorySyncService repositorySyncService;

    public RepositoryController(RepositoryService repositoryService,
                                IssueService issueService,
                                PullRequestService pullRequestService,
                                CommitService commitService,
                                RepositorySyncService repositorySyncService) {
        this.repositoryService = repositoryService;
        this.issueService = issueService;
        this.pullRequestService = pullRequestService;
        this.commitService = commitService;
        this.repositorySyncService = repositorySyncService;
    }

    @PostMapping
    public RepositoryResponse createRepository(@Valid @RequestBody RepositoryRequest request,
                                               Principal principal) {
        String email = principal != null ? principal.getName() : null;
        return repositoryService.createRepository(request, email);
    }

    @PostMapping("/import")
    public ResponseEntity<SyncJobResponse> importRepository(@Valid @RequestBody RepositoryRequest request,
                                                            Principal principal) {
        String email = principal != null ? principal.getName() : null;
        SyncJobResponse response = repositorySyncService.queueImport(request, email);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<SyncJobResponse> syncRepository(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : null;
        SyncJobResponse response = repositorySyncService.queueSync(id, email);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public List<RepositoryResponse> getAllRepositories(Principal principal) {
        String email = principal != null ? principal.getName() : null;
        return repositoryService.getRepositoriesForUser(email);
    }

    @GetMapping("/{id}")
    public RepositoryResponse getRepositoryById(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : null;
        return repositoryService.getRepositoryById(id, email);
    }

    @DeleteMapping("/{id}")
    public String deleteRepository(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : null;
        repositoryService.deleteRepository(id, email);
        return "Repository deleted successfully.";
    }

    @GetMapping("/{id}/issues")
    public List<IssueResponse> getRepositoryIssues(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : null;
        repositoryService.getRepositoryEntityForUser(id, email);
        return issueService.getIssuesByRepository(id);
    }

    @GetMapping("/{id}/pull-requests")
    public List<PullRequestResponse> getRepositoryPullRequests(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : null;
        repositoryService.getRepositoryEntityForUser(id, email);
        return pullRequestService.getPullRequestsByRepository(id);
    }

    @GetMapping("/{id}/commits")
    public List<CommitResponse> getRepositoryCommits(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : null;
        repositoryService.getRepositoryEntityForUser(id, email);
        return commitService.getCommitsByRepository(id);
    }
}
