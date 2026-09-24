package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.CandidateNote;

@Repository
public interface CandidateNoteRepository extends JpaRepository<CandidateNote, Long> {
    Optional<CandidateNote> findByRecruiterIdAndCandidateUserId(Long recruiterId, Long candidateUserId);
    List<CandidateNote> findByRecruiterId(Long recruiterId);
}
