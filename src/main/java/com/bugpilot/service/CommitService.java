package com.bugpilot.service;

import com.bugpilot.dto.CommitResponse;
import com.bugpilot.entity.Commit;
import com.bugpilot.repository.CommitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CommitService {

    private final CommitRepository commitRepository;

    public CommitService(CommitRepository commitRepository) {
        this.commitRepository = commitRepository;
    }

    public List<CommitResponse> getCommitsByRepository(Long repositoryId) {
        return commitRepository.findByRepositoryIdOrderByCommittedAtDesc(repositoryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CommitResponse mapToResponse(Commit commit) {
        CommitResponse response = new CommitResponse();
        response.setId(commit.getId());
        response.setSha(commit.getSha());
        response.setMessage(commit.getMessage());
        response.setAuthorName(commit.getAuthorName());
        response.setAuthorEmail(commit.getAuthorEmail());
        response.setCommittedAt(commit.getCommittedAt());
        response.setHtmlUrl(commit.getHtmlUrl());
        if (commit.getRepository() != null) {
            response.setRepositoryId(commit.getRepository().getId());
        }
        return response;
    }
}
