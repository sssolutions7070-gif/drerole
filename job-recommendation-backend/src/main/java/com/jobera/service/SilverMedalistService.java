package com.jobera.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.UserSkillRepository;

/**
 * Re-engages a recruiter's past "near misses" - candidates who applied to one of their jobs before
 * but didn't get it (rejected, or declined an offer) - when a new, skill-similar opening goes live.
 * Turns the talent pool recruiters already browse manually (see TalentPoolService) into a proactive
 * notification instead of something they have to remember to check.
 */
@Service
public class SilverMedalistService {

    /** Past statuses worth re-engaging on. Still-open or successful applications are left alone. */
    static final List<String> PAST_MISS_STATUSES = List.of("REJECTED", "OFFER_DECLINED");

    /** Fraction of the new job's skills a candidate must already have to be worth notifying. */
    static final double MATCH_THRESHOLD = 0.4;

    private final ApplicationRepository applicationRepository;
    private final UserSkillRepository userSkillRepository;
    private final NotificationFeedService notificationFeedService;
    private final RealtimeNotifier realtimeNotifier;

    public SilverMedalistService(ApplicationRepository applicationRepository,
                                  UserSkillRepository userSkillRepository,
                                  NotificationFeedService notificationFeedService,
                                  RealtimeNotifier realtimeNotifier) {
        this.applicationRepository = applicationRepository;
        this.userSkillRepository = userSkillRepository;
        this.notificationFeedService = notificationFeedService;
        this.realtimeNotifier = realtimeNotifier;
    }

    /**
     * Called once a job posting is approved and goes live. Returns how many past candidates were
     * notified. A job with no parseable skills, or a recruiter with no past near-misses, notifies no
     * one; this is intentionally silent (no exception) so it never blocks the approval itself.
     */
    public int notifyMatchingPastCandidates(JobPosting job) {
        if (job == null || job.getRecruiter() == null || job.getRecruiter().getId() == null) {
            return 0;
        }
        Set<String> jobSkills = parseSkills(job.getRequiredSkills());
        jobSkills.addAll(parseSkills(job.getPreferredSkills()));
        if (jobSkills.isEmpty()) {
            return 0;
        }

        Long recruiterId = job.getRecruiter().getId();
        Set<Long> alreadyApplied = applicationRepository.findByJobPostingId(job.getId()).stream()
            .map(a -> a.getUser() != null ? a.getUser().getId() : null)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        Map<Long, User> candidates = new LinkedHashMap<>();
        for (Application pastMiss : applicationRepository.findByRecruiterIdAndStatusIn(recruiterId, PAST_MISS_STATUSES)) {
            User candidate = pastMiss.getUser();
            if (candidate != null && !alreadyApplied.contains(candidate.getId())) {
                candidates.putIfAbsent(candidate.getId(), candidate);
            }
        }

        int notified = 0;
        for (User candidate : candidates.values()) {
            if (matchesWell(candidate, jobSkills)) {
                notify(candidate, job);
                notified++;
            }
        }
        return notified;
    }

    private boolean matchesWell(User candidate, Set<String> jobSkills) {
        Set<String> candidateSkills = userSkillRepository.findByUserId(candidate.getId()).stream()
            .map(skill -> skill.getSkillName() == null ? "" : skill.getSkillName().trim().toLowerCase())
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toCollection(HashSet::new));
        if (candidateSkills.isEmpty()) {
            return false;
        }
        long overlap = jobSkills.stream().filter(candidateSkills::contains).count();
        return ((double) overlap / jobSkills.size()) >= MATCH_THRESHOLD;
    }

    private void notify(User candidate, JobPosting job) {
        String title = "A new role might be a better fit: " + job.getTitle();
        String message = (job.getCompany() != null ? job.getCompany() : "A recruiter you applied to before")
            + " just opened \"" + job.getTitle() + "\", and your skills look like a strong match.";
        notificationFeedService.record(candidate.getId(), "SILVER_MEDALIST_MATCH", title, message,
            "/jobs/" + job.getId() + "?source=silver_medalist");
        realtimeNotifier.notifyUser(candidate.getId(), "SILVER_MEDALIST_MATCH", Map.of(
            "jobId", job.getId(),
            "jobTitle", job.getTitle()
        ));
    }

    private Set<String> parseSkills(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashSet<>();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
