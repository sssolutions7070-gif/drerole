package com.jobera.config;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jobera.entity.JobPosting;
import com.jobera.entity.User;
import com.jobera.repository.UserRepository;
import com.jobera.service.JobRecommendationService;
import com.jobera.service.NotificationService;

/**
 * Emails candidates a digest of newly-posted jobs that match the skills on their resume, once a
 * day, for anyone who has turned job alerts on in their profile. Reuses the same skill-matching
 * engine as the recommendations page, so "a match" here means exactly what it means there.
 */
@Component
public class JobAlertService {

    private static final Logger log = LoggerFactory.getLogger(JobAlertService.class);

    private final UserRepository userRepository;
    private final JobRecommendationService recommendationService;
    private final NotificationService notificationService;

    public JobAlertService(UserRepository userRepository,
                            JobRecommendationService recommendationService,
                            NotificationService notificationService) {
        this.userRepository = userRepository;
        this.recommendationService = recommendationService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "UTC")
    public void sendDailyDigests() {
        LocalDateTime runStartedAt = LocalDateTime.now();
        List<User> subscribers = userRepository.findByJobAlertsEnabledTrueAndIsActiveTrue();
        int sent = 0;
        for (User user : subscribers) {
            try {
                if (sendDigestIfAnyNewMatches(user, runStartedAt)) {
                    sent++;
                }
            } catch (Exception e) {
                // One user's malformed resume/skills shouldn't stop everyone else's digest.
                log.warn("Failed to build job alert digest for user {}: {}", user.getId(), e.getMessage());
            }
        }
        if (sent > 0) {
            log.info("Sent {} job alert digest(s) out of {} subscriber(s)", sent, subscribers.size());
        }
    }

    private boolean sendDigestIfAnyNewMatches(User user, LocalDateTime runStartedAt) {
        LocalDateTime since = user.getLastJobAlertSentAt() != null ? user.getLastJobAlertSentAt() : user.getCreatedAt();

        List<JobPosting> newMatches = recommendationService.recommendJobsForUser(user.getId()).stream()
            .filter(job -> job.getPostedDate() != null && (since == null || job.getPostedDate().isAfter(since)))
            .toList();

        // Advance the watermark regardless of whether anything matched this run, so a quiet
        // stretch doesn't turn into one giant catch-up digest the next time something posts.
        user.setLastJobAlertSentAt(runStartedAt);
        userRepository.save(user);

        if (newMatches.isEmpty()) {
            return false;
        }

        notificationService.sendJobAlertDigest(user.getEmail(), user.getFullName(), newMatches);
        return true;
    }
}
