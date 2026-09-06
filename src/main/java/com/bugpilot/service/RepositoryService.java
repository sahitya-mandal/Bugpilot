package com.bugpilot.service;

import com.bugpilot.dto.RepositoryRequest;
import com.bugpilot.dto.RepositoryResponse;
import com.bugpilot.entity.Repository;
import com.bugpilot.entity.User;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.RepoRepository;
import com.bugpilot.repository.UserRepository;
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

    public RepositoryService(RepoRepository repoRepository, UserRepository userRepository) {
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
    }

    public RepositoryResponse createRepository(RepositoryRequest request, String userEmail) {
        User user = null;
        if (userEmail != null) {
            user = userRepository.findFirstByEmailOrderByIdDesc(userEmail)
                    .or(() -> userRepository.findByEmail(userEmail))
                    .orElse(null);
        }

        String fullName = request.getOwner() + "/" + request.getName();

        Optional<Repository> existing = repoRepository.findByOwnerAndName(request.getOwner(), request.getName());
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
        if (!isOwner) {
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

    public void deleteRepository(Long id, String userEmail) {
        Repository repository = getRepositoryEntityForUser(id, userEmail);
        repoRepository.delete(repository);
    }

    public void deleteRepository(Long id) {
        Repository repository = repoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", id));
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
