package com.jobera.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.jobera.entity.InterviewScorecard;
import com.jobera.repository.InterviewRoundRepository;
import com.jobera.repository.InterviewScorecardRepository;

/** A recruiter's structured feedback on one interview round - a fixed 1-5 rubric plus a hiring
 *  recommendation, so a candidate's rounds can be compared and rolled up into a hiring decision
 *  instead of relying on free-form notes alone. */
@Service
public class InterviewScorecardService {

    public static final Set<String> RECOMMENDATIONS = Set.of(
        "STRONG_YES", "YES", "NEUTRAL", "NO", "STRONG_NO");

    public static class RoundNotFoundException extends RuntimeException {
        public RoundNotFoundException(Long roundId) {
            super("Interview round not found: " + roundId);
        }
    }

    public static class ScorecardNotFoundException extends RuntimeException {
        public ScorecardNotFoundException(Long roundId) {
            super("No scorecard has been submitted for interview round " + roundId);
        }
    }

    public static class InvalidRatingException extends RuntimeException {
        public InvalidRatingException(String field) {
            super(field + " must be between 1 and 5");
        }
    }

    public static class InvalidRecommendationException extends RuntimeException {
        public InvalidRecommendationException(String recommendation) {
            super("Unknown recommendation: " + recommendation);
        }
    }

    private final InterviewScorecardRepository scorecardRepository;
    private final InterviewRoundRepository interviewRoundRepository;

    public InterviewScorecardService(InterviewScorecardRepository scorecardRepository,
                                      InterviewRoundRepository interviewRoundRepository) {
        this.scorecardRepository = scorecardRepository;
        this.interviewRoundRepository = interviewRoundRepository;
    }

    private void validateRating(String field, Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new InvalidRatingException(field);
        }
    }

    public InterviewScorecard submitScorecard(Long roundId, Long submittedByUserId, Integer communicationRating,
                                               Integer technicalRating, Integer cultureFitRating,
                                               String recommendation, String notes) {
        if (interviewRoundRepository.findById(roundId).isEmpty()) {
            throw new RoundNotFoundException(roundId);
        }
        validateRating("communicationRating", communicationRating);
        validateRating("technicalRating", technicalRating);
        validateRating("cultureFitRating", cultureFitRating);
        if (!RECOMMENDATIONS.contains(recommendation)) {
            throw new InvalidRecommendationException(recommendation);
        }

        InterviewScorecard scorecard = scorecardRepository.findByInterviewRoundId(roundId)
            .orElseGet(InterviewScorecard::new);
        scorecard.setInterviewRoundId(roundId);
        scorecard.setSubmittedByUserId(submittedByUserId);
        scorecard.setCommunicationRating(communicationRating);
        scorecard.setTechnicalRating(technicalRating);
        scorecard.setCultureFitRating(cultureFitRating);
        scorecard.setRecommendation(recommendation);
        scorecard.setNotes(notes);
        scorecard.setUpdatedAt(LocalDateTime.now());
        return scorecardRepository.save(scorecard);
    }

    public InterviewScorecard getScorecard(Long roundId) {
        return scorecardRepository.findByInterviewRoundId(roundId)
            .orElseThrow(() -> new ScorecardNotFoundException(roundId));
    }

    public Optional<InterviewScorecard> findScorecard(Long roundId) {
        return scorecardRepository.findByInterviewRoundId(roundId);
    }

    public List<InterviewScorecard> getScorecardsForRounds(List<Long> roundIds) {
        return scorecardRepository.findByInterviewRoundIdIn(roundIds);
    }
}
