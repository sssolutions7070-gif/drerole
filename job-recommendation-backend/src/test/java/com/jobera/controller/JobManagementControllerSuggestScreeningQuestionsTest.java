package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.jobera.service.AiInterviewClient;
import com.jobera.service.JobPostingService;
import com.jobera.service.RecruiterService;

/** Only the new suggest-screening-questions endpoint - same scoping rationale as the other
 *  *Test files that extend this controller with no prior dedicated coverage of the new path. */
@ExtendWith(MockitoExtension.class)
class JobManagementControllerSuggestScreeningQuestionsTest {

    @Mock JobPostingService jobPostingService;
    @Mock RecruiterService recruiterService;
    @Mock AiInterviewClient aiInterviewClient;

    JobManagementController controller;

    @BeforeEach
    void setUp() {
        controller = new JobManagementController(jobPostingService, recruiterService, aiInterviewClient);
    }

    @Test
    void returnsTheSuggestedQuestionsFromTheAiClient() {
        List<Map<String, Object>> suggested = List.of(
            Map.of("questionText", "Are you authorized to work in this country?", "expectedAnswer", true, "knockout", true));
        when(aiInterviewClient.generateScreeningQuestions("Backend Engineer", "Build APIs", "Java, Spring", 4))
            .thenReturn(suggested);

        ResponseEntity<Map<String, Object>> response = controller.suggestScreeningQuestions(Map.of(
            "title", "Backend Engineer", "description", "Build APIs", "requiredSkills", "Java, Spring"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(suggested, response.getBody().get("screeningQuestions"));
    }

    @Test
    void returnsBadRequestWhenTheAiClientThrows() {
        when(aiInterviewClient.generateScreeningQuestions(null, null, null, 4))
            .thenThrow(new RuntimeException("boom"));

        ResponseEntity<Map<String, Object>> response = controller.suggestScreeningQuestions(Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
