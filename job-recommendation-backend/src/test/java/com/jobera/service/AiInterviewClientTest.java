package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.jobera.service.AiInterviewClient.AiInterviewException;

@ExtendWith(MockitoExtension.class)
class AiInterviewClientTest {

    @Mock RestTemplate restTemplate;

    AiInterviewClient configured;
    AiInterviewClient unconfigured;

    @BeforeEach
    void setUp() {
        configured = new AiInterviewClient(restTemplate, "sk-ant-test123", "claude-3-5-haiku-20241022");
        unconfigured = new AiInterviewClient(restTemplate, "", "claude-3-5-haiku-20241022");
    }

    private Map<String, Object> claudeResponse(String text) {
        return Map.of("content", List.of(Map.of("type", "text", "text", text)));
    }

    @Test
    void isConfiguredReflectsWhetherAnApiKeyIsSet() {
        assertTrue(configured.isConfigured());
        assertFalse(unconfigured.isConfigured());
    }

    @Test
    void generateQuestionsParsesACleanJsonArray() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(claudeResponse("[\"Tell me about a challenge you faced.\", \"How do you debug a memory leak?\"]"));

        List<String> questions = configured.generateQuestions("Backend Engineer", "Build APIs", "Java, Spring", 2);

        assertEquals(2, questions.size());
        assertEquals("Tell me about a challenge you faced.", questions.get(0));
    }

    @Test
    void generateQuestionsExtractsJsonEvenWhenWrappedInProseOrAFence() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(claudeResponse("Sure, here are the questions:\n```json\n[\"Q1?\", \"Q2?\"]\n```\nGood luck!"));

        List<String> questions = configured.generateQuestions("Backend Engineer", "Build APIs", "Java", 2);

        assertEquals(List.of("Q1?", "Q2?"), questions);
    }

    @Test
    void generateQuestionsFailsWhenNotConfigured() {
        assertThrows(AiInterviewException.class, () ->
            unconfigured.generateQuestions("Title", "Desc", "Skills", 5));
    }

    @Test
    void generateQuestionsWrapsANetworkFailure() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenThrow(new RestClientException("timeout"));

        assertThrows(AiInterviewException.class, () ->
            configured.generateQuestions("Title", "Desc", "Skills", 5));
    }

    @Test
    void generateQuestionsFailsOnUnparseableResponse() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(claudeResponse("I'm not going to give you JSON today."));

        assertThrows(AiInterviewException.class, () ->
            configured.generateQuestions("Title", "Desc", "Skills", 5));
    }

    @Test
    void evaluateAnswerParsesScoreAndFeedback() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(claudeResponse("{\"score\": 4, \"feedback\": \"Good structure, add more detail on trade-offs.\"}"));

        Map<String, Object> result = configured.evaluateAnswer("Backend Engineer", "How do you scale a database?", "Sharding.");

        assertEquals(4, result.get("score"));
        assertEquals("Good structure, add more detail on trade-offs.", result.get("feedback"));
    }

    @Test
    void evaluateAnswerFailsWhenNotConfigured() {
        assertThrows(AiInterviewException.class, () ->
            unconfigured.evaluateAnswer("Title", "Q", "A"));
    }

    @Test
    void anEmptyResponseFails() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class))).thenReturn(null);

        assertThrows(AiInterviewException.class, () ->
            configured.generateQuestions("Title", "Desc", "Skills", 5));
    }

    @Test
    void generateTechnicalQuestionsParsesClaudeResponse() {
        String json = "[{\"question\":\"What is B-tree indexing?\",\"optionA\":\"A\",\"optionB\":\"B\",\"optionC\":\"C\",\"optionD\":\"D\",\"correctAnswer\":\"A\",\"explanation\":\"B-tree details\",\"difficultyLevel\":\"Hard\"}]";
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(claudeResponse(json));

        List<Map<String, Object>> questions = configured.generateTechnicalQuestions("DB Engineer", "Design schemas", "SQL", "CSE", 1);
        assertEquals(1, questions.size());
        assertEquals("What is B-tree indexing?", questions.get(0).get("question"));
        assertEquals("A", questions.get(0).get("correctAnswer"));
    }

    @Test
    void generateTechnicalQuestionsFallsBackWhenUnconfigured() {
        List<Map<String, Object>> fallback = unconfigured.generateTechnicalQuestions("DevOps Engineer", "CI/CD", "Docker", "CSE", 5);
        assertEquals(5, fallback.size());
        assertTrue(fallback.get(0).containsKey("question"));
        assertTrue(fallback.get(0).containsKey("optionA"));
        assertTrue(fallback.get(0).containsKey("correctAnswer"));
    }

    @Test
    void generateResumeTailoringFallsBackWhenUnconfigured() {
        Map<String, Object> result = unconfigured.generateResumeTailoring(
            "Full Stack Engineer",
            "Build scalable web applications with React and Docker",
            List.of("React", "JavaScript"),
            List.of("Docker", "GraphQL"),
            65
        );

        assertNotNull(result);
        assertEquals("Full Stack Engineer", result.get("targetJob"));
        assertEquals(65, result.get("currentScore"));
        assertTrue((int) result.get("projectedScore") > 65);
        assertTrue(((String) result.get("boostSummary")).contains("boost your match"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recs = (List<Map<String, Object>>) result.get("missingSkillRecommendations");
        assertFalse(recs.isEmpty());
        assertEquals("Docker", recs.get(0).get("skill"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bullets = (List<Map<String, Object>>) result.get("bulletPointSuggestions");
        assertFalse(bullets.isEmpty());
        assertTrue(((String) bullets.get(0).get("enhancedBullet")).contains("Docker"));
    }

    @Test
    void generateResumeTailoringParsesClaudeResponse() {
        String json = "{\"targetJob\":\"Cloud Architect\",\"currentScore\":70,\"projectedScore\":92,"
            + "\"boostSummary\":\"Your match for Cloud Architect is 70%. Adding Kubernetes would boost your match to 92%.\","
            + "\"missingSkillRecommendations\":[{\"skill\":\"Kubernetes\",\"priority\":\"High Priority\",\"impactReason\":\"Key qualification\",\"actionTip\":\"Add cluster orchestration\"}],"
            + "\"bulletPointSuggestions\":[{\"category\":\"Cloud\",\"enhancedBullet\":\"Deployed multi-region Kubernetes clusters\",\"skillsHighlighted\":[\"Kubernetes\"],\"metricImpact\":\"99.99% uptime\"}],"
            + "\"keywordOptimization\":[\"Kubernetes\",\"Helm\",\"Terraform\"]}";

        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(claudeResponse(json));

        Map<String, Object> result = configured.generateResumeTailoring(
            "Cloud Architect",
            "Manage AWS infrastructure",
            List.of("AWS", "Terraform"),
            List.of("Kubernetes"),
            70
        );

        assertEquals("Cloud Architect", result.get("targetJob"));
        assertEquals(92, result.get("projectedScore"));
        assertEquals("Your match for Cloud Architect is 70%. Adding Kubernetes would boost your match to 92%.", result.get("boostSummary"));
    }

    @Test
    void generateLiveCodingProbesReturnsFallbackWhenUnconfigured() {
        String code = "function twoSum(nums, target) {\n"
            + "  for (let i = 0; i < nums.length; i++) {\n"
            + "    for (let j = i + 1; j < nums.length; j++) {\n"
            + "      if (nums[i] + nums[j] === target) return [i, j];\n"
            + "    }\n"
            + "  }\n"
            + "  return [];\n"
            + "}";

        Map<String, Object> result = unconfigured.generateLiveCodingProbes("Technical Round", "JavaScript", code);
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        Map<String, String> complexity = (Map<String, String>) result.get("estimatedComplexity");
        assertEquals("O(N²)", complexity.get("time"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> edgeCases = (List<Map<String, String>>) result.get("edgeCases");
        assertFalse(edgeCases.isEmpty());

        @SuppressWarnings("unchecked")
        List<Map<String, String>> optimizations = (List<Map<String, String>>) result.get("optimizationProbes");
        assertFalse(optimizations.isEmpty());

        @SuppressWarnings("unchecked")
        List<String> arch = (List<String>) result.get("architecturalFollowUps");
        assertFalse(arch.isEmpty());
    }

    @Test
    void generateLiveCodingProbesParsesClaudeResponseWhenConfigured() {
        String json = "{\"estimatedComplexity\":{\"time\":\"O(N)\",\"space\":\"O(N)\",\"explanation\":\"Single hash-table pass.\"},"
            + "\"strengths\":[\"Clean variable naming\",\"Early exit implemented\"],"
            + "\"edgeCases\":[{\"scenario\":\"Negative numbers\",\"probe\":\"How do negative inputs impact target sum?\"}],"
            + "\"optimizationProbes\":[{\"topic\":\"Memory efficiency\",\"question\":\"Can we avoid auxiliary map?\",\"expectedInsight\":\"Sorting tradeoff.\"}],"
            + "\"architecturalFollowUps\":[\"How to scale this across shards?\"]}";

        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(claudeResponse(json));

        Map<String, Object> result = configured.generateLiveCodingProbes("Algorithmic Round", "JavaScript", "const a = 1;");
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        Map<String, String> complexity = (Map<String, String>) result.get("estimatedComplexity");
        assertEquals("O(N)", complexity.get("time"));
        assertEquals("O(N)", complexity.get("space"));

        @SuppressWarnings("unchecked")
        List<String> strengths = (List<String>) result.get("strengths");
        assertEquals(2, strengths.size());
    }

    @Test
    void generateRecordingInsightsReturnsFallbackWhenUnconfigured() {
        Map<String, Object> result = unconfigured.generateRecordingInsights("System Design Round", "TECHNICAL", "Candidate explained sharding");
        assertNotNull(result);
        assertEquals(92, result.get("clarityScore"));
        assertEquals(138, result.get("wordsPerMinute"));
        assertTrue(result.containsKey("sentiment"));
        assertTrue(result.containsKey("technicalKeywords"));
        assertTrue(result.containsKey("keyTakeaways"));
    }

    @Test
    void generateCoverLetterReturnsTheModelsPlainTextResponseDirectly() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(claudeResponse("Dear Hiring Manager,\n\nI am excited to apply...\n\nSincerely,\nAsha"));

        Map<String, Object> result = configured.generateCoverLetter(
            "Asha Rao", "Backend Engineer", "Acme Corp", "Build APIs", List.of("Java", "Spring"));

        assertEquals("Dear Hiring Manager,\n\nI am excited to apply...\n\nSincerely,\nAsha", result.get("coverLetter"));
    }

    @Test
    void generateCoverLetterFallsBackWhenUnconfigured() {
        Map<String, Object> result = unconfigured.generateCoverLetter(
            "Asha Rao", "Backend Engineer", "Acme Corp", "Build APIs", List.of("Java", "Spring"));

        String letter = (String) result.get("coverLetter");
        assertTrue(letter.contains("Backend Engineer"));
        assertTrue(letter.contains("Acme Corp"));
        assertTrue(letter.contains("Java"));
        assertTrue(letter.contains("Asha Rao"));
    }

    @Test
    void generateCoverLetterFallsBackOnANetworkFailure() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenThrow(new RestClientException("boom"));

        Map<String, Object> result = configured.generateCoverLetter(
            "Asha Rao", "Backend Engineer", "Acme Corp", "Build APIs", List.of("Java"));

        assertNotNull(result.get("coverLetter"));
        assertTrue(((String) result.get("coverLetter")).contains("Backend Engineer"));
    }

    @Test
    void generateFallbackCoverLetterHandlesMissingDetailsGracefully() {
        Map<String, Object> result = unconfigured.generateCoverLetter(null, null, null, null, null);

        String letter = (String) result.get("coverLetter");
        assertNotNull(letter);
        assertTrue(letter.contains("this role"));
    }
}
