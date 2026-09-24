package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.ApiKey;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyHashAndRevokedFalse(String keyHash);
    List<ApiKey> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);
    Optional<ApiKey> findByIdAndRecruiterId(Long id, Long recruiterId);
}
