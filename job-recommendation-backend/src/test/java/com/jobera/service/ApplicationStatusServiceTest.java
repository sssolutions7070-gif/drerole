package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class ApplicationStatusServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock NotificationService notificationService;

    ApplicationStatusService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationStatusService(applicationRepository, notificationService);
    }

    private Application application(long id) {
        Application application = new Application();
        application.setId(id);
        application.setApplicantName("Asha Rao");
        application.setApplicantEmail("asha@example.com");
        JobPosting job = new JobPosting();
        job.setTitle("Backend Developer");
        application.setJobPosting(job);
        when(applicationRepository.save(application)).thenReturn(application);
        return application;
    }

    @Test
    void shortlistingEmailsTheCandidate() {
        Application application = application(1L);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        service.shortlistApplication(1L, "great fit");

        verify(notificationService).sendApplicationStatusChanged(
            "asha@example.com", "Asha Rao", "Backend Developer", "SHORTLISTED");
    }

    @Test
    void rejectingEmailsTheCandidate() {
        Application application = application(2L);
        when(applicationRepository.findById(2L)).thenReturn(Optional.of(application));

        service.rejectApplication(2L, "not a fit");

        verify(notificationService).sendApplicationStatusChanged(
            "asha@example.com", "Asha Rao", "Backend Developer", "REJECTED");
    }

    @Test
    void sendingAnOfferEmailsTheCandidate() {
        Application application = application(3L);
        when(applicationRepository.findById(3L)).thenReturn(Optional.of(application));

        service.sendOffer(3L, "50 LPA");

        verify(notificationService).sendApplicationStatusChanged(
            "asha@example.com", "Asha Rao", "Backend Developer", "OFFER_SENT");
    }

    @Test
    void schedulingAnInterviewSendsTheInterviewEmailWithDateAndLocation() {
        Application application = application(4L);
        when(applicationRepository.findById(4L)).thenReturn(Optional.of(application));
        LocalDateTime interviewDate = LocalDateTime.of(2026, 10, 1, 14, 0);

        service.scheduleInterview(4L, interviewDate, "Zoom", "bring laptop");

        verify(notificationService).sendInterviewScheduled(
            eq("asha@example.com"), eq("Asha Rao"), eq("Backend Developer"), eq(interviewDate.toString()), eq("Zoom"));
    }

    @Test
    void unknownApplicationIdNeverTriggersAnEmail() {
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());

        try {
            service.rejectApplication(99L, "n/a");
        } catch (ApplicationStatusService.ApplicationStatusException expected) {
            // expected
        }

        verify(notificationService, org.mockito.Mockito.never())
            .sendApplicationStatusChanged(any(), any(), any(), any());
    }
}
