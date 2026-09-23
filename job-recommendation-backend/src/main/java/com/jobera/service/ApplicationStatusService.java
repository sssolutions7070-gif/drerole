package com.jobera.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import java.util.Map;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.repository.ApplicationRepository;

@Service
public class ApplicationStatusService {
    private static final Logger log = LoggerFactory.getLogger(ApplicationStatusService.class);


    private final ApplicationRepository applicationRepository;
    private final NotificationService notificationService;
    private final RealtimeNotifier realtimeNotifier;

    public ApplicationStatusService(ApplicationRepository applicationRepository,
                                    NotificationService notificationService,
                                    RealtimeNotifier realtimeNotifier) {
        this.applicationRepository = applicationRepository;
        this.notificationService = notificationService;
        this.realtimeNotifier = realtimeNotifier;
    }

    private String jobTitleOf(Application application) {
        return application.getJobPosting() != null ? application.getJobPosting().getTitle() : "your application";
    }

    private void notifyStatusChanged(Application application, String status) {
        String jobTitle = jobTitleOf(application);
        notificationService.sendApplicationStatusChanged(
            application.getSafeApplicantEmail(), application.getSafeApplicantName(), jobTitle, status);
        pushRealtime(application, "APPLICATION_STATUS_CHANGED", Map.of(
            "applicationId", application.getId(), "jobTitle", jobTitle, "status", status));
    }

    private void pushRealtime(Application application, String type, Map<String, Object> data) {
        if (application.getUser() != null) {
            realtimeNotifier.notifyUser(application.getUser().getId(), type, data);
        }
    }
    
    public Application updateApplicationStatus(Long applicationId, String newStatus, String recruiterNotes) {
        Optional<Application> applicationOpt = applicationRepository.findById(applicationId);
        if (applicationOpt.isEmpty()) {
            throw new ApplicationStatusException("Application not found with ID: " + applicationId);
        }
        
        Application application = applicationOpt.get();
        application.setStatus(newStatus);
        application.setRecruiterNotes(recruiterNotes);
        application.setStatusUpdatedAt(LocalDateTime.now());
        
        return applicationRepository.save(application);
    }
    
    public Application markAsViewed(Long applicationId) {
        Optional<Application> applicationOpt = applicationRepository.findById(applicationId);
        if (applicationOpt.isEmpty()) {
            throw new ApplicationStatusException("Application not found with ID: " + applicationId);
        }
        
        Application application = applicationOpt.get();
        application.markAsViewed();
        
        return applicationRepository.save(application);
    }
    
    public Application shortlistApplication(Long applicationId, String notes) {
        Optional<Application> applicationOpt = applicationRepository.findById(applicationId);
        if (applicationOpt.isEmpty()) {
            throw new ApplicationStatusException("Application not found with ID: " + applicationId);
        }
        
        Application application = applicationOpt.get();
        application.shortlist();
        application.setRecruiterNotes(notes);

        Application saved = applicationRepository.save(application);
        notifyStatusChanged(saved, saved.getStatus());
        return saved;
    }

    public Application scheduleInterview(Long applicationId, LocalDateTime interviewDate,
                                       String interviewLocation, String notes) {
        Optional<Application> applicationOpt = applicationRepository.findById(applicationId);
        if (applicationOpt.isEmpty()) {
            throw new ApplicationStatusException("Application not found with ID: " + applicationId);
        }

        Application application = applicationOpt.get();
        application.scheduleInterview(interviewDate, interviewLocation);
        application.setRecruiterNotes(notes);

        Application saved = applicationRepository.save(application);
        String jobTitle = jobTitleOf(saved);
        String dateText = interviewDate != null ? interviewDate.toString() : "TBD";
        String locationText = interviewLocation != null ? interviewLocation : "TBD";
        notificationService.sendInterviewScheduled(
            saved.getSafeApplicantEmail(), saved.getSafeApplicantName(), jobTitle, dateText, locationText);
        pushRealtime(saved, "INTERVIEW_SCHEDULED", Map.of(
            "applicationId", saved.getId(), "jobTitle", jobTitle, "interviewDate", dateText, "interviewLocation", locationText));
        return saved;
    }

    public Application rejectApplication(Long applicationId, String rejectionReason) {
        Optional<Application> applicationOpt = applicationRepository.findById(applicationId);
        if (applicationOpt.isEmpty()) {
            throw new ApplicationStatusException("Application not found with ID: " + applicationId);
        }

        Application application = applicationOpt.get();
        application.reject();
        application.setRecruiterNotes(rejectionReason);

        Application saved = applicationRepository.save(application);
        notifyStatusChanged(saved, saved.getStatus());
        return saved;
    }

    public Application sendOffer(Long applicationId, String offerDetails) {
        Optional<Application> applicationOpt = applicationRepository.findById(applicationId);
        if (applicationOpt.isEmpty()) {
            throw new ApplicationStatusException("Application not found with ID: " + applicationId);
        }

        Application application = applicationOpt.get();
        application.sendOffer();
        application.setRecruiterNotes(offerDetails);

        Application saved = applicationRepository.save(application);
        notifyStatusChanged(saved, saved.getStatus());
        return saved;
    }
    
    public List<Application> getUserApplications(Long userId) {
        try {
            log.debug("=== GETTING APPLICATIONS FOR USER: " + userId + " ===");
            List<Application> applications = applicationRepository.findByUserId(userId);
            log.debug("✅ Found " + applications.size() + " applications");
            
            // Debug: Print each application
            for (Application app : applications) {
                log.debug("📄 Application ID: " + app.getId());
                log.debug("   Job: " + (app.getJobPosting() != null ? app.getJobPosting().getTitle() : "No job"));
                log.debug("   Status: " + app.getStatus());
                log.debug("   Applicant: " + app.getSafeApplicantName());
            }
            
            return applications;
        } catch (Exception e) {
            log.error("❌ ERROR in getUserApplications: " + e.getMessage());
            log.error("Unexpected error", e);
            throw new ApplicationStatusException("Failed to get user applications: " + e.getMessage());
        }
    }
    
    public List<Application> getJobApplications(Long jobId) {
        return applicationRepository.findByJobPostingId(jobId);
    }

    private static final String STATUS_SHORTLISTED = "SHORTLISTED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_OFFER_SENT = "OFFER_SENT";

    private static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * Aggregates the funnel a recruiter's applications are actually sitting in, plus two honest
     * timing metrics derivable from the timestamps already recorded: how long applications sit
     * before a recruiter first looks at them, and how long it takes from application to offer.
     * There's no separate status-history table, so this reflects current status, not a full
     * stage-by-stage history.
     */
    public Map<String, Object> getRecruiterAnalytics(List<JobPosting> jobs) {
        List<Map<String, Object>> perJob = new ArrayList<>();

        long totalApplications = 0;
        long totalViewed = 0;
        long totalOffers = 0;
        double sumMatchPercentage = 0;
        long matchCount = 0;
        double sumHoursToFirstView = 0;
        long viewedWithTimestamps = 0;
        double sumDaysToOffer = 0;
        long offersWithTimestamps = 0;

        for (JobPosting job : jobs) {
            List<Application> apps = getJobApplications(job.getId());

            long shortlisted = apps.stream().filter(a -> STATUS_SHORTLISTED.equals(a.getStatus())).count();
            long rejected = apps.stream().filter(a -> STATUS_REJECTED.equals(a.getStatus())).count();
            long offers = apps.stream().filter(a -> STATUS_OFFER_SENT.equals(a.getStatus())).count();
            double avgMatch = apps.stream()
                .filter(a -> a.getMatchPercentage() != null)
                .mapToDouble(Application::getMatchPercentage)
                .average().orElse(0.0);

            Map<String, Object> jobStats = new HashMap<>();
            jobStats.put("jobId", job.getId());
            jobStats.put("jobTitle", job.getTitle());
            jobStats.put("totalApplications", apps.size());
            jobStats.put("shortlisted", shortlisted);
            jobStats.put("rejected", rejected);
            jobStats.put("offers", offers);
            jobStats.put("averageMatchPercentage", round1(avgMatch));
            perJob.add(jobStats);

            totalApplications += apps.size();
            totalOffers += offers;

            for (Application a : apps) {
                if (Boolean.TRUE.equals(a.getViewedByRecruiter()) && a.getViewedAt() != null && a.getApplicationDate() != null) {
                    totalViewed++;
                    sumHoursToFirstView += Duration.between(a.getApplicationDate(), a.getViewedAt()).toMinutes() / 60.0;
                    viewedWithTimestamps++;
                }
                if (STATUS_OFFER_SENT.equals(a.getStatus()) && a.getStatusUpdatedAt() != null && a.getApplicationDate() != null) {
                    sumDaysToOffer += Duration.between(a.getApplicationDate(), a.getStatusUpdatedAt()).toHours() / 24.0;
                    offersWithTimestamps++;
                }
                if (a.getMatchPercentage() != null) {
                    sumMatchPercentage += a.getMatchPercentage();
                    matchCount++;
                }
            }
        }

        perJob.sort((a, b) -> Integer.compare((int) b.get("totalApplications"), (int) a.get("totalApplications")));

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalJobs", jobs.size());
        analytics.put("totalApplications", totalApplications);
        analytics.put("totalViewed", totalViewed);
        analytics.put("totalOffers", totalOffers);
        analytics.put("averageMatchPercentage", matchCount > 0 ? round1(sumMatchPercentage / matchCount) : null);
        analytics.put("averageHoursToFirstView", viewedWithTimestamps > 0 ? round1(sumHoursToFirstView / viewedWithTimestamps) : null);
        analytics.put("averageDaysToOffer", offersWithTimestamps > 0 ? round1(sumDaysToOffer / offersWithTimestamps) : null);
        analytics.put("jobs", perJob);
        return analytics;
    }

    public static class ApplicationStatusException extends RuntimeException {
        public ApplicationStatusException(String message) {
            super(message);
        }
    }
}