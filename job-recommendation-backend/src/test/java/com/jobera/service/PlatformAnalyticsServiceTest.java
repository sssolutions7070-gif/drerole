package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PlatformAnalyticsServiceTest {

    @Mock UserRepository userRepository;
    @Mock JobPostingRepository jobPostingRepository;
    @Mock ApplicationRepository applicationRepository;

    PlatformAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new PlatformAnalyticsService(userRepository, jobPostingRepository, applicationRepository);
        when(userRepository.countByRoleId(1L)).thenReturn(10L);
        when(userRepository.countByRoleId(3L)).thenReturn(2L);
        when(userRepository.countByRoleId(2L)).thenReturn(1L);
        when(userRepository.count()).thenReturn(13L);
        when(userRepository.findUsersRegisteredAfter(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
    }

    private JobPosting job(String moderationStatus, boolean active) {
        JobPosting job = new JobPosting("T", "C", "d");
        job.setModerationStatus(moderationStatus);
        job.setIsActive(active);
        return job;
    }

    private Application application(String status, boolean viewed, LocalDateTime appliedAt) {
        Application app = new Application();
        app.setStatus(status);
        app.setViewedByRecruiter(viewed);
        app.setApplicationDate(appliedAt);
        return app;
    }

    @Test
    void aggregatesUserCounts() {
        when(jobPostingRepository.findAll()).thenReturn(List.of());
        when(applicationRepository.findAll()).thenReturn(List.of());

        Map<String, Object> overview = service.getOverview();

        Map<String, Object> users = (Map<String, Object>) overview.get("users");
        assertEquals(10L, users.get("candidates"));
        assertEquals(2L, users.get("recruiters"));
        assertEquals(1L, users.get("admins"));
        assertEquals(13L, users.get("total"));
    }

    @Test
    void aggregatesJobPostingCountsByModerationStatus() {
        when(jobPostingRepository.findAll()).thenReturn(List.of(
            job(JobPosting.MODERATION_APPROVED, true),
            job(JobPosting.MODERATION_APPROVED, true),
            job(JobPosting.MODERATION_PENDING, true),
            job(JobPosting.MODERATION_REJECTED, false)
        ));
        when(applicationRepository.findAll()).thenReturn(List.of());

        Map<String, Object> overview = service.getOverview();

        Map<String, Object> jobs = (Map<String, Object>) overview.get("jobPostings");
        assertEquals(4, jobs.get("total"));
        assertEquals(2L, jobs.get("approved"));
        assertEquals(1L, jobs.get("pending"));
        assertEquals(1L, jobs.get("rejected"));
        assertEquals(3L, jobs.get("active"));
    }

    @Test
    void buildsAConversionFunnelAcrossAllApplications() {
        when(jobPostingRepository.findAll()).thenReturn(List.of());
        when(applicationRepository.findAll()).thenReturn(List.of(
            application("APPLICATION_SUBMITTED", false, LocalDateTime.now()),
            application("SHORTLISTED", true, LocalDateTime.now()),
            application("INTERVIEW_SCHEDULED", true, LocalDateTime.now()),
            application("OFFER_SENT", true, LocalDateTime.now()),
            application("OFFER_ACCEPTED", true, LocalDateTime.now())
        ));

        Map<String, Object> overview = service.getOverview();
        Map<String, Object> funnel = (Map<String, Object>) overview.get("conversionFunnel");

        assertEquals(5L, funnel.get("submitted"));
        assertEquals(4L, funnel.get("viewedByRecruiter"));
        assertEquals(4L, funnel.get("shortlisted"));
        assertEquals(3L, funnel.get("interviewScheduled"));
        assertEquals(2L, funnel.get("offerSent"));
        assertEquals(1L, funnel.get("offerAccepted"));
    }

    @Test
    void applicationsByDayZeroFillsTheTrailingThirtyDays() {
        when(jobPostingRepository.findAll()).thenReturn(List.of());
        when(applicationRepository.findAll()).thenReturn(List.of(
            application("APPLICATION_SUBMITTED", false, LocalDateTime.now())
        ));

        Map<String, Object> overview = service.getOverview();
        List<Map<String, Object>> trend = (List<Map<String, Object>>) overview.get("applicationsByDay");

        assertEquals(30, trend.size());
        assertEquals(LocalDate.now().toString(), trend.get(trend.size() - 1).get("date"));
        assertEquals(1L, trend.get(trend.size() - 1).get("count"));
    }
}
