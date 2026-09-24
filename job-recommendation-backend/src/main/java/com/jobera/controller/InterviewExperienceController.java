package com.jobera.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.entity.InterviewExperience;
import com.jobera.service.InterviewExperienceService;

/** Writing and checking your own interview-experience report. Already covered by the existing
 *  "/api/users/{userId}/**" self-ownership rule in AccessPolicy - no new rule needed. */
@RestController
@RequestMapping("/api/users/{userId}/interview-experiences")
public class InterviewExperienceController {

    private static final String ERROR_KEY = "error";

    private final InterviewExperienceService interviewExperienceService;

    public InterviewExperienceController(InterviewExperienceService interviewExperienceService) {
        this.interviewExperienceService = interviewExperienceService;
    }

    @PostMapping("/{recruiterId}")
    public ResponseEntity<?> submit(@PathVariable Long userId, @PathVariable Long recruiterId,
                                     @RequestBody Map<String, Object> payload) {
        try {
            Integer difficultyRating = payload.get("difficultyRating") == null ? null
                : Integer.valueOf(payload.get("difficultyRating").toString());
            String outcome = (String) payload.get("outcome");
            String processDescription = (String) payload.get("processDescription");
            InterviewExperience experience = interviewExperienceService.submitExperience(
                userId, recruiterId, difficultyRating, outcome, processDescription);
            return ResponseEntity.ok(Map.of(
                "message", "Interview experience submitted for approval",
                "moderationStatus", experience.getModerationStatus()
            ));
        } catch (InterviewExperienceService.ExperienceException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/{recruiterId}")
    public ResponseEntity<?> getMine(@PathVariable Long userId, @PathVariable Long recruiterId) {
        return interviewExperienceService.getMyExperience(userId, recruiterId)
            .<ResponseEntity<?>>map(experience -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("difficultyRating", experience.getDifficultyRating());
                body.put("outcome", experience.getOutcome());
                body.put("processDescription", experience.getProcessDescription());
                body.put("moderationStatus", experience.getModerationStatus());
                body.put("rejectionReason", experience.getRejectionReason());
                return ResponseEntity.ok(body);
            })
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
