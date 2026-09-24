package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.InterviewExperience;

@Repository
public interface InterviewExperienceRepository extends JpaRepository<InterviewExperience, Long> {
    Optional<InterviewExperience> findByUserIdAndRecruiterId(Long userId, Long recruiterId);

    List<InterviewExperience> findByRecruiterIdAndModerationStatusOrderByCreatedAtDesc(Long recruiterId, String moderationStatus);

    List<InterviewExperience> findByModerationStatusOrderByCreatedAtDesc(String moderationStatus);
}
