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

import com.jobera.service.CandidateDiscoveryService;

@ExtendWith(MockitoExtension.class)
class CandidateDiscoveryControllerTest {

    @Mock CandidateDiscoveryService candidateDiscoveryService;

    CandidateDiscoveryController controller;

    @BeforeEach
    void setUp() {
        controller = new CandidateDiscoveryController(candidateDiscoveryService);
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
}
