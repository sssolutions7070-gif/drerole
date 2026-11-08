package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.Application;
import com.jobera.repository.ApplicationRepository;
import com.jobera.service.JobPostingService;
import com.jobera.service.JobRecommendationService;

/** Only the screeningAnswers wire-format glue and the autoRejected response flag - the rest of
 *  this controller has no prior dedicated test file to extend. */
@ExtendWith(MockitoExtension.class)
class JobControllerScreeningAnswersTest {

    @Mock JobPostingService jobPostingService;
    @Mock JobRecommendationService recommendationService;
    @Mock ApplicationRepository applicationRepository;

    JobController controller;

    @BeforeEach
    void setUp() {
        controller = new JobController(jobPostingService, recommendationService, applicationRepository);
    }

    private Application application(long id, String status) {
        Application a = new Application();
        a.setId(id);
        a.setStatus(status);
        a.setMatchPercentage(42.0);
        return a;
    }

    @Test
    void reportsAutoRejectedTrueAndAClearMessageWhenTheServiceRejectsOnSubmit() {
        when(jobPostingService.applyForJobWithResume(
            eq(5L), eq(1L), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(),
            isNull(), anyString()
        )).thenReturn(application(900L, "REJECTED"));

        Map<String, Object> body = Map.of(
            "coverLetter", "cover", "applicantSkills", "[]", "applicantExperience", 2,
            "applicantEducation", "BS", "resumeFilePath", "r.pdf", "resumeParsedText", "text",
            "screeningAnswers", List.of(Map.of("questionText", "Authorized?", "answer", false))
        );

        ResponseEntity<Map<String, Object>> response = controller.applyForJobWithResume(1L, 5L, body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().get("autoRejected"));
        assertEquals(900L, response.getBody().get("applicationId"));
    }

    @Test
    void reportsAutoRejectedFalseOnAnOrdinarySubmission() {
        when(jobPostingService.applyForJobWithResume(
            eq(5L), eq(1L), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(),
            isNull(), isNull()
        )).thenReturn(application(901L, "APPLICATION_SUBMITTED"));

        Map<String, Object> body = Map.of(
            "coverLetter", "cover", "applicantSkills", "[]", "applicantExperience", 2,
            "applicantEducation", "BS", "resumeFilePath", "r.pdf", "resumeParsedText", "text"
        );

        ResponseEntity<Map<String, Object>> response = controller.applyForJobWithResume(1L, 5L, body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(false, response.getBody().get("autoRejected"));
    }
}
