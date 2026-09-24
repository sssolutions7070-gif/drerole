package com.jobera.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.DirectMessage;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {
    List<DirectMessage> findByRecruiterIdAndCandidateUserIdOrderBySentAtAsc(Long recruiterId, Long candidateUserId);

    List<DirectMessage> findByCandidateUserIdOrderBySentAtDesc(Long candidateUserId);

    List<DirectMessage> findByRecruiterIdOrderBySentAtDesc(Long recruiterId);
}
