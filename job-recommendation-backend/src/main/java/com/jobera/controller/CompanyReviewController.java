package com.jobera.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.entity.CompanyReview;
import com.jobera.service.CompanyReviewService;

/** Writing and checking your own company review. Already covered by the existing
 *  "/api/users/{userId}/**" self-ownership rule in AccessPolicy - no new rule needed. */
@RestController
@RequestMapping("/api/users/{userId}/company-reviews")
public class CompanyReviewController {

    private static final String ERROR_KEY = "error";

    private final CompanyReviewService companyReviewService;

    public CompanyReviewController(CompanyReviewService companyReviewService) {
        this.companyReviewService = companyReviewService;
    }

    @PostMapping("/{recruiterId}")
    public ResponseEntity<?> submitReview(@PathVariable Long userId, @PathVariable Long recruiterId,
                                           @RequestBody Map<String, Object> payload) {
        try {
            Integer rating = payload.get("rating") == null ? null : Integer.valueOf(payload.get("rating").toString());
            String comment = (String) payload.get("comment");
            CompanyReview review = companyReviewService.submitReview(userId, recruiterId, rating, comment);
            return ResponseEntity.ok(Map.of(
                "message", "Review submitted for approval",
                "moderationStatus", review.getModerationStatus()
            ));
        } catch (CompanyReviewService.ReviewException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/{recruiterId}")
    public ResponseEntity<?> getMyReview(@PathVariable Long userId, @PathVariable Long recruiterId) {
        // Map.of() throws on any null value (rejectionReason and comment are both legitimately
        // null for most reviews), so this needs a plain mutable map, not Map.of().
        return companyReviewService.getMyReview(userId, recruiterId)
            .<ResponseEntity<?>>map(review -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("rating", review.getRating());
                body.put("comment", review.getComment());
                body.put("moderationStatus", review.getModerationStatus());
                body.put("rejectionReason", review.getRejectionReason());
                return ResponseEntity.ok(body);
            })
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
