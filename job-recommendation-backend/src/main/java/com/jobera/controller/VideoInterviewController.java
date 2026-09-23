package com.jobera.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.User;
import com.jobera.entity.VideoInterview;
import com.jobera.service.VideoInterviewService;

import jakarta.servlet.http.HttpServletRequest;

/** Scheduling and lifecycle for the video-interview attached to one application. Access is
 *  governed by AccessPolicy's participatesInApplication()/recruiterManagesApplication() checks -
 *  the same rules that already gate messaging and test assignments for an application. */
@RestController
public class VideoInterviewController {

    private static final String ERROR_KEY = "error";

    private final VideoInterviewService videoInterviewService;

    public VideoInterviewController(VideoInterviewService videoInterviewService) {
        this.videoInterviewService = videoInterviewService;
    }

    private User callingUser(HttpServletRequest request) {
        return (User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
    }

    @PostMapping("/api/applications/{applicationId}/video-interview")
    public ResponseEntity<?> schedule(@PathVariable Long applicationId, @RequestBody Map<String, String> payload) {
        String scheduledAtRaw = payload.get("scheduledAt");
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
            VideoInterview interview = videoInterviewService.scheduleInterview(applicationId, scheduledAt);
            return ResponseEntity.ok(interview);
        } catch (VideoInterviewService.ApplicationNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/api/applications/{applicationId}/video-interview")
    public ResponseEntity<?> getJoinInfo(@PathVariable Long applicationId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(videoInterviewService.getJoinInfo(applicationId, callingUser(request).getId()));
        } catch (VideoInterviewService.ApplicationNotFoundException | VideoInterviewService.InterviewNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PutMapping("/api/applications/{applicationId}/video-interview/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long applicationId, @RequestBody Map<String, String> payload,
                                           HttpServletRequest request) {
        String status = payload.get("status");
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "status is required"));
        }
        try {
            VideoInterview interview = videoInterviewService.updateStatus(applicationId, status, callingUser(request).getId());
            return ResponseEntity.ok(interview);
        } catch (VideoInterviewService.ApplicationNotFoundException | VideoInterviewService.InterviewNotFoundException
                 | VideoInterviewService.InvalidStatusException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
