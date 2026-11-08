package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.JobPosting;
import com.jobera.service.JobPostingService;
import com.jobera.service.RecruiterService;

/** Only the screeningQuestions wire-format glue (a real nested JSON array in the request,
 *  re-serialized to the JSON-string the entity column stores) - the rest of this controller has
 *  no prior dedicated test file to extend. */
@ExtendWith(MockitoExtension.class)
class JobManagementControllerScreeningQuestionsTest {

    @Mock JobPostingService jobPostingService;
    @Mock RecruiterService recruiterService;

    JobManagementController controller;

    @BeforeEach
    void setUp() {
        controller = new JobManagementController(jobPostingService, recruiterService);
    }

    @Test
    void createJobSerializesTheNestedScreeningQuestionsArrayToAJsonString() {
        JobPosting saved = new JobPosting("Backend Engineer", "Acme", "desc");
        saved.setId(1L);
        when(jobPostingService.createJobPosting(
            eq(10L), anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(),
            isNull(), anyString()
        )).thenReturn(saved);

        Map<String, Object> request = Map.of(
            "recruiterId", 10,
            "title", "Backend Engineer",
            "company", "Acme",
            "description", "desc",
            "screeningQuestions", List.of(Map.of("questionText", "Authorized to work?", "expectedAnswer", true, "knockout", true))
        );

        ResponseEntity<Map<String, Object>> response = controller.createJob(request);

        assertEquals(200, response.getStatusCode().value());
        verify(jobPostingService).createJobPosting(
            eq(10L), anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(),
            isNull(),
            org.mockito.ArgumentMatchers.argThat(json -> json != null && json.contains("Authorized to work?"))
        );
    }

    @Test
    void createJobWithNoScreeningQuestionsPassesNullThrough() {
        JobPosting saved = new JobPosting("Backend Engineer", "Acme", "desc");
        saved.setId(1L);
        when(jobPostingService.createJobPosting(
            eq(10L), anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(),
            isNull(), isNull()
        )).thenReturn(saved);

        Map<String, Object> request = Map.of(
            "recruiterId", 10, "title", "Backend Engineer", "company", "Acme", "description", "desc"
        );

        ResponseEntity<Map<String, Object>> response = controller.createJob(request);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(((Map<?, ?>) response.getBody().get("job")).containsKey("screeningQuestions"));
    }
}
