package com.bugpilot.repository;

import com.bugpilot.entity.PullRequest;
import com.bugpilot.enums.PullRequestState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    List<PullRequest> findByRepositoryId(Long repositoryId);

    Optional<PullRequest> findByRepositoryIdAndNumber(Long repositoryId, Integer number);

    List<PullRequest> findByRepositoryIdAndNumberIn(Long repositoryId, java.util.Collection<Integer> numbers);

    long countByRepositoryId(Long repositoryId);

    long countByRepositoryIdAndState(Long repositoryId, PullRequestState state);
}
