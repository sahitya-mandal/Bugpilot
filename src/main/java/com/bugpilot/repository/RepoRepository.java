package com.bugpilot.repository;

import com.bugpilot.entity.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RepoRepository extends JpaRepository<Repository, Long> {

    Optional<Repository> findByOwnerAndName(String owner, String name);

    List<Repository> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Repository r WHERE r.id = :id")
    Optional<Repository> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Repository r WHERE r.owner = :owner AND r.name = :name")
    Optional<Repository> findByOwnerAndNameForUpdate(@Param("owner") String owner, @Param("name") String name);
}
