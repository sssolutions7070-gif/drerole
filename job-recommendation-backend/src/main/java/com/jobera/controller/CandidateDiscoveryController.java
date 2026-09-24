package com.jobera.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.service.AiCandidateSearchService;
import com.jobera.service.CandidateDiscoveryService;

/** Any recruiter's window into the "open to work" candidate pool - see AccessPolicy's plain
 *  recruiter check, not scoped to one company since this isn't a company-owned resource. */
@RestController
@RequestMapping("/api/candidates")
public class CandidateDiscoveryController {

    private static final String ERROR_KEY = "error";

    private final CandidateDiscoveryService candidateDiscoveryService;
    private final AiCandidateSearchService aiCandidateSearchService;

    public CandidateDiscoveryController(CandidateDiscoveryService candidateDiscoveryService,
                                         AiCandidateSearchService aiCandidateSearchService) {
        this.candidateDiscoveryService = candidateDiscoveryService;
        this.aiCandidateSearchService = aiCandidateSearchService;
    }

    @GetMapping("/discover")
    public ResponseEntity<List<Map<String, Object>>> discover(
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String location) {
        return ResponseEntity.ok(candidateDiscoveryService.discover(skill, location));
    }

    @GetMapping("/discover/ai-search")
    public ResponseEntity<?> aiSearch(@RequestParam String query) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "query is required"));
        }
        try {
            return ResponseEntity.ok(aiCandidateSearchService.search(query));
        } catch (AiCandidateSearchService.NotConfiguredException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (AiCandidateSearchService.AiSearchException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
