package com.jobera.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Calls Anthropic's Messages API directly with RestTemplate rather than adding an SDK dependency
 * - consistent with how this codebase already talks to Stripe and the OAuth providers. Used to
 * generate mock-interview questions from a job posting and give feedback on a candidate's typed
 * answer to one.
 */
@Service
public class AiInterviewClient {

    public static class AiInterviewException extends RuntimeException {
        public AiInterviewException(String message) {
            super(message);
        }
    }

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public AiInterviewClient(RestTemplate restTemplate,
                              @Value("${app.ai.anthropic-api-key:}") String apiKey,
                              @Value("${app.ai.anthropic-model:claude-3-5-haiku-20241022}") String model) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public List<String> generateQuestions(String jobTitle, String jobDescription, String requiredSkills, int count) {
        String prompt = "You are an interview coach. Based on this job, write exactly " + count + " realistic "
            + "interview questions a candidate should practice answering. Mix behavioral and technical questions "
            + "relevant to the role.\n\nJob title: " + jobTitle
            + "\nRequired skills: " + nullToEmpty(requiredSkills)
            + "\nJob description: " + nullToEmpty(jobDescription)
            + "\n\nRespond with ONLY a JSON array of " + count + " question strings, nothing else. "
            + "Example: [\"Question one?\", \"Question two?\"]";

        String responseText = callClaude(prompt);
        try {
            JsonNode array = objectMapper.readTree(extractJson(responseText));
            return objectMapper.convertValue(array, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            throw new AiInterviewException("The AI service returned an unexpected response");
        }
    }

    public Map<String, Object> evaluateAnswer(String jobTitle, String question, String answer) {
        String prompt = "You are an interview coach reviewing a candidate's practice answer for a " + jobTitle
            + " role.\n\nQuestion: " + question
            + "\nCandidate's answer: " + answer
            + "\n\nScore the answer from 1 (weak) to 5 (excellent) and give 2-3 sentences of constructive feedback. "
            + "Respond with ONLY a JSON object, nothing else. Example: {\"score\": 4, \"feedback\": \"...\"}";

        String responseText = callClaude(prompt);
        try {
            JsonNode obj = objectMapper.readTree(extractJson(responseText));
            return Map.of("score", obj.path("score").asInt(3), "feedback", obj.path("feedback").asText(""));
        } catch (Exception e) {
            throw new AiInterviewException("The AI service returned an unexpected response");
        }
    }

    private String callClaude(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("x-api-key", apiKey);
        headers.add("anthropic-version", ANTHROPIC_VERSION);

        Map<String, Object> body = Map.of(
            "model", model,
            "max_tokens", 1024,
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        Map<String, Object> response;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(API_URL, new HttpEntity<>(body, headers), Map.class);
            response = result;
        } catch (RestClientException e) {
            throw new AiInterviewException("Failed to reach the AI interview service");
        }
        if (response == null) {
            throw new AiInterviewException("The AI interview service returned an empty response");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty() || content.get(0).get("text") == null) {
            throw new AiInterviewException("The AI interview service returned an empty response");
        }
        return content.get(0).get("text").toString();
    }

    /** Claude sometimes wraps its JSON answer in prose or a markdown code fence despite being
     *  asked not to - pull out the first {...} or [...] block rather than failing on that. */
    private String extractJson(String text) {
        int start = -1;
        char close = ' ';
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[' || c == '{') {
                start = i;
                close = (c == '[') ? ']' : '}';
                break;
            }
        }
        if (start == -1) {
            throw new AiInterviewException("The AI service returned an unexpected response");
        }
        int end = text.lastIndexOf(close);
        if (end == -1 || end < start) {
            throw new AiInterviewException("The AI service returned an unexpected response");
        }
        return text.substring(start, end + 1);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
