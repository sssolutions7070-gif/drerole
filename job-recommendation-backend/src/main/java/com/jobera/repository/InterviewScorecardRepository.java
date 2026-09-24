package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.InterviewScorecard;

@Repository
public interface InterviewScorecardRepository extends JpaRepository<InterviewScorecard, Long> {
    Optional<InterviewScorecard> findByInterviewRoundId(Long interviewRoundId);
    List<InterviewScorecard> findByInterviewRoundIdIn(List<Long> interviewRoundIds);
}
