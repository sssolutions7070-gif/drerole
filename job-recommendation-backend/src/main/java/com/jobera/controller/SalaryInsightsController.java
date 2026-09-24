package com.jobera.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.service.SalaryInsightsService;

/** Salary benchmarking from posted job ranges - deliberately public (AuthInterceptor.
 *  PUBLIC_GET_PATTERNS), same as the job listings it's built from: a candidate should be able to
 *  see what a role typically pays before creating an account. */
@RestController
@RequestMapping("/api/salary-insights")
public class SalaryInsightsController {

    private final SalaryInsightsService salaryInsightsService;

    public SalaryInsightsController(SalaryInsightsService salaryInsightsService) {
        this.salaryInsightsService = salaryInsightsService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getInsights(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String experienceLevel) {
        return ResponseEntity.ok(salaryInsightsService.getInsights(title, location, experienceLevel));
    }
}
