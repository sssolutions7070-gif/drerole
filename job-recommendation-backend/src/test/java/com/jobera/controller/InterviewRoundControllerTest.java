package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import com.jobera.entity.User;
import com.jobera.service.InterviewRoundService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class InterviewRoundControllerTest {

    @Mock InterviewRoundService interviewRoundService;

    InterviewRoundController controller;
    HttpServletRequest request;
    User candidate;

    @BeforeEach
    void setUp() {
        controller = new InterviewRoundController(interviewRoundService);
        candidate = new User("candidate@example.com", "hash", "Candidate");
        candidate.setId(1L);
        request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).thenReturn(candidate);
    }

    @Test
    void schedulingWithValidDataReturnsTheRound() {
        InterviewRound round = new InterviewRound(1000L, 1, "Phone screen", "PHONE_SCREEN", "room-1",
            LocalDateTime.parse("2026-01-01T10:00:00"));
        when(interviewRoundService.scheduleRound(1000L, "Phone screen", "PHONE_SCREEN",
            LocalDateTime.parse("2026-01-01T10:00:00"))).thenReturn(round);

        ResponseEntity<?> response = controller.schedule(1000L, Map.of(
            "title", "Phone screen", "roundType", "PHONE_SCREEN", "scheduledAt", "2026-01-01T10:00:00"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(round, response.getBody());
    }

    @Test
    void schedulingWithoutATitleIsRejected() {
        ResponseEntity<?> response = controller.schedule(1000L, Map.of(
            "roundType", "PHONE_SCREEN", "scheduledAt", "2026-01-01T10:00:00"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void schedulingWithoutATimeIsRejected() {
        ResponseEntity<?> response = controller.schedule(1000L, Map.of("title", "Phone screen", "roundType", "PHONE_SCREEN"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void schedulingWithAnInvalidRoundTypeReturnsBadRequest() {
        when(interviewRoundService.scheduleRound(anyLong(), any(), any(), any()))
            .thenThrow(new InterviewRoundService.InvalidRoundTypeException("BOGUS"));

        ResponseEntity<?> response = controller.schedule(1000L, Map.of(
            "title", "Round", "roundType", "BOGUS", "scheduledAt", "2026-01-01T10:00:00"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void listingRoundsReturnsWhatTheServiceProvides() {
        List<InterviewRound> rounds = List.of(new InterviewRound(1000L, 1, "Phone screen", "PHONE_SCREEN", "room-1", null));
        when(interviewRoundService.listRounds(1000L)).thenReturn(rounds);

        ResponseEntity<?> response = controller.listRounds(1000L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(rounds, response.getBody());
    }

    @Test
    void gettingJoinInfoReturnsWhatTheServiceProvides() {
        Map<String, Object> info = Map.of("roomId", "room-1", "role", "candidate");
        when(interviewRoundService.getJoinInfo(5000L, 1L)).thenReturn(info);

        ResponseEntity<?> response = controller.getJoinInfo(5000L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(info, response.getBody());
    }

    @Test
    void gettingJoinInfoForAnUnknownRoundReturnsBadRequest() {
        when(interviewRoundService.getJoinInfo(5000L, 1L))
            .thenThrow(new InterviewRoundService.RoundNotFoundException(5000L));

        ResponseEntity<?> response = controller.getJoinInfo(5000L, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void updatingStatusReturnsTheUpdatedRound() {
        InterviewRound round = new InterviewRound(1000L, 1, "Phone screen", "PHONE_SCREEN", "room-1", null);
        round.setStatus("IN_PROGRESS");
        when(interviewRoundService.updateStatus(5000L, "IN_PROGRESS", 1L)).thenReturn(round);

        ResponseEntity<?> response = controller.updateStatus(5000L, Map.of("status", "IN_PROGRESS"), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(round, response.getBody());
    }

    @Test
    void updatingStatusWithoutAStatusIsRejected() {
        ResponseEntity<?> response = controller.updateStatus(5000L, Map.of(), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void reschedulingReturnsTheUpdatedRound() {
        InterviewRound round = new InterviewRound(1000L, 1, "Phone screen", "PHONE_SCREEN", "room-1",
            LocalDateTime.parse("2026-02-01T10:00:00"));
        when(interviewRoundService.rescheduleRound(5000L, LocalDateTime.parse("2026-02-01T10:00:00"))).thenReturn(round);

        ResponseEntity<?> response = controller.reschedule(5000L, Map.of("scheduledAt", "2026-02-01T10:00:00"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(round, response.getBody());
    }

    @Test
    void reschedulingWithoutATimeIsRejected() {
        ResponseEntity<?> response = controller.reschedule(5000L, Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void reschedulingWithAnUnparseableTimeIsRejected() {
        ResponseEntity<?> response = controller.reschedule(5000L, Map.of("scheduledAt", "not-a-date"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
