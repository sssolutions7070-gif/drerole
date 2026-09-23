package com.jobera.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.jobera.entity.Application;
import com.jobera.entity.JobRating;
import com.jobera.entity.Message;
import com.jobera.entity.Resume;
import com.jobera.entity.SavedJob;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.InternshipRatingRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.JobRatingRepository;
import com.jobera.repository.MessageRepository;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.SavedJobRepository;
import com.jobera.repository.UserCertificationRepository;
import com.jobera.repository.UserEducationRepository;
import com.jobera.repository.UserExperienceRepository;
import com.jobera.repository.UserRepository;
import com.jobera.repository.UserSkillRepository;

/** Builds a self-service "download my data" export - everything a user has entered or that has
 *  accumulated against their account, in one machine-readable structure. Read-only; nothing here
 *  changes any data. */
@Service
public class DataExportService {

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(Long userId) {
            super("User not found: " + userId);
        }
    }

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserEducationRepository userEducationRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final UserCertificationRepository userCertificationRepository;
    private final ResumeRepository resumeRepository;
    private final ApplicationRepository applicationRepository;
    private final SavedJobRepository savedJobRepository;
    private final JobRatingRepository jobRatingRepository;
    private final InternshipRatingRepository internshipRatingRepository;
    private final MessageRepository messageRepository;
    private final JobPostingRepository jobPostingRepository;

    public DataExportService(UserRepository userRepository,
                              UserSkillRepository userSkillRepository,
                              UserEducationRepository userEducationRepository,
                              UserExperienceRepository userExperienceRepository,
                              UserCertificationRepository userCertificationRepository,
                              ResumeRepository resumeRepository,
                              ApplicationRepository applicationRepository,
                              SavedJobRepository savedJobRepository,
                              JobRatingRepository jobRatingRepository,
                              InternshipRatingRepository internshipRatingRepository,
                              MessageRepository messageRepository,
                              JobPostingRepository jobPostingRepository) {
        this.userRepository = userRepository;
        this.userSkillRepository = userSkillRepository;
        this.userEducationRepository = userEducationRepository;
        this.userExperienceRepository = userExperienceRepository;
        this.userCertificationRepository = userCertificationRepository;
        this.resumeRepository = resumeRepository;
        this.applicationRepository = applicationRepository;
        this.savedJobRepository = savedJobRepository;
        this.jobRatingRepository = jobRatingRepository;
        this.internshipRatingRepository = internshipRatingRepository;
        this.messageRepository = messageRepository;
        this.jobPostingRepository = jobPostingRepository;
    }

    public Map<String, Object> exportMyData(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("profile", profile(user));
        export.put("skills", userSkillRepository.findByUserId(userId));
        export.put("education", userEducationRepository.findByUserId(userId));
        export.put("experience", userExperienceRepository.findByUserId(userId));
        export.put("certifications", userCertificationRepository.findByUserId(userId));
        export.put("resumes", resumeRepository.findByUserId(userId).stream().map(this::resumeSummary).toList());
        export.put("applications", applicationRepository.findByUserId(userId).stream().map(this::applicationSummary).toList());
        export.put("savedJobs", savedJobRepository.findByUserIdOrderBySavedAtDesc(userId).stream().map(this::savedJobSummary).toList());
        export.put("jobRatingsGiven", jobRatingRepository.findByUserId(userId));
        export.put("internshipRatingsGiven", internshipRatingRepository.findByUserId(userId));
        export.put("messages", messageRepository.findBySenderIdOrRecipientIdOrderBySentAtAsc(userId, userId));
        return export;
    }

    private Map<String, Object> profile(User user) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("email", user.getEmail());
        profile.put("fullName", user.getFullName());
        profile.put("phone", user.getPhone());
        profile.put("dateOfBirth", user.getDateOfBirth());
        profile.put("location", user.getLocation());
        profile.put("bio", user.getBio());
        profile.put("headline", user.getHeadline());
        profile.put("website", user.getWebsite());
        profile.put("linkedin", user.getLinkedin());
        profile.put("github", user.getGithub());
        profile.put("jobAlertsEnabled", user.getJobAlertsEnabled());
        profile.put("createdAt", user.getCreatedAt());
        profile.put("lastLogin", user.getLastLogin());
        return profile;
    }

    private Map<String, Object> resumeSummary(Resume resume) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", resume.getId());
        summary.put("fileName", resume.getFileName());
        summary.put("uploadDate", resume.getUploadDate());
        summary.put("skills", resume.getSkills());
        summary.put("experienceYears", resume.getExperienceYears());
        summary.put("educationLevel", resume.getEducationLevel());
        return summary;
    }

    private Map<String, Object> applicationSummary(Application application) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", application.getId());
        summary.put("jobTitle", application.getJobPosting() != null ? application.getJobPosting().getTitle() : null);
        summary.put("company", application.getJobPosting() != null ? application.getJobPosting().getCompany() : null);
        summary.put("status", application.getStatus());
        summary.put("applicationDate", application.getApplicationDate());
        summary.put("statusUpdatedAt", application.getStatusUpdatedAt());
        summary.put("coverLetter", application.getCoverLetter());
        summary.put("matchPercentage", application.getMatchPercentage());
        return summary;
    }

    private Map<String, Object> savedJobSummary(SavedJob savedJob) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("jobId", savedJob.getJobPostingId());
        summary.put("savedAt", savedJob.getSavedAt());
        jobPostingRepository.findById(savedJob.getJobPostingId()).ifPresent(job -> {
            summary.put("jobTitle", job.getTitle());
            summary.put("company", job.getCompany());
        });
        return summary;
    }
}
