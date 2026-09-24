package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.InterviewRound;
import com.jobera.entity.InterviewScorecard;
import com.jobera.repository.InterviewRoundRepository;
import com.jobera.repository.InterviewScorecardRepository;

@ExtendWith(MockitoExtension.class)
class InterviewScorecardServiceTest {

    @Mock InterviewScorecardRepository scorecardRepository;
    @Mock InterviewRoundRepository interviewRoundRepository;

    InterviewScorecardService service;

    @BeforeEach
    void setUp() {
        service = new InterviewScorecardService(scorecardRepository, interviewRoundRepository);
        lenient().when(interviewRoundRepository.findById(5000L)).thenReturn(Optional.of(new InterviewRound()));
        lenient().when(scorecardRepository.save(any(InterviewScorecard.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void submittingACompleteScorecardSavesIt() {
        when(scorecardRepository.findByInterviewRoundId(5000L)).thenReturn(Optional.empty());

        InterviewScorecard saved = service.submitScorecard(5000L, 2L, 4, 5, 3, "YES", "Strong candidate");

        assertEquals(5000L, saved.getInterviewRoundId());
        assertEquals(2L, saved.getSubmittedByUserId());
        assertEquals(4, saved.getCommunicationRating());
        assertEquals("YES", saved.getRecommendation());
    }

    @Test
    void resubmittingUpdatesTheExistingScorecardRatherThanCreatingASecondOne() {
        InterviewScorecard existing = new InterviewScorecard();
        existing.setId(1L);
        existing.setInterviewRoundId(5000L);
        when(scorecardRepository.findByInterviewRoundId(5000L)).thenReturn(Optional.of(existing));

        InterviewScorecard updated = service.submitScorecard(5000L, 2L, 5, 5, 5, "STRONG_YES", "Even better on reflection");

        assertEquals(1L, updated.getId());
        assertEquals("STRONG_YES", updated.getRecommendation());
    }

    @Test
    void submittingForAMissingRoundFails() {
        when(interviewRoundRepository.findById(6000L)).thenReturn(Optional.empty());

        assertThrows(InterviewScorecardService.RoundNotFoundException.class,
            () -> service.submitScorecard(6000L, 2L, 3, 3, 3, "NEUTRAL", null));
    }

    @Test
    void aRatingOutOfRangeIsRejected() {
        assertThrows(InterviewScorecardService.InvalidRatingException.class,
            () -> service.submitScorecard(5000L, 2L, 6, 3, 3, "NEUTRAL", null));
        assertThrows(InterviewScorecardService.InvalidRatingException.class,
            () -> service.submitScorecard(5000L, 2L, 0, 3, 3, "NEUTRAL", null));
        assertThrows(InterviewScorecardService.InvalidRatingException.class,
            () -> service.submitScorecard(5000L, 2L, 3, 3, null, "NEUTRAL", null));
    }

    @Test
    void anUnknownRecommendationIsRejected() {
        assertThrows(InterviewScorecardService.InvalidRecommendationException.class,
            () -> service.submitScorecard(5000L, 2L, 3, 3, 3, "MAYBE", null));
    }

    @Test
    void gettingAMissingScorecardFails() {
        when(scorecardRepository.findByInterviewRoundId(5000L)).thenReturn(Optional.empty());

        assertThrows(InterviewScorecardService.ScorecardNotFoundException.class, () -> service.getScorecard(5000L));
    }

    @Test
    void getScorecardsForRoundsDelegatesToTheRepository() {
        InterviewScorecard sc = new InterviewScorecard();
        sc.setInterviewRoundId(5000L);
        when(scorecardRepository.findByInterviewRoundIdIn(List.of(5000L, 5001L))).thenReturn(List.of(sc));

        List<InterviewScorecard> result = service.getScorecardsForRounds(List.of(5000L, 5001L));

        assertEquals(1, result.size());
    }
}
