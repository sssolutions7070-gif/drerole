package com.jobera.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jobera.entity.Application;
import com.jobera.entity.InterviewRound;
import com.jobera.entity.InterviewRoundFeedback;
import com.jobera.entity.JobPosting;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.InterviewRoundFeedbackRepository;
import com.jobera.repository.InterviewRoundRepository;

/**
 * The candidate's own rating of one specific interview round - "how was this interview," not
 * "how is this company overall" (that's InterviewExperienceService, a separate public review).
 * Feeds the same accountability section of a company's profile as the response-time badge
 * (see CompanyService.getCompanyProfile), so a recruiter who decides fast but runs a chaotic,
 * unprofessional interview doesn't get a clean bill of health from speed alone.
 */
@Service
public class InterviewRoundFeedbackService {

    private final InterviewRoundFeedbackRepository feedbackRepository;
    private final InterviewRoundRepository interviewRoundRepository;
    private final ApplicationRepository applicationRepository;

    public InterviewRoundFeedbackService(InterviewRoundFeedbackRepository feedbackRepository,
                                          InterviewRoundRepository interviewRoundRepository,
                                          ApplicationRepository applicationRepository) {
        this.feedbackRepository = feedbackRepository;
        this.interviewRoundRepository = interviewRoundRepository;
        this.applicationRepository = applicationRepository;
    }

    public static class RoundNotFoundException extends RuntimeException {
        public RoundNotFoundException(Long roundId) {
            super("Interview round not found: " + roundId);
        }
    }

    public static class NotTheCandidateException extends RuntimeException {
        public NotTheCandidateException() {
            super("Only the candidate who attended this round can rate it");
        }
    }

    public static class RoundNotCompletedException extends RuntimeException {
        public RoundNotCompletedException() {
            super("This round hasn't happened yet - feedback opens once it's marked completed");
        }
    }

    public static class InvalidRatingException extends RuntimeException {
        public InvalidRatingException() {
            super("Rating must be between 1 and 5");
        }
    }

    private InterviewRound requireRound(Long roundId) {
        return interviewRoundRepository.findById(roundId)
            .orElseThrow(() -> new RoundNotFoundException(roundId));
    }

    private Application applicationFor(InterviewRound round) {
        return applicationRepository.findById(round.getApplicationId())
            .orElseThrow(() -> new RoundNotFoundException(round.getId()));
    }

    public InterviewRoundFeedback submitFeedback(Long roundId, Long candidateUserId, Integer rating, String comment) {
        InterviewRound round = requireRound(roundId);
        Application application = applicationFor(round);

        if (application.getUser() == null || !application.getUser().getId().equals(candidateUserId)) {
            throw new NotTheCandidateException();
        }
        if (!"COMPLETED".equals(round.getStatus())) {
            throw new RoundNotCompletedException();
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new InvalidRatingException();
        }

        InterviewRoundFeedback feedback = feedbackRepository
            .findByInterviewRoundIdAndCandidateUserId(roundId, candidateUserId)
            .orElseGet(InterviewRoundFeedback::new);
        feedback.setInterviewRoundId(roundId);
        feedback.setCandidateUserId(candidateUserId);
        feedback.setRating(rating);
        feedback.setComment(comment);
        return feedbackRepository.save(feedback);
    }

    /** There's exactly one candidate per round (via its application), so unlike submitFeedback -
     *  where the caller's identity has to be checked against it - a read just needs the round;
     *  either participant (candidate or the recruiter who owns it) can see it, enforced by
     *  AccessPolicy's participatesInRound at the controller boundary. */
    public Optional<InterviewRoundFeedback> getFeedbackForRound(Long roundId) {
        InterviewRound round = requireRound(roundId);
        Application application = applicationFor(round);
        if (application.getUser() == null) {
            return Optional.empty();
        }
        return feedbackRepository.findByInterviewRoundIdAndCandidateUserId(roundId, application.getUser().getId());
    }

    private static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** Average candidate-rated interview-conduct score across every round belonging to any
     *  application against any of this recruiter's jobs - the same shape of "feeds the public
     *  profile" computation as ApplicationStatusService.getResponsivenessSummary. */
    public Map<String, Object> getAverageRatingForRecruiter(List<JobPosting> jobs) {
        List<Long> roundIds = new ArrayList<>();
        for (JobPosting job : jobs) {
            for (Application application : applicationRepository.findByJobPostingId(job.getId())) {
                for (InterviewRound round : interviewRoundRepository.findByApplicationIdOrderByRoundNumberAsc(application.getId())) {
                    roundIds.add(round.getId());
                }
            }
        }

        List<InterviewRoundFeedback> feedback = roundIds.isEmpty()
            ? List.of()
            : feedbackRepository.findByInterviewRoundIdIn(roundIds);

        double average = feedback.stream().mapToInt(InterviewRoundFeedback::getRating).average().orElse(0.0);

        Map<String, Object> result = new HashMap<>();
        result.put("averageInterviewRating", feedback.isEmpty() ? null : round1(average));
        result.put("interviewFeedbackCount", (long) feedback.size());
        return result;
    }
}
