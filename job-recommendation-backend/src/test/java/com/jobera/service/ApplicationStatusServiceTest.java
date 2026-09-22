package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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

    ApplicationStatusService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationStatusService(applicationRepository, notificationService, realtimeNotifier);
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
}
