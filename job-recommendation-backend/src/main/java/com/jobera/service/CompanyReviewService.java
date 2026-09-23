package com.jobera.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jobera.entity.CompanyReview;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.CompanyReviewRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.UserRepository;

@Service
public class CompanyReviewService {

    public static class ReviewException extends RuntimeException {
        public ReviewException(String message) {
            super(message);
        }
    }

    private final CompanyReviewRepository companyReviewRepository;
    private final RecruiterRepository recruiterRepository;
    private final UserRepository userRepository;

    public CompanyReviewService(CompanyReviewRepository companyReviewRepository,
                                 RecruiterRepository recruiterRepository,
                                 UserRepository userRepository) {
        this.companyReviewRepository = companyReviewRepository;
        this.recruiterRepository = recruiterRepository;
        this.userRepository = userRepository;
    }

    /** New or edited reviews always (re-)enter the moderation queue: unlike a job posting's
     *  fields, the review text itself IS the thing that was moderated, so any change to it
     *  hasn't been reviewed yet. */
    public CompanyReview submitReview(Long userId, Long recruiterId, Integer rating, String comment) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new ReviewException("Rating must be between 1 and 5");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ReviewException("User not found: " + userId));
        Recruiter recruiter = recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new ReviewException("Company not found: " + recruiterId));

        CompanyReview review = companyReviewRepository.findByUserIdAndRecruiterId(userId, recruiterId)
            .orElseGet(() -> new CompanyReview(user, recruiter, rating, comment));
        review.setRating(rating);
        review.setComment(comment);
        review.setModerationStatus(CompanyReview.MODERATION_PENDING);
        review.setRejectionReason(null);

        return companyReviewRepository.save(review);
    }

    public Optional<CompanyReview> getMyReview(Long userId, Long recruiterId) {
        return companyReviewRepository.findByUserIdAndRecruiterId(userId, recruiterId);
    }

    /** Approved reviews for a company, plus the average rating and count - what the public
     *  company profile page shows. */
    public Map<String, Object> getPublicReviews(Long recruiterId) {
        List<CompanyReview> approved = companyReviewRepository
            .findByRecruiterIdAndModerationStatusOrderByCreatedAtDesc(recruiterId, CompanyReview.MODERATION_APPROVED);

        double average = approved.stream().mapToInt(CompanyReview::getRating).average().orElse(0.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("averageRating", Math.round(average * 10.0) / 10.0);
        result.put("totalReviews", approved.size());
        result.put("reviews", approved.stream().map(this::toPublicView).toList());
        return result;
    }

    private Map<String, Object> toPublicView(CompanyReview review) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", review.getId());
        view.put("rating", review.getRating());
        view.put("comment", review.getComment());
        view.put("createdAt", review.getCreatedAt());
        // The reviewer's identity is deliberately left off the public response - an honest
        // review shouldn't cost a candidate their anonymity from the company they're reviewing.
        return view;
    }

    public List<CompanyReview> getPendingReviews() {
        return companyReviewRepository.findByModerationStatusOrderByCreatedAtDesc(CompanyReview.MODERATION_PENDING);
    }

    public CompanyReview approveReview(Long reviewId) {
        CompanyReview review = companyReviewRepository.findById(reviewId)
            .orElseThrow(() -> new ReviewException("Review not found: " + reviewId));
        review.setModerationStatus(CompanyReview.MODERATION_APPROVED);
        review.setRejectionReason(null);
        return companyReviewRepository.save(review);
    }

    public CompanyReview rejectReview(Long reviewId, String reason) {
        CompanyReview review = companyReviewRepository.findById(reviewId)
            .orElseThrow(() -> new ReviewException("Review not found: " + reviewId));
        review.setModerationStatus(CompanyReview.MODERATION_REJECTED);
        review.setRejectionReason(reason);
        return companyReviewRepository.save(review);
    }
}
