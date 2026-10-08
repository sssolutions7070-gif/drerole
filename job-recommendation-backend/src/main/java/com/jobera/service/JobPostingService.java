package com.jobera.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.entity.Webhook;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.ReferralRepository;
import com.jobera.repository.SavedJobRepository;
import com.jobera.repository.UserRepository;

@Service
public class JobPostingService {
    private static final Logger log = LoggerFactory.getLogger(JobPostingService.class);


    private final JobPostingRepository jobPostingRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final RecruiterRepository recruiterRepository;
    private final SavedJobRepository savedJobRepository;
    private final SubscriptionService subscriptionService;
    private final WebhookService webhookService;
    private final SilverMedalistService silverMedalistService;
    private final ReferralRepository referralRepository;
    private final ObjectMapper objectMapper;

    public JobPostingService(JobPostingRepository jobPostingRepository,
                           ApplicationRepository applicationRepository,
                           UserRepository userRepository,
                           RecruiterRepository recruiterRepository,
                           SavedJobRepository savedJobRepository,
                           SubscriptionService subscriptionService,
                           WebhookService webhookService,
                           SilverMedalistService silverMedalistService,
                           ReferralRepository referralRepository) {
        this.jobPostingRepository = jobPostingRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.recruiterRepository = recruiterRepository;
        this.savedJobRepository = savedJobRepository;
        this.subscriptionService = subscriptionService;
        this.webhookService = webhookService;
        this.silverMedalistService = silverMedalistService;
        this.referralRepository = referralRepository;
        this.objectMapper = new ObjectMapper();
    }

    private void dispatchApplicationSubmitted(Application application, JobPosting job) {
        if (job.getRecruiter() == null) {
            return;
        }
        webhookService.dispatch(job.getRecruiter().getId(), Webhook.EVENT_APPLICATION_SUBMITTED, Map.of(
            "applicationId", application.getId(),
            "jobId", job.getId(),
            "jobTitle", job.getTitle(),
            "applicantName", application.getSafeApplicantName()
        ));
    }
    
    public List<JobPosting> getActiveJobPostings() {
        return getActiveJobPostingsSortedByDate();
    }
    
    public List<JobPosting> searchJobs(String keyword) {
        return jobPostingRepository.searchActiveJobs(keyword);
    }
    
    public Optional<JobPosting> getJobPostingById(Long id) {
        return jobPostingRepository.findById(id);
    }
    
    // Updated method with additional applicant information and enhanced logging
    public void applyForJob(Long userId, Long jobId, String coverLetter,
                           String applicantSkills, Integer applicantExperience,
                           String applicantEducation) {
        applyForJob(userId, jobId, coverLetter, applicantSkills, applicantExperience, applicantEducation, null);
    }

    public void applyForJob(Long userId, Long jobId, String coverLetter,
                           String applicantSkills, Integer applicantExperience,
                           String applicantEducation, String requestedSource) {
        log.debug("=== JOB POSTING SERVICE - APPLY FOR JOB ===");
        log.debug("User ID: " + userId);
        log.debug("Job ID: " + jobId);
        log.debug("Cover Letter: " + (coverLetter != null ? coverLetter.substring(0, Math.min(50, coverLetter.length())) + "..." : "null"));
        log.debug("Skills: " + applicantSkills);
        log.debug("Experience: " + applicantExperience);
        log.debug("Education: " + applicantEducation);
        
        Optional<User> user = userRepository.findById(userId);
        Optional<JobPosting> job = jobPostingRepository.findById(jobId);
        
        log.debug("User found: " + user.isPresent());
        log.debug("Job found: " + job.isPresent());
        
        if (user.isPresent() && job.isPresent()) {
            try {
                log.debug("Creating Application entity...");
                Application application = new Application(
                    user.get(), 
                    job.get(), 
                    coverLetter,
                    applicantSkills,
                    applicantExperience,
                    applicantEducation
                );
                application.setSource(resolveApplicationSource(requestedSource, userId));

                log.debug("Application entity created, saving to database...");
                Application savedApplication = applicationRepository.save(application);
                log.debug("✅ Application saved successfully with ID: " + savedApplication.getId());
                log.debug("✅ Applicant Name: " + savedApplication.getApplicantName());
                log.debug("✅ Applicant Email: " + savedApplication.getApplicantEmail());
                log.debug("✅ Application Date: " + savedApplication.getApplicationDate());
                log.debug("✅ Status: " + savedApplication.getStatus());
                dispatchApplicationSubmitted(savedApplication, job.get());

            } catch (Exception e) {
                log.error("❌ Error saving application: " + e.getMessage());
                log.error("Unexpected error", e);
                throw new JobApplicationException("Failed to save application: " + e.getMessage());
            }
        } else {
            String errorMsg = "User or Job not found with ID: User=" + userId + ", Job=" + jobId;
            log.error("❌ " + errorMsg);
            if (user.isEmpty()) {
                log.error("❌ User not found in database");
            }
            if (job.isEmpty()) {
                log.error("❌ Job not found in database");
            }
            throw new JobApplicationException(errorMsg);
        }
    }

    // ENHANCED: Apply for job with resume matching
    public Application applyForJobWithResume(Long userId, Long jobId, String coverLetter,
                                            String applicantSkills, Integer applicantExperience,
                                            String applicantEducation, String resumeFilePath,
                                            String resumeParsedText) {
        return applyForJobWithResume(userId, jobId, coverLetter, applicantSkills, applicantExperience,
            applicantEducation, resumeFilePath, resumeParsedText, null);
    }

    public Application applyForJobWithResume(Long userId, Long jobId, String coverLetter,
                                            String applicantSkills, Integer applicantExperience,
                                            String applicantEducation, String resumeFilePath,
                                            String resumeParsedText, String requestedSource) {
        log.debug("=== JOB POSTING SERVICE - APPLY FOR JOB WITH RESUME ===");
        log.debug("User ID: " + userId);
        log.debug("Job ID: " + jobId);
        log.debug("Resume File Path: " + resumeFilePath);
        
        Optional<User> user = userRepository.findById(userId);
        Optional<JobPosting> job = jobPostingRepository.findById(jobId);
        
        log.debug("User found: " + user.isPresent());
        log.debug("Job found: " + job.isPresent());
        
        if (user.isPresent() && job.isPresent()) {
            try {
                log.debug("Creating Application entity with resume data...");
                Application application = new Application(
                    user.get(), 
                    job.get(), 
                    coverLetter,
                    applicantSkills,
                    applicantExperience,
                    applicantEducation
                );
                application.setSource(resolveApplicationSource(requestedSource, userId));

                // Set resume data
                application.setResumeFilePath(resumeFilePath);
                application.setResumeParsedText(resumeParsedText);
                
                // Calculate match percentage
                double matchPercentage = calculateResumeMatchPercentage(job.get(), resumeParsedText, applicantSkills);
                application.setMatchPercentage(matchPercentage);
                
                // Calculate matched and missing skills
                Map<String, Object> skillsAnalysis = analyzeSkillsMatch(job.get(), applicantSkills, resumeParsedText);
                application.setMatchedSkills(objectMapper.writeValueAsString(skillsAnalysis.get("matchedSkills")));
                application.setMissingSkills(objectMapper.writeValueAsString(skillsAnalysis.get("missingSkills")));
                
                log.debug("Application entity created, saving to database...");
                Application savedApplication = applicationRepository.save(application);
                log.debug("✅ Application saved successfully with ID: " + savedApplication.getId());
                log.debug("✅ Match Percentage: " + savedApplication.getMatchPercentage() + "%");
                log.debug("✅ Applicant Name: " + savedApplication.getApplicantName());
                dispatchApplicationSubmitted(savedApplication, job.get());

                return savedApplication;
                
            } catch (Exception e) {
                log.error("❌ Error saving application: " + e.getMessage());
                log.error("Unexpected error", e);
                throw new JobApplicationException("Failed to save application: " + e.getMessage());
            }
        } else {
            String errorMsg = "User or Job not found with ID: User=" + userId + ", Job=" + jobId;
            log.error("❌ " + errorMsg);
            throw new JobApplicationException(errorMsg);
        }
    }

    private static final Set<String> VALID_APPLICATION_SOURCES = Set.of(
        Application.SOURCE_DIRECT, Application.SOURCE_SILVER_MEDALIST, Application.SOURCE_REFERRAL);

    /** An explicit, recognized source (e.g. a candidate clicking through a silver-medalist
     *  notification) wins; otherwise, an account that was itself referred counts as a referral
     *  hire; otherwise it's a plain direct application. */
    private String resolveApplicationSource(String requestedSource, Long userId) {
        if (requestedSource != null) {
            String normalized = requestedSource.trim().toUpperCase();
            if (VALID_APPLICATION_SOURCES.contains(normalized)) {
                return normalized;
            }
        }
        if (referralRepository.existsByReferredUserId(userId)) {
            return Application.SOURCE_REFERRAL;
        }
        return Application.SOURCE_DIRECT;
    }

    // Calculate resume match percentage
    private double calculateResumeMatchPercentage(JobPosting job, String resumeText, String applicantSkillsJson) {
        try {
            log.debug("=== CALCULATING RESUME MATCH PERCENTAGE ===");
            
            // Parse required skills from job
            Set<String> requiredSkills = parseSkillsFromJson(job.getRequiredSkills());
            Set<String> preferredSkills = parseSkillsFromJson(job.getPreferredSkills() != null ? job.getPreferredSkills() : "[]");
            
            // Parse applicant skills
            Set<String> applicantSkills = parseSkillsFromJson(applicantSkillsJson);
            
            // Extract skills from resume text
            Set<String> resumeSkills = extractSkillsFromText(resumeText);
            
            // Combine all applicant skills
            Set<String> allApplicantSkills = new HashSet<>();
            allApplicantSkills.addAll(applicantSkills);
            allApplicantSkills.addAll(resumeSkills);
            
            log.debug("Required Skills: " + requiredSkills.size());
            log.debug("Applicant Skills: " + allApplicantSkills.size());
            
            if (requiredSkills.isEmpty()) {
                return 50.0; // Default score if no required skills specified
            }
            
            // Calculate match for required skills (70% weight)
            double requiredMatchScore = calculateSkillsMatchScore(requiredSkills, allApplicantSkills);
            
            // Calculate match for preferred skills (30% weight)
            double preferredMatchScore = preferredSkills.isEmpty() ? 1.0 : calculateSkillsMatchScore(preferredSkills, allApplicantSkills);
            
            // Calculate experience match (bonus)
            double experienceBonus = calculateExperienceBonus(job, allApplicantSkills);
            
            double totalScore = (requiredMatchScore * 0.7) + (preferredMatchScore * 0.3) + experienceBonus;
            
            // Convert to percentage and cap at 100%
            double percentage = Math.min(totalScore * 100, 100.0);
            
            log.debug("Required Match: " + (requiredMatchScore * 100) + "%");
            log.debug("Preferred Match: " + (preferredMatchScore * 100) + "%");
            log.debug("Experience Bonus: " + (experienceBonus * 100) + "%");
            log.debug("Final Match Percentage: " + percentage + "%");
            
            return percentage;
            
        } catch (Exception e) {
            log.error("Error calculating match percentage: " + e.getMessage());
            return 0.0;
        }
    }

    // Calculate skills match score
    private double calculateSkillsMatchScore(Set<String> jobSkills, Set<String> applicantSkills) {
        if (jobSkills.isEmpty()) return 0.0;
        
        long matchedCount = jobSkills.stream()
            .filter(jobSkill -> applicantSkills.stream()
                .anyMatch(applicantSkill -> calculateSkillSimilarity(jobSkill, applicantSkill) > 0.7))
            .count();
        
        return (double) matchedCount / jobSkills.size();
    }

    // Calculate skill similarity
    private double calculateSkillSimilarity(String skill1, String skill2) {
        String s1 = skill1.toLowerCase();
        String s2 = skill2.toLowerCase();
        
        // Exact match
        if (s1.equals(s2)) return 1.0;
        
        // Contains match
        if (s1.contains(s2) || s2.contains(s1)) return 0.9;
        
        // Common variations
        if ((s1.equals("js") && s2.equals("javascript")) || 
            (s2.equals("js") && s1.equals("javascript"))) return 0.95;
        
        if ((s1.equals("react") && s2.equals("reactjs")) || 
            (s2.equals("react") && s1.equals("reactjs"))) return 0.9;
        
        return 0.0;
    }

    // Extract skills from text
    private Set<String> extractSkillsFromText(String text) {
        Set<String> skills = new HashSet<>();
        if (text == null || text.trim().isEmpty()) return skills;
        
        String lowerText = text.toLowerCase();
        
        // Common technical skills to look for
        Set<String> technicalSkills = Set.of(
            "java", "python", "javascript", "typescript", "react", "angular", "vue", "node.js",
            "spring boot", "spring", "hibernate", "mysql", "postgresql", "mongodb",
            "aws", "azure", "docker", "kubernetes", "jenkins", "git", "rest api",
            "microservices", "machine learning", "ai", "data analysis", "sql",
            "html", "css", "express.js", "django", "flask", "c++", "c#", "ruby"
        );
        
        for (String skill : technicalSkills) {
            if (lowerText.contains(skill)) {
                skills.add(skill);
            }
        }
        
        return skills;
    }

    // Calculate experience bonus
    private double calculateExperienceBonus(JobPosting job, Set<String> applicantSkills) {
        // Simple bonus based on seniority indicators in skills
        long seniorSkills = applicantSkills.stream()
            .filter(skill -> Set.of("architecture", "leadership", "management", "mentoring", 
                                   "kubernetes", "docker", "aws", "azure", "microservices")
                    .contains(skill))
            .count();
        
        return Math.min(seniorSkills * 0.05, 0.15); // Max 15% bonus
    }

    // Analyze skills match
    private Map<String, Object> analyzeSkillsMatch(JobPosting job, String applicantSkillsJson, String resumeText) {
        Map<String, Object> analysis = new HashMap<>();
        
        try {
            Set<String> requiredSkills = parseSkillsFromJson(job.getRequiredSkills());
            Set<String> preferredSkills = parseSkillsFromJson(job.getPreferredSkills() != null ? job.getPreferredSkills() : "[]");
            Set<String> applicantSkills = parseSkillsFromJson(applicantSkillsJson);
            Set<String> resumeSkills = extractSkillsFromText(resumeText);
            
            // Combine all applicant skills
            Set<String> allApplicantSkills = new HashSet<>();
            allApplicantSkills.addAll(applicantSkills);
            allApplicantSkills.addAll(resumeSkills);
            
            // Find matched skills
            Set<String> matchedSkills = requiredSkills.stream()
                .filter(jobSkill -> allApplicantSkills.stream()
                    .anyMatch(applicantSkill -> calculateSkillSimilarity(jobSkill, applicantSkill) > 0.7))
                .collect(Collectors.toSet());
            
            // Find missing skills
            Set<String> missingSkills = requiredSkills.stream()
                .filter(jobSkill -> matchedSkills.stream()
                    .noneMatch(matchedSkill -> calculateSkillSimilarity(jobSkill, matchedSkill) > 0.7))
                .collect(Collectors.toSet());
            
            analysis.put("matchedSkills", matchedSkills);
            analysis.put("missingSkills", missingSkills);
            analysis.put("applicantSkills", allApplicantSkills);
            
        } catch (Exception e) {
            log.error("Error analyzing skills match: " + e.getMessage());
            analysis.put("matchedSkills", new HashSet<>());
            analysis.put("missingSkills", new HashSet<>());
            analysis.put("applicantSkills", new HashSet<>());
        }
        
        return analysis;
    }

    // Parse skills from JSON
    private Set<String> parseSkillsFromJson(String skillsJson) {
        try {
            if (skillsJson != null && !skillsJson.isEmpty()) {
                return objectMapper.readValue(skillsJson, new TypeReference<Set<String>>() {});
            }
        } catch (Exception e) {
            log.error("Failed to parse skills JSON: " + skillsJson);
        }
        return new HashSet<>();
    }

    // Add these methods to your existing JobPostingService class
    public Application updateApplicationStatus(Long applicationId, Long recruiterId, String newStatus, String notes) {
        Optional<Application> applicationOpt = applicationRepository.findById(applicationId);
        if (applicationOpt.isEmpty()) {
            throw new JobPostingException("Application not found with ID: " + applicationId);
        }
        
        Application application = applicationOpt.get();
        
        // Verify the recruiter owns this job
        if (application.getJobPosting().getRecruiter() == null || 
            !application.getJobPosting().getRecruiter().getId().equals(recruiterId)) {
            throw new JobPostingException("You are not authorized to update this application");
        }
        
        application.setStatus(newStatus);
        application.setRecruiterNotes(notes);
        application.setStatusUpdatedAt(LocalDateTime.now());
        
        return applicationRepository.save(application);
    }

    public Application markApplicationAsViewed(Long applicationId, Long recruiterId) {
        Optional<Application> applicationOpt = applicationRepository.findById(applicationId);
        if (applicationOpt.isEmpty()) {
            throw new JobPostingException("Application not found with ID: " + applicationId);
        }
        
        Application application = applicationOpt.get();
        
        // Verify the recruiter owns this job
        if (application.getJobPosting().getRecruiter() == null || 
            !application.getJobPosting().getRecruiter().getId().equals(recruiterId)) {
            throw new JobPostingException("You are not authorized to update this application");
        }
        
        application.markAsViewed();
        
        return applicationRepository.save(application);
    }
    
    // Keep the old method for backward compatibility
    public void applyForJob(Long userId, Long jobId, String coverLetter) {
        applyForJob(userId, jobId, coverLetter, null, null, null);
    }
    
    // New methods for recruiter functionality
    
    public JobPosting createJobPosting(Long recruiterId, String title, String company, String description,
                                      String requiredSkills, String preferredSkills, String location,
                                      String salaryRange, String jobType, String experienceLevel) {
        return createJobPosting(recruiterId, title, company, description, requiredSkills, preferredSkills,
            location, salaryRange, jobType, experienceLevel, null);
    }

    /** @param scheduledFor if set, the posting still goes through moderation immediately like any
     *   other, but stays out of every public listing until this moment arrives, even once
     *   approved - see JobPostingRepository's public listing queries. Must be in the future. */
    public JobPosting createJobPosting(Long recruiterId, String title, String company, String description,
                                      String requiredSkills, String preferredSkills, String location,
                                      String salaryRange, String jobType, String experienceLevel,
                                      LocalDateTime scheduledFor) {
        Optional<Recruiter> recruiter = recruiterRepository.findById(recruiterId);
        if (recruiter.isEmpty()) {
            throw new JobPostingException("Recruiter not found with ID: " + recruiterId);
        }
        subscriptionService.enforceActiveJobLimit(recruiter.get());
        if (scheduledFor != null && !scheduledFor.isAfter(LocalDateTime.now())) {
            throw new JobPostingException("scheduledFor must be in the future");
        }

        JobPosting jobPosting = new JobPosting();
        jobPosting.setTitle(title);
        jobPosting.setCompany(company);
        jobPosting.setDescription(description);
        jobPosting.setRequiredSkills(requiredSkills);
        jobPosting.setPreferredSkills(preferredSkills);
        jobPosting.setLocation(location);
        jobPosting.setSalaryRange(salaryRange);
        jobPosting.setJobType(jobType);
        jobPosting.setExperienceLevel(experienceLevel);
        jobPosting.setRecruiter(recruiter.get());
        jobPosting.setIsActive(true);
        jobPosting.setModerationStatus(JobPosting.MODERATION_PENDING);
        jobPosting.setScheduledFor(scheduledFor);

        return jobPostingRepository.save(jobPosting);
    }

    public List<JobPosting> getPendingJobPostings() {
        return jobPostingRepository.findByModerationStatusOrderByPostedDateDesc(JobPosting.MODERATION_PENDING);
    }

    public JobPosting approveJobPosting(Long jobId) {
        JobPosting jobPosting = jobPostingRepository.findById(jobId)
            .orElseThrow(() -> new JobPostingException("Job posting not found with ID: " + jobId));
        jobPosting.setModerationStatus(JobPosting.MODERATION_APPROVED);
        jobPosting.setRejectionReason(null);
        JobPosting saved = jobPostingRepository.save(jobPosting);

        try {
            silverMedalistService.notifyMatchingPastCandidates(saved);
        } catch (RuntimeException e) {
            log.warn("Silver-medalist notification failed for job {}: {}", jobId, e.getMessage());
        }

        return saved;
    }

    /** Clones a job posting's content as a fresh draft for a recruiter re-hiring a similar role, so
     *  they don't have to retype title/description/skills/salary. The copy starts pending moderation
     *  like any new posting; it isn't linked to the original's applications or history. */
    public JobPosting duplicateJobPosting(Long jobId, Long recruiterId) {
        JobPosting original = jobPostingRepository.findById(jobId)
            .orElseThrow(() -> new JobPostingException("Job posting not found with ID: " + jobId));

        if (original.getRecruiter() == null || !original.getRecruiter().getId().equals(recruiterId)) {
            throw new JobPostingException("You are not authorized to duplicate this job posting");
        }
        subscriptionService.enforceActiveJobLimit(original.getRecruiter());

        JobPosting copy = new JobPosting();
        copy.setTitle(original.getTitle() + " (Copy)");
        copy.setCompany(original.getCompany());
        copy.setDescription(original.getDescription());
        copy.setRequiredSkills(original.getRequiredSkills());
        copy.setPreferredSkills(original.getPreferredSkills());
        copy.setLocation(original.getLocation());
        copy.setSalaryRange(original.getSalaryRange());
        copy.setJobType(original.getJobType());
        copy.setExperienceLevel(original.getExperienceLevel());
        copy.setRecruiter(original.getRecruiter());
        copy.setIsActive(true);
        copy.setModerationStatus(JobPosting.MODERATION_PENDING);

        return jobPostingRepository.save(copy);
    }

    public JobPosting rejectJobPosting(Long jobId, String reason) {
        JobPosting jobPosting = jobPostingRepository.findById(jobId)
            .orElseThrow(() -> new JobPostingException("Job posting not found with ID: " + jobId));
        jobPosting.setModerationStatus(JobPosting.MODERATION_REJECTED);
        jobPosting.setRejectionReason(reason);
        return jobPostingRepository.save(jobPosting);
    }
    
    public JobPosting updateJobPosting(Long jobId, Long recruiterId, String title, String description,
                                      String requiredSkills, String preferredSkills, String location,
                                      String salaryRange, String jobType, String experienceLevel, Boolean isActive) {
        Optional<JobPosting> jobOpt = jobPostingRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            throw new JobPostingException("Job posting not found with ID: " + jobId);
        }
        
        JobPosting jobPosting = jobOpt.get();
        
        // Verify the recruiter owns this job posting
        if (jobPosting.getRecruiter() == null || !jobPosting.getRecruiter().getId().equals(recruiterId)) {
            throw new JobPostingException("You are not authorized to update this job posting");
        }
        
        jobPosting.setTitle(title);
        jobPosting.setDescription(description);
        jobPosting.setRequiredSkills(requiredSkills);
        jobPosting.setPreferredSkills(preferredSkills);
        jobPosting.setLocation(location);
        jobPosting.setSalaryRange(salaryRange);
        jobPosting.setJobType(jobType);
        jobPosting.setExperienceLevel(experienceLevel);

        // A rejected posting goes back into the review queue once edited, so a recruiter can fix
        // what was wrong and resubmit. An already-approved posting is left alone on edit - it
        // shouldn't vanish from public listings just because the recruiter fixed a typo.
        if (JobPosting.MODERATION_REJECTED.equals(jobPosting.getModerationStatus())) {
            jobPosting.setModerationStatus(JobPosting.MODERATION_PENDING);
            jobPosting.setRejectionReason(null);
        }

        if (isActive != null) {
            jobPosting.setIsActive(isActive);
        }
        
        return jobPostingRepository.save(jobPosting);
    }
    
    public void deleteJobPosting(Long jobId, Long recruiterId) {
        Optional<JobPosting> jobOpt = jobPostingRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            throw new JobPostingException("Job posting not found with ID: " + jobId);
        }
        
        JobPosting jobPosting = jobOpt.get();
        
        // Verify the recruiter owns this job posting
        if (jobPosting.getRecruiter() == null || !jobPosting.getRecruiter().getId().equals(recruiterId)) {
            throw new JobPostingException("You are not authorized to delete this job posting");
        }
        
        savedJobRepository.deleteByJobPostingId(jobId);
        jobPostingRepository.delete(jobPosting);
    }

    public List<JobPosting> getJobsByRecruiter(Long recruiterId) {
        return jobPostingRepository.findByRecruiterIdAndIsActiveTrue(recruiterId);
    }
    
    // ENHANCED METHOD - FIXED VERSION
    public List<Application> getApplicationsForJob(Long jobId, Long recruiterId) {
        log.debug("=== GET APPLICATIONS FOR JOB - ENHANCED ===");
        log.debug("Job ID: " + jobId);
        log.debug("Recruiter ID: " + recruiterId);
        
        Optional<JobPosting> jobOpt = jobPostingRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.error("❌ Job posting not found with ID: " + jobId);
            throw new JobPostingException("Job posting not found with ID: " + jobId);
        }
        
        JobPosting jobPosting = jobOpt.get();
        log.debug("✅ Found job: " + jobPosting.getTitle() + " by " + jobPosting.getCompany());
        
        // Check if job has recruiter
        if (jobPosting.getRecruiter() == null) {
            log.error("❌ Job has no recruiter assigned. Job ID: " + jobId);
            log.warn("⚠️  This might be a sample job created during initialization");
            // Return empty list instead of throwing exception for sample jobs
            return List.of();
        }
        
        log.debug("Job Recruiter ID: " + jobPosting.getRecruiter().getId());
        log.debug("Requested Recruiter ID: " + recruiterId);
        
        // Verify the recruiter owns this job posting
        if (!jobPosting.getRecruiter().getId().equals(recruiterId)) {
            log.error("❌ Recruiter ID mismatch. Job belongs to recruiter: " + 
                jobPosting.getRecruiter().getId() + ", but requested: " + recruiterId);
            throw new JobPostingException("You are not authorized to view applications for this job");
        }
        
        log.debug("✅ Recruiter verification passed");
        
        // Get applications for this job
        List<Application> applications = applicationRepository.findByJobPostingId(jobId);
        log.debug("✅ Found " + applications.size() + " applications for job " + jobId);
        
        // Debug: Print application details
        for (Application app : applications) {
            log.debug("📄 Application ID: " + app.getId());
            log.debug("   Applicant: " + (app.getUser() != null ? app.getUser().getFullName() : "No user"));
            log.debug("   Application Date: " + app.getApplicationDate());
            log.debug("   Status: " + app.getStatus());
            log.debug("   Cover Letter Length: " + (app.getCoverLetter() != null ? app.getCoverLetter().length() : 0));
            log.debug("   ---");
        }
        
        return applications;
    }
    
    // Add this method to JobPostingService class
    public List<JobPosting> getActiveJobPostingsSortedByDate() {
        return jobPostingRepository.findByIsActiveTrueAndModerationStatusOrderByFeaturedDescPostedDateDesc(
            JobPosting.MODERATION_APPROVED);
    }

    /** Toggles whether a posting is featured - a Pro-tier perk that sorts it ahead of everything
     *  else in the public listing (see getActiveJobPostingsSortedByDate). */
    public JobPosting setFeatured(Long jobId, Long recruiterId, boolean featured) {
        JobPosting job = jobPostingRepository.findById(jobId)
            .orElseThrow(() -> new JobPostingException("Job posting not found with ID: " + jobId));

        if (job.getRecruiter() == null || !job.getRecruiter().getId().equals(recruiterId)) {
            throw new JobPostingException("You are not authorized to feature this job posting");
        }
        if (featured && !SubscriptionService.PRO_TIER.equals(job.getRecruiter().getSubscriptionTier())) {
            throw new JobPostingException("Featuring a job requires a Pro subscription");
        }

        job.setFeatured(featured);
        return jobPostingRepository.save(job);
    }
    
    // Helper method to check if user is recruiter
    public boolean isUserRecruiter(Long userId) {
        Optional<Recruiter> recruiter = recruiterRepository.findByUserId(userId);
        return recruiter.isPresent();
    }
    
    // Helper method to get recruiter by user ID
    public Optional<Recruiter> getRecruiterByUserId(Long userId) {
        return recruiterRepository.findByUserId(userId);
    }

    // Enhanced method to get applications with resume data
    public List<Application> getApplicationsWithResumeAnalysis(Long jobId, Long recruiterId) {
        log.debug("=== GET APPLICATIONS WITH RESUME ANALYSIS ===");
        
        List<Application> applications = getApplicationsForJob(jobId, recruiterId);
        
        // Enhance applications with resume analysis
        return applications.stream()
            .map(application -> {
                if (application.getResumeParsedText() != null && !application.getResumeParsedText().isEmpty()) {
                    // Recalculate match percentage if needed
                    if (application.getMatchPercentage() == null || application.getMatchPercentage() == 0.0) {
                        double matchPercentage = calculateResumeMatchPercentage(
                            application.getJobPosting(),
                            application.getResumeParsedText(),
                            application.getApplicantSkills()
                        );
                        application.setMatchPercentage(matchPercentage);
                        
                        // Save the updated application
                        applicationRepository.save(application);
                    }
                }
                return application;
            })
            .sorted((a1, a2) -> {
                Double score1 = a1.getMatchPercentage() != null ? a1.getMatchPercentage() : 0.0;
                Double score2 = a2.getMatchPercentage() != null ? a2.getMatchPercentage() : 0.0;
                return Double.compare(score2, score1);
            })
            .collect(Collectors.toList());
    }

    // Method to get application resume text
    public String getApplicationResumeText(Long applicationId) {
        Optional<Application> application = applicationRepository.findById(applicationId);
        return application.map(Application::getResumeParsedText).orElse("");
    }

    // Method to update match percentage for existing applications
    public void recalculateAllMatchPercentages(Long jobId) {
        List<Application> applications = applicationRepository.findByJobPostingId(jobId);
        
        for (Application application : applications) {
            if (application.getResumeParsedText() != null && !application.getResumeParsedText().isEmpty()) {
                double newMatchPercentage = calculateResumeMatchPercentage(
                    application.getJobPosting(),
                    application.getResumeParsedText(),
                    application.getApplicantSkills()
                );
                application.setMatchPercentage(newMatchPercentage);
                applicationRepository.save(application);
                
                log.debug("Recalculated match for application " + application.getId() + 
                                 ": " + newMatchPercentage + "%");
            }
        }
    }
    
    // Exception classes
    public static class JobApplicationException extends RuntimeException {
        public JobApplicationException(String message) {
            super(message);
        }
    }
    
    public static class JobPostingException extends RuntimeException {
        public JobPostingException(String message) {
            super(message);
        }
    }
}