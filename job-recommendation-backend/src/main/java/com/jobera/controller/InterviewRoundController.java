package com.jobera.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.InterviewRound;
import com.jobera.entity.User;
import com.jobera.service.InterviewRoundService;

import jakarta.servlet.http.HttpServletRequest;

/** The interview pipeline (an ordered sequence of rounds) attached to one application. Access is
 *  governed by AccessPolicy's recruiterManagesApplication()/participatesInApplication() checks
 *  for application-scoped routes, and their round-scoped equivalents for a specific round. */
@RestController
public class InterviewRoundController {

    private static final String ERROR_KEY = "error";

    private final InterviewRoundService interviewRoundService;

    public InterviewRoundController(InterviewRoundService interviewRoundService) {
        this.interviewRoundService = interviewRoundService;
    }

    private User callingUser(HttpServletRequest request) {
        return (User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
    }

    @PostMapping("/api/applications/{applicationId}/interview-rounds")
    public ResponseEntity<?> schedule(@PathVariable Long applicationId, @RequestBody Map<String, String> payload) {
        String title = payload.get("title");
        String roundType = payload.get("roundType");
        String scheduledAtRaw = payload.get("scheduledAt");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "title is required"));
        }
        if (scheduledAtRaw == null || scheduledAtRaw.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "scheduledAt is required"));
        }
        LocalDateTime scheduledAt;
        try {
            scheduledAt = LocalDateTime.parse(scheduledAtRaw);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "scheduledAt must be an ISO-8601 date-time"));
        }
        try {
            InterviewRound round = interviewRoundService.scheduleRound(applicationId, title, roundType, scheduledAt);
            return ResponseEntity.ok(round);
        } catch (InterviewRoundService.ApplicationNotFoundException | InterviewRoundService.InvalidRoundTypeException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/api/applications/{applicationId}/interview-rounds")
    public ResponseEntity<?> listRounds(@PathVariable Long applicationId) {
        try {
            List<InterviewRound> rounds = interviewRoundService.listRounds(applicationId);
            return ResponseEntity.ok(rounds);
        } catch (InterviewRoundService.ApplicationNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/api/interview-rounds/{roundId}/calendar.ics")
    public ResponseEntity<String> downloadCalendarInvite(@PathVariable Long roundId) {
        try {
            String ics = interviewRoundService.generateIcs(roundId);
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"interview-" + roundId + ".ics\"");
            return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType("text/calendar")).body(ics);
        } catch (InterviewRoundService.RoundNotFoundException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/interview-rounds/{roundId}/join")
    public ResponseEntity<?> getJoinInfo(@PathVariable Long roundId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(interviewRoundService.getJoinInfo(roundId, callingUser(request).getId()));
        } catch (InterviewRoundService.RoundNotFoundException | InterviewRoundService.ApplicationNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PutMapping("/api/interview-rounds/{roundId}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long roundId, @RequestBody Map<String, String> payload,
                                           HttpServletRequest request) {
        String status = payload.get("status");
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "status is required"));
        }
        try {
            InterviewRound round = interviewRoundService.updateStatus(roundId, status, callingUser(request).getId());
            return ResponseEntity.ok(round);
        } catch (InterviewRoundService.RoundNotFoundException | InterviewRoundService.ApplicationNotFoundException
                 | InterviewRoundService.InvalidStatusException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PutMapping("/api/interview-rounds/{roundId}/reschedule")
    public ResponseEntity<?> reschedule(@PathVariable Long roundId, @RequestBody Map<String, String> payload) {
        String scheduledAtRaw = payload.get("scheduledAt");
        if (scheduledAtRaw == null || scheduledAtRaw.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "scheduledAt is required"));
        }
        try {
            LocalDateTime scheduledAt = LocalDateTime.parse(scheduledAtRaw);
            InterviewRound round = interviewRoundService.rescheduleRound(roundId, scheduledAt);
            return ResponseEntity.ok(round);
        } catch (InterviewRoundService.RoundNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "scheduledAt must be an ISO-8601 date-time"));
        }
    }
}
