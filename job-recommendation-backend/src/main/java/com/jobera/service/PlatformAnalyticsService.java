package com.jobera.service;

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
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobPostingRepository;
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

    public PlatformAnalyticsService(UserRepository userRepository,
                                     JobPostingRepository jobPostingRepository,
                                     ApplicationRepository applicationRepository) {
        this.userRepository = userRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.applicationRepository = applicationRepository;
    }

    public Map<String, Object> getOverview() {
        List<JobPosting> jobs = jobPostingRepository.findAll();
        List<Application> applications = applicationRepository.findAll();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("users", userCounts());
        overview.put("jobPostings", jobPostingCounts(jobs));
        overview.put("applications", applicationCounts(applications));
        overview.put("conversionFunnel", conversionFunnel(applications));
        overview.put("signupsByDay", signupsByDay());
        overview.put("applicationsByDay", applicationsByDay(applications));
        return overview;
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
