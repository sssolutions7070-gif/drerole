package com.jobera.service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobera.entity.Recruiter;
import com.jobera.entity.SavedCandidateSearch;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.SavedCandidateSearchRepository;

@Service
public class SavedCandidateSearchService {

    private static final Logger log = LoggerFactory.getLogger(SavedCandidateSearchService.class);

    private final SavedCandidateSearchRepository searchRepository;
    private final RecruiterRepository recruiterRepository;
    private final CandidateDiscoveryService candidateDiscoveryService;
    private final NotificationFeedService notificationFeedService;
    private final RealtimeNotifier realtimeNotifier;

    public SavedCandidateSearchService(SavedCandidateSearchRepository searchRepository,
                                       RecruiterRepository recruiterRepository,
                                       CandidateDiscoveryService candidateDiscoveryService,
                                       NotificationFeedService notificationFeedService,
                                       RealtimeNotifier realtimeNotifier) {
        this.searchRepository = searchRepository;
        this.recruiterRepository = recruiterRepository;
        this.candidateDiscoveryService = candidateDiscoveryService;
        this.notificationFeedService = notificationFeedService;
        this.realtimeNotifier = realtimeNotifier;
    }

    public SavedCandidateSearch saveSearch(Long recruiterId, String name, String skillFilter,
                                           String locationFilter, String aiQuery, Boolean alertEnabled) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Search name cannot be empty");
        }
        if (recruiterId == null || !recruiterRepository.existsById(recruiterId)) {
            throw new NoSuchElementException("Recruiter not found: " + recruiterId);
        }

        SavedCandidateSearch search = new SavedCandidateSearch(
            recruiterId,
            name.trim(),
            skillFilter != null && !skillFilter.trim().isEmpty() ? skillFilter.trim() : null,
            locationFilter != null && !locationFilter.trim().isEmpty() ? locationFilter.trim() : null,
            aiQuery != null && !aiQuery.trim().isEmpty() ? aiQuery.trim() : null,
            alertEnabled != null ? alertEnabled : true
        );
        return searchRepository.save(search);
    }

    public List<SavedCandidateSearch> getSearchesByRecruiter(Long recruiterId) {
        return searchRepository.findByRecruiterIdOrderByCreatedAtDesc(recruiterId);
    }

    @Transactional
    public void deleteSearch(Long id, Long recruiterId) {
        SavedCandidateSearch search = searchRepository.findByIdAndRecruiterId(id, recruiterId)
            .orElseThrow(() -> new NoSuchElementException("Saved search not found"));
        searchRepository.delete(search);
    }

    public SavedCandidateSearch toggleAlert(Long id, Long recruiterId, boolean enabled) {
        SavedCandidateSearch search = searchRepository.findByIdAndRecruiterId(id, recruiterId)
            .orElseThrow(() -> new NoSuchElementException("Saved search not found"));
        search.setAlertEnabled(enabled);
        return searchRepository.save(search);
    }

    /** Manual "check now" from the recruiter. Returns the total number of current matches. */
    @Transactional
    public int checkAndNotifyMatches(Long searchId, Long recruiterId) {
        SavedCandidateSearch search = searchRepository.findByIdAndRecruiterId(searchId, recruiterId)
            .orElseThrow(() -> new NoSuchElementException("Saved search not found"));

        if (!search.isAlertEnabled()) {
            return 0;
        }
        return evaluate(search);
    }

    /**
     * Re-runs every alert-enabled search. Called on a timer by {@link SavedCandidateSearchScheduler}.
     * A failure on one search is logged and does not stop the others.
     */
    public void runAllAlerts() {
        for (SavedCandidateSearch search : searchRepository.findByAlertEnabledTrue()) {
            try {
                evaluate(search);
            } catch (RuntimeException e) {
                log.warn("Saved search alert {} failed: {}", search.getId(), e.getMessage());
            }
        }
    }

    /**
     * Runs the search and notifies the recruiter only when the match count has grown since the last
     * evaluation, so an unchanged result set does not produce the same alert every hour.
     */
    private int evaluate(SavedCandidateSearch search) {
        List<Map<String, Object>> matches = candidateDiscoveryService.discover(
            search.getSkillFilter(),
            search.getLocationFilter()
        );
        int total = matches.size();
        int newMatches = total - search.getLastMatchCount();

        if (newMatches > 0) {
            Recruiter recruiter = recruiterRepository.findById(search.getRecruiterId()).orElse(null);
            if (recruiter != null && recruiter.getUser() != null) {
                Long userId = recruiter.getUser().getId();
                String title = "Candidate Alert: " + search.getName();
                String message = newMatches + " new candidate(s) match your saved search (" + total + " in total).";
                notificationFeedService.record(
                    userId,
                    "SAVED_SEARCH_ALERT",
                    title,
                    message,
                    "/recruiter/dashboard"
                );
                realtimeNotifier.notifyUser(userId, "SAVED_SEARCH_ALERT", Map.of(
                    "searchId", search.getId(),
                    "searchName", search.getName(),
                    "matchCount", total,
                    "newMatchCount", newMatches
                ));
            }
        }

        search.setLastMatchCount(total);
        searchRepository.save(search);
        return total;
    }
}
