package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.CompanyReview;
import com.jobera.service.CompanyReviewService;

@ExtendWith(MockitoExtension.class)
class CompanyReviewReplyControllerTest {

    @Mock CompanyReviewService companyReviewService;

    CompanyReviewReplyController controller;

    @BeforeEach
    void setUp() {
        controller = new CompanyReviewReplyController(companyReviewService);
    }

    @Test
    void replyReturnsOkWithTheReplyText() {
        CompanyReview review = new CompanyReview();
        review.setRecruiterReply("Thanks for the feedback!");
        when(companyReviewService.replyToReview(5L, 10L, "Thanks for the feedback!")).thenReturn(review);

        ResponseEntity<?> response = controller.reply(10L, 5L, Map.of("reply", "Thanks for the feedback!"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Thanks for the feedback!", ((Map<?, ?>) response.getBody()).get("recruiterReply"));
    }

    @Test
    void replyReturns403ForTheWrongRecruiter() {
        when(companyReviewService.replyToReview(5L, 999L, "x"))
            .thenThrow(new CompanyReviewService.NotTheReviewedCompanyException());

        ResponseEntity<?> response = controller.reply(999L, 5L, Map.of("reply", "x"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void replyReturns400WhenTheReviewIsNotYetPublished() {
        when(companyReviewService.replyToReview(5L, 10L, "x"))
            .thenThrow(new CompanyReviewService.ReviewException("Only a published review can be replied to"));

        ResponseEntity<?> response = controller.reply(10L, 5L, Map.of("reply", "x"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
