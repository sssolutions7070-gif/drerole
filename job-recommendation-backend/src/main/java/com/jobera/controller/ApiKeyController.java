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
import com.jobera.service.ApiKeyService;

import jakarta.servlet.http.HttpServletRequest;

/** Managing a recruiter's own API keys - covered by AccessPolicy's ownRecruiterId rule for route
 *  access, with an owner-only check inside the service since minting a credential with full API
 *  access is stricter than the day-to-day actions team members already share. */
@RestController
@RequestMapping("/api/recruiters/{recruiterId}/api-keys")
public class ApiKeyController {

    private static final String ERROR_KEY = "error";

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    private Long callingUserId(HttpServletRequest request) {
        return ((User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).getId();
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long recruiterId) {
        try {
            return ResponseEntity.ok(apiKeyService.listKeys(recruiterId));
        } catch (ApiKeyService.RecruiterNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> generate(@PathVariable Long recruiterId, @RequestBody Map<String, String> payload,
                                       HttpServletRequest request) {
        try {
            return ResponseEntity.ok(apiKeyService.generateKey(recruiterId, callingUserId(request), payload.get("label")));
        } catch (ApiKeyService.RecruiterNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (ApiKeyService.NotTheAccountOwnerException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<?> revoke(@PathVariable Long recruiterId, @PathVariable Long keyId, HttpServletRequest request) {
        try {
            apiKeyService.revokeKey(recruiterId, keyId, callingUserId(request));
            return ResponseEntity.ok().build();
        } catch (ApiKeyService.RecruiterNotFoundException | ApiKeyService.ApiKeyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (ApiKeyService.NotTheAccountOwnerException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
