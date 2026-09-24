package com.jobera.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.DirectMessage;
import com.jobera.entity.User;
import com.jobera.service.DirectMessageService;

import jakarta.servlet.http.HttpServletRequest;

/** Chat between a recruiter and a candidate outside of any application - see DirectMessageService
 *  for why Message/MessageController (application-scoped) doesn't cover this. Access is governed
 *  by AccessPolicy's participatesInDirectThread check: either side of the (recruiterId,
 *  candidateUserId) pair, nothing else. */
@RestController
public class DirectMessageController {

    private static final String ERROR_KEY = "error";

    private final DirectMessageService directMessageService;

    public DirectMessageController(DirectMessageService directMessageService) {
        this.directMessageService = directMessageService;
    }

    private User callingUser(HttpServletRequest request) {
        return (User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
    }

    @GetMapping("/api/recruiters/{recruiterId}/direct-messages/{candidateUserId}")
    public ResponseEntity<?> getThread(@PathVariable Long recruiterId, @PathVariable Long candidateUserId) {
        try {
            return ResponseEntity.ok(directMessageService.getThread(recruiterId, candidateUserId));
        } catch (DirectMessageService.RecruiterNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping("/api/recruiters/{recruiterId}/direct-messages/{candidateUserId}")
    public ResponseEntity<?> sendMessage(@PathVariable Long recruiterId, @PathVariable Long candidateUserId,
                                          @RequestBody Map<String, String> payload, HttpServletRequest request) {
        String body = payload.get("body");
        if (body == null || body.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Message body cannot be empty"));
        }
        try {
            DirectMessage saved = directMessageService.sendMessage(
                recruiterId, candidateUserId, callingUser(request).getId(), body.trim());
            return ResponseEntity.ok(saved);
        } catch (DirectMessageService.RecruiterNotFoundException | DirectMessageService.CandidateNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/api/recruiters/{recruiterId}/direct-messages")
    public ResponseEntity<List<Map<String, Object>>> listForRecruiter(@PathVariable Long recruiterId) {
        return ResponseEntity.ok(directMessageService.listThreadsForRecruiter(recruiterId));
    }

    @GetMapping("/api/users/{userId}/direct-messages")
    public ResponseEntity<List<Map<String, Object>>> listForCandidate(@PathVariable Long userId) {
        return ResponseEntity.ok(directMessageService.listThreadsForCandidate(userId));
    }
}
