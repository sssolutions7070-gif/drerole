package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.User;
import com.jobera.service.JobPostingService;
import com.jobera.service.RecruiterService;

/** Only the duplicate and CSV-export endpoints added to JobManagementController - the rest of the
 *  controller has no existing test coverage to extend, and this is scoped to what was added. */
@ExtendWith(MockitoExtension.class)
class JobManagementControllerDuplicateExportTest {

    @Mock JobPostingService jobPostingService;
    @Mock RecruiterService recruiterService;

    JobManagementController controller;

    @BeforeEach
    void setUp() {
        controller = new JobManagementController(jobPostingService, recruiterService);
    }

    @Test
    void duplicateReturnsTheClonedJob() {
        JobPosting copy = new JobPosting();
        copy.setId(2L);
        copy.setTitle("Backend Developer (Copy)");
        copy.setModerationStatus(JobPosting.MODERATION_PENDING);
        when(jobPostingService.duplicateJobPosting(1L, 10L)).thenReturn(copy);

        ResponseEntity<Map<String, Object>> response = controller.duplicateJob(1L, Map.of("recruiterId", 10));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
        assertEquals("Backend Developer (Copy)", job.get("title"));
    }

    @Test
    void duplicateReturnsBadRequestWhenNotAuthorized() {
        when(jobPostingService.duplicateJobPosting(1L, 999L))
            .thenThrow(new JobPostingService.JobPostingException("You are not authorized to duplicate this job posting"));

        ResponseEntity<Map<String, Object>> response = controller.duplicateJob(1L, Map.of("recruiterId", 999));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void exportReturnsACsvWithOneRowPerApplicant() {
        User user = new User();
        user.setFullName("Bob Candidate");
        user.setEmail("bob@example.com");
        user.setPhone("555-1234");
        Application application = new Application();
        application.setUser(user);
        application.setStatus("SHORTLISTED");
        application.setMatchPercentage(85.0);
        application.setApplicantSkills("Java, Spring");
        when(jobPostingService.getApplicationsForJob(1L, 10L)).thenReturn(List.of(application));

        ResponseEntity<byte[]> response = controller.exportApplications(1L, 10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("text/csv", response.getHeaders().getContentType().toString());
        String body = new String(response.getBody());
        assertTrue(body.contains("Name,Email,Phone,Status,Match %,Applied Date,Skills"));
        assertTrue(body.contains("Bob Candidate"));
        assertTrue(body.contains("bob@example.com"));
        assertTrue(body.contains("SHORTLISTED"));
    }

    @Test
    void exportQuotesFieldsThatContainCommas() {
        User user = new User();
        user.setFullName("Candidate, Jr.");
        user.setEmail("c@example.com");
        Application application = new Application();
        application.setUser(user);
        application.setStatus("APPLICATION_SUBMITTED");
        when(jobPostingService.getApplicationsForJob(1L, 10L)).thenReturn(List.of(application));

        ResponseEntity<byte[]> response = controller.exportApplications(1L, 10L);

        String body = new String(response.getBody());
        assertTrue(body.contains("\"Candidate, Jr.\""));
    }

    @Test
    void exportReturnsBadRequestOnServiceError() {
        when(jobPostingService.getApplicationsForJob(anyLong(), anyLong()))
            .thenThrow(new JobPostingService.JobPostingException("Job posting not found"));

        ResponseEntity<byte[]> response = controller.exportApplications(999L, 10L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
