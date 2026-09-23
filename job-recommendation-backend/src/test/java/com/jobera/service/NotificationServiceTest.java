package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock EmailService emailService;

    @Test
    void passwordResetEmailIncludesTheLink() {
        NotificationService service = new NotificationService(emailService);

        service.sendPasswordReset("a@b.com", "http://localhost:3000/reset-password?token=xyz");

        verify(emailService).send(eq("a@b.com"), org.mockito.ArgumentMatchers.anyString(),
            contains("http://localhost:3000/reset-password?token=xyz"));
    }

    @Test
    void applicationStatusEmailNamesTheJobAndStatus() {
        NotificationService service = new NotificationService(emailService);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

        service.sendApplicationStatusChanged("a@b.com", "Asha Rao", "Backend Developer", "TEST_PASSED");

        verify(emailService).send(eq("a@b.com"), contains("Backend Developer"), body.capture());
        assertTrue(body.getValue().contains("test passed"));
    }

    @Test
    void missingNameFallsBackToAGenericGreeting() {
        NotificationService service = new NotificationService(emailService);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

        service.sendWelcome("a@b.com", null);

        verify(emailService).send(eq("a@b.com"), org.mockito.ArgumentMatchers.anyString(), body.capture());
        assertTrue(body.getValue().contains("Hi there"));
    }

    @Test
    void jobPostingApprovedEmailNamesTheJob() {
        NotificationService service = new NotificationService(emailService);

        service.sendJobPostingApproved("recruiter@b.com", "Sam", "Backend Developer");

        verify(emailService).send(eq("recruiter@b.com"), contains("Backend Developer"),
            contains("approved"));
    }

    @Test
    void jobPostingRejectedEmailIncludesTheReasonWhenGiven() {
        NotificationService service = new NotificationService(emailService);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

        service.sendJobPostingRejected("recruiter@b.com", "Sam", "Backend Developer", "Missing a real description");

        verify(emailService).send(eq("recruiter@b.com"), contains("Backend Developer"), body.capture());
        assertTrue(body.getValue().contains("Missing a real description"));
    }

    @Test
    void jobPostingRejectedEmailOmitsTheReasonLineWhenNoneGiven() {
        NotificationService service = new NotificationService(emailService);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

        service.sendJobPostingRejected("recruiter@b.com", "Sam", "Backend Developer", null);

        verify(emailService).send(eq("recruiter@b.com"), contains("Backend Developer"), body.capture());
        assertTrue(!body.getValue().contains("Reason:"));
    }
}
