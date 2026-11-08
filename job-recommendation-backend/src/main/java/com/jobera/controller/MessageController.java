package com.jobera.controller;

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

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.Message;
import com.jobera.entity.User;
import com.jobera.service.MessageService;

import jakarta.servlet.http.HttpServletRequest;

/** Chat between a candidate and a recruiter, scoped to one application. Access is governed by
 *  AccessPolicy's participatesInApplication() check - the same rule that already gates test
 *  assignments and resume downloads for an application. */
@RestController
public class MessageController {

    private static final String ERROR_KEY = "error";

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    private User callingUser(HttpServletRequest request) {
        return (User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
    }

    @GetMapping("/api/applications/{applicationId}/messages")
    public ResponseEntity<List<Message>> getThread(@PathVariable Long applicationId) {
        return ResponseEntity.ok(messageService.getThread(applicationId));
    }

    @PostMapping("/api/applications/{applicationId}/messages")
    public ResponseEntity<?> sendMessage(@PathVariable Long applicationId,
                                          @RequestBody Map<String, String> payload,
                                          HttpServletRequest request) {
        String body = payload.get("body");
        if (body == null || body.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Message body cannot be empty"));
        }
        try {
            Message saved = messageService.sendMessage(applicationId, callingUser(request).getId(), body.trim());
            return ResponseEntity.ok(saved);
        } catch (MessageService.ApplicationNotFoundException | MessageService.NotAParticipantException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    /** Sends one message to every selected application's candidate for this job in a single
     *  action - scoped by jobId purely so AccessPolicy's existing ownsJob() check (the same rule
     *  guarding the applicant CSV export for this job) covers it; the actual per-recipient
     *  authorization still happens in MessageService.sendMessage for each application. */
    @PostMapping("/api/recruiters/jobs/{jobId}/applications/bulk-message")
    public ResponseEntity<?> sendBulkMessage(@PathVariable Long jobId,
                                              @RequestBody Map<String, Object> payload,
                                              HttpServletRequest request) {
        String body = (String) payload.get("body");
        if (body == null || body.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Message body cannot be empty"));
        }

        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) payload.get("applicationIds");
        if (rawIds == null || rawIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "applicationIds cannot be empty"));
        }
        List<Long> applicationIds = rawIds.stream().map(id -> Long.valueOf(id.toString())).collect(java.util.stream.Collectors.toList());

        Map<String, Object> result = messageService.sendBulkMessage(applicationIds, callingUser(request).getId(), body.trim());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/api/applications/{applicationId}/messages/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long applicationId, HttpServletRequest request) {
        messageService.markThreadRead(applicationId, callingUser(request).getId());
        return ResponseEntity.ok(Map.of("message", "Marked as read"));
    }

    @GetMapping("/api/messages/unread-count/{userId}")
    public ResponseEntity<Map<String, Object>> getUnreadCount(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("unreadCount", messageService.getUnreadCount(userId)));
    }
}
