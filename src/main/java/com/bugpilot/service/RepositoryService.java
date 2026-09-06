package com.bugpilot.service;

import com.bugpilot.dto.RepositoryRequest;
import com.bugpilot.dto.RepositoryResponse;
import com.bugpilot.entity.Repository;
import com.bugpilot.entity.SyncJob;
import com.bugpilot.entity.User;
import com.bugpilot.enums.Role;
import com.bugpilot.enums.SyncJobStatus;
import com.bugpilot.exception.ConcurrentSyncException;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.ActivityRepository;
import com.bugpilot.repository.RepoRepository;
import com.bugpilot.repository.SyncJobRepository;
import com.bugpilot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class RepositoryService {

    private final RepoRepository repoRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final SyncJobRepository syncJobRepository;

    @Autowired
    public RepositoryService(
            RepoRepository repoRepository,
            UserRepository userRepository,
            ActivityRepository activityRepository,
            SyncJobRepository syncJobRepository) {
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.syncJobRepository = syncJobRepository;
    }

    public RepositoryResponse createRepository(RepositoryRequest request, String userEmail) {
        User user = null;
        if (userEmail != null) {
            user = userRepository.findFirstByEmailOrderByIdDesc(userEmail)
                    .or(() -> userRepository.findByEmail(userEmail))
                    .orElse(null);
        }

        String owner = request.getOwner() != null ? request.getOwner().trim() : "";
        String name = request.getName() != null ? request.getName().trim() : "";
        if (owner.contains("/")) {
            throw new IllegalArgumentException("Repository owner must not contain '/'");
        }
        if (name.contains("/")) {
            throw new IllegalArgumentException("Repository name must not contain '/'");
        }

        String fullName = owner + "/" + name;

        Optional<Repository> existing = repoRepository.findByOwnerAndName(owner, name);
        if (existing.isPresent()) {
            Repository existingRepo = existing.get();
            if (existingRepo.getUser() != null && (user == null || !existingRepo.getUser().getId().equals(user.getId()))) {
                throw new AccessDeniedException("Access denied: Repository is already registered by another user");
            }
        }

        Repository repository = existing.orElse(new Repository());

        repository.setName(request.getName());
        repository.setOwner(request.getOwner());
        repository.setFullName(fullName);
        if (request.getDescription() != null) {
            repository.setDescription(request.getDescription());
        }
        if (request.getGithubUrl() != null && !request.getGithubUrl().isBlank()) {
            repository.setHtmlUrl(request.getGithubUrl().trim());
        } else if (repository.getHtmlUrl() == null || repository.getHtmlUrl().isBlank()) {
            repository.setHtmlUrl("https://github.com/" + fullName);
        }
        if (user != null) {
            repository.setUser(user);
        }
        repository.setUpdatedAt(LocalDateTime.now());

        Repository saved = repoRepository.save(repository);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Repository getRepositoryEntityForUser(Long repositoryId, String userEmail) {
        Repository repository = repoRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", repositoryId));

        if (userEmail == null) {
            throw new AccessDeniedException("Access denied: unauthenticated");
        }

        User user = userRepository.findFirstByEmailOrderByIdDesc(userEmail)
                .or(() -> userRepository.findByEmail(userEmail))
                .orElseThrow(() -> new AccessDeniedException("Access denied: user not found"));

        boolean isOwner = repository.getUser() != null && repository.getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Access denied: You do not own this repository");
        }

        return repository;
    }

    @Transactional(readOnly = true)
    public List<RepositoryResponse> getRepositoriesForUser(String userEmail) {
        if (userEmail == null) {
            return List.of();
        }
        User user = userRepository.findFirstByEmailOrderByIdDesc(userEmail)
                .or(() -> userRepository.findByEmail(userEmail))
                .orElse(null);
        if (user == null) {
            return List.of();
        }
        return repoRepository.findByUserId(user.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RepositoryResponse> getAllRepositories() {
        return repoRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RepositoryResponse getRepositoryById(Long id, String userEmail) {
        Repository repository = getRepositoryEntityForUser(id, userEmail);
        return mapToResponse(repository);
    }

    @Transactional(readOnly = true)
    public RepositoryResponse getRepositoryById(Long id) {
        Repository repository = repoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", id));
        return mapToResponse(repository);
    }

    @Transactional
    public void deleteRepository(Long id, String userEmail) {
        Repository repository = getRepositoryEntityForUser(id, userEmail);

        // 1. Concurrency guard: reject if sync is actively IN_PROGRESS
        Optional<SyncJob> inProgressJob = syncJobRepository.findFirstByRepositoryIdAndStatusOrderByCreatedAtDesc(id, SyncJobStatus.IN_PROGRESS);
        if (inProgressJob.isPresent()) {
            throw new ConcurrentSyncException(
                    "Cannot delete repository while synchronization is in progress. Please wait for sync to complete.",
                    inProgressJob.get().getId());
        }

        // 2. Safely handle any QUEUED / stale sync jobs before removal
        List<SyncJob> queuedJobs = syncJobRepository.findByRepositoryIdAndStatus(id, SyncJobStatus.QUEUED);
        for (SyncJob queued : queuedJobs) {
            queued.setStatus(SyncJobStatus.CANCELLED);
            queued.setErrorMessage("Repository deleted before sync started.");
        }
        if (!queuedJobs.isEmpty()) {
            syncJobRepository.saveAll(queuedJobs);
        }

        // 3. Delete Activity records referencing this repository
        activityRepository.deleteByRepositoryId(id);

        // 4. Delete SyncJob records referencing this repository
        syncJobRepository.deleteByRepositoryId(id);

        // 5. Delete the Repository entity (cascades to issues -> bug_analyses, pull_requests -> pull_request_analyses, commits)
        repoRepository.delete(repository);
    }

    @Transactional
    public void deleteRepository(Long id) {
        Repository repository = repoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", id));

        // 1. Concurrency guard: reject if sync is actively IN_PROGRESS
        Optional<SyncJob> inProgressJob = syncJobRepository.findFirstByRepositoryIdAndStatusOrderByCreatedAtDesc(id, SyncJobStatus.IN_PROGRESS);
        if (inProgressJob.isPresent()) {
            throw new ConcurrentSyncException(
                    "Cannot delete repository while synchronization is in progress. Please wait for sync to complete.",
                    inProgressJob.get().getId());
        }

        // 2. Safely handle any QUEUED / stale sync jobs before removal
        List<SyncJob> queuedJobs = syncJobRepository.findByRepositoryIdAndStatus(id, SyncJobStatus.QUEUED);
        for (SyncJob queued : queuedJobs) {
            queued.setStatus(SyncJobStatus.CANCELLED);
            queued.setErrorMessage("Repository deleted before sync started.");
        }
        if (!queuedJobs.isEmpty()) {
            syncJobRepository.saveAll(queuedJobs);
        }

        // 3. Delete Activity records referencing this repository
        activityRepository.deleteByRepositoryId(id);

        // 4. Delete SyncJob records referencing this repository
        syncJobRepository.deleteByRepositoryId(id);

        // 5. Delete the Repository entity
        repoRepository.delete(repository);
    }

    public RepositoryResponse mapToResponse(Repository repo) {
        RepositoryResponse response = new RepositoryResponse();
        response.setId(repo.getId());
        response.setName(repo.getName());
        response.setOwner(repo.getOwner());
        response.setFullName(repo.getFullName());
        response.setDescription(repo.getDescription());
        response.setHtmlUrl(repo.getHtmlUrl());
        response.setDefaultBranch(repo.getDefaultBranch());
        response.setGithubId(repo.getGithubId());
        response.setOpenIssuesCount(repo.getOpenIssuesCount());
        response.setForksCount(repo.getForksCount());
        response.setStargazersCount(repo.getStargazersCount());
        response.setCreatedAt(repo.getCreatedAt());
        response.setUpdatedAt(repo.getUpdatedAt());
        response.setSyncedAt(repo.getSyncedAt());
        if (repo.getUser() != null) {
            response.setUserId(repo.getUser().getId());
        }
        return response;
    }
}
