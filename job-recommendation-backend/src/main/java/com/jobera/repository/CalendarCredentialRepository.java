package com.jobera.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.CalendarCredential;

@Repository
public interface CalendarCredentialRepository extends JpaRepository<CalendarCredential, Long> {
    Optional<CalendarCredential> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
