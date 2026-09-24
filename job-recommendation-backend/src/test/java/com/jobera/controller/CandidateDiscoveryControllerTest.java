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

import com.jobera.service.AiCandidateSearchService;
import com.jobera.service.CandidateDiscoveryService;

@ExtendWith(MockitoExtension.class)
class CandidateDiscoveryControllerTest {

    @Mock CandidateDiscoveryService candidateDiscoveryService;
    @Mock AiCandidateSearchService aiCandidateSearchService;

    CandidateDiscoveryController controller;

    @BeforeEach
    void setUp() {
        controller = new CandidateDiscoveryController(candidateDiscoveryService, aiCandidateSearchService);
    }

    @Test
    void discoverReturnsMatchingCandidates() {
        when(candidateDiscoveryService.discover("java", "remote"))
            .thenReturn(List.of(Map.of("fullName", "Alice")));

        ResponseEntity<List<Map<String, Object>>> response = controller.discover("java", "remote");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void discoverWorksWithNoFilters() {
        when(candidateDiscoveryService.discover(null, null)).thenReturn(List.of());

        ResponseEntity<List<Map<String, Object>>> response = controller.discover(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void aiSearchReturnsMatchingCandidates() {
        when(aiCandidateSearchService.search("react dev in pune")).thenReturn(List.of(Map.of("fullName", "Alice")));

        ResponseEntity<?> response = controller.aiSearch("react dev in pune");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void aiSearchRejectsABlankQuery() {
        ResponseEntity<?> response = controller.aiSearch("  ");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void aiSearchReturns503WhenNotConfigured() {
        when(aiCandidateSearchService.search("query")).thenThrow(new AiCandidateSearchService.NotConfiguredException());

        ResponseEntity<?> response = controller.aiSearch("query");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void aiSearchReturns400WhenTheAiServiceFails() {
        when(aiCandidateSearchService.search("query")).thenThrow(new AiCandidateSearchService.AiSearchException("boom"));

        ResponseEntity<?> response = controller.aiSearch("query");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
