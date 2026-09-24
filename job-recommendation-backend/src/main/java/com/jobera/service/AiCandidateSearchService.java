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
 * Turns a recruiter's plain-English request ("senior React dev in Pune, open to work") into the
 * structured skill/location filters CandidateDiscoveryService already knows how to search on -
 * a thin layer in front of that service, not a replacement for it. Calls Anthropic directly with
 * RestTemplate, the same hand-rolled approach as AiInterviewClient, kept separate from it since
 * the two have nothing else in common besides which API they call.
 */
@Service
public class AiCandidateSearchService {

    public static class AiSearchException extends RuntimeException {
        public AiSearchException(String message) {
            super(message);
        }
    }

    public static class NotConfiguredException extends RuntimeException {
        public NotConfiguredException() {
            super("AI candidate search isn't configured on this server");
        }
    }

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestTemplate restTemplate;
    private final CandidateDiscoveryService candidateDiscoveryService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public AiCandidateSearchService(RestTemplate restTemplate,
                                     CandidateDiscoveryService candidateDiscoveryService,
                                     @Value("${app.ai.anthropic-api-key:}") String apiKey,
                                     @Value("${app.ai.anthropic-model:claude-3-5-haiku-20241022}") String model) {
        this.restTemplate = restTemplate;
        this.candidateDiscoveryService = candidateDiscoveryService;
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /** Parses the query into {skill, location} and runs it through the same discover() every
     *  other candidate search uses - the AI only ever narrows what gets searched, it never sees
     *  or ranks the candidates themselves. */
    public List<Map<String, Object>> search(String naturalLanguageQuery) {
        if (!isConfigured()) {
            throw new NotConfiguredException();
        }
        Map<String, String> filters = parseQuery(naturalLanguageQuery);
        return candidateDiscoveryService.discover(filters.get("skill"), filters.get("location"));
    }

    Map<String, String> parseQuery(String naturalLanguageQuery) {
        String prompt = "Extract a skill and a location from this recruiter's candidate search request, if "
            + "present. Respond with ONLY a JSON object with exactly two keys, \"skill\" and \"location\", "
            + "each either a short string or an empty string if not mentioned. Do not include experience "
            + "level, job title words like \"developer\" unless they name a specific technology, or anything "
            + "else.\n\nRequest: " + naturalLanguageQuery
            + "\n\nExample response: {\"skill\": \"React\", \"location\": \"Pune\"}";

        String responseText = callClaude(prompt);
        try {
            JsonNode obj = objectMapper.readTree(extractJson(responseText));
            String skill = obj.path("skill").asText("");
            String location = obj.path("location").asText("");
            return Map.of("skill", skill, "location", location);
        } catch (Exception e) {
            throw new AiSearchException("The AI service returned an unexpected response");
        }
    }

    private String callClaude(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("x-api-key", apiKey);
        headers.add("anthropic-version", ANTHROPIC_VERSION);

        Map<String, Object> body = Map.of(
            "model", model,
            "max_tokens", 256,
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        Map<String, Object> response;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(API_URL, new HttpEntity<>(body, headers), Map.class);
            response = result;
        } catch (RestClientException e) {
            throw new AiSearchException("Failed to reach the AI service");
        }
        if (response == null) {
            throw new AiSearchException("The AI service returned an empty response");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty() || content.get(0).get("text") == null) {
            throw new AiSearchException("The AI service returned an empty response");
        }
        return content.get(0).get("text").toString();
    }

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
            throw new AiSearchException("The AI service returned an unexpected response");
        }
        int end = text.lastIndexOf(close);
        if (end == -1 || end < start) {
            throw new AiSearchException("The AI service returned an unexpected response");
        }
        return text.substring(start, end + 1);
    }
}
