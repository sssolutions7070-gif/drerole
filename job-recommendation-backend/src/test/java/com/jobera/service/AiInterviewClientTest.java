package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
