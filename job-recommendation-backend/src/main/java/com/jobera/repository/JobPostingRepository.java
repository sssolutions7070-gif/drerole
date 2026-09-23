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

    // A company's public profile page only shows its approved, active postings - same rule as
    // the general public listing, just scoped to one recruiter.
    List<JobPosting> findByRecruiterIdAndIsActiveTrueAndModerationStatusOrderByPostedDateDesc(
        Long recruiterId, String moderationStatus);

    // Candidate/public-facing job lists only ever show approved postings - a pending or
    // rejected job is only visible to the recruiter who owns it (via findByRecruiterId*).
    List<JobPosting> findByIsActiveTrueAndModerationStatusOrderByPostedDateDesc(String moderationStatus);

    @Query("SELECT jp FROM JobPosting jp WHERE jp.isActive = true AND jp.moderationStatus = 'APPROVED' AND " +
           "(LOWER(jp.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(jp.company) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(jp.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<JobPosting> searchActiveJobs(@Param("keyword") String keyword);

    List<JobPosting> findByLocationContainingAndIsActiveTrue(String location);

    List<JobPosting> findByModerationStatusOrderByPostedDateDesc(String moderationStatus);
}