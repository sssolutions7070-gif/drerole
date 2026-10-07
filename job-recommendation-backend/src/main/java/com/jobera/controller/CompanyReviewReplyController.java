package com.jobera.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.entity.CompanyReview;
import com.jobera.service.CompanyReviewService;

/** The reviewed company replying to one of its own public reviews. Authorization is the existing
 *  AccessPolicy ownsCompanyReview check (recruiterId in the path must own the review itself, not
 *  just any recruiter record), with a second ownership check in the service as defense in depth. */
@RestController
@RequestMapping("/api/recruiters/{recruiterId}/company-reviews")
public class CompanyReviewReplyController {

    private static final String ERROR_KEY = "error";

    private final CompanyReviewService companyReviewService;

    public CompanyReviewReplyController(CompanyReviewService companyReviewService) {
        this.companyReviewService = companyReviewService;
    }

    @PostMapping("/{reviewId}/reply")
    public ResponseEntity<?> reply(@PathVariable Long recruiterId, @PathVariable Long reviewId,
                                    @RequestBody Map<String, String> payload) {
        try {
            CompanyReview review = companyReviewService.replyToReview(reviewId, recruiterId, payload.get("reply"));
            return ResponseEntity.ok(Map.of(
                "message", "Reply posted",
                "recruiterReply", review.getRecruiterReply()
            ));
        } catch (CompanyReviewService.NotTheReviewedCompanyException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (CompanyReviewService.ReviewException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
