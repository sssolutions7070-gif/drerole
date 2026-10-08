package com.jobera.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.RecruiterRepository;
import com.jobera.service.AiCandidateSearchService;
import com.jobera.service.CandidateDiscoveryService;
import com.jobera.service.CandidateProfileViewService;

import jakarta.servlet.http.HttpServletRequest;

/** Any recruiter's window into the "open to work" candidate pool - see AccessPolicy's plain
 *  recruiter check, not scoped to one company since this isn't a company-owned resource. */
@RestController
@RequestMapping("/api/candidates")
public class CandidateDiscoveryController {

    private static final String ERROR_KEY = "error";

    private final CandidateDiscoveryService candidateDiscoveryService;
    private final AiCandidateSearchService aiCandidateSearchService;
    private final CandidateProfileViewService candidateProfileViewService;
    private final RecruiterRepository recruiterRepository;

    public CandidateDiscoveryController(CandidateDiscoveryService candidateDiscoveryService,
                                         AiCandidateSearchService aiCandidateSearchService,
                                         CandidateProfileViewService candidateProfileViewService,
                                         RecruiterRepository recruiterRepository) {
        this.candidateDiscoveryService = candidateDiscoveryService;
        this.aiCandidateSearchService = aiCandidateSearchService;
        this.candidateProfileViewService = candidateProfileViewService;
        this.recruiterRepository = recruiterRepository;
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

    /** Records that the calling recruiter opened this candidate's expanded profile. The recruiter
     *  is resolved from the authenticated caller, not a request parameter, so one recruiter can't
     *  record a view under another's name. */
    @PostMapping("/{candidateUserId}/views")
    public ResponseEntity<?> recordView(@PathVariable Long candidateUserId, HttpServletRequest request) {
        User caller = (User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        Optional<Recruiter> recruiter = recruiterRepository.findByUserId(caller.getId());
        if (recruiter.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, "No recruiter profile for this account"));
        }
        candidateProfileViewService.recordView(recruiter.get().getId(), candidateUserId);
        return ResponseEntity.ok().build();
    }
}
