package com.jobera.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.service.CompanyReviewService;
import com.jobera.service.CompanyService;

/** Public company browsing - built from a recruiter's own profile fields and their approved
 *  postings, not a separate Company entity. See CompanyService for why. All routes here are
 *  public GETs (AuthInterceptor.PUBLIC_GET_PATTERNS), same as job browsing itself. */
@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final CompanyReviewService companyReviewService;

    public CompanyController(CompanyService companyService, CompanyReviewService companyReviewService) {
        this.companyService = companyService;
        this.companyReviewService = companyReviewService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listCompanies() {
        return ResponseEntity.ok(companyService.listCompanies());
    }

    @GetMapping("/{recruiterId}")
    public ResponseEntity<?> getCompanyProfile(@PathVariable Long recruiterId) {
        try {
            return ResponseEntity.ok(companyService.getCompanyProfile(recruiterId));
        } catch (CompanyService.CompanyNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{recruiterId}/reviews")
    public ResponseEntity<Map<String, Object>> getCompanyReviews(@PathVariable Long recruiterId) {
        return ResponseEntity.ok(companyReviewService.getPublicReviews(recruiterId));
    }
}
