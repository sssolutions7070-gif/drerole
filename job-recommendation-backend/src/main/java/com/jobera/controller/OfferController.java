package com.jobera.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.JobOffer;
import com.jobera.entity.User;
import com.jobera.service.OfferService;

import jakarta.servlet.http.HttpServletRequest;

/** Sending, accepting, declining, and withdrawing a job offer for one application - the terminal
 *  step of the hiring pipeline. Access is governed by AccessPolicy's recruiterManagesApplication()/
 *  participatesInApplication() checks; accept/decline additionally verify in the service layer
 *  that the caller is the candidate the offer was sent to, not the recruiter who sent it. */
@RestController
public class OfferController {

    private static final String ERROR_KEY = "error";

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    private User callingUser(HttpServletRequest request) {
        return (User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
    }

    @PostMapping("/api/applications/{applicationId}/offer")
    public ResponseEntity<?> send(@PathVariable Long applicationId, @RequestBody Map<String, String> payload) {
        String title = payload.get("title");
        String salaryOffered = payload.get("salaryOffered");
        String startDateRaw = payload.get("startDate");
        if (title == null || title.isBlank() || salaryOffered == null || salaryOffered.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "title and salaryOffered are required"));
        }
        LocalDate startDate = null;
        if (startDateRaw != null && !startDateRaw.isBlank()) {
            try {
                startDate = LocalDate.parse(startDateRaw);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "startDate must be an ISO-8601 date"));
            }
        }
        try {
            JobOffer offer = offerService.sendOffer(applicationId, title, salaryOffered, startDate, payload.get("additionalTerms"));
            return ResponseEntity.ok(offer);
        } catch (OfferService.ApplicationNotFoundException | OfferService.OfferAlreadyAcceptedException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/api/applications/{applicationId}/offer")
    public ResponseEntity<?> get(@PathVariable Long applicationId) {
        try {
            return ResponseEntity.ok(offerService.getOffer(applicationId));
        } catch (OfferService.OfferNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping("/api/applications/{applicationId}/offer/accept")
    public ResponseEntity<?> accept(@PathVariable Long applicationId, @RequestBody Map<String, String> payload,
                                     HttpServletRequest request) {
        String signatureName = payload.get("signatureName");
        if (signatureName == null || signatureName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "signatureName is required"));
        }
        try {
            JobOffer offer = offerService.acceptOffer(applicationId, callingUser(request).getId(),
                signatureName.trim(), request.getRemoteAddr());
            return ResponseEntity.ok(offer);
        } catch (OfferService.ApplicationNotFoundException | OfferService.OfferNotFoundException
                 | OfferService.OfferNotPendingException | OfferService.NotTheCandidateException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping("/api/applications/{applicationId}/offer/decline")
    public ResponseEntity<?> decline(@PathVariable Long applicationId, @RequestBody Map<String, String> payload,
                                      HttpServletRequest request) {
        try {
            JobOffer offer = offerService.declineOffer(applicationId, callingUser(request).getId(), payload.get("reason"));
            return ResponseEntity.ok(offer);
        } catch (OfferService.ApplicationNotFoundException | OfferService.OfferNotFoundException
                 | OfferService.OfferNotPendingException | OfferService.NotTheCandidateException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping("/api/applications/{applicationId}/offer/withdraw")
    public ResponseEntity<?> withdraw(@PathVariable Long applicationId) {
        try {
            JobOffer offer = offerService.withdrawOffer(applicationId);
            return ResponseEntity.ok(offer);
        } catch (OfferService.OfferNotFoundException | OfferService.OfferAlreadyAcceptedException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
