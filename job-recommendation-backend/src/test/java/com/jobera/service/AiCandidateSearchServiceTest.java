package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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

@ExtendWith(MockitoExtension.class)
class AiCandidateSearchServiceTest {

    @Mock RestTemplate restTemplate;
    @Mock CandidateDiscoveryService candidateDiscoveryService;

    AiCandidateSearchService configured;
    AiCandidateSearchService unconfigured;

    @BeforeEach
    void setUp() {
        configured = new AiCandidateSearchService(restTemplate, candidateDiscoveryService, "sk-ant-test123", "claude-3-5-haiku-20241022");
        unconfigured = new AiCandidateSearchService(restTemplate, candidateDiscoveryService, "", "claude-3-5-haiku-20241022");
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
    void parseQueryExtractsSkillAndLocation() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(claudeResponse("{\"skill\": \"React\", \"location\": \"Pune\"}"));

        Map<String, String> filters = configured.parseQuery("senior React dev in Pune, open to work");

        assertEquals("React", filters.get("skill"));
        assertEquals("Pune", filters.get("location"));
    }

    @Test
    void parseQueryExtractsJsonEvenWhenWrappedInProse() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(claudeResponse("Sure, here you go:\n```json\n{\"skill\": \"Java\", \"location\": \"\"}\n```"));

        Map<String, String> filters = configured.parseQuery("java devs anywhere");

        assertEquals("Java", filters.get("skill"));
        assertEquals("", filters.get("location"));
    }

    @Test
    void searchFailsWhenNotConfigured() {
        assertThrows(AiCandidateSearchService.NotConfiguredException.class,
            () -> unconfigured.search("react dev in pune"));
    }

    @Test
    void searchWrapsANetworkFailure() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenThrow(new RestClientException("timeout"));

        assertThrows(AiCandidateSearchService.AiSearchException.class,
            () -> configured.search("react dev in pune"));
    }

    @Test
    void searchDelegatesToCandidateDiscoveryServiceWithTheParsedFilters() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(claudeResponse("{\"skill\": \"React\", \"location\": \"Pune\"}"));
        when(candidateDiscoveryService.discover("React", "Pune")).thenReturn(List.of(Map.of("fullName", "Alice")));

        List<Map<String, Object>> results = configured.search("senior React dev in Pune");

        assertEquals(1, results.size());
        verify(candidateDiscoveryService).discover("React", "Pune");
    }

    @Test
    void parseQueryFailsWhenTheResponseHasNoJson() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(claudeResponse("Sorry, I don't understand."));

        assertThrows(AiCandidateSearchService.AiSearchException.class,
            () -> configured.parseQuery("gibberish"));
    }
}
