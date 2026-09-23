package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.User;
import com.jobera.entity.VideoInterview;
import com.jobera.service.VideoInterviewService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class VideoInterviewControllerTest {

    @Mock VideoInterviewService videoInterviewService;

    VideoInterviewController controller;
    HttpServletRequest request;
    User candidate;

    @BeforeEach
    void setUp() {
        controller = new VideoInterviewController(videoInterviewService);
        candidate = new User("candidate@example.com", "hash", "Candidate");
        candidate.setId(1L);
        request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).thenReturn(candidate);
    }

    @Test
    void schedulingWithAValidTimeReturnsTheInterview() {
        VideoInterview interview = new VideoInterview(1000L, "room-1", LocalDateTime.parse("2026-01-01T10:00:00"));
        when(videoInterviewService.scheduleInterview(eq(1000L), eq(LocalDateTime.parse("2026-01-01T10:00:00"))))
            .thenReturn(interview);

        ResponseEntity<?> response = controller.schedule(1000L, Map.of("scheduledAt", "2026-01-01T10:00:00"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(interview, response.getBody());
    }

    @Test
    void schedulingWithoutATimeIsRejected() {
        ResponseEntity<?> response = controller.schedule(1000L, Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void schedulingWithAnUnparseableTimeIsRejected() {
        ResponseEntity<?> response = controller.schedule(1000L, Map.of("scheduledAt", "not-a-date"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void schedulingForAMissingApplicationReturnsBadRequest() {
        when(videoInterviewService.scheduleInterview(anyLong(), any()))
            .thenThrow(new VideoInterviewService.ApplicationNotFoundException(1000L));

        ResponseEntity<?> response = controller.schedule(1000L, Map.of("scheduledAt", "2026-01-01T10:00:00"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void gettingJoinInfoReturnsWhatTheServiceProvides() {
        Map<String, Object> info = Map.of("roomId", "room-1", "role", "candidate");
        when(videoInterviewService.getJoinInfo(1000L, 1L)).thenReturn(info);

        ResponseEntity<?> response = controller.getJoinInfo(1000L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(info, response.getBody());
    }

    @Test
    void gettingJoinInfoBeforeSchedulingReturnsBadRequest() {
        when(videoInterviewService.getJoinInfo(1000L, 1L))
            .thenThrow(new VideoInterviewService.InterviewNotFoundException(1000L));

        ResponseEntity<?> response = controller.getJoinInfo(1000L, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void updatingStatusReturnsTheUpdatedInterview() {
        VideoInterview interview = new VideoInterview(1000L, "room-1", LocalDateTime.now());
        interview.setStatus("IN_PROGRESS");
        when(videoInterviewService.updateStatus(1000L, "IN_PROGRESS", 1L)).thenReturn(interview);

        ResponseEntity<?> response = controller.updateStatus(1000L, Map.of("status", "IN_PROGRESS"), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(interview, response.getBody());
    }

    @Test
    void updatingStatusWithoutAStatusIsRejected() {
        ResponseEntity<?> response = controller.updateStatus(1000L, Map.of(), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void updatingWithAnInvalidStatusReturnsBadRequest() {
        when(videoInterviewService.updateStatus(1000L, "BOGUS", 1L))
            .thenThrow(new VideoInterviewService.InvalidStatusException("BOGUS"));

        ResponseEntity<?> response = controller.updateStatus(1000L, Map.of("status", "BOGUS"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
