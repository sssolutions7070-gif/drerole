package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.RecruiterRepository;
import com.jobera.service.AiCandidateSearchService;
import com.jobera.service.CandidateDiscoveryService;
import com.jobera.service.CandidateProfileViewService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class CandidateDiscoveryControllerTest {

    @Mock CandidateDiscoveryService candidateDiscoveryService;
    @Mock AiCandidateSearchService aiCandidateSearchService;
    @Mock CandidateProfileViewService candidateProfileViewService;
    @Mock RecruiterRepository recruiterRepository;

    CandidateDiscoveryController controller;

    @BeforeEach
    void setUp() {
        controller = new CandidateDiscoveryController(candidateDiscoveryService, aiCandidateSearchService,
            candidateProfileViewService, recruiterRepository);
    }

    private HttpServletRequest requestFor(long callerId) {
        User user = new User();
        user.setId(callerId);
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).thenReturn(user);
        return request;
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

    @Test
    void recordViewUsesTheCallersOwnRecruiterIdNotARequestParameter() {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(77L);
        when(recruiterRepository.findByUserId(20L)).thenReturn(Optional.of(recruiter));

        ResponseEntity<?> response = controller.recordView(55L, requestFor(20L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(candidateProfileViewService).recordView(77L, 55L);
    }

    @Test
    void recordViewReturns403WhenTheCallerHasNoRecruiterProfile() {
        when(recruiterRepository.findByUserId(20L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.recordView(55L, requestFor(20L));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
