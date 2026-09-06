package com.bugpilot.repository;

import com.bugpilot.entity.Issue;
import com.bugpilot.enums.IssueState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    List<Issue> findByRepositoryId(Long repositoryId);

    Optional<Issue> findByRepositoryIdAndNumber(Long repositoryId, Integer number);

    List<Issue> findByRepositoryIdAndNumberIn(Long repositoryId, java.util.Collection<Integer> numbers);

    long countByRepositoryId(Long repositoryId);

    long countByRepositoryIdAndState(Long repositoryId, IssueState state);
}
