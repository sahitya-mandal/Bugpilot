package com.bugpilot.controller;

import com.bugpilot.dto.SyncJobResponse;
import com.bugpilot.service.RepositorySyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
public class SyncJobController {

    private final RepositorySyncService repositorySyncService;

    public SyncJobController(RepositorySyncService repositorySyncService) {
        this.repositorySyncService = repositorySyncService;
    }

    @GetMapping("/sync-jobs/{jobId}")
    public SyncJobResponse getSyncJob(@PathVariable Long jobId, Principal principal) {
        String email = principal != null ? principal.getName() : null;
        return repositorySyncService.getJobByIdForUser(jobId, email);
    }

    @GetMapping("/repositories/{id}/sync-jobs")
    public List<SyncJobResponse> getRepositorySyncJobs(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : null;
        return repositorySyncService.getJobsForRepository(id, email);
    }

    @GetMapping("/repositories/{id}/sync-jobs/active")
    public ResponseEntity<SyncJobResponse> getActiveSyncJob(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : null;
        Optional<SyncJobResponse> activeJob = repositorySyncService.getActiveJobForRepository(id, email);
        return activeJob.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
