package com.jobera.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.entity.MockInterviewQuestion;
import com.jobera.entity.MockInterviewSession;
import com.jobera.service.AiInterviewClient;
import com.jobera.service.MockInterviewService;

/** AI mock-interview practice, entirely scoped under a candidate's own user id - covered by
 *  AccessPolicy's existing self(userId) wildcard rule for /api/users/{userId}/**, so no new
 *  access-control rule is needed here. */
@RestController
@RequestMapping("/api/users/{userId}/mock-interviews")
public class MockInterviewController {

    private static final String ERROR_KEY = "error";

    private final MockInterviewService mockInterviewService;

    public MockInterviewController(MockInterviewService mockInterviewService) {
        this.mockInterviewService = mockInterviewService;
    }

    @PostMapping
    public ResponseEntity<?> start(@PathVariable Long userId, @RequestBody Map<String, Object> payload) {
        Object jobIdRaw = payload.get("jobId");
        if (jobIdRaw == null) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "jobId is required"));
        }
        try {
            Long jobId = Long.valueOf(jobIdRaw.toString());
            MockInterviewSession session = mockInterviewService.startSession(userId, jobId);
            List<MockInterviewQuestion> questions = mockInterviewService.getQuestions(session.getId(), userId);
            return ResponseEntity.ok(sessionResponse(session, questions));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "jobId must be a number"));
        } catch (MockInterviewService.JobNotFoundException | MockInterviewService.NotConfiguredException
                 | AiInterviewClient.AiInterviewException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<MockInterviewSession>> list(@PathVariable Long userId) {
        return ResponseEntity.ok(mockInterviewService.listSessions(userId));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<?> get(@PathVariable Long userId, @PathVariable Long sessionId) {
        try {
            List<MockInterviewQuestion> questions = mockInterviewService.getQuestions(sessionId, userId);
            return ResponseEntity.ok(Map.of("sessionId", sessionId, "questions", questions));
        } catch (MockInterviewService.SessionNotFoundException | MockInterviewService.NotYourSessionException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PutMapping("/{sessionId}/questions/{questionId}/answer")
    public ResponseEntity<?> answer(@PathVariable Long userId, @PathVariable Long sessionId,
                                     @PathVariable Long questionId, @RequestBody Map<String, String> payload) {
        String answer = payload.get("answer");
        if (answer == null || answer.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "answer is required"));
        }
        try {
            MockInterviewQuestion updated = mockInterviewService.submitAnswer(sessionId, questionId, userId, answer);
            return ResponseEntity.ok(updated);
        } catch (MockInterviewService.SessionNotFoundException | MockInterviewService.NotYourSessionException
                 | MockInterviewService.QuestionNotFoundException | AiInterviewClient.AiInterviewException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    private Map<String, Object> sessionResponse(MockInterviewSession session, List<MockInterviewQuestion> questions) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId", session.getId());
        response.put("jobTitle", session.getJobTitle());
        response.put("status", session.getStatus());
        response.put("questions", questions);
        return response;
    }
}
