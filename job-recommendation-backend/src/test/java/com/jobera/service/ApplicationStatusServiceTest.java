package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class ApplicationStatusServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock NotificationService notificationService;
    @Mock RealtimeNotifier realtimeNotifier;
    @Mock NotificationFeedService notificationFeedService;

    ApplicationStatusService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationStatusService(applicationRepository, notificationService, realtimeNotifier,
            notificationFeedService);
    }

    private Application application(long id) {
        Application application = new Application();
        application.setId(id);
        application.setApplicantName("Asha Rao");
        application.setApplicantEmail("asha@example.com");
        User user = new User();
        user.setId(100L + id);
        application.setUser(user);
        JobPosting job = new JobPosting();
        job.setTitle("Backend Developer");
        application.setJobPosting(job);
        when(applicationRepository.save(application)).thenReturn(application);
        return application;
    }

    @Test
    void shortlistingEmailsAndPushesTheCandidate() {
        Application application = application(1L);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        service.shortlistApplication(1L, "great fit");

        verify(notificationService).sendApplicationStatusChanged(
            "asha@example.com", "Asha Rao", "Backend Developer", "SHORTLISTED");
        verify(realtimeNotifier).notifyUser(eq(101L), eq("APPLICATION_STATUS_CHANGED"), any(Map.class));
    }

    @Test
    void rejectingEmailsAndPushesTheCandidate() {
        Application application = application(2L);
        when(applicationRepository.findById(2L)).thenReturn(Optional.of(application));

        service.rejectApplication(2L, "not a fit");

        verify(notificationService).sendApplicationStatusChanged(
            "asha@example.com", "Asha Rao", "Backend Developer", "REJECTED");
        verify(realtimeNotifier).notifyUser(eq(102L), eq("APPLICATION_STATUS_CHANGED"), any(Map.class));
    }

    @Test
    void sendingAnOfferEmailsAndPushesTheCandidate() {
        Application application = application(3L);
        when(applicationRepository.findById(3L)).thenReturn(Optional.of(application));

        service.sendOffer(3L, "50 LPA");

        verify(notificationService).sendApplicationStatusChanged(
            "asha@example.com", "Asha Rao", "Backend Developer", "OFFER_SENT");
        verify(realtimeNotifier).notifyUser(eq(103L), eq("APPLICATION_STATUS_CHANGED"), any(Map.class));
    }

    @Test
    void schedulingAnInterviewSendsTheInterviewEmailAndPushWithDateAndLocation() {
        Application application = application(4L);
        when(applicationRepository.findById(4L)).thenReturn(Optional.of(application));
        LocalDateTime interviewDate = LocalDateTime.of(2026, 10, 1, 14, 0);

        service.scheduleInterview(4L, interviewDate, "Zoom", "bring laptop");

        verify(notificationService).sendInterviewScheduled(
            eq("asha@example.com"), eq("Asha Rao"), eq("Backend Developer"), eq(interviewDate.toString()), eq("Zoom"));
        verify(realtimeNotifier).notifyUser(eq(104L), eq("INTERVIEW_SCHEDULED"), any(Map.class));
    }

    @Test
    void externalCandidatesWithNoLinkedUserAreNotPushedTo() {
        Application application = application(5L);
        application.setUser(null);
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(application));

        service.shortlistApplication(5L, "great fit");

        verify(notificationService).sendApplicationStatusChanged(any(), any(), any(), any());
        verify(realtimeNotifier, never()).notifyUser(any(), any(), any());
    }

    @Test
    void unknownApplicationIdNeverTriggersAnEmailOrPush() {
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());

        try {
            service.rejectApplication(99L, "n/a");
        } catch (ApplicationStatusService.ApplicationStatusException expected) {
            // expected
        }

        verify(notificationService, never()).sendApplicationStatusChanged(any(), any(), any(), any());
        verify(realtimeNotifier, never()).notifyUser(any(), any(), any());
    }

    private Application analyticsApplication(String status, Double matchPercentage) {
        Application application = new Application();
        application.setStatus(status);
        application.setMatchPercentage(matchPercentage);
        return application;
    }

    @Test
    void analyticsAggregatesFunnelCountsAcrossAllOfARecruitersJobs() {
        JobPosting jobA = new JobPosting();
        jobA.setId(1L);
        jobA.setTitle("Backend Developer");
        JobPosting jobB = new JobPosting();
        jobB.setId(2L);
        jobB.setTitle("Frontend Developer");

        when(applicationRepository.findByJobPostingId(1L)).thenReturn(java.util.List.of(
            analyticsApplication("SHORTLISTED", 80.0),
            analyticsApplication("REJECTED", 30.0)
        ));
        when(applicationRepository.findByJobPostingId(2L)).thenReturn(java.util.List.of(
            analyticsApplication("OFFER_SENT", 90.0)
        ));

        Map<String, Object> analytics = service.getRecruiterAnalytics(java.util.List.of(jobA, jobB));

        assertEquals(2, analytics.get("totalJobs"));
        assertEquals(3L, analytics.get("totalApplications"));
        assertEquals(1L, analytics.get("totalOffers"));
        assertEquals(200.0 / 3.0, (double) analytics.get("averageMatchPercentage"), 0.2);
    }

    @Test
    void analyticsComputesAverageTimeToFirstView() {
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setTitle("Backend Developer");

        Application viewedQuickly = analyticsApplication("SHORTLISTED", 80.0);
        viewedQuickly.setApplicationDate(LocalDateTime.now().minusHours(10));
        viewedQuickly.setViewedByRecruiter(true);
        viewedQuickly.setViewedAt(LocalDateTime.now().minusHours(8)); // 2 hours later

        Application neverViewed = analyticsApplication("APPLICATION_SUBMITTED", 50.0);
        neverViewed.setApplicationDate(LocalDateTime.now().minusHours(5));

        when(applicationRepository.findByJobPostingId(1L)).thenReturn(java.util.List.of(viewedQuickly, neverViewed));

        Map<String, Object> analytics = service.getRecruiterAnalytics(java.util.List.of(job));

        assertEquals(1L, analytics.get("totalViewed"));
        assertEquals(2.0, (double) analytics.get("averageHoursToFirstView"), 0.1);
    }

    @Test
    void analyticsComputesAverageTimeToOffer() {
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setTitle("Backend Developer");

        Application offered = analyticsApplication("OFFER_SENT", 95.0);
        offered.setApplicationDate(LocalDateTime.now().minusDays(5));
        offered.setStatusUpdatedAt(LocalDateTime.now()); // 5 days later

        when(applicationRepository.findByJobPostingId(1L)).thenReturn(java.util.List.of(offered));

        Map<String, Object> analytics = service.getRecruiterAnalytics(java.util.List.of(job));

        assertEquals(5.0, (double) analytics.get("averageDaysToOffer"), 0.1);
    }

    @Test
    void analyticsHandlesARecruiterWithNoApplicationsGracefully() {
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setTitle("Backend Developer");

        when(applicationRepository.findByJobPostingId(1L)).thenReturn(java.util.List.of());

        Map<String, Object> analytics = service.getRecruiterAnalytics(java.util.List.of(job));

        assertEquals(0L, analytics.get("totalApplications"));
        assertEquals(null, analytics.get("averageMatchPercentage"));
        assertEquals(null, analytics.get("averageHoursToFirstView"));
        assertEquals(null, analytics.get("averageDaysToOffer"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void perJobBreakdownIsSortedByApplicationCountDescending() {
        JobPosting smallJob = new JobPosting();
        smallJob.setId(1L);
        smallJob.setTitle("Small Job");
        JobPosting bigJob = new JobPosting();
        bigJob.setId(2L);
        bigJob.setTitle("Big Job");

        when(applicationRepository.findByJobPostingId(1L)).thenReturn(java.util.List.of(
            analyticsApplication("APPLICATION_SUBMITTED", 50.0)
        ));
        when(applicationRepository.findByJobPostingId(2L)).thenReturn(java.util.List.of(
            analyticsApplication("APPLICATION_SUBMITTED", 50.0),
            analyticsApplication("SHORTLISTED", 70.0),
            analyticsApplication("REJECTED", 20.0)
        ));

        Map<String, Object> analytics = service.getRecruiterAnalytics(java.util.List.of(smallJob, bigJob));

        List<Map<String, Object>> perJob = (List<Map<String, Object>>) analytics.get("jobs");
        assertEquals("Big Job", perJob.get(0).get("jobTitle"));
        assertEquals("Small Job", perJob.get(1).get("jobTitle"));
    }

    // ----------------------------------------------------------------- withdrawApplication

    private Application applicationWithRecruiter(long id, long candidateUserId, long recruiterUserId, String status) {
        Application application = new Application();
        application.setId(id);
        application.setApplicantName("Asha Rao");
        application.setApplicantEmail("asha@example.com");
        application.setStatus(status);
        User candidate = new User();
        candidate.setId(candidateUserId);
        application.setUser(candidate);

        User recruiterUser = new User();
        recruiterUser.setId(recruiterUserId);
        com.jobera.entity.Recruiter recruiter = new com.jobera.entity.Recruiter();
        recruiter.setUser(recruiterUser);
        JobPosting job = new JobPosting();
        job.setTitle("Backend Developer");
        job.setRecruiter(recruiter);
        application.setJobPosting(job);

        org.mockito.Mockito.lenient().when(applicationRepository.save(application)).thenReturn(application);
        return application;
    }

    @Test
    void candidateCanWithdrawASubmittedApplication() {
        Application application = applicationWithRecruiter(1L, 10L, 20L, "APPLICATION_SUBMITTED");
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        Application result = service.withdrawApplication(1L, 10L);

        assertEquals("WITHDRAWN", result.getStatus());
        verify(realtimeNotifier).notifyUser(eq(20L), eq("APPLICATION_WITHDRAWN"), any(Map.class));
        verify(notificationFeedService).record(eq(20L), eq("APPLICATION_WITHDRAWN"), any(), any(), eq("/recruiter/dashboard"));
    }

    @Test
    void onlyTheApplicantCanWithdraw() {
        Application application = applicationWithRecruiter(1L, 10L, 20L, "APPLICATION_SUBMITTED");
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        org.junit.jupiter.api.Assertions.assertThrows(ApplicationStatusService.NotTheApplicantException.class,
            () -> service.withdrawApplication(1L, 999L));
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void anAcceptedOfferCannotBeWithdrawn() {
        Application application = applicationWithRecruiter(1L, 10L, 20L, "OFFER_ACCEPTED");
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        org.junit.jupiter.api.Assertions.assertThrows(ApplicationStatusService.CannotWithdrawException.class,
            () -> service.withdrawApplication(1L, 10L));
    }

    @Test
    void anAlreadyWithdrawnApplicationCannotBeWithdrawnAgain() {
        Application application = applicationWithRecruiter(1L, 10L, 20L, "WITHDRAWN");
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        org.junit.jupiter.api.Assertions.assertThrows(ApplicationStatusService.CannotWithdrawException.class,
            () -> service.withdrawApplication(1L, 10L));
    }

    @Test
    void withdrawingAMissingApplicationFails() {
        when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(ApplicationStatusService.ApplicationStatusException.class,
            () -> service.withdrawApplication(999L, 10L));
    }

    @Test
    void sendFollowUpIncrementsCountAndRecordsNotification() {
        Application application = applicationWithRecruiter(1L, 10L, 20L, "APPLICATION_SUBMITTED");
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));

        Application updated = service.sendFollowUp(1L, "INTERVIEW_REMINDER", null);

        assertNotNull(updated);
        assertEquals(1, updated.getFollowUpCount());
        assertNotNull(updated.getLastFollowUpAt());
        verify(notificationFeedService).record(eq(10L), eq("APPLICATION_FOLLOW_UP"), anyString(), anyString(), eq("/applications"));
    }
}
