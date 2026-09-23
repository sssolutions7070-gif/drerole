package com.jobera.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.jobera.entity.JobPosting;

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

    public void sendJobPostingApproved(String toEmail, String fullName, String jobTitle) {
        emailService.send(toEmail, "Your job posting is now live: " + jobTitle,
            "Hi " + safe(fullName) + ",\n\n"
                + "\"" + jobTitle + "\" has been reviewed and approved. It's now visible to candidates on DreamRole.");
    }

    public void sendJobPostingRejected(String toEmail, String fullName, String jobTitle, String reason) {
        emailService.send(toEmail, "Your job posting needs changes: " + jobTitle,
            "Hi " + safe(fullName) + ",\n\n"
                + "\"" + jobTitle + "\" was not approved and is not visible to candidates.\n"
                + (reason != null && !reason.isBlank() ? "Reason: " + reason + "\n\n" : "\n")
                + "Log in to DreamRole to update and resubmit it.");
    }

    public void sendJobAlertDigest(String toEmail, String fullName, List<JobPosting> matchingJobs) {
        String jobList = matchingJobs.stream()
            .map(job -> "- " + job.getTitle() + " at " + job.getCompany()
                + (job.getLocation() != null ? " (" + job.getLocation() + ")" : ""))
            .collect(Collectors.joining("\n"));

        String subject = matchingJobs.size() == 1
            ? "A new job matches your skills"
            : matchingJobs.size() + " new jobs match your skills";

        emailService.send(toEmail, subject,
            "Hi " + safe(fullName) + ",\n\n"
                + "Since your last alert, these new postings matched the skills on your resume:\n\n"
                + jobList + "\n\n"
                + "Log in to DreamRole to view and apply.\n\n"
                + "You're receiving this because job alerts are turned on in your profile settings; "
                + "you can turn them off there at any time.");
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "there" : value;
    }

    private static String humanize(String status) {
        if (status == null) return "updated";
        return status.replace('_', ' ').toLowerCase();
    }
}
