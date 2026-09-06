package com.bugpilot.service;

import com.bugpilot.entity.SyncJob;
import com.bugpilot.enums.SyncJobStatus;
import com.bugpilot.repository.SyncJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class SyncJobStartupCleanup {

    private static final Logger log = LoggerFactory.getLogger(SyncJobStartupCleanup.class);

    private final SyncJobRepository syncJobRepository;

    public SyncJobStartupCleanup(SyncJobRepository syncJobRepository) {
        this.syncJobRepository = syncJobRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupOrphanedSyncJobs() {
        List<SyncJob> orphanedJobs = syncJobRepository.findByStatusIn(
                List.of(SyncJobStatus.QUEUED, SyncJobStatus.IN_PROGRESS)
        );

        if (orphanedJobs.isEmpty()) {
            return;
        }

        log.warn("Found {} orphaned sync job(s) in QUEUED or IN_PROGRESS state. Marking as FAILED due to application restart.", orphanedJobs.size());
        LocalDateTime now = LocalDateTime.now();
        for (SyncJob job : orphanedJobs) {
            job.setStatus(SyncJobStatus.FAILED);
            job.setErrorMessage("Sync was interrupted because the application restarted.");
            job.setCompletedAt(now);
            syncJobRepository.save(job);
        }
    }
}
