package com.jobera.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.User;
import com.jobera.service.WebhookService;

import jakarta.servlet.http.HttpServletRequest;

/** Managing a recruiter's own webhook subscriptions - covered by AccessPolicy's ownRecruiterId
 *  rule for route access, with an owner-only check inside the service (same reasoning as
 *  ApiKeyController: this is an account-level integration, not a shared day-to-day action). */
@RestController
@RequestMapping("/api/recruiters/{recruiterId}/webhooks")
public class WebhookController {

    private static final String ERROR_KEY = "error";

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    private Long callingUserId(HttpServletRequest request) {
        return ((User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).getId();
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long recruiterId) {
        try {
            return ResponseEntity.ok(webhookService.list(recruiterId));
        } catch (WebhookService.RecruiterNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> register(@PathVariable Long recruiterId, @RequestBody Map<String, String> payload,
                                       HttpServletRequest request) {
        try {
            return ResponseEntity.ok(webhookService.register(
                recruiterId, callingUserId(request), payload.get("url"), payload.get("eventType")));
        } catch (WebhookService.RecruiterNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (WebhookService.NotTheAccountOwnerException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (WebhookService.InvalidEventTypeException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @DeleteMapping("/{webhookId}")
    public ResponseEntity<?> delete(@PathVariable Long recruiterId, @PathVariable Long webhookId, HttpServletRequest request) {
        try {
            webhookService.delete(recruiterId, webhookId, callingUserId(request));
            return ResponseEntity.ok().build();
        } catch (WebhookService.RecruiterNotFoundException | WebhookService.WebhookNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (WebhookService.NotTheAccountOwnerException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
