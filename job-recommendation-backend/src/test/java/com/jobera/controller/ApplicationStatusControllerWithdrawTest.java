package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.Application;
import com.jobera.entity.User;
import com.jobera.service.ApplicationStatusService;
import com.jobera.service.CandidateAnalyticsService;
import com.jobera.service.JobPostingService;

import jakarta.servlet.http.HttpServletRequest;

/** Only the new withdraw endpoint - ApplicationStatusController itself has no existing test
 *  coverage to extend, and this is scoped to what was added, not a retrofit of the whole class. */
@ExtendWith(MockitoExtension.class)
class ApplicationStatusControllerWithdrawTest {

    @Mock ApplicationStatusService applicationStatusService;
    @Mock JobPostingService jobPostingService;
    @Mock CandidateAnalyticsService candidateAnalyticsService;

    ApplicationStatusController controller;

    @BeforeEach
    void setUp() {
        controller = new ApplicationStatusController(applicationStatusService, jobPostingService, candidateAnalyticsService);
    }

    private HttpServletRequest requestFor(long userId) {
        User user = new User();
        user.setId(userId);
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(com.jobera.config.AuthInterceptor.AUTHENTICATED_USER)).thenReturn(user);
        return request;
    }

    @Test
    void withdrawReturnsTheUpdatedApplication() {
        Application application = new Application();
        application.setId(1L);
        application.setStatus("WITHDRAWN");
        when(applicationStatusService.withdrawApplication(1L, 10L)).thenReturn(application);

        ResponseEntity<Map<String, Object>> response = controller.withdrawApplication(1L, requestFor(10L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void withdrawReturns403ForTheWrongCaller() {
        when(applicationStatusService.withdrawApplication(1L, 999L))
            .thenThrow(new ApplicationStatusService.NotTheApplicantException());

        ResponseEntity<Map<String, Object>> response = controller.withdrawApplication(1L, requestFor(999L));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void withdrawReturns400WhenTheStatusDoesNotAllowIt() {
        when(applicationStatusService.withdrawApplication(1L, 10L))
            .thenThrow(new ApplicationStatusService.CannotWithdrawException("OFFER_ACCEPTED"));

        ResponseEntity<Map<String, Object>> response = controller.withdrawApplication(1L, requestFor(10L));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void sendFollowUpReturnsOkWithUpdatedApplication() {
        Application application = new Application();
        application.setId(1L);
        application.setStatus("SHORTLISTED");
        application.setFollowUpCount(1);
        when(applicationStatusService.sendFollowUp(1L, "INTERVIEW_REMINDER", "Please confirm time")).thenReturn(application);

        ResponseEntity<Map<String, Object>> response = controller.sendFollowUp(
            1L, Map.of("templateType", "INTERVIEW_REMINDER", "customMessage", "Please confirm time"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Follow-up nudge sent successfully", response.getBody().get("message"));
        assertEquals(1, response.getBody().get("followUpCount"));
    }

    @Test
    void sendFollowUpReturnsBadRequestOnServiceError() {
        when(applicationStatusService.sendFollowUp(999L, "CHECK_IN", null))
            .thenThrow(new ApplicationStatusService.ApplicationStatusException("Application not found"));

        ResponseEntity<Map<String, Object>> response = controller.sendFollowUp(
            999L, Map.of("templateType", "CHECK_IN"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
