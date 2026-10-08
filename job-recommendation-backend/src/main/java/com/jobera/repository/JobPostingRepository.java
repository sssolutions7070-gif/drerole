// File: src/main/java/com/jobera/repository/JobPostingRepository.java
package com.jobera.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jobera.entity.JobPosting;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    List<JobPosting> findByIsActiveTrue();
    List<JobPosting> findByIsActiveTrueOrderByPostedDateDesc();
    List<JobPosting> findByRecruiterIdAndIsActiveTrue(Long recruiterId);
    long countByRecruiterIdAndIsActiveTrue(Long recruiterId);

    // A company's public profile page only shows its approved, active, not-yet-scheduled-for-later
    // postings - same rule as the general public listing, just scoped to one recruiter.
    @Query("SELECT jp FROM JobPosting jp WHERE jp.recruiter.id = :recruiterId AND jp.isActive = true " +
           "AND jp.moderationStatus = :moderationStatus " +
           "AND (jp.scheduledFor IS NULL OR jp.scheduledFor <= CURRENT_TIMESTAMP) " +
           "ORDER BY jp.postedDate DESC")
    List<JobPosting> findByRecruiterIdAndIsActiveTrueAndModerationStatusOrderByPostedDateDesc(
        @Param("recruiterId") Long recruiterId, @Param("moderationStatus") String moderationStatus);

    // Candidate/public-facing job lists only ever show approved postings - a pending or
    // rejected job is only visible to the recruiter who owns it (via findByRecruiterId*).
    List<JobPosting> findByIsActiveTrueAndModerationStatusOrderByPostedDateDesc(String moderationStatus);

    // Same listing, but featured (a Pro-tier perk) sorts first, and a posting scheduled for a
    // future date/time stays hidden until then even though it's already approved and active -
    // see JobPostingService.createJobPosting. Used for the public/candidate-facing listing.
    @Query("SELECT jp FROM JobPosting jp WHERE jp.isActive = true AND jp.moderationStatus = :moderationStatus " +
           "AND (jp.scheduledFor IS NULL OR jp.scheduledFor <= CURRENT_TIMESTAMP) " +
           "ORDER BY jp.featured DESC, jp.postedDate DESC")
    List<JobPosting> findByIsActiveTrueAndModerationStatusOrderByFeaturedDescPostedDateDesc(
        @Param("moderationStatus") String moderationStatus);

    @Query("SELECT jp FROM JobPosting jp WHERE jp.isActive = true AND jp.moderationStatus = 'APPROVED' AND " +
           "(jp.scheduledFor IS NULL OR jp.scheduledFor <= CURRENT_TIMESTAMP) AND " +
           "(LOWER(jp.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(jp.company) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(jp.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<JobPosting> searchActiveJobs(@Param("keyword") String keyword);

    List<JobPosting> findByLocationContainingAndIsActiveTrue(String location);

    List<JobPosting> findByModerationStatusOrderByPostedDateDesc(String moderationStatus);
}