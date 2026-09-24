package com.jobera.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.service.CandidateDiscoveryService;

/** Any recruiter's window into the "open to work" candidate pool - see AccessPolicy's plain
 *  recruiter check, not scoped to one company since this isn't a company-owned resource. */
@RestController
@RequestMapping("/api/candidates")
public class CandidateDiscoveryController {

    private final CandidateDiscoveryService candidateDiscoveryService;

    public CandidateDiscoveryController(CandidateDiscoveryService candidateDiscoveryService) {
        this.candidateDiscoveryService = candidateDiscoveryService;
    }

    @GetMapping("/discover")
    public ResponseEntity<List<Map<String, Object>>> discover(
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String location) {
        return ResponseEntity.ok(candidateDiscoveryService.discover(skill, location));
    }
}
