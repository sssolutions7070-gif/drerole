package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.SavedCandidateSearch;

@Repository
public interface SavedCandidateSearchRepository extends JpaRepository<SavedCandidateSearch, Long> {
    List<SavedCandidateSearch> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);
    List<SavedCandidateSearch> findByAlertEnabledTrue();
    Optional<SavedCandidateSearch> findByIdAndRecruiterId(Long id, Long recruiterId);
    void deleteByIdAndRecruiterId(Long id, Long recruiterId);
}
