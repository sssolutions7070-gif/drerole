package com.jobera.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jobera.entity.OutreachSequence;

public interface OutreachSequenceRepository extends JpaRepository<OutreachSequence, Long> {
    List<OutreachSequence> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);

    List<OutreachSequence> findByStatus(String status);

    boolean existsByRecruiterIdAndCandidateUserIdAndStatus(Long recruiterId, Long candidateUserId, String status);
}
