package com.bugpilot.service;

import com.bugpilot.dto.PullRequestResponse;
import com.bugpilot.entity.PullRequest;
import com.bugpilot.entity.Repository;
import com.bugpilot.entity.User;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.PullRequestRepository;
import com.bugpilot.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PullRequestService {

    private final PullRequestRepository pullRequestRepository;
    private final UserRepository userRepository;

    public PullRequestService(PullRequestRepository pullRequestRepository, UserRepository userRepository) {
        this.pullRequestRepository = pullRequestRepository;
        this.userRepository = userRepository;
    }

    public PullRequestService(PullRequestRepository pullRequestRepository) {
        this(pullRequestRepository, null);
    }

    private User getAuthenticatedUser(String userEmail) {
        if (userEmail == null) {
            throw new AccessDeniedException("Access denied: unauthenticated");
        }
        if (userRepository == null) {
            throw new AccessDeniedException("Access denied: user repository unavailable");
        }
        return userRepository.findFirstByEmailOrderByIdDesc(userEmail)
                .or(() -> userRepository.findByEmail(userEmail))
                .orElseThrow(() -> new AccessDeniedException("Access denied: user not found"));
    }

    private User verifyPullRequestOwnership(PullRequest pr, String userEmail) {
        User user = getAuthenticatedUser(userEmail);
        Repository repository = pr.getRepository();
        boolean isOwner = repository != null && repository.getUser() != null
                && repository.getUser().getId().equals(user.getId());
        if (!isOwner) {
            throw new AccessDeniedException("Access denied: You do not own this repository");
        }
        return user;
    }

    public List<PullRequestResponse> getPullRequestsByRepository(Long repositoryId) {
        return pullRequestRepository.findByRepositoryId(repositoryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public PullRequestResponse getPullRequestById(Long id, String userEmail) {
        PullRequest pr = pullRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PullRequest", id));
        verifyPullRequestOwnership(pr, userEmail);
        return mapToResponse(pr);
    }

    public PullRequestResponse getPullRequestById(Long id) {
        PullRequest pr = pullRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PullRequest", id));
        return mapToResponse(pr);
    }

    public PullRequestResponse mapToResponse(PullRequest pr) {
        PullRequestResponse response = new PullRequestResponse();
        response.setId(pr.getId());
        response.setGithubId(pr.getGithubId());
        response.setNumber(pr.getNumber());
        response.setTitle(pr.getTitle());
        response.setBody(pr.getBody());
        response.setState(pr.getState());
        response.setDraft(pr.getDraft());
        response.setAuthor(pr.getAuthor());
        response.setHtmlUrl(pr.getHtmlUrl());
        response.setSourceBranch(pr.getSourceBranch());
        response.setTargetBranch(pr.getTargetBranch());
        response.setAdditions(pr.getAdditions());
        response.setDeletions(pr.getDeletions());
        response.setChangedFiles(pr.getChangedFiles());
        response.setGithubCreatedAt(pr.getGithubCreatedAt());
        response.setGithubUpdatedAt(pr.getGithubUpdatedAt());
        response.setGithubClosedAt(pr.getGithubClosedAt());
        response.setGithubMergedAt(pr.getGithubMergedAt());
        if (pr.getRepository() != null) {
            response.setRepositoryId(pr.getRepository().getId());
        }
        response.setHasAiAnalysis(pr.getPrAnalysis() != null);
        return response;
    }
}
