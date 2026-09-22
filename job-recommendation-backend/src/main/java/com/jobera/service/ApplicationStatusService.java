package com.jobera.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jobera.entity.Application;
import com.jobera.repository.ApplicationRepository;

@Service
public class ApplicationStatusService {
    private static final Logger log = LoggerFactory.getLogger(ApplicationStatusService.class);


    private final ApplicationRepository applicationRepository;
    private final NotificationService notificationService;

    public ApplicationStatusService(ApplicationRepository applicationRepository,
                                    NotificationService notificationService) {
        this.applicationRepository = applicationRepository;
        this.notificationService = notificationService;
    }

    private String jobTitleOf(Application application) {
        return application.getJobPosting() != null ? application.getJobPosting().getTitle() : "your application";
    }

    private void notifyStatusChanged(Application application, String status) {
        notificationService.sendApplicationStatusChanged(
            application.getSafeApplicantEmail(), application.getSafeApplicantName(), jobTitleOf(application), status);
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
        notificationService.sendInterviewScheduled(saved.getSafeApplicantEmail(), saved.getSafeApplicantName(),
            jobTitleOf(saved), interviewDate != null ? interviewDate.toString() : "TBD",
            interviewLocation != null ? interviewLocation : "TBD");
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
    
    public static class ApplicationStatusException extends RuntimeException {
        public ApplicationStatusException(String message) {
            super(message);
        }
    }
}