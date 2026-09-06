package com.bugpilot.repository;

import com.bugpilot.entity.Commit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommitRepository extends JpaRepository<Commit, Long> {

    List<Commit> findByRepositoryIdOrderByCommittedAtDesc(Long repositoryId);

    List<Commit> findTop5ByRepositoryIdOrderByCommittedAtDesc(Long repositoryId);

    Optional<Commit> findByRepositoryIdAndSha(Long repositoryId, String sha);

    List<Commit> findByRepositoryIdAndShaIn(Long repositoryId, java.util.Collection<String> shas);

    long countByRepositoryId(Long repositoryId);

    @Query("SELECT COUNT(DISTINCT c.authorEmail) FROM Commit c WHERE c.repository.id = :repositoryId")
    long countDistinctAuthorsByRepositoryId(@Param("repositoryId") Long repositoryId);
}
