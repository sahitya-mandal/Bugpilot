package com.bugpilot.service;

import com.bugpilot.entity.SyncJob;
import com.bugpilot.enums.SyncJobStatus;
import com.bugpilot.repository.SyncJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class SyncJobStartupCleanup {

    private static final Logger log = LoggerFactory.getLogger(SyncJobStartupCleanup.class);

    public static final Duration ORPHAN_QUEUED_THRESHOLD = Duration.ofMinutes(5);
    public static final Duration ORPHAN_IN_PROGRESS_THRESHOLD = Duration.ofMinutes(15);

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

    public boolean isJobOrphaned(SyncJob job) {
        if (job == null || job.getStatus() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (job.getStatus() == SyncJobStatus.QUEUED) {
            LocalDateTime createdAt = job.getCreatedAt();
            return createdAt != null && createdAt.isBefore(now.minus(ORPHAN_QUEUED_THRESHOLD));
        }
        if (job.getStatus() == SyncJobStatus.IN_PROGRESS) {
            LocalDateTime refTime = job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt();
            return refTime != null && refTime.isBefore(now.minus(ORPHAN_IN_PROGRESS_THRESHOLD));
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverJobIfOrphaned(SyncJob job) {
        if (job == null || !isJobOrphaned(job)) {
            return false;
        }
        log.warn("Recovering orphaned sync job ID {} (status: {}, created: {}, started: {}). Marking as FAILED.",
                job.getId(), job.getStatus(), job.getCreatedAt(), job.getStartedAt());
        LocalDateTime now = LocalDateTime.now();
        SyncJob target = job;
        if (job.getId() != null) {
            target = syncJobRepository.findById(job.getId()).orElse(job);
        }
        target.setStatus(SyncJobStatus.FAILED);
        target.setErrorMessage("Sync timed out or was orphaned without an active worker.");
        target.setCompletedAt(now);
        syncJobRepository.save(target);
        if (target != job) {
            job.setStatus(SyncJobStatus.FAILED);
            job.setErrorMessage(target.getErrorMessage());
            job.setCompletedAt(now);
        }
        return true;
    }
}
