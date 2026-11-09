package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.InterviewRound;
import com.jobera.entity.InterviewRoundFeedback;
import com.jobera.entity.JobPosting;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.InterviewRoundFeedbackRepository;
import com.jobera.repository.InterviewRoundRepository;
import com.jobera.service.InterviewRoundFeedbackService.InvalidRatingException;
import com.jobera.service.InterviewRoundFeedbackService.NotTheCandidateException;
import com.jobera.service.InterviewRoundFeedbackService.RoundNotCompletedException;
import com.jobera.service.InterviewRoundFeedbackService.RoundNotFoundException;

@ExtendWith(MockitoExtension.class)
class InterviewRoundFeedbackServiceTest {

    @Mock InterviewRoundFeedbackRepository feedbackRepository;
    @Mock InterviewRoundRepository interviewRoundRepository;
    @Mock ApplicationRepository applicationRepository;

    InterviewRoundFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new InterviewRoundFeedbackService(feedbackRepository, interviewRoundRepository, applicationRepository);
    }

    private InterviewRound completedRound(long roundId, long applicationId) {
        InterviewRound round = new InterviewRound(applicationId, 1, "Technical round", "TECHNICAL", "room-1", null);
        round.setId(roundId);
        round.setStatus("COMPLETED");
        return round;
    }

    private Application applicationWithCandidate(long applicationId, long candidateUserId) {
        Application application = new Application();
        application.setId(applicationId);
        User candidate = new User();
        candidate.setId(candidateUserId);
        application.setUser(candidate);
        return application;
    }

    @Test
    void theCandidateCanRateACompletedRound() {
        InterviewRound round = completedRound(10L, 5L);
        when(interviewRoundRepository.findById(10L)).thenReturn(Optional.of(round));
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(applicationWithCandidate(5L, 1L)));
        when(feedbackRepository.findByInterviewRoundIdAndCandidateUserId(10L, 1L)).thenReturn(Optional.empty());
        when(feedbackRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewRoundFeedback saved = service.submitFeedback(10L, 1L, 5, "Great interviewers");

        assertEquals(10L, saved.getInterviewRoundId());
        assertEquals(1L, saved.getCandidateUserId());
        assertEquals(5, saved.getRating());
    }

    @Test
    void submittingFeedbackTwiceUpdatesTheSameRowRatherThanCreatingASecondOne() {
        InterviewRound round = completedRound(10L, 5L);
        when(interviewRoundRepository.findById(10L)).thenReturn(Optional.of(round));
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(applicationWithCandidate(5L, 1L)));
        InterviewRoundFeedback existing = new InterviewRoundFeedback();
        existing.setId(99L);
        existing.setInterviewRoundId(10L);
        existing.setCandidateUserId(1L);
        existing.setRating(3);
        when(feedbackRepository.findByInterviewRoundIdAndCandidateUserId(10L, 1L)).thenReturn(Optional.of(existing));
        when(feedbackRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewRoundFeedback updated = service.submitFeedback(10L, 1L, 5, "Changed my mind, it was great");

        assertEquals(99L, updated.getId());
        assertEquals(5, updated.getRating());
    }

    @Test
    void onlyTheCandidateOnThisApplicationCanRateTheirRound() {
        InterviewRound round = completedRound(10L, 5L);
        when(interviewRoundRepository.findById(10L)).thenReturn(Optional.of(round));
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(applicationWithCandidate(5L, 1L)));

        assertThrows(NotTheCandidateException.class, () -> service.submitFeedback(10L, 2L, 5, "not my round"));
    }

    @Test
    void feedbackIsRejectedBeforeTheRoundIsMarkedCompleted() {
        InterviewRound round = completedRound(10L, 5L);
        round.setStatus("SCHEDULED");
        when(interviewRoundRepository.findById(10L)).thenReturn(Optional.of(round));
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(applicationWithCandidate(5L, 1L)));

        assertThrows(RoundNotCompletedException.class, () -> service.submitFeedback(10L, 1L, 5, null));
    }

    @Test
    void ratingMustBeBetweenOneAndFive() {
        InterviewRound round = completedRound(10L, 5L);
        when(interviewRoundRepository.findById(10L)).thenReturn(Optional.of(round));
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(applicationWithCandidate(5L, 1L)));

        assertThrows(InvalidRatingException.class, () -> service.submitFeedback(10L, 1L, 0, null));
        assertThrows(InvalidRatingException.class, () -> service.submitFeedback(10L, 1L, 6, null));
        assertThrows(InvalidRatingException.class, () -> service.submitFeedback(10L, 1L, null, null));
    }

    @Test
    void anUnknownRoundFailsCleanly() {
        when(interviewRoundRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RoundNotFoundException.class, () -> service.submitFeedback(404L, 1L, 5, null));
    }

    @Test
    void getFeedbackForRoundLooksUpByTheRoundsCandidateNotTheCaller() {
        InterviewRound round = completedRound(10L, 5L);
        when(interviewRoundRepository.findById(10L)).thenReturn(Optional.of(round));
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(applicationWithCandidate(5L, 1L)));
        InterviewRoundFeedback existing = new InterviewRoundFeedback();
        existing.setRating(4);
        when(feedbackRepository.findByInterviewRoundIdAndCandidateUserId(10L, 1L)).thenReturn(Optional.of(existing));

        // A recruiter viewing this (not the candidate) still gets the right feedback back, since
        // getFeedbackForRound resolves the candidate from the round's own application.
        Optional<InterviewRoundFeedback> found = service.getFeedbackForRound(10L);

        assertTrue(found.isPresent());
        assertEquals(4, found.get().getRating());
    }

    @Test
    void getAverageRatingForRecruiterAveragesAcrossEveryRoundOfEveryApplicationToEveryJob() {
        JobPosting job = new JobPosting();
        job.setId(1L);
        Application app1 = applicationWithCandidate(5L, 1L);
        Application app2 = applicationWithCandidate(6L, 2L);
        when(applicationRepository.findByJobPostingId(1L)).thenReturn(List.of(app1, app2));

        InterviewRound round1 = completedRound(10L, 5L);
        InterviewRound round2 = completedRound(11L, 6L);
        when(interviewRoundRepository.findByApplicationIdOrderByRoundNumberAsc(5L)).thenReturn(List.of(round1));
        when(interviewRoundRepository.findByApplicationIdOrderByRoundNumberAsc(6L)).thenReturn(List.of(round2));

        InterviewRoundFeedback feedback1 = new InterviewRoundFeedback();
        feedback1.setRating(4);
        InterviewRoundFeedback feedback2 = new InterviewRoundFeedback();
        feedback2.setRating(2);
        when(feedbackRepository.findByInterviewRoundIdIn(List.of(10L, 11L))).thenReturn(List.of(feedback1, feedback2));

        var result = service.getAverageRatingForRecruiter(List.of(job));

        assertEquals(3.0, (double) result.get("averageInterviewRating"), 0.01);
        assertEquals(2L, result.get("interviewFeedbackCount"));
    }

    @Test
    void getAverageRatingForRecruiterHandlesNoFeedbackYetGracefully() {
        JobPosting job = new JobPosting();
        job.setId(1L);
        when(applicationRepository.findByJobPostingId(1L)).thenReturn(List.of());

        var result = service.getAverageRatingForRecruiter(List.of(job));

        assertEquals(null, result.get("averageInterviewRating"));
        assertEquals(0L, result.get("interviewFeedbackCount"));
    }
}
