package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.jobera.entity.JobPosting;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.UserRepository;
import com.jobera.service.AdminAuditService;
import com.jobera.service.CompanyReviewService;
import com.jobera.service.FraudDetectionService;
import com.jobera.service.InterviewExperienceService;
import com.jobera.service.JobPostingService;
import com.jobera.service.NotificationService;

/** Only the new fraud-scores endpoint - this controller's other routes are covered by
 *  AdminControllerTest, which uses the older constructor overload. */
@ExtendWith(MockitoExtension.class)
class AdminControllerFraudScoresTest {

    @Mock UserRepository userRepository;
    @Mock RecruiterRepository recruiterRepository;
    @Mock JobPostingRepository jobPostingRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock ResumeRepository resumeRepository;
    @Mock AdminAuditService adminAuditService;
    @Mock JobPostingService jobPostingService;
    @Mock NotificationService notificationService;
    @Mock CompanyReviewService companyReviewService;
    @Mock InterviewExperienceService interviewExperienceService;

    AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(userRepository, recruiterRepository, jobPostingRepository,
            applicationRepository, resumeRepository, adminAuditService, jobPostingService, notificationService,
            companyReviewService, interviewExperienceService, new FraudDetectionService());
    }

    @Test
    void scoresEveryPendingJobKeyedById() {
        JobPosting legit = new JobPosting();
        legit.setId(1L);
        legit.setTitle("Backend Engineer");
        legit.setDescription("Build APIs");
        JobPosting scam = new JobPosting();
        scam.setId(2L);
        scam.setTitle("Easy money");
        scam.setDescription("Pay a registration fee to start");
        when(jobPostingService.getPendingJobPostings()).thenReturn(List.of(legit, scam));

        ResponseEntity<Map<Long, Map<String, Object>>> response = controller.getPendingJobFraudScores();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().get(1L).get("riskScore"));
        assertTrue((Integer) response.getBody().get(2L).get("riskScore") > 0);
    }
}
