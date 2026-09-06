package com.bugpilot.repository;

import com.bugpilot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Temporary compatibility query to safely handle legacy duplicate records
    Optional<User> findFirstByEmailOrderByIdDesc(String email);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}