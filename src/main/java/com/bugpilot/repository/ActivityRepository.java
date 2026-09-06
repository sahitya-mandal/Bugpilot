package com.bugpilot.repository;

import com.bugpilot.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    @Modifying
    @Query("DELETE FROM Activity a WHERE a.repository.id = :repositoryId")
    void deleteByRepositoryId(@Param("repositoryId") Long repositoryId);
}
