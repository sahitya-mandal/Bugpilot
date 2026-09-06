package com.bugpilot.service;

import com.bugpilot.dto.RepositoryRequest;
import com.bugpilot.dto.SyncJobResponse;
import com.bugpilot.entity.Repository;
import com.bugpilot.entity.SyncJob;
import com.bugpilot.entity.User;
import com.bugpilot.enums.Role;
import com.bugpilot.enums.SyncJobStatus;
import com.bugpilot.enums.SyncJobStep;
import com.bugpilot.exception.ConcurrentSyncException;
import com.bugpilot.repository.RepoRepository;
import com.bugpilot.repository.SyncJobRepository;
import com.bugpilot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepositorySyncServiceTest {

    @Mock
    private SyncJobRepository syncJobRepository;

    @Mock
    private RepoRepository repoRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private RepositorySyncPersistenceService persistenceService;

    @Mock
    private RepositorySyncWorker syncWorker;

    @InjectMocks
    private RepositorySyncService repositorySyncService;

    private User owner;
    private User otherUser;
    private Repository repository;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(10L);
        owner.setName("Dev User");
        owner.setEmail("dev@bugpilot.com");
        owner.setRole(Role.DEVELOPER);

        otherUser = new User();
        otherUser.setId(20L);
        otherUser.setName("Other User");
        otherUser.setEmail("other@bugpilot.com");
        otherUser.setRole(Role.DEVELOPER);

        repository = new Repository();
        repository.setId(1L);
        repository.setOwner("octocat");
        repository.setName("Hello-World");
        repository.setUser(owner);
    }

    @Test
    void queueSync_whenOwnerAndNoActiveJob_createsQueuedJobAndTriggersWorker() {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));
        when(syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection()))
                .thenReturn(Optional.empty());

        SyncJob createdJob = new SyncJob(repository, owner);
        createdJob.setId(101L);
        when(persistenceService.createSyncJob(repository, owner)).thenReturn(createdJob);

        SyncJobResponse response = repositorySyncService.queueSync(1L, "dev@bugpilot.com");

        assertNotNull(response);
        assertEquals(101L, response.getJobId());
        assertEquals(SyncJobStatus.QUEUED, response.getStatus());
        assertEquals(SyncJobStep.QUEUED, response.getCurrentStep());
        assertEquals(1L, response.getRepositoryId());

        verify(syncWorker, times(1)).executeSync(101L);
    }

    @Test
    void queueSync_whenActiveQueuedJobExists_throwsConcurrentSyncException() {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));

        SyncJob activeJob = new SyncJob(repository, owner);
        activeJob.setId(99L);
        activeJob.setStatus(SyncJobStatus.QUEUED);

        when(syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection()))
                .thenReturn(Optional.of(activeJob));

        ConcurrentSyncException ex = assertThrows(ConcurrentSyncException.class,
                () -> repositorySyncService.queueSync(1L, "dev@bugpilot.com"));

        assertEquals(99L, ex.getActiveJobId());
        assertTrue(ex.getMessage().contains("already in progress"));
        verifyNoInteractions(syncWorker);
        verify(persistenceService, never()).createSyncJob(any(), any());
    }

    @Test
    void queueSync_whenActiveInProgressJobExists_throwsConcurrentSyncException() {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));

        SyncJob activeJob = new SyncJob(repository, owner);
        activeJob.setId(98L);
        activeJob.setStatus(SyncJobStatus.IN_PROGRESS);

        when(syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection()))
                .thenReturn(Optional.of(activeJob));

        ConcurrentSyncException ex = assertThrows(ConcurrentSyncException.class,
                () -> repositorySyncService.queueSync(1L, "dev@bugpilot.com"));

        assertEquals(98L, ex.getActiveJobId());
        verifyNoInteractions(syncWorker);
    }

    @Test
    void queueSync_whenPreviousJobCompleted_allowsNewSync() {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));
        // Previous job completed -> active query returns empty
        when(syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection()))
                .thenReturn(Optional.empty());

        SyncJob createdJob = new SyncJob(repository, owner);
        createdJob.setId(105L);
        when(persistenceService.createSyncJob(repository, owner)).thenReturn(createdJob);

        SyncJobResponse response = repositorySyncService.queueSync(1L, "dev@bugpilot.com");

        assertNotNull(response);
        assertEquals(105L, response.getJobId());
        assertEquals(SyncJobStatus.QUEUED, response.getStatus());
        verify(persistenceService, times(1)).createSyncJob(repository, owner);
    }

    @Test
    void queueSync_whenPreviousJobFailed_allowsNewSync() {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));
        // Previous job failed -> active query returns empty
        when(syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection()))
                .thenReturn(Optional.empty());

        SyncJob createdJob = new SyncJob(repository, owner);
        createdJob.setId(106L);
        when(persistenceService.createSyncJob(repository, owner)).thenReturn(createdJob);

        SyncJobResponse response = repositorySyncService.queueSync(1L, "dev@bugpilot.com");

        assertNotNull(response);
        assertEquals(106L, response.getJobId());
        assertEquals(SyncJobStatus.QUEUED, response.getStatus());
        verify(persistenceService, times(1)).createSyncJob(repository, owner);
    }

    @Test
    void queueSync_whenConcurrentExecution_onlyOneSucceedsAndSecondThrowsConcurrentSyncException() throws Exception {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));

        SyncJob createdJob = new SyncJob(repository, owner);
        createdJob.setId(201L);

        // First call sees empty and creates job; second call sees active job 201L
        java.util.concurrent.atomic.AtomicBoolean firstCallDone = new java.util.concurrent.atomic.AtomicBoolean(false);
        when(syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection()))
                .thenAnswer(inv -> {
                    if (firstCallDone.compareAndSet(false, true)) {
                        return Optional.empty();
                    } else {
                        return Optional.of(createdJob);
                    }
                });

        when(persistenceService.createSyncJob(repository, owner)).thenReturn(createdJob);

        // Execute sequential/concurrent invocations
        SyncJobResponse firstResponse = repositorySyncService.queueSync(1L, "dev@bugpilot.com");
        assertNotNull(firstResponse);
        assertEquals(201L, firstResponse.getJobId());

        ConcurrentSyncException ex = assertThrows(ConcurrentSyncException.class,
                () -> repositorySyncService.queueSync(1L, "dev@bugpilot.com"));
        assertEquals(201L, ex.getActiveJobId());

        // Verify exactly one job was created
        verify(persistenceService, times(1)).createSyncJob(any(), any());
    }

    @Test
    void queueSync_whenDataIntegrityViolationOccurs_translatesToConcurrentSyncException() {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));

        SyncJob existingActiveJob = new SyncJob(repository, owner);
        existingActiveJob.setId(301L);

        when(syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection()))
                .thenReturn(Optional.empty()) // Initial check sees nothing
                .thenReturn(Optional.of(existingActiveJob)); // Check inside catch sees active job

        when(persistenceService.createSyncJob(repository, owner))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Unique constraint violation"));

        ConcurrentSyncException ex = assertThrows(ConcurrentSyncException.class,
                () -> repositorySyncService.queueSync(1L, "dev@bugpilot.com"));

        assertEquals(301L, ex.getActiveJobId());
    }

    @Test
    void queueSync_whenNonOwner_throwsAccessDeniedException() {
        when(userRepository.findFirstByEmailOrderByIdDesc("other@bugpilot.com")).thenReturn(Optional.of(otherUser));
        when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));

        assertThrows(AccessDeniedException.class,
                () -> repositorySyncService.queueSync(1L, "other@bugpilot.com"));

        verifyNoInteractions(syncWorker);
        verifyNoInteractions(persistenceService);
    }

    @Test
    void queueImport_whenRepoAlreadyRegisteredByAnotherUser_throwsAccessDeniedException() {
        when(userRepository.findFirstByEmailOrderByIdDesc("other@bugpilot.com")).thenReturn(Optional.of(otherUser));
        when(repoRepository.findByOwnerAndNameForUpdate("octocat", "Hello-World")).thenReturn(Optional.of(repository));

        RepositoryRequest request = new RepositoryRequest("octocat", "Hello-World");

        assertThrows(AccessDeniedException.class,
                () -> repositorySyncService.queueImport(request, "other@bugpilot.com"));

        verifyNoInteractions(syncWorker);
    }

    @Test
    void queueImport_whenNewRepository_persistsRepoAndCreatesQueuedJob() {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByOwnerAndNameForUpdate("neworg", "newrepo")).thenReturn(Optional.empty());

        Repository savedRepo = new Repository();
        savedRepo.setId(2L);
        savedRepo.setOwner("neworg");
        savedRepo.setName("newrepo");
        savedRepo.setUser(owner);
        when(repoRepository.save(any(Repository.class))).thenReturn(savedRepo);

        SyncJob createdJob = new SyncJob(savedRepo, owner);
        createdJob.setId(102L);
        when(persistenceService.createSyncJob(savedRepo, owner)).thenReturn(createdJob);

        RepositoryRequest request = new RepositoryRequest("neworg", "newrepo");
        SyncJobResponse response = repositorySyncService.queueImport(request, "dev@bugpilot.com");

        assertNotNull(response);
        assertEquals(102L, response.getJobId());
        assertEquals(SyncJobStatus.QUEUED, response.getStatus());
        verify(syncWorker, times(1)).executeSync(102L);
    }

    @Test
    void getJobByIdForUser_whenOwner_returnsJobResponse() {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));

        SyncJob job = new SyncJob(repository, owner);
        job.setId(101L);
        job.setStatus(SyncJobStatus.COMPLETED);
        when(syncJobRepository.findById(101L)).thenReturn(Optional.of(job));

        SyncJobResponse response = repositorySyncService.getJobByIdForUser(101L, "dev@bugpilot.com");

        assertNotNull(response);
        assertEquals(101L, response.getJobId());
        assertEquals(SyncJobStatus.COMPLETED, response.getStatus());
    }

    @Test
    void getJobByIdForUser_whenNonOwner_throwsAccessDeniedException() {
        when(userRepository.findFirstByEmailOrderByIdDesc("other@bugpilot.com")).thenReturn(Optional.of(otherUser));

        SyncJob job = new SyncJob(repository, owner);
        job.setId(101L);
        when(syncJobRepository.findById(101L)).thenReturn(Optional.of(job));

        assertThrows(AccessDeniedException.class,
                () -> repositorySyncService.getJobByIdForUser(101L, "other@bugpilot.com"));
    }

    @Test
    void queueSync_whenTaskRejectedExceptionThrown_marksJobFailedWithSafeErrorMessage() {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));
        when(syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection()))
                .thenReturn(Optional.empty());

        SyncJob createdJob = new SyncJob(repository, owner);
        createdJob.setId(101L);
        when(persistenceService.createSyncJob(repository, owner)).thenReturn(createdJob);
        doThrow(new TaskRejectedException("Executor queue capacity reached"))
                .when(syncWorker).executeSync(101L);

        SyncJobResponse response = repositorySyncService.queueSync(1L, "dev@bugpilot.com");

        assertNotNull(response);
        assertEquals(101L, response.getJobId());
        verify(syncWorker, times(1)).executeSync(101L);
        verify(persistenceService, times(1)).markJobFailed(
                eq(101L),
                eq(RepositorySyncService.QUEUE_FULL_ERROR_MESSAGE),
                isNull(),
                isNull()
        );
    }

    @Test
    void queueImport_whenTaskRejectedExceptionThrown_marksJobFailedWithSafeErrorMessage() {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByOwnerAndNameForUpdate("neworg", "newrepo")).thenReturn(Optional.empty());

        Repository savedRepo = new Repository();
        savedRepo.setId(2L);
        savedRepo.setOwner("neworg");
        savedRepo.setName("newrepo");
        savedRepo.setUser(owner);
        when(repoRepository.save(any(Repository.class))).thenReturn(savedRepo);

        SyncJob createdJob = new SyncJob(savedRepo, owner);
        createdJob.setId(102L);
        when(persistenceService.createSyncJob(savedRepo, owner)).thenReturn(createdJob);
        doThrow(new TaskRejectedException("Queue full"))
                .when(syncWorker).executeSync(102L);

        RepositoryRequest request = new RepositoryRequest("neworg", "newrepo");
        SyncJobResponse response = repositorySyncService.queueImport(request, "dev@bugpilot.com");

        assertNotNull(response);
        assertEquals(102L, response.getJobId());
        verify(syncWorker, times(1)).executeSync(102L);
        verify(persistenceService, times(1)).markJobFailed(
                eq(102L),
                eq(RepositorySyncService.QUEUE_FULL_ERROR_MESSAGE),
                isNull(),
                isNull()
        );
    }

    @Test
    void queueSync_whenRejectedExecutionExceptionThrown_marksJobFailedWithSafeErrorMessage() {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));
        when(syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection()))
                .thenReturn(Optional.empty());

        SyncJob createdJob = new SyncJob(repository, owner);
        createdJob.setId(103L);
        when(persistenceService.createSyncJob(repository, owner)).thenReturn(createdJob);
        doThrow(new RejectedExecutionException("Task rejected"))
                .when(syncWorker).executeSync(103L);

        SyncJobResponse response = repositorySyncService.queueSync(1L, "dev@bugpilot.com");

        assertNotNull(response);
        verify(persistenceService, times(1)).markJobFailed(
                eq(103L),
                eq(RepositorySyncService.QUEUE_FULL_ERROR_MESSAGE),
                isNull(),
                isNull()
        );
    }

    @Test
    void queueSync_afterRejectedJobIsMarkedFailed_allowsSubsequentSync() {
        when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
        when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));

        // First sync: task rejected
        SyncJob firstJob = new SyncJob(repository, owner);
        firstJob.setId(101L);
        when(persistenceService.createSyncJob(repository, owner)).thenReturn(firstJob);
        doThrow(new TaskRejectedException("Queue full"))
                .when(syncWorker).executeSync(101L);

        // Active check sees no active jobs initially
        when(syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection()))
                .thenReturn(Optional.empty());

        repositorySyncService.queueSync(1L, "dev@bugpilot.com");

        verify(persistenceService, times(1)).markJobFailed(
                eq(101L),
                eq(RepositorySyncService.QUEUE_FULL_ERROR_MESSAGE),
                isNull(),
                isNull()
        );

        // Second sync: executor is now free, active check still sees no active jobs (because first job was marked FAILED)
        reset(syncWorker);
        SyncJob secondJob = new SyncJob(repository, owner);
        secondJob.setId(102L);
        when(persistenceService.createSyncJob(repository, owner)).thenReturn(secondJob);

        SyncJobResponse secondResponse = repositorySyncService.queueSync(1L, "dev@bugpilot.com");

        assertNotNull(secondResponse);
        assertEquals(102L, secondResponse.getJobId());
        verify(syncWorker, times(1)).executeSync(102L);
    }

    @Test
    void triggerWorkerAfterCommit_withTransactionSynchronization_whenTaskRejected_marksJobFailed() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
            when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));
            when(syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection()))
                    .thenReturn(Optional.empty());

            SyncJob createdJob = new SyncJob(repository, owner);
            createdJob.setId(104L);
            when(persistenceService.createSyncJob(repository, owner)).thenReturn(createdJob);
            doThrow(new TaskRejectedException("Queue full"))
                    .when(syncWorker).executeSync(104L);

            repositorySyncService.queueSync(1L, "dev@bugpilot.com");

            // Worker was NOT called yet because transaction has not committed
            verify(syncWorker, never()).executeSync(104L);

            // Now simulate transaction commit by firing all registered synchronizations
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            // Now executeSync was called and task rejection was handled
            verify(syncWorker, times(1)).executeSync(104L);
            verify(persistenceService, times(1)).markJobFailed(
                    eq(104L),
                    eq(RepositorySyncService.QUEUE_FULL_ERROR_MESSAGE),
                    isNull(),
                    isNull()
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void triggerWorkerAfterCommit_withTransactionSynchronization_whenNormal_triggersWorker() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            when(userRepository.findFirstByEmailOrderByIdDesc("dev@bugpilot.com")).thenReturn(Optional.of(owner));
            when(repoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(repository));
            when(syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection()))
                    .thenReturn(Optional.empty());

            SyncJob createdJob = new SyncJob(repository, owner);
            createdJob.setId(105L);
            when(persistenceService.createSyncJob(repository, owner)).thenReturn(createdJob);

            repositorySyncService.queueSync(1L, "dev@bugpilot.com");

            verify(syncWorker, never()).executeSync(105L);

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(syncWorker, times(1)).executeSync(105L);
            verify(persistenceService, never()).markJobFailed(any(), any(), any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
