package com.jobera.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.service.TwoFactorService;

/** Enabling/disabling two-factor auth on one's own account. Already covered by the existing
 *  "/api/users/{userId}/**" self-ownership rule in AccessPolicy - no new rule needed. */
@RestController
@RequestMapping("/api/users/{userId}/2fa")
public class TwoFactorController {

    private static final String ERROR_KEY = "error";

    private final TwoFactorService twoFactorService;

    public TwoFactorController(TwoFactorService twoFactorService) {
        this.twoFactorService = twoFactorService;
    }

    @PostMapping("/setup")
    public ResponseEntity<?> beginSetup(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(twoFactorService.beginSetup(userId));
        } catch (TwoFactorService.TwoFactorException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmSetup(@PathVariable Long userId, @RequestBody Map<String, String> payload) {
        try {
            List<String> backupCodes = twoFactorService.confirmSetup(userId, payload.get("secret"), payload.get("code"));
            return ResponseEntity.ok(Map.of(
                "message", "Two-factor authentication enabled",
                "backupCodes", backupCodes
            ));
        } catch (TwoFactorService.TwoFactorException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping("/disable")
    public ResponseEntity<Map<String, String>> disable(@PathVariable Long userId, @RequestBody Map<String, String> payload) {
        try {
            twoFactorService.disable(userId, payload.get("password"));
            return ResponseEntity.ok(Map.of("message", "Two-factor authentication disabled"));
        } catch (TwoFactorService.TwoFactorException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
