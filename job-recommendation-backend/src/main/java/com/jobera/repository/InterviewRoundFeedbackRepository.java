package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.InterviewRoundFeedback;

@Repository
public interface InterviewRoundFeedbackRepository extends JpaRepository<InterviewRoundFeedback, Long> {
    Optional<InterviewRoundFeedback> findByInterviewRoundIdAndCandidateUserId(Long interviewRoundId, Long candidateUserId);
    List<InterviewRoundFeedback> findByInterviewRoundIdIn(List<Long> interviewRoundIds);
}
