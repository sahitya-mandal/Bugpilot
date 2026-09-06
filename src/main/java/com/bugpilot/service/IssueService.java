package com.bugpilot.service;

import com.bugpilot.dto.IssueResponse;
import com.bugpilot.entity.Issue;
import com.bugpilot.entity.Repository;
import com.bugpilot.entity.User;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.IssueRepository;
import com.bugpilot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class IssueService {

    private final IssueRepository issueRepository;
    private final UserRepository userRepository;

    @Autowired
    public IssueService(IssueRepository issueRepository, UserRepository userRepository) {
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
    }

    public IssueService(IssueRepository issueRepository) {
        this(issueRepository, null);
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

    private User verifyIssueOwnership(Issue issue, String userEmail) {
        User user = getAuthenticatedUser(userEmail);
        Repository repository = issue.getRepository();
        boolean isOwner = repository != null && repository.getUser() != null
                && repository.getUser().getId().equals(user.getId());
        if (!isOwner) {
            throw new AccessDeniedException("Access denied: You do not own this repository");
        }
        return user;
    }

    public List<IssueResponse> getIssuesByRepository(Long repositoryId) {
        return issueRepository.findByRepositoryId(repositoryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public IssueResponse getIssueById(Long id, String userEmail) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Issue", id));
        verifyIssueOwnership(issue, userEmail);
        return mapToResponse(issue);
    }

    public IssueResponse getIssueById(Long id) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Issue", id));
        return mapToResponse(issue);
    }

    public IssueResponse mapToResponse(Issue issue) {
        IssueResponse response = new IssueResponse();
        response.setId(issue.getId());
        response.setGithubId(issue.getGithubId());
        response.setNumber(issue.getNumber());
        response.setTitle(issue.getTitle());
        response.setBody(issue.getBody());
        response.setState(issue.getState());
        response.setAuthor(issue.getAuthor());
        response.setHtmlUrl(issue.getHtmlUrl());
        response.setGithubCreatedAt(issue.getGithubCreatedAt());
        response.setGithubUpdatedAt(issue.getGithubUpdatedAt());
        response.setGithubClosedAt(issue.getGithubClosedAt());
        if (issue.getRepository() != null) {
            response.setRepositoryId(issue.getRepository().getId());
        }
        boolean hasAi = issue.getBugAnalysis() != null;
        response.setHasAiAnalysis(hasAi);
        response.setAiSeverity(hasAi ? issue.getBugAnalysis().getSeverity() : null);
        return response;
    }
}
