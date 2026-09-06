package com.bugpilot.repository;

import com.bugpilot.entity.SyncJob;
import com.bugpilot.enums.SyncJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

    @Query("SELECT j FROM SyncJob j JOIN FETCH j.repository JOIN FETCH j.triggeredBy WHERE j.id = :id")
    Optional<SyncJob> findByIdWithAssociations(@Param("id") Long id);

    Optional<SyncJob> findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(Long repositoryId, Collection<SyncJobStatus> statuses);

    Optional<SyncJob> findFirstByRepositoryIdAndStatusOrderByCreatedAtDesc(Long repositoryId, SyncJobStatus status);

    boolean existsByRepositoryIdAndStatus(Long repositoryId, SyncJobStatus status);

    List<SyncJob> findByRepositoryIdAndStatus(Long repositoryId, SyncJobStatus status);

    List<SyncJob> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    List<SyncJob> findByStatusIn(Collection<SyncJobStatus> statuses);

    @Modifying
    @Query("DELETE FROM SyncJob s WHERE s.repository.id = :repositoryId")
    void deleteByRepositoryId(@Param("repositoryId") Long repositoryId);
}
