package com.jobera.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.service.AccountDeletionService;
import com.jobera.service.DataExportService;

/** Self-service data export and account deletion. Both routes fall under the existing
 *  "/api/users/{userId}/**" self-ownership rule in AccessPolicy - no new rule needed. */
@RestController
@RequestMapping("/api/users/{userId}")
public class AccountController {

    private static final String ERROR_KEY = "error";

    private final DataExportService dataExportService;
    private final AccountDeletionService accountDeletionService;

    public AccountController(DataExportService dataExportService, AccountDeletionService accountDeletionService) {
        this.dataExportService = dataExportService;
        this.accountDeletionService = accountDeletionService;
    }

    @GetMapping("/data-export")
    public ResponseEntity<?> exportMyData(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(dataExportService.exportMyData(userId));
        } catch (DataExportService.UserNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/account")
    public ResponseEntity<Map<String, String>> deleteMyAccount(@PathVariable Long userId,
                                                                 @RequestBody Map<String, String> payload) {
        try {
            accountDeletionService.deleteMyAccount(userId, payload.get("password"));
            return ResponseEntity.ok(Map.of("message", "Account deleted"));
        } catch (AccountDeletionService.CannotDeleteAccountException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
