package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.jobera.service.AiInterviewClient;
import com.jobera.service.JobPostingService;

@ExtendWith(MockitoExtension.class)
class ResumeMatchingControllerTest {

    @Mock
    JobPostingService jobPostingService;

    @Mock
    AiInterviewClient aiInterviewClient;

    ResumeMatchingController controller;

    @BeforeEach
    void setUp() {
        controller = new ResumeMatchingController(jobPostingService, aiInterviewClient);
    }

    @Test
    void tailorResumeForJobCallsAiClient() {
        Map<String, Object> mockAiResult = Map.of(
            "targetJob", "Backend Engineer",
            "currentScore", 60,
            "projectedScore", 85,
            "boostSummary", "Your match for Backend Engineer is 60%. Adding Docker would boost your match to 85%.",
            "missingSkillRecommendations", List.of(Map.of("skill", "Docker")),
            "bulletPointSuggestions", List.of(Map.of("enhancedBullet", "Built microservices using Docker"))
        );

        when(aiInterviewClient.generateResumeTailoring(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(mockAiResult);

        Map<String, Object> request = Map.of(
            "jobTitle", "Backend Engineer",
            "jobDescription", "Build distributed Java backend services",
            "userSkills", List.of("Java", "Spring"),
            "missingSkills", List.of("Docker"),
            "currentMatchScore", 60
        );

        ResponseEntity<Map<String, Object>> response = controller.tailorResumeForJob(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Backend Engineer", response.getBody().get("targetJob"));
        assertEquals(85, response.getBody().get("projectedScore"));
    }

    @Test
    void tailorResumeForJobHandlesFallbackWhenClientNull() {
        ResumeMatchingController controllerNoAi = new ResumeMatchingController(jobPostingService);

        Map<String, Object> request = Map.of(
            "jobTitle", "Full Stack Engineer",
            "jobDescription", "Node and React with Docker",
            "userSkills", List.of("React"),
            "missingSkills", List.of("Docker", "Node"),
            "currentMatchScore", 55
        );

        ResponseEntity<Map<String, Object>> response = controllerNoAi.tailorResumeForJob(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("boostSummary"));
        assertTrue(response.getBody().containsKey("bulletPointSuggestions"));
    }

    @Test
    void getCareerRoadmapCallsAiClient() {
        Map<String, Object> mockRoadmap = Map.of(
            "targetJob", "Cloud Architect",
            "currentScore", 65,
            "projectedScore", 90,
            "estimatedWeeks", 4,
            "milestones", List.of(Map.of("week", 1, "title", "Core Foundations"))
        );

        when(aiInterviewClient.generateCareerRoadmap(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(mockRoadmap);

        Map<String, Object> request = Map.of(
            "jobTitle", "Cloud Architect",
            "jobDescription", "AWS, Terraform, Kubernetes",
            "userSkills", List.of("Linux", "Python"),
            "missingSkills", List.of("AWS", "Terraform"),
            "currentMatchScore", 65
        );

        ResponseEntity<Map<String, Object>> response = controller.getCareerRoadmap(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Cloud Architect", response.getBody().get("targetJob"));
        assertEquals(90, response.getBody().get("projectedScore"));
        assertEquals(4, response.getBody().get("estimatedWeeks"));
    }

    @Test
    void getCareerRoadmapHandlesFallbackWhenClientNull() {
        ResumeMatchingController controllerNoAi = new ResumeMatchingController(jobPostingService);

        Map<String, Object> request = Map.of(
            "jobTitle", "DevOps Engineer",
            "jobDescription", "CI/CD and Kubernetes",
            "userSkills", List.of("Git"),
            "missingSkills", List.of("Kubernetes", "Docker"),
            "currentMatchScore", 50
        );

        ResponseEntity<Map<String, Object>> response = controllerNoAi.getCareerRoadmap(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("milestones"));
        assertTrue(response.getBody().containsKey("capstoneProject"));
    }
}
