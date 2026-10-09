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
import com.jobera.entity.TestAssignment;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.TestAssignmentRepository;
import com.jobera.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PlatformAnalyticsServiceTest {

    @Mock UserRepository userRepository;
    @Mock JobPostingRepository jobPostingRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock TestAssignmentRepository testAssignmentRepository;

    PlatformAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new PlatformAnalyticsService(userRepository, jobPostingRepository, applicationRepository,
            testAssignmentRepository);
        when(userRepository.countByRoleId(1L)).thenReturn(10L);
        when(userRepository.countByRoleId(3L)).thenReturn(2L);
        when(userRepository.countByRoleId(2L)).thenReturn(1L);
        when(userRepository.count()).thenReturn(13L);
        when(userRepository.findUsersRegisteredAfter(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(testAssignmentRepository.findAll()).thenReturn(List.of());
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
    void aggregatesApplicationCountsBySource() {
        when(jobPostingRepository.findAll()).thenReturn(List.of());
        Application direct = application("APPLICATION_SUBMITTED", false, LocalDateTime.now());
        Application referral1 = application("APPLICATION_SUBMITTED", false, LocalDateTime.now());
        referral1.setSource(Application.SOURCE_REFERRAL);
        Application referral2 = application("APPLICATION_SUBMITTED", false, LocalDateTime.now());
        referral2.setSource(Application.SOURCE_REFERRAL);
        Application silverMedalist = application("APPLICATION_SUBMITTED", false, LocalDateTime.now());
        silverMedalist.setSource(Application.SOURCE_SILVER_MEDALIST);
        when(applicationRepository.findAll()).thenReturn(List.of(direct, referral1, referral2, silverMedalist));

        Map<String, Object> overview = service.getOverview();
        Map<String, Object> applications = (Map<String, Object>) overview.get("applications");
        Map<String, Long> bySource = (Map<String, Long>) applications.get("bySource");

        assertEquals(1L, bySource.get(Application.SOURCE_DIRECT));
        assertEquals(2L, bySource.get(Application.SOURCE_REFERRAL));
        assertEquals(1L, bySource.get(Application.SOURCE_SILVER_MEDALIST));
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

    @Test
    void groupsJobPostingsByExperienceLevelAndType() {
        JobPosting senior = job(JobPosting.MODERATION_APPROVED, true);
        senior.setExperienceLevel("Senior");
        senior.setJobType("Full-time");
        JobPosting entry = job(JobPosting.MODERATION_APPROVED, true);
        entry.setExperienceLevel("Entry");
        entry.setJobType("Internship");
        JobPosting unspecified = job(JobPosting.MODERATION_APPROVED, true);
        when(jobPostingRepository.findAll()).thenReturn(List.of(senior, entry, unspecified));
        when(applicationRepository.findAll()).thenReturn(List.of());

        Map<String, Object> overview = service.getOverview();
        Map<String, Long> byLevel = (Map<String, Long>) overview.get("jobPostingsByExperienceLevel");
        Map<String, Long> byType = (Map<String, Long>) overview.get("jobPostingsByType");

        assertEquals(1L, byLevel.get("Senior"));
        assertEquals(1L, byLevel.get("Entry"));
        assertEquals(1L, byLevel.get("Not specified"));
        assertEquals(1L, byType.get("Full-time"));
        assertEquals(1L, byType.get("Internship"));
    }

    @Test
    void computesRealAverageTimeToHireFromOfferTimestampsNotAHardcodedNumber() {
        when(jobPostingRepository.findAll()).thenReturn(List.of());
        Application offeredIn5Days = application("OFFER_SENT", true, LocalDateTime.now().minusDays(5));
        offeredIn5Days.setStatusUpdatedAt(LocalDateTime.now());
        Application stillPending = application("APPLICATION_SUBMITTED", false, LocalDateTime.now());
        when(applicationRepository.findAll()).thenReturn(List.of(offeredIn5Days, stillPending));

        Map<String, Object> overview = service.getOverview();
        Map<String, Object> timeToHire = (Map<String, Object>) overview.get("timeToHire");

        assertEquals(5.0, (double) timeToHire.get("averageDays"), 0.1);
        assertEquals(1L, timeToHire.get("sampleSize"));
    }

    @Test
    void timeToHireIsNullWithNoOffersYetRatherThanAFabricatedNumber() {
        when(jobPostingRepository.findAll()).thenReturn(List.of());
        when(applicationRepository.findAll()).thenReturn(List.of(
            application("APPLICATION_SUBMITTED", false, LocalDateTime.now())
        ));

        Map<String, Object> overview = service.getOverview();
        Map<String, Object> timeToHire = (Map<String, Object>) overview.get("timeToHire");

        assertEquals(null, timeToHire.get("averageDays"));
        assertEquals(0L, timeToHire.get("sampleSize"));
    }

    private TestAssignment proctoredAssignment(boolean terminated, int warningCount) {
        TestAssignment assignment = new TestAssignment();
        assignment.setProctoringActive(true);
        assignment.setProctoringTerminated(terminated);
        assignment.setWarningCount(warningCount);
        return assignment;
    }

    @Test
    void computesRealProctoringIntegrityFromViolationCountersNotAHardcodedNumber() {
        when(jobPostingRepository.findAll()).thenReturn(List.of());
        when(applicationRepository.findAll()).thenReturn(List.of());
        TestAssignment clean1 = proctoredAssignment(false, 0);
        TestAssignment clean2 = proctoredAssignment(false, 0);
        TestAssignment flagged = proctoredAssignment(false, 2);
        TestAssignment terminated = proctoredAssignment(true, 0);
        TestAssignment notProctored = new TestAssignment(); // proctoringActive defaults to false
        when(testAssignmentRepository.findAll()).thenReturn(List.of(clean1, clean2, flagged, terminated, notProctored));

        Map<String, Object> overview = service.getOverview();
        Map<String, Object> integrity = (Map<String, Object>) overview.get("proctoringIntegrity");

        assertEquals(4, integrity.get("totalProctoredSessions"));
        assertEquals(2L, integrity.get("cleanSessions"));
        assertEquals(50.0, (double) integrity.get("cleanPercentage"), 0.1);
    }

    @Test
    void proctoringIntegrityIsNullWithNoProctoredSessionsRatherThanAFabricatedNumber() {
        when(jobPostingRepository.findAll()).thenReturn(List.of());
        when(applicationRepository.findAll()).thenReturn(List.of());
        when(testAssignmentRepository.findAll()).thenReturn(List.of());

        Map<String, Object> overview = service.getOverview();
        Map<String, Object> integrity = (Map<String, Object>) overview.get("proctoringIntegrity");

        assertEquals(0, integrity.get("totalProctoredSessions"));
        assertEquals(null, integrity.get("cleanPercentage"));
    }

    @Test
    void rollsUpTheMostCommonlyMissingSkillsAcrossEveryApplicationPlatformWide() {
        when(jobPostingRepository.findAll()).thenReturn(List.of());
        Application appA = application("APPLICATION_SUBMITTED", false, LocalDateTime.now());
        appA.setMissingSkills("[\"Docker\", \"Kubernetes\"]");
        Application appB = application("APPLICATION_SUBMITTED", false, LocalDateTime.now());
        appB.setMissingSkills("[\"Docker\"]");
        when(applicationRepository.findAll()).thenReturn(List.of(appA, appB));

        Map<String, Object> overview = service.getOverview();
        List<Map<String, Object>> topMissingSkills = (List<Map<String, Object>>) overview.get("topMissingSkills");

        assertEquals("Docker", topMissingSkills.get(0).get("skill"));
        assertEquals(2, topMissingSkills.get(0).get("count"));
    }
}
