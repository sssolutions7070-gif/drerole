package com.jobera.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.service.RecruiterVerificationService;

/** Reviewing "Verified Employer" submissions - covered by AccessPolicy's existing
 *  rule(null, "/api/admin/**", admin), so no new access-control rule is needed here. */
@RestController
@RequestMapping("/api/admin/verifications")
public class AdminVerificationController {

    private static final String ERROR_KEY = "error";

    private final RecruiterVerificationService recruiterVerificationService;

    public AdminVerificationController(RecruiterVerificationService recruiterVerificationService) {
        this.recruiterVerificationService = recruiterVerificationService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> pending() {
        return ResponseEntity.ok(recruiterVerificationService.getPending());
    }

    @GetMapping("/{recruiterId}/document")
    public ResponseEntity<?> downloadDocument(@PathVariable Long recruiterId) {
        try {
            Path filePath = recruiterVerificationService.getDocumentFile(recruiterId);
            Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filePath.getFileName() + "\"")
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
        } catch (RecruiterVerificationService.RecruiterNotFoundException
                 | RecruiterVerificationService.DocumentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(ERROR_KEY, "Failed to read the document"));
        }
    }

    @PostMapping("/{recruiterId}/approve")
    public ResponseEntity<?> approve(@PathVariable Long recruiterId) {
        try {
            recruiterVerificationService.approve(recruiterId);
            return ResponseEntity.ok(recruiterVerificationService.getStatus(recruiterId));
        } catch (RecruiterVerificationService.RecruiterNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping("/{recruiterId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long recruiterId, @RequestBody Map<String, String> payload) {
        try {
            recruiterVerificationService.reject(recruiterId, payload.get("reason"));
            return ResponseEntity.ok(recruiterVerificationService.getStatus(recruiterId));
        } catch (RecruiterVerificationService.RecruiterNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
