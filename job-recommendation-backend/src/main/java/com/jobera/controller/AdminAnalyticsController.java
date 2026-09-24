package com.jobera.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.service.PlatformAnalyticsService;

/** Platform-wide growth and funnel metrics - covered by AccessPolicy's existing
 *  rule(null, "/api/admin/**", admin), so no new access-control rule is needed here. Distinct
 *  from AdminController.getDashboardStats(), which only exposes raw current totals. */
@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final PlatformAnalyticsService platformAnalyticsService;

    public AdminAnalyticsController(PlatformAnalyticsService platformAnalyticsService) {
        this.platformAnalyticsService = platformAnalyticsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        return ResponseEntity.ok(platformAnalyticsService.getOverview());
    }
}
