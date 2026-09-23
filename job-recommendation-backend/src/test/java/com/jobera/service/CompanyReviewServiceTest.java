package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.CompanyReview;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.CompanyReviewRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CompanyReviewServiceTest {

    @Mock CompanyReviewRepository companyReviewRepository;
    @Mock RecruiterRepository recruiterRepository;
    @Mock UserRepository userRepository;

    CompanyReviewService service;
    User user;
    Recruiter recruiter;

    @BeforeEach
    void setUp() {
        service = new CompanyReviewService(companyReviewRepository, recruiterRepository, userRepository);
        user = new User("candidate@example.com", "hash", "Candidate");
        user.setId(1L);
        recruiter = new Recruiter();
        recruiter.setId(10L);
        recruiter.setCompanyName("Acme");
    }

    @Test
    void submittingANewReviewStartsPending() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(recruiterRepository.findById(10L)).thenReturn(Optional.of(recruiter));
        when(companyReviewRepository.findByUserIdAndRecruiterId(1L, 10L)).thenReturn(Optional.empty());
        when(companyReviewRepository.save(any(CompanyReview.class))).thenAnswer(inv -> inv.getArgument(0));

        CompanyReview review = service.submitReview(1L, 10L, 5, "Great place to work");

        assertEquals(CompanyReview.MODERATION_PENDING, review.getModerationStatus());
        assertEquals(5, review.getRating());
    }

    @Test
    void editingAnExistingReviewResetsItToPendingRegardlessOfPriorStatus() {
        CompanyReview existing = new CompanyReview(user, recruiter, 3, "It was okay");
        existing.setModerationStatus(CompanyReview.MODERATION_APPROVED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(recruiterRepository.findById(10L)).thenReturn(Optional.of(recruiter));
        when(companyReviewRepository.findByUserIdAndRecruiterId(1L, 10L)).thenReturn(Optional.of(existing));
        when(companyReviewRepository.save(any(CompanyReview.class))).thenAnswer(inv -> inv.getArgument(0));

        CompanyReview updated = service.submitReview(1L, 10L, 5, "Actually it's great");

        assertEquals(CompanyReview.MODERATION_PENDING, updated.getModerationStatus());
        assertEquals(5, updated.getRating());
        assertEquals("Actually it's great", updated.getComment());
    }

    @Test
    void rejectsRatingsOutsideOneToFive() {
        assertThrows(CompanyReviewService.ReviewException.class, () -> service.submitReview(1L, 10L, 0, "x"));
        assertThrows(CompanyReviewService.ReviewException.class, () -> service.submitReview(1L, 10L, 6, "x"));
        assertThrows(CompanyReviewService.ReviewException.class, () -> service.submitReview(1L, 10L, null, "x"));
    }

    @Test
    void publicReviewsOnlyIncludeApprovedOnesAndComputeTheAverage() {
        CompanyReview a = new CompanyReview(user, recruiter, 4, "Good");
        CompanyReview b = new CompanyReview(user, recruiter, 2, "Meh");
        when(companyReviewRepository.findByRecruiterIdAndModerationStatusOrderByCreatedAtDesc(10L, "APPROVED"))
            .thenReturn(List.of(a, b));

        Map<String, Object> result = service.getPublicReviews(10L);

        assertEquals(3.0, result.get("averageRating"));
        assertEquals(2, result.get("totalReviews"));
    }

    @Test
    void publicReviewViewDoesNotExposeTheReviewerIdentity() {
        CompanyReview review = new CompanyReview(user, recruiter, 4, "Good");
        when(companyReviewRepository.findByRecruiterIdAndModerationStatusOrderByCreatedAtDesc(10L, "APPROVED"))
            .thenReturn(List.of(review));

        Map<String, Object> result = service.getPublicReviews(10L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reviews = (List<Map<String, Object>>) result.get("reviews");
        assertFalse(reviews.get(0).containsKey("userId"));
        assertFalse(reviews.get(0).containsKey("user"));
    }

    @Test
    void handlesACompanyWithNoReviewsYet() {
        when(companyReviewRepository.findByRecruiterIdAndModerationStatusOrderByCreatedAtDesc(10L, "APPROVED"))
            .thenReturn(List.of());

        Map<String, Object> result = service.getPublicReviews(10L);

        assertEquals(0.0, result.get("averageRating"));
        assertEquals(0, result.get("totalReviews"));
    }

    @Test
    void approvingAReviewClearsAnyPriorRejectionReason() {
        CompanyReview review = new CompanyReview(user, recruiter, 3, "x");
        review.setModerationStatus(CompanyReview.MODERATION_REJECTED);
        review.setRejectionReason("Too vague");
        when(companyReviewRepository.findById(5L)).thenReturn(Optional.of(review));
        when(companyReviewRepository.save(any(CompanyReview.class))).thenAnswer(inv -> inv.getArgument(0));

        CompanyReview approved = service.approveReview(5L);

        assertEquals(CompanyReview.MODERATION_APPROVED, approved.getModerationStatus());
        assertNull(approved.getRejectionReason());
    }

    @Test
    void rejectingAReviewRecordsTheReason() {
        CompanyReview review = new CompanyReview(user, recruiter, 1, "spam spam spam");
        when(companyReviewRepository.findById(5L)).thenReturn(Optional.of(review));
        when(companyReviewRepository.save(any(CompanyReview.class))).thenAnswer(inv -> inv.getArgument(0));

        CompanyReview rejected = service.rejectReview(5L, "Looks like spam");

        assertEquals(CompanyReview.MODERATION_REJECTED, rejected.getModerationStatus());
        assertEquals("Looks like spam", rejected.getRejectionReason());
    }
}
