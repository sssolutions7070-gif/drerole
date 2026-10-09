package com.jobera.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.TestAssignment;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.TestAssignmentRepository;
import com.jobera.repository.UserRepository;

/** Platform-wide growth and funnel metrics for the admin dashboard - the existing analytics on
 *  ApplicationStatusService are all scoped to one recruiter's own jobs; this is the same idea
 *  rolled up across the whole platform instead. */
@Service
public class PlatformAnalyticsService {

    private static final long ROLE_USER = 1L;
    private static final long ROLE_ADMIN = 2L;
    private static final long ROLE_RECRUITER = 3L;
    private static final int TREND_DAYS = 30;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final UserRepository userRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ApplicationRepository applicationRepository;
    private final TestAssignmentRepository testAssignmentRepository;

    public PlatformAnalyticsService(UserRepository userRepository,
                                     JobPostingRepository jobPostingRepository,
                                     ApplicationRepository applicationRepository,
                                     TestAssignmentRepository testAssignmentRepository) {
        this.userRepository = userRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.applicationRepository = applicationRepository;
        this.testAssignmentRepository = testAssignmentRepository;
    }

    public Map<String, Object> getOverview() {
        List<JobPosting> jobs = jobPostingRepository.findAll();
        List<Application> applications = applicationRepository.findAll();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("users", userCounts());
        overview.put("jobPostings", jobPostingCounts(jobs));
        overview.put("jobPostingsByExperienceLevel", groupJobsBy(jobs, JobPosting::getExperienceLevel));
        overview.put("jobPostingsByType", groupJobsBy(jobs, JobPosting::getJobType));
        overview.put("applications", applicationCounts(applications));
        overview.put("conversionFunnel", conversionFunnel(applications));
        overview.put("signupsByDay", signupsByDay());
        overview.put("applicationsByDay", applicationsByDay(applications));
        overview.put("timeToHire", timeToHire(applications));
        overview.put("proctoringIntegrity", proctoringIntegrity());
        // Most commonly missing skills across every application on the platform - previously
        // recorded per-application and never rolled up anywhere, admin or otherwise.
        overview.put("topMissingSkills", SkillGapAnalyzer.topMissingSkills(applications, 8));
        return overview;
    }

    private Map<String, Long> groupJobsBy(List<JobPosting> jobs, java.util.function.Function<JobPosting, String> field) {
        return jobs.stream()
            .collect(Collectors.groupingBy(
                j -> {
                    String value = field.apply(j);
                    return (value == null || value.isBlank()) ? "Not specified" : value;
                },
                LinkedHashMap::new, Collectors.counting()));
    }

    /** Real average days from application to offer, platform-wide - the same derivation
     *  ApplicationStatusService.getRecruiterAnalytics already does per-recruiter, rolled up
     *  across every job. There's no status-history table, so this uses statusUpdatedAt, which
     *  only reflects the most recent status change (accurate for OFFER_SENT, since that's
     *  normally the application's final state). */
    private Map<String, Object> timeToHire(List<Application> applications) {
        double sumDays = 0;
        long count = 0;
        for (Application application : applications) {
            if ("OFFER_SENT".equals(application.getStatus())
                && application.getStatusUpdatedAt() != null
                && application.getApplicationDate() != null) {
                sumDays += Duration.between(application.getApplicationDate(), application.getStatusUpdatedAt()).toHours() / 24.0;
                count++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("averageDays", count > 0 ? round1(sumDays / count) : null);
        result.put("sampleSize", count);
        return result;
    }

    /** What fraction of proctored test sessions came back clean (no violations, not terminated
     *  for cause) - computed from the actual per-session violation counters TestAssignment
     *  already records, not a hardcoded marketing number. */
    private Map<String, Object> proctoringIntegrity() {
        List<TestAssignment> proctored = testAssignmentRepository.findAll().stream()
            .filter(ta -> Boolean.TRUE.equals(ta.getProctoringActive()))
            .collect(Collectors.toList());

        long clean = proctored.stream()
            .filter(ta -> !Boolean.TRUE.equals(ta.getProctoringTerminated())
                && (ta.getWarningCount() == null || ta.getWarningCount() == 0))
            .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalProctoredSessions", proctored.size());
        result.put("cleanSessions", clean);
        result.put("cleanPercentage", proctored.isEmpty() ? null : round1(100.0 * clean / proctored.size()));
        return result;
    }

    private static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Map<String, Object> userCounts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("candidates", userRepository.countByRoleId(ROLE_USER));
        counts.put("recruiters", userRepository.countByRoleId(ROLE_RECRUITER));
        counts.put("admins", userRepository.countByRoleId(ROLE_ADMIN));
        counts.put("total", userRepository.count());
        return counts;
    }

    private Map<String, Object> jobPostingCounts(List<JobPosting> jobs) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("total", jobs.size());
        counts.put("pending", jobs.stream().filter(j -> JobPosting.MODERATION_PENDING.equals(j.getModerationStatus())).count());
        counts.put("approved", jobs.stream().filter(j -> JobPosting.MODERATION_APPROVED.equals(j.getModerationStatus())).count());
        counts.put("rejected", jobs.stream().filter(j -> JobPosting.MODERATION_REJECTED.equals(j.getModerationStatus())).count());
        counts.put("active", jobs.stream().filter(j -> Boolean.TRUE.equals(j.getIsActive())).count());
        return counts;
    }

    private Map<String, Object> applicationCounts(List<Application> applications) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("total", applications.size());
        Map<String, Long> byStatus = applications.stream()
            .collect(Collectors.groupingBy(
                a -> a.getStatus() == null ? "UNKNOWN" : a.getStatus(),
                LinkedHashMap::new, Collectors.counting()));
        counts.put("byStatus", byStatus);
        Map<String, Long> bySource = applications.stream()
            .collect(Collectors.groupingBy(
                a -> a.getSource() == null ? Application.SOURCE_DIRECT : a.getSource(),
                LinkedHashMap::new, Collectors.counting()));
        counts.put("bySource", bySource);
        return counts;
    }

    /** How many applications ever reached each stage, platform-wide - not a state machine count
     *  (an application only has one current status), but a "how far did it get" funnel. */
    private Map<String, Object> conversionFunnel(List<Application> applications) {
        long submitted = applications.size();
        long viewed = applications.stream().filter(a -> Boolean.TRUE.equals(a.getViewedByRecruiter())).count();
        long shortlistedOrBeyond = applications.stream().filter(a -> isAtOrBeyond(a.getStatus(), STAGE_ORDER, "SHORTLISTED")).count();
        long interviewOrBeyond = applications.stream().filter(a -> isAtOrBeyond(a.getStatus(), STAGE_ORDER, "INTERVIEW_SCHEDULED")).count();
        long offerOrBeyond = applications.stream().filter(a -> isAtOrBeyond(a.getStatus(), STAGE_ORDER, "OFFER_SENT")).count();
        long accepted = applications.stream().filter(a -> "OFFER_ACCEPTED".equals(a.getStatus())).count();

        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("submitted", submitted);
        funnel.put("viewedByRecruiter", viewed);
        funnel.put("shortlisted", shortlistedOrBeyond);
        funnel.put("interviewScheduled", interviewOrBeyond);
        funnel.put("offerSent", offerOrBeyond);
        funnel.put("offerAccepted", accepted);
        return funnel;
    }

    // A rough, best-effort ordering used only to bucket a snapshot status into "reached at least
    // this stage" - there's no separate status-history table to derive this exactly from.
    private static final List<String> STAGE_ORDER = List.of(
        "APPLICATION_SUBMITTED", "VIEWED_BY_RECRUITER", "SHORTLISTED", "INTERVIEW_SCHEDULED",
        "OFFER_SENT", "OFFER_ACCEPTED");

    private boolean isAtOrBeyond(String status, List<String> order, String stage) {
        int stageIndex = order.indexOf(stage);
        int statusIndex = order.indexOf(status);
        return statusIndex >= 0 && statusIndex >= stageIndex;
    }

    private List<Map<String, Object>> signupsByDay() {
        LocalDateTime since = LocalDate.now().minusDays(TREND_DAYS - 1L).atStartOfDay();
        Map<String, Long> byDay = userRepository.findUsersRegisteredAfter(since).stream()
            .filter(u -> u.getCreatedAt() != null)
            .collect(Collectors.groupingBy(
                u -> u.getCreatedAt().toLocalDate().format(DAY_FORMAT),
                LinkedHashMap::new, Collectors.counting()));
        return fillTrend(byDay);
    }

    private List<Map<String, Object>> applicationsByDay(List<Application> applications) {
        LocalDate cutoff = LocalDate.now().minusDays(TREND_DAYS - 1L);
        Map<String, Long> byDay = applications.stream()
            .filter(a -> a.getApplicationDate() != null && !a.getApplicationDate().toLocalDate().isBefore(cutoff))
            .collect(Collectors.groupingBy(
                a -> a.getApplicationDate().toLocalDate().format(DAY_FORMAT),
                LinkedHashMap::new, Collectors.counting()));
        return fillTrend(byDay);
    }

    /** Zero-fills every day in the trailing window so the frontend can chart it without gaps. */
    private List<Map<String, Object>> fillTrend(Map<String, Long> byDay) {
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate day = LocalDate.now().minusDays(TREND_DAYS - 1L);
        LocalDate today = LocalDate.now();
        while (!day.isAfter(today)) {
            String key = day.format(DAY_FORMAT);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", key);
            point.put("count", byDay.getOrDefault(key, 0L));
            trend.add(point);
            day = day.plusDays(1);
        }
        return trend;
    }
}
