package com.jobera.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.User;
import com.jobera.service.RecruiterVerificationService;

import jakarta.servlet.http.HttpServletRequest;

/** A recruiter submitting proof of their business for the "Verified Employer" badge, and
 *  checking their own status - covered by AccessPolicy's ownRecruiterId rule for route access,
 *  with an owner-only check inside the service (an account-level action, like API keys). */
@RestController
@RequestMapping("/api/recruiters/{recruiterId}/verification")
public class RecruiterVerificationController {

    private static final String ERROR_KEY = "error";

    private final RecruiterVerificationService recruiterVerificationService;

    public RecruiterVerificationController(RecruiterVerificationService recruiterVerificationService) {
        this.recruiterVerificationService = recruiterVerificationService;
    }

    private Long callingUserId(HttpServletRequest request) {
        return ((User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).getId();
    }

    @GetMapping
    public ResponseEntity<?> getStatus(@PathVariable Long recruiterId) {
        try {
            return ResponseEntity.ok(recruiterVerificationService.getStatus(recruiterId));
        } catch (RecruiterVerificationService.RecruiterNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> submit(@PathVariable Long recruiterId, @RequestParam("file") MultipartFile file,
                                     HttpServletRequest request) {
        try {
            recruiterVerificationService.submit(recruiterId, callingUserId(request), file);
            return ResponseEntity.ok(recruiterVerificationService.getStatus(recruiterId));
        } catch (RecruiterVerificationService.RecruiterNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (RecruiterVerificationService.NotTheAccountOwnerException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (RecruiterVerificationService.MissingDocumentException
                 | RecruiterVerificationService.DocumentUploadException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
