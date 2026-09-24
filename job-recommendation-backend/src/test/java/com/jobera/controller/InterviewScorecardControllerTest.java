package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.InterviewRound;
import com.jobera.entity.InterviewScorecard;
import com.jobera.entity.User;
import com.jobera.service.InterviewRoundService;
import com.jobera.service.InterviewScorecardService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class InterviewScorecardControllerTest {

    @Mock InterviewScorecardService scorecardService;
    @Mock InterviewRoundService interviewRoundService;

    InterviewScorecardController controller;
    HttpServletRequest request;

    @BeforeEach
    void setUp() {
        controller = new InterviewScorecardController(scorecardService, interviewRoundService);
        User recruiterUser = new User("recruiter@example.com", "hash", "Recruiter");
        recruiterUser.setId(2L);
        request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).thenReturn(recruiterUser);
    }

    @Test
    void submittingAValidScorecardReturnsIt() {
        InterviewScorecard scorecard = new InterviewScorecard();
        scorecard.setInterviewRoundId(5000L);
        when(scorecardService.submitScorecard(5000L, 2L, 4, 5, 3, "YES", "Great"))
            .thenReturn(scorecard);

        ResponseEntity<?> response = controller.submit(5000L, Map.of(
            "communicationRating", 4, "technicalRating", 5, "cultureFitRating", 3,
            "recommendation", "YES", "notes", "Great"), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(scorecard, response.getBody());
    }

    @Test
    void submittingWithAnInvalidRatingReturnsBadRequest() {
        when(scorecardService.submitScorecard(5000L, 2L, 6, 5, 3, "YES", null))
            .thenThrow(new InterviewScorecardService.InvalidRatingException("communicationRating"));

        ResponseEntity<?> response = controller.submit(5000L, Map.of(
            "communicationRating", 6, "technicalRating", 5, "cultureFitRating", 3, "recommendation", "YES"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void gettingAScorecardReturnsIt() {
        InterviewScorecard scorecard = new InterviewScorecard();
        when(scorecardService.getScorecard(5000L)).thenReturn(scorecard);

        ResponseEntity<?> response = controller.get(5000L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(scorecard, response.getBody());
    }

    @Test
    void gettingAMissingScorecardReturnsBadRequest() {
        when(scorecardService.getScorecard(5000L)).thenThrow(new InterviewScorecardService.ScorecardNotFoundException(5000L));

        ResponseEntity<?> response = controller.get(5000L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getForApplicationBuildsAPipelineWithScorecardsAttached() {
        InterviewRound round1 = new InterviewRound(1000L, 1, "Phone screen", "PHONE_SCREEN", "room-1", null);
        round1.setId(5000L);
        InterviewRound round2 = new InterviewRound(1000L, 2, "Technical", "TECHNICAL", "room-2", null);
        round2.setId(5001L);
        when(interviewRoundService.listRounds(1000L)).thenReturn(List.of(round1, round2));

        InterviewScorecard scorecardForRound1 = new InterviewScorecard();
        scorecardForRound1.setInterviewRoundId(5000L);
        when(scorecardService.getScorecardsForRounds(List.of(5000L, 5001L))).thenReturn(List.of(scorecardForRound1));

        ResponseEntity<?> response = controller.getForApplication(1000L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        assertEquals(2, body.size());
        assertEquals(scorecardForRound1, body.get(0).get("scorecard"));
        assertEquals(null, body.get(1).get("scorecard"));
    }

    @Test
    void getForApplicationWithAMissingApplicationReturnsBadRequest() {
        when(interviewRoundService.listRounds(1000L)).thenThrow(new InterviewRoundService.ApplicationNotFoundException(1000L));

        ResponseEntity<?> response = controller.getForApplication(1000L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
