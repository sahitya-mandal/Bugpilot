package com.bugpilot.service;

import com.bugpilot.entity.Repository;
import com.bugpilot.entity.SyncJob;
import com.bugpilot.entity.User;
import com.bugpilot.enums.Role;
import com.bugpilot.enums.SyncJobStatus;
import com.bugpilot.repository.SyncJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncJobStartupCleanupTest {

    @Mock
    private SyncJobRepository syncJobRepository;

    @InjectMocks
    private SyncJobStartupCleanup startupCleanup;

    @Test
    void cleanupOrphanedSyncJobs_marksQueuedAndInProgressJobsAsFailed() {
        User user = new User();
        user.setName("Dev");
        user.setEmail("dev@test.com");
        user.setRole(Role.DEVELOPER);

        Repository repo = new Repository();
        repo.setName("Repo");

        SyncJob queuedJob = new SyncJob(repo, user);
        queuedJob.setId(1L);
        queuedJob.setStatus(SyncJobStatus.QUEUED);

        SyncJob inProgressJob = new SyncJob(repo, user);
        inProgressJob.setId(2L);
        inProgressJob.setStatus(SyncJobStatus.IN_PROGRESS);

        when(syncJobRepository.findByStatusIn(List.of(SyncJobStatus.QUEUED, SyncJobStatus.IN_PROGRESS)))
                .thenReturn(List.of(queuedJob, inProgressJob));

        startupCleanup.cleanupOrphanedSyncJobs();

        assertEquals(SyncJobStatus.FAILED, queuedJob.getStatus());
        assertEquals("Sync was interrupted because the application restarted.", queuedJob.getErrorMessage());
        assertNotNull(queuedJob.getCompletedAt());

        assertEquals(SyncJobStatus.FAILED, inProgressJob.getStatus());
        assertEquals("Sync was interrupted because the application restarted.", inProgressJob.getErrorMessage());
        assertNotNull(inProgressJob.getCompletedAt());

        verify(syncJobRepository, times(2)).save(any(SyncJob.class));
    }

    @Test
    void cleanupOrphanedSyncJobs_whenNoOrphanedJobs_doesNothing() {
        when(syncJobRepository.findByStatusIn(anyList())).thenReturn(List.of());

        startupCleanup.cleanupOrphanedSyncJobs();

        verify(syncJobRepository, never()).save(any(SyncJob.class));
    }

    @Test
    void isJobOrphaned_whenFreshJob_returnsFalse() {
        SyncJob job = new SyncJob();
        job.setStatus(SyncJobStatus.QUEUED);
        job.setCreatedAt(java.time.LocalDateTime.now());

        assertFalse(startupCleanup.isJobOrphaned(job));
    }

    @Test
    void isJobOrphaned_whenQueuedAndOlderThanThreshold_returnsTrue() {
        SyncJob job = new SyncJob();
        job.setStatus(SyncJobStatus.QUEUED);
        job.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(6));

        assertTrue(startupCleanup.isJobOrphaned(job));
    }

    @Test
    void isJobOrphaned_whenInProgressAndOlderThanThreshold_returnsTrue() {
        SyncJob job = new SyncJob();
        job.setStatus(SyncJobStatus.IN_PROGRESS);
        job.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(20));
        job.setStartedAt(java.time.LocalDateTime.now().minusMinutes(16));

        assertTrue(startupCleanup.isJobOrphaned(job));
    }

    @Test
    void recoverJobIfOrphaned_whenOrphaned_marksAsFailedAndSaves() {
        SyncJob job = new SyncJob();
        job.setId(10L);
        job.setStatus(SyncJobStatus.QUEUED);
        job.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(10));

        when(syncJobRepository.findById(10L)).thenReturn(java.util.Optional.of(job));
        when(syncJobRepository.save(any(SyncJob.class))).thenAnswer(i -> i.getArgument(0));

        boolean recovered = startupCleanup.recoverJobIfOrphaned(job);

        assertTrue(recovered);
        assertEquals(SyncJobStatus.FAILED, job.getStatus());
        assertEquals("Sync timed out or was orphaned without an active worker.", job.getErrorMessage());
        assertNotNull(job.getCompletedAt());
        verify(syncJobRepository).save(job);
    }

    @Test
    void recoverJobIfOrphaned_whenFresh_doesNothingAndReturnsFalse() {
        SyncJob job = new SyncJob();
        job.setId(10L);
        job.setStatus(SyncJobStatus.QUEUED);
        job.setCreatedAt(java.time.LocalDateTime.now().minusSeconds(30));

        boolean recovered = startupCleanup.recoverJobIfOrphaned(job);

        assertFalse(recovered);
        assertEquals(SyncJobStatus.QUEUED, job.getStatus());
        verify(syncJobRepository, never()).save(any(SyncJob.class));
    }
}
