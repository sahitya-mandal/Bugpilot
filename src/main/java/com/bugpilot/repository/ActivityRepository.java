package com.bugpilot.repository;

import com.bugpilot.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);
}
