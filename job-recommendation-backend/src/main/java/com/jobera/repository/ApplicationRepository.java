// File: src/main/java/com/jobera/repository/ApplicationRepository.java
package com.jobera.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jobera.entity.Application;
import com.jobera.entity.User;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByUserId(Long userId);
    List<Application> findByJobPostingId(Long jobPostingId);

    // The talent pool: every distinct candidate who has ever applied to one of this recruiter's
    // postings, regardless of which job or how that application turned out - lets a recruiter
    // reuse a strong "silver medalist" candidate for a different opening later.
    @Query("SELECT DISTINCT a.user FROM Application a WHERE a.jobPosting.recruiter.id = :recruiterId AND a.user IS NOT NULL")
    List<User> findDistinctCandidatesByRecruiterId(@Param("recruiterId") Long recruiterId);

    @Query("SELECT a FROM Application a WHERE a.jobPosting.recruiter.id = :recruiterId AND a.user.id = :candidateUserId "
        + "ORDER BY a.applicationDate DESC")
    List<Application> findByRecruiterIdAndCandidateUserId(@Param("recruiterId") Long recruiterId,
                                                           @Param("candidateUserId") Long candidateUserId);
}