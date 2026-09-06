package com.bugpilot.service;

import com.bugpilot.dto.RepositoryRequest;
import com.bugpilot.dto.SyncJobResponse;
import com.bugpilot.entity.Repository;
import com.bugpilot.entity.SyncJob;
import com.bugpilot.entity.User;
import com.bugpilot.enums.SyncJobStatus;
import com.bugpilot.exception.ConcurrentSyncException;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.RepoRepository;
import com.bugpilot.repository.SyncJobRepository;
import com.bugpilot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

@Service
@Transactional
public class RepositorySyncService {

    private static final Logger log = LoggerFactory.getLogger(RepositorySyncService.class);
    public static final String QUEUE_FULL_ERROR_MESSAGE = "Sync queue is full. Please retry shortly.";

    private final SyncJobRepository syncJobRepository;
    private final RepoRepository repoRepository;
    private final UserRepository userRepository;
    private final RepositoryService repositoryService;
    private final RepositorySyncPersistenceService persistenceService;
    private final RepositorySyncWorker syncWorker;

    public RepositorySyncService(SyncJobRepository syncJobRepository,
                                 RepoRepository repoRepository,
                                 UserRepository userRepository,
                                 RepositoryService repositoryService,
                                 RepositorySyncPersistenceService persistenceService,
                                 RepositorySyncWorker syncWorker) {
        this.syncJobRepository = syncJobRepository;
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
        this.repositoryService = repositoryService;
        this.persistenceService = persistenceService;
        this.syncWorker = syncWorker;
    }

    private User getAuthenticatedUser(String userEmail) {
        if (userEmail == null) {
            throw new AccessDeniedException("Access denied: unauthenticated");
        }
        return userRepository.findFirstByEmailOrderByIdDesc(userEmail)
                .or(() -> userRepository.findByEmail(userEmail))
                .orElseThrow(() -> new AccessDeniedException("Access denied: user not found"));
    }

    public SyncJobResponse queueSync(Long repositoryId, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        // Concurrency serialization: Acquire pessimistic write lock on the repository row in the database
        Repository repository = repoRepository.findByIdForUpdate(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", repositoryId));

        // Ownership authorization check
        boolean isOwner = repository.getUser() != null && repository.getUser().getId().equals(user.getId());
        if (!isOwner) {
            throw new AccessDeniedException("Access denied: You do not own this repository");
        }

        // Concurrency protection: Check for active job while holding repository row lock
        Optional<SyncJob> activeJob = syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(
                repositoryId, List.of(SyncJobStatus.QUEUED, SyncJobStatus.IN_PROGRESS)
        );
        if (activeJob.isPresent()) {
            throw new ConcurrentSyncException(activeJob.get().getId());
        }

        // Create and persist job with status QUEUED
        SyncJob job;
        try {
            job = persistenceService.createSyncJob(repository, user);
        } catch (DataIntegrityViolationException e) {
            log.warn("Database constraint violation creating SyncJob for repository {}: {}", repositoryId, e.getMessage());
            Optional<SyncJob> active = syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(
                    repositoryId, List.of(SyncJobStatus.QUEUED, SyncJobStatus.IN_PROGRESS)
            );
            throw new ConcurrentSyncException(active.map(SyncJob::getId).orElse(null));
        }

        Long jobId = job.getId();

        // Queue async worker only after the transaction commits (or immediately if no active transaction)
        triggerWorkerAfterCommit(jobId);

        return SyncJobResponse.fromEntity(job, "Repository synchronization has been queued.");
    }

    public SyncJobResponse queueImport(RepositoryRequest request, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        String owner = request.getOwner().trim();
        String name = request.getName().trim();

        // Check if repository already exists (with row lock if present)
        Optional<Repository> existing = repoRepository.findByOwnerAndNameForUpdate(owner, name);
        Repository repository;

        if (existing.isPresent()) {
            repository = existing.get();
            // IDOR / ownership check on existing repo
            if (repository.getUser() != null && !repository.getUser().getId().equals(user.getId())) {
                throw new AccessDeniedException("Access denied: Repository is already registered by another user");
            }
            // Check for active job while holding repository lock
            Optional<SyncJob> activeJob = syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(
                    repository.getId(), List.of(SyncJobStatus.QUEUED, SyncJobStatus.IN_PROGRESS)
            );
            if (activeJob.isPresent()) {
                throw new ConcurrentSyncException(activeJob.get().getId());
            }
        } else {
            // New repository creation: serialize across JVM by normalized repo key
            String lockKey = ("repo-import:" + owner.toLowerCase() + "/" + name.toLowerCase()).intern();
            synchronized (lockKey) {
                Optional<Repository> recheck = repoRepository.findByOwnerAndNameForUpdate(owner, name);
                if (recheck.isPresent()) {
                    repository = recheck.get();
                    if (repository.getUser() != null && !repository.getUser().getId().equals(user.getId())) {
                        throw new AccessDeniedException("Access denied: Repository is already registered by another user");
                    }
                    Optional<SyncJob> activeJob = syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(
                            repository.getId(), List.of(SyncJobStatus.QUEUED, SyncJobStatus.IN_PROGRESS)
                    );
                    if (activeJob.isPresent()) {
                        throw new ConcurrentSyncException(activeJob.get().getId());
                    }
                } else {
                    repository = new Repository();
                    repository.setOwner(owner);
                    repository.setName(name);
                    repository.setFullName(owner + "/" + name);
                    if (request.getDescription() != null) {
                        repository.setDescription(request.getDescription());
                    }
                    if (request.getGithubUrl() != null && !request.getGithubUrl().isBlank()) {
                        repository.setHtmlUrl(request.getGithubUrl().trim());
                    } else {
                        repository.setHtmlUrl("https://github.com/" + owner + "/" + name);
                    }
                    repository.setUser(user);
                    repository.setUpdatedAt(LocalDateTime.now());
                    repository = repoRepository.save(repository);
                }
            }
        }

        // Create and persist SyncJob
        SyncJob job;
        try {
            job = persistenceService.createSyncJob(repository, user);
        } catch (DataIntegrityViolationException e) {
            log.warn("Database constraint violation creating SyncJob for repository {}: {}", repository.getId(), e.getMessage());
            Optional<SyncJob> active = syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(
                    repository.getId(), List.of(SyncJobStatus.QUEUED, SyncJobStatus.IN_PROGRESS)
            );
            throw new ConcurrentSyncException(active.map(SyncJob::getId).orElse(null));
        }

        Long jobId = job.getId();

        // Trigger worker after commit
        triggerWorkerAfterCommit(jobId);

        return SyncJobResponse.fromEntity(job, "Repository import has been queued.");
    }

    void triggerWorkerAfterCommit(Long jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchWorker(jobId);
                }
            });
        } else {
            dispatchWorker(jobId);
        }
    }

    void dispatchWorker(Long jobId) {
        try {
            syncWorker.executeSync(jobId);
        } catch (TaskRejectedException e) {
            log.error("Sync worker task rejected for SyncJob ID {}: sync executor queue is full", jobId, e);
            try {
                persistenceService.markJobFailed(jobId, QUEUE_FULL_ERROR_MESSAGE, null, null);
            } catch (Exception ex) {
                log.error("Failed to mark SyncJob ID {} as FAILED after task rejection", jobId, ex);
            }
        } catch (RejectedExecutionException e) {
            log.error("Sync worker task rejected for SyncJob ID {}: sync executor queue is full", jobId, e);
            try {
                persistenceService.markJobFailed(jobId, QUEUE_FULL_ERROR_MESSAGE, null, null);
            } catch (Exception ex) {
                log.error("Failed to mark SyncJob ID {} as FAILED after task rejection", jobId, ex);
            }
        }
    }

    @Transactional(readOnly = true)
    public SyncJobResponse getJobByIdForUser(Long jobId, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        SyncJob job = syncJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("SyncJob", jobId));

        // Enforce repository ownership
        Repository repository = job.getRepository();
        if (repository == null || repository.getUser() == null || !repository.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied: You do not own this repository");
        }

        return SyncJobResponse.fromEntity(job, null);
    }

    @Transactional(readOnly = true)
    public List<SyncJobResponse> getJobsForRepository(Long repositoryId, String userEmail) {
        // Enforce ownership check via RepositoryService
        repositoryService.getRepositoryEntityForUser(repositoryId, userEmail);

        return syncJobRepository.findByRepositoryIdOrderByCreatedAtDesc(repositoryId).stream()
                .map(job -> SyncJobResponse.fromEntity(job, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SyncJobResponse> getActiveJobForRepository(Long repositoryId, String userEmail) {
        // Enforce ownership check via RepositoryService
        repositoryService.getRepositoryEntityForUser(repositoryId, userEmail);

        return syncJobRepository.findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(
                repositoryId, List.of(SyncJobStatus.QUEUED, SyncJobStatus.IN_PROGRESS)
        ).map(job -> SyncJobResponse.fromEntity(job, null));
    }
}
