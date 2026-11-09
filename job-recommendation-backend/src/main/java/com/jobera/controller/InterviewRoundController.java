package com.jobera.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.InterviewRound;
import com.jobera.entity.InterviewRoundFeedback;
import com.jobera.entity.User;
import com.jobera.service.AiInterviewClient;
import com.jobera.service.InterviewRoundFeedbackService;
import com.jobera.service.InterviewRoundService;

import jakarta.servlet.http.HttpServletRequest;

/** The interview pipeline (an ordered sequence of rounds) attached to one application. Access is
 *  governed by AccessPolicy's recruiterManagesApplication()/participatesInApplication() checks
 *  for application-scoped routes, and their round-scoped equivalents for a specific round. */
@RestController
public class InterviewRoundController {

    private static final String ERROR_KEY = "error";

    private final InterviewRoundService interviewRoundService;
    private final InterviewRoundFeedbackService interviewRoundFeedbackService;
    private final AiInterviewClient aiInterviewClient;

    public InterviewRoundController(InterviewRoundService interviewRoundService,
                                     InterviewRoundFeedbackService interviewRoundFeedbackService) {
        this(interviewRoundService, interviewRoundFeedbackService, null);
    }

    @Autowired
    public InterviewRoundController(InterviewRoundService interviewRoundService,
                                     InterviewRoundFeedbackService interviewRoundFeedbackService,
                                     AiInterviewClient aiInterviewClient) {
        this.interviewRoundService = interviewRoundService;
        this.interviewRoundFeedbackService = interviewRoundFeedbackService;
        this.aiInterviewClient = aiInterviewClient;
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

    @PostMapping("/api/applications/{applicationId}/interview-rounds/propose")
    public ResponseEntity<?> propose(@PathVariable Long applicationId, @RequestBody Map<String, Object> payload) {
        String title = (String) payload.get("title");
        String roundType = (String) payload.get("roundType");
        Object slotsRaw = payload.get("slots");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "title is required"));
        }
        if (!(slotsRaw instanceof List<?> slotsList)) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "slots must be a list of ISO-8601 date-times"));
        }
        List<LocalDateTime> slots = new ArrayList<>();
        try {
            for (Object slot : slotsList) {
                slots.add(LocalDateTime.parse(String.valueOf(slot)));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Each slot must be an ISO-8601 date-time"));
        }
        try {
            InterviewRound round = interviewRoundService.proposeSlots(applicationId, title, roundType, slots);
            return ResponseEntity.ok(round);
        } catch (InterviewRoundService.ApplicationNotFoundException | InterviewRoundService.InvalidRoundTypeException
                 | InterviewRoundService.InvalidSlotCountException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping("/api/interview-rounds/{roundId}/confirm-slot")
    public ResponseEntity<?> confirmSlot(@PathVariable Long roundId, @RequestBody Map<String, String> payload,
                                          HttpServletRequest request) {
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
            InterviewRound round = interviewRoundService.confirmSlot(roundId, callingUser(request).getId(), scheduledAt);
            return ResponseEntity.ok(round);
        } catch (InterviewRoundService.RoundNotFoundException | InterviewRoundService.ApplicationNotFoundException
                 | InterviewRoundService.RoundNotAwaitingSelectionException | InterviewRoundService.InvalidSlotChoiceException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (InterviewRoundService.NotTheCandidateException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, e.getMessage()));
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

    @PostMapping("/api/interview-rounds/{roundId}/feedback")
    public ResponseEntity<?> submitFeedback(@PathVariable Long roundId, @RequestBody Map<String, Object> payload,
                                             HttpServletRequest request) {
        Integer rating = payload.get("rating") instanceof Number n ? n.intValue() : null;
        String comment = (String) payload.get("comment");
        try {
            InterviewRoundFeedback feedback = interviewRoundFeedbackService.submitFeedback(
                roundId, callingUser(request).getId(), rating, comment);
            return ResponseEntity.ok(feedback);
        } catch (InterviewRoundFeedbackService.RoundNotFoundException
                 | InterviewRoundFeedbackService.RoundNotCompletedException
                 | InterviewRoundFeedbackService.InvalidRatingException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (InterviewRoundFeedbackService.NotTheCandidateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/api/interview-rounds/{roundId}/feedback")
    public ResponseEntity<?> getFeedback(@PathVariable Long roundId) {
        try {
            return ResponseEntity.ok(interviewRoundFeedbackService.getFeedbackForRound(roundId).orElse(null));
        } catch (InterviewRoundFeedbackService.RoundNotFoundException e) {
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

    @PostMapping("/api/interview-rounds/{roundId}/recording")
    public ResponseEntity<?> uploadRecording(@PathVariable Long roundId,
                                            @RequestParam("file") MultipartFile file) {
        try {
            InterviewRound round = interviewRoundService.saveRecording(roundId, file);
            return ResponseEntity.ok(round);
        } catch (InterviewRoundService.RoundNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(ERROR_KEY, "Failed to save interview recording: " + e.getMessage()));
        }
    }

    @GetMapping("/api/interview-rounds/{roundId}/recording")
    public ResponseEntity<Resource> getRecording(@PathVariable Long roundId) {
        try {
            Resource resource = interviewRoundService.getRecordingResource(roundId);
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/webm"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"interview_round_" + roundId + ".webm\"")
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/api/interview-rounds/{roundId}/copilot-probes")
    public ResponseEntity<?> getCopilotProbes(@PathVariable Long roundId, @RequestBody Map<String, String> payload) {
        try {
            InterviewRound round = interviewRoundService.getRound(roundId);
            String language = payload != null ? payload.getOrDefault("language", "JavaScript") : "JavaScript";
            String code = payload != null ? payload.getOrDefault("code", "") : "";
            if (aiInterviewClient == null) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "AI client is unavailable"));
            }
            Map<String, Object> probes = aiInterviewClient.generateLiveCodingProbes(round.getTitle(), language, code);
            return ResponseEntity.ok(probes);
        } catch (InterviewRoundService.RoundNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(ERROR_KEY, "Failed to generate copilot probes: " + e.getMessage()));
        }
    }

    @GetMapping("/api/interview-rounds/{roundId}/recording-insights")
    public ResponseEntity<?> getRecordingInsights(@PathVariable Long roundId) {
        try {
            InterviewRound round = interviewRoundService.getRound(roundId);
            String roundType = round.getRoundType() != null ? round.getRoundType() : "TECHNICAL";
            String title = round.getTitle() != null ? round.getTitle() : "Interview Round";
            Map<String, Object> insights = (aiInterviewClient != null)
                ? aiInterviewClient.generateRecordingInsights(title, roundType, "")
                : new AiInterviewClient(new org.springframework.web.client.RestTemplate(), "", "").generateFallbackRecordingInsights(title, roundType, "");
            return ResponseEntity.ok(insights);
        } catch (InterviewRoundService.RoundNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(ERROR_KEY, "Failed to generate recording insights: " + e.getMessage()));
        }
    }
}
