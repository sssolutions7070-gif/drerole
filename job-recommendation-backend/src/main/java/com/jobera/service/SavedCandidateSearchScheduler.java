package com.jobera.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Re-runs recruiters' alert-enabled saved searches so new matching candidates are reported without a manual check. */
@Component
public class SavedCandidateSearchScheduler {

    private final SavedCandidateSearchService searchService;

    public SavedCandidateSearchScheduler(SavedCandidateSearchService searchService) {
        this.searchService = searchService;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void runAlerts() {
        searchService.runAllAlerts();
    }
}
