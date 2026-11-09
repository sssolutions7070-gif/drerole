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

import com.jobera.service.ApplicationStatusService;
import com.jobera.service.CandidateAnalyticsService;
import com.jobera.service.JobPostingService;

/** Only the new rejection-explanation endpoint - same scoping rationale as
 *  ApplicationStatusControllerWithdrawTest. */
@ExtendWith(MockitoExtension.class)
class ApplicationStatusControllerRejectionExplanationTest {

    @Mock ApplicationStatusService applicationStatusService;
    @Mock JobPostingService jobPostingService;
    @Mock CandidateAnalyticsService candidateAnalyticsService;

    ApplicationStatusController controller;

    @BeforeEach
    void setUp() {
        controller = new ApplicationStatusController(applicationStatusService, jobPostingService, candidateAnalyticsService);
    }

    @Test
    void returnsTheExplanationFromTheService() {
        when(applicationStatusService.getRejectionExplanation(5L))
            .thenReturn(Map.of("explanation", "The role needed stronger Kubernetes experience."));

        ResponseEntity<Map<String, Object>> response = controller.getRejectionExplanation(5L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("The role needed stronger Kubernetes experience.", response.getBody().get("explanation"));
    }

    @Test
    void anApplicationThatWasNeverRejectedFailsWithABadRequest() {
        when(applicationStatusService.getRejectionExplanation(5L))
            .thenThrow(new ApplicationStatusService.ApplicationNotRejectedException());

        ResponseEntity<Map<String, Object>> response = controller.getRejectionExplanation(5L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void anUnknownApplicationFailsWithABadRequestRatherThanA500() {
        when(applicationStatusService.getRejectionExplanation(404L))
            .thenThrow(new ApplicationStatusService.ApplicationStatusException("Application not found with ID: 404"));

        ResponseEntity<Map<String, Object>> response = controller.getRejectionExplanation(404L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
