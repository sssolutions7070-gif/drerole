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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.repository.ApplicationRepository;
import com.jobera.service.AiInterviewClient;
import com.jobera.service.JobPostingService;

@ExtendWith(MockitoExtension.class)
class ResumeMatchingControllerTest {

    @Mock
    JobPostingService jobPostingService;

    @Mock
    AiInterviewClient aiInterviewClient;

    @Mock
    ApplicationRepository applicationRepository;

    ResumeMatchingController controller;

    @BeforeEach
    void setUp() {
        controller = new ResumeMatchingController(jobPostingService, aiInterviewClient, applicationRepository);
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
        ResumeMatchingController controllerNoAi = new ResumeMatchingController(jobPostingService, applicationRepository);

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
        ResumeMatchingController controllerNoAi = new ResumeMatchingController(jobPostingService, applicationRepository);

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

    private Application applicationWithJob(Double matchPercentage, String matchedSkillsJson,
                                             String missingSkillsJson, Integer applicantExperience,
                                             String applicantEducation, String jobExperienceLevel) {
        Application application = new Application();
        application.setMatchPercentage(matchPercentage);
        application.setMatchedSkills(matchedSkillsJson);
        application.setMissingSkills(missingSkillsJson);
        application.setApplicantExperience(applicantExperience);
        application.setApplicantEducation(applicantEducation);

        JobPosting job = new JobPosting();
        job.setExperienceLevel(jobExperienceLevel);
        application.setJobPosting(job);
        return application;
    }

    @Test
    void resumeAnalysisReturnsTheApplicationsOwnRecordedDataNotAFixedMockValue() {
        Application application = applicationWithJob(
            42.0, "[\"React\"]", "[\"Docker\", \"AWS\"]", 3, "B.Tech Computer Science", "Mid-level");
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(application));

        ResponseEntity<Map<String, Object>> response = controller.getResumeAnalysis(5L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(42.0, response.getBody().get("matchPercentage"));
        assertEquals(List.of("React"), response.getBody().get("strengths"));
        assertEquals(List.of("Docker", "AWS"), response.getBody().get("improvements"));
        assertEquals("B.Tech Computer Science", response.getBody().get("educationMatch"));
    }

    @Test
    void differentApplicationsGetDifferentAnalysisProvingItIsNoLongerAFixedMock() {
        Application applicationA = applicationWithJob(20.0, "[]", "[\"Java\"]", 0, null, "Entry");
        Application applicationB = applicationWithJob(95.0, "[\"Java\", \"Spring\"]", "[]", 10, "MBA", "Senior");
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(applicationA));
        when(applicationRepository.findById(2L)).thenReturn(Optional.of(applicationB));

        Map<String, Object> analysisA = controller.getResumeAnalysis(1L).getBody();
        Map<String, Object> analysisB = controller.getResumeAnalysis(2L).getBody();

        assertEquals(20.0, analysisA.get("matchPercentage"));
        assertEquals(95.0, analysisB.get("matchPercentage"));
        assertTrue(!analysisA.get("matchPercentage").equals(analysisB.get("matchPercentage")));
    }

    @Test
    void experienceMatchComparesTheApplicantsOwnYearsAgainstTheJobsExperienceLevel() {
        Application underqualified = applicationWithJob(50.0, "[]", "[]", 1, "BA", "Senior");
        Application overqualified = applicationWithJob(50.0, "[]", "[]", 10, "BA", "Senior");
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(underqualified));
        when(applicationRepository.findById(2L)).thenReturn(Optional.of(overqualified));

        String underqualifiedMatch = (String) controller.getResumeAnalysis(1L).getBody().get("experienceMatch");
        String overqualifiedMatch = (String) controller.getResumeAnalysis(2L).getBody().get("experienceMatch");

        assertTrue(underqualifiedMatch.startsWith("Below requirement"), underqualifiedMatch);
        assertTrue(overqualifiedMatch.startsWith("Exceeds requirement"), overqualifiedMatch);
    }

    @Test
    void resumeAnalysisReturns404ForAMissingApplicationInsteadOfFabricatingData() {
        when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.getResumeAnalysis(999L);

        assertEquals(404, response.getStatusCode().value());
    }
}
