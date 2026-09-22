package com.jobera.service;

import org.springframework.stereotype.Service;

/** Composes the email content for each notifiable event; {@link EmailService} handles transport. */
@Service
public class NotificationService {

    private final EmailService emailService;

    public NotificationService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void sendPasswordReset(String toEmail, String resetLink) {
        emailService.send(toEmail, "Reset your DreamRole password",
            "We received a request to reset your DreamRole password.\n\n"
                + "Reset it here (valid for 30 minutes): " + resetLink + "\n\n"
                + "If you didn't request this, you can ignore this email - your password will stay unchanged.");
    }

    public void sendWelcome(String toEmail, String fullName) {
        emailService.send(toEmail, "Welcome to DreamRole",
            "Hi " + safe(fullName) + ",\n\n"
                + "Your DreamRole account is ready. Upload your resume to start getting job recommendations.\n\n"
                + "- The DreamRole team");
    }

    public void sendApplicationStatusChanged(String toEmail, String fullName, String jobTitle, String status) {
        emailService.send(toEmail, "Update on your application for " + jobTitle,
            "Hi " + safe(fullName) + ",\n\n"
                + "Your application for \"" + jobTitle + "\" has a new status: " + humanize(status) + ".\n\n"
                + "Log in to DreamRole to see the details.");
    }

    public void sendTestAssigned(String toEmail, String fullName, String testName, String jobTitle, String deadline) {
        emailService.send(toEmail, "A test has been assigned to you: " + testName,
            "Hi " + safe(fullName) + ",\n\n"
                + "You've been assigned \"" + testName + "\" for your application to \"" + jobTitle + "\".\n"
                + "Deadline: " + deadline + "\n\n"
                + "Log in to DreamRole to take the test.");
    }

    public void sendInterviewScheduled(String toEmail, String fullName, String jobTitle,
                                       String interviewDate, String interviewLocation) {
        emailService.send(toEmail, "Interview scheduled for " + jobTitle,
            "Hi " + safe(fullName) + ",\n\n"
                + "Your interview for \"" + jobTitle + "\" has been scheduled.\n"
                + "When: " + interviewDate + "\n"
                + "Where: " + interviewLocation + "\n\n"
                + "Log in to DreamRole for more details.");
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "there" : value;
    }

    private static String humanize(String status) {
        if (status == null) return "updated";
        return status.replace('_', ' ').toLowerCase();
    }
}
