package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.CompanyReview;

@Repository
public interface CompanyReviewRepository extends JpaRepository<CompanyReview, Long> {
    Optional<CompanyReview> findByUserIdAndRecruiterId(Long userId, Long recruiterId);

    List<CompanyReview> findByRecruiterIdAndModerationStatusOrderByCreatedAtDesc(Long recruiterId, String moderationStatus);

    List<CompanyReview> findByModerationStatusOrderByCreatedAtDesc(String moderationStatus);

    long countByRecruiterIdAndModerationStatus(Long recruiterId, String moderationStatus);
}
