package com.bugpilot.repository;

import com.bugpilot.entity.SyncJob;
import com.bugpilot.enums.SyncJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

    Optional<SyncJob> findFirstByRepositoryIdAndStatusInOrderByCreatedAtDesc(Long repositoryId, Collection<SyncJobStatus> statuses);

    List<SyncJob> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    List<SyncJob> findByStatusIn(Collection<SyncJobStatus> statuses);
}
