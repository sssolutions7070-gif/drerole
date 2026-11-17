// File: src/main/java/com/jobera/controller/AdminController.java
package com.jobera.controller;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.AdminAuditLog;
import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.UserRepository;
import com.jobera.service.AdminAuditService;
import com.jobera.service.CompanyReviewService;
import com.jobera.service.FraudDetectionService;
import com.jobera.service.InterviewExperienceService;
import com.jobera.service.JobPostingService;
import com.jobera.service.NotificationService;
import com.jobera.util.CsvField;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final String ERROR_KEY = "error";
    private static final String MESSAGE_KEY = "message";
    private static final String EMAIL_KEY = "email";
    private static final String FULL_NAME_KEY = "fullName";
    private static final String IS_ACTIVE_KEY = "isActive";
    private static final long ADMIN_ROLE_ID = 2L;

    private final UserRepository userRepository;
    private final RecruiterRepository recruiterRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ApplicationRepository applicationRepository;
    private final ResumeRepository resumeRepository;
    private final AdminAuditService adminAuditService;
    private final JobPostingService jobPostingService;
    private final NotificationService notificationService;
    private final CompanyReviewService companyReviewService;
    private final InterviewExperienceService interviewExperienceService;
    private final FraudDetectionService fraudDetectionService;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminController(UserRepository userRepository,
                         RecruiterRepository recruiterRepository,
                         JobPostingRepository jobPostingRepository,
                         ApplicationRepository applicationRepository,
                         ResumeRepository resumeRepository,
                         AdminAuditService adminAuditService,
                         JobPostingService jobPostingService,
                         NotificationService notificationService,
                         CompanyReviewService companyReviewService,
                         InterviewExperienceService interviewExperienceService,
                         FraudDetectionService fraudDetectionService) {
        this.userRepository = userRepository;
        this.recruiterRepository = recruiterRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.applicationRepository = applicationRepository;
        this.resumeRepository = resumeRepository;
        this.adminAuditService = adminAuditService;
        this.jobPostingService = jobPostingService;
        this.notificationService = notificationService;
        this.companyReviewService = companyReviewService;
        this.interviewExperienceService = interviewExperienceService;
        this.fraudDetectionService = fraudDetectionService;
    }

    public AdminController(UserRepository userRepository,
                         RecruiterRepository recruiterRepository,
                         JobPostingRepository jobPostingRepository,
                         ApplicationRepository applicationRepository,
                         ResumeRepository resumeRepository,
                         AdminAuditService adminAuditService,
                         JobPostingService jobPostingService,
                         NotificationService notificationService,
                         CompanyReviewService companyReviewService,
                         InterviewExperienceService interviewExperienceService) {
        this(userRepository, recruiterRepository, jobPostingRepository, applicationRepository, resumeRepository,
            adminAuditService, jobPostingService, notificationService, companyReviewService,
            interviewExperienceService, new FraudDetectionService());
    }

    private User actingAdmin(HttpServletRequest request) {
        return (User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
    }
    
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        try {
            long totalUsers = userRepository.count();
            long totalRecruiters = recruiterRepository.count();
            long totalJobs = jobPostingRepository.count();
            long totalApplications = applicationRepository.count();
            long totalResumes = resumeRepository.count();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", totalUsers);
            stats.put("totalRecruiters", totalRecruiters);
            stats.put("totalJobs", totalJobs);
            stats.put("totalApplications", totalApplications);
            stats.put("totalResumes", totalResumes);
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to fetch stats: " + e.getMessage()));
        }
    }
    
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        try {
            List<User> users = userRepository.findAll();
            
            List<Map<String, Object>> userResponses = users.stream()
                .map(this::createSafeUserResponse)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(userResponses);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(List.of());
        }
    }
    
    private Map<String, Object> createSafeUserResponse(User user) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", user.getId());
        userResponse.put(EMAIL_KEY, user.getEmail());
        userResponse.put(FULL_NAME_KEY, user.getFullName());
        userResponse.put("phone", user.getPhone());
        userResponse.put("roleId", user.getRoleId());
        userResponse.put("role", user.getRole());
        userResponse.put(IS_ACTIVE_KEY, user.getIsActive());
        userResponse.put("createdAt", user.getCreatedAt());
        userResponse.put("lastLogin", user.getLastLogin());
        return userResponse;
    }
    
    @GetMapping("/recruiters")
    public ResponseEntity<List<Map<String, Object>>> getAllRecruiters() {
        try {
            List<Recruiter> recruiters = recruiterRepository.findAll();
            List<Map<String, Object>> recruiterDetails = recruiters.stream().map(recruiter -> {
                Map<String, Object> details = new HashMap<>();
                details.put("id", recruiter.getId());
                details.put("companyName", recruiter.getCompanyName());
                details.put("companySize", recruiter.getCompanySize());
                details.put("industry", recruiter.getIndustry());
                details.put("contactEmail", recruiter.getContactEmail());
                details.put("createdAt", recruiter.getCreatedAt());
                
                if (recruiter.getUser() != null) {
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("id", recruiter.getUser().getId());
                    userInfo.put(EMAIL_KEY, recruiter.getUser().getEmail());
                    userInfo.put(FULL_NAME_KEY, recruiter.getUser().getFullName());
                    userInfo.put("phone", recruiter.getUser().getPhone());
                    details.put("user", userInfo);
                }
                
                long jobCount = jobPostingRepository.findByRecruiterIdAndIsActiveTrue(recruiter.getId()).size();
                details.put("jobCount", jobCount);
                
                return details;
            }).collect(Collectors.toList());
            
            return ResponseEntity.ok(recruiterDetails);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(List.of());
        }
    }
    
    @GetMapping("/jobs")
    public ResponseEntity<List<Map<String, Object>>> getAllJobs() {
        try {
            List<JobPosting> jobs = jobPostingRepository.findAll();
            List<Map<String, Object>> jobDetails = jobs.stream().map(job -> {
                Map<String, Object> details = new HashMap<>();
                details.put("id", job.getId());
                details.put("title", job.getTitle());
                details.put("company", job.getCompany());
                details.put("location", job.getLocation());
                details.put("jobType", job.getJobType());
                details.put("experienceLevel", job.getExperienceLevel());
                details.put("salaryRange", job.getSalaryRange());
                details.put("postedDate", job.getPostedDate());
                details.put(IS_ACTIVE_KEY, job.getIsActive());
                details.put("moderationStatus", job.getModerationStatus());
                details.put("rejectionReason", job.getRejectionReason());

                long applicationCount = applicationRepository.findByJobPostingId(job.getId()).size();
                details.put("applicationCount", applicationCount);
                
                if (job.getRecruiter() != null) {
                    Map<String, Object> recruiterInfo = new HashMap<>();
                    recruiterInfo.put("id", job.getRecruiter().getId());
                    recruiterInfo.put("companyName", job.getRecruiter().getCompanyName());
                    recruiterInfo.put("contactEmail", job.getRecruiter().getContactEmail());
                    details.put("recruiter", recruiterInfo);
                } else {
                    details.put("recruiter", "System Posted");
                }
                
                return details;
            }).collect(Collectors.toList());
            
            return ResponseEntity.ok(jobDetails);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(List.of());
        }
    }
    
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long userId, HttpServletRequest request) {
        try {
            Optional<User> user = userRepository.findById(userId);
            if (user.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(ERROR_KEY, "User not found"));
            }

            if (user.get().getRoleId() == ADMIN_ROLE_ID) {
                return ResponseEntity.status(403).body(Map.of(ERROR_KEY, "Cannot delete admin user"));
            }

            String email = user.get().getEmail();
            Optional<Recruiter> recruiter = recruiterRepository.findByUserId(userId);
            if (recruiter.isPresent()) {
                recruiterRepository.deleteById(recruiter.get().getId());
            } else {
                userRepository.deleteById(userId);
            }

            adminAuditService.record(actingAdmin(request), "USER_DELETED", "USER", userId, email);
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "User deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(ERROR_KEY, "Failed to delete user: " + e.getMessage()));
        }
    }

    @PutMapping("/users/{userId}/toggle-active")
    public ResponseEntity<Map<String, Object>> toggleUserActive(@PathVariable Long userId, HttpServletRequest request) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(ERROR_KEY, "User not found"));
            }

            User user = userOpt.get();
            if (user.getRoleId() == ADMIN_ROLE_ID) {
                return ResponseEntity.status(403).body(Map.of(ERROR_KEY, "Cannot deactivate an admin user"));
            }

            boolean nowActive = !Boolean.TRUE.equals(user.getIsActive());
            user.setIsActive(nowActive);
            userRepository.save(user);

            adminAuditService.record(actingAdmin(request), nowActive ? "USER_REACTIVATED" : "USER_DEACTIVATED",
                "USER", userId, user.getEmail());

            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "User status updated successfully", IS_ACTIVE_KEY, nowActive));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(ERROR_KEY, "Failed to update user status: " + e.getMessage()));
        }
    }

    @DeleteMapping("/recruiters/{recruiterId}")
    public ResponseEntity<Map<String, String>> deleteRecruiter(@PathVariable Long recruiterId, HttpServletRequest request) {
        try {
            Optional<Recruiter> recruiter = recruiterRepository.findById(recruiterId);
            if (recruiter.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(ERROR_KEY, "Recruiter not found"));
            }

            Long userId = recruiter.get().getUser().getId();
            String companyName = recruiter.get().getCompanyName();

            recruiterRepository.deleteById(recruiterId);
            userRepository.deleteById(userId);

            adminAuditService.record(actingAdmin(request), "RECRUITER_DELETED", "RECRUITER", recruiterId, companyName);
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Recruiter deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(ERROR_KEY, "Failed to delete recruiter: " + e.getMessage()));
        }
    }

    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, String>> deleteJob(@PathVariable Long jobId, HttpServletRequest request) {
        try {
            Optional<JobPosting> job = jobPostingRepository.findById(jobId);
            if (job.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(ERROR_KEY, "Job not found"));
            }

            String title = job.get().getTitle();
            jobPostingRepository.deleteById(jobId);
            adminAuditService.record(actingAdmin(request), "JOB_DELETED", "JOB", jobId, title);
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Job deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(ERROR_KEY, "Failed to delete job: " + e.getMessage()));
        }
    }

    @PutMapping("/jobs/{jobId}/toggle-active")
    public ResponseEntity<Map<String, Object>> toggleJobActive(@PathVariable Long jobId, HttpServletRequest request) {
        try {
            Optional<JobPosting> jobOpt = jobPostingRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(ERROR_KEY, "Job not found"));
            }

            JobPosting job = jobOpt.get();
            job.setIsActive(!job.getIsActive());
            jobPostingRepository.save(job);

            adminAuditService.record(actingAdmin(request), job.getIsActive() ? "JOB_ACTIVATED" : "JOB_DEACTIVATED",
                "JOB", jobId, job.getTitle());

            return ResponseEntity.ok(Map.of(
                MESSAGE_KEY, "Job status updated successfully",
                IS_ACTIVE_KEY, job.getIsActive()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(ERROR_KEY, "Failed to update job status: " + e.getMessage()));
        }
    }

    @GetMapping("/jobs/pending")
    public ResponseEntity<List<JobPosting>> getPendingJobs() {
        return ResponseEntity.ok(jobPostingService.getPendingJobPostings());
    }

    /** Heuristic fraud/spam risk score for every pending job, keyed by job id - an aid for the
     *  admin reviewing the queue above, never an automatic rejection (see FraudDetectionService). */
    @GetMapping("/jobs/pending/fraud-scores")
    public ResponseEntity<Map<Long, Map<String, Object>>> getPendingJobFraudScores() {
        return ResponseEntity.ok(fraudDetectionService.scanAll(jobPostingService.getPendingJobPostings()));
    }

    @PutMapping("/jobs/{jobId}/approve")
    public ResponseEntity<Map<String, Object>> approveJob(@PathVariable Long jobId, HttpServletRequest request) {
        try {
            JobPosting job = jobPostingService.approveJobPosting(jobId);
            adminAuditService.record(actingAdmin(request), "JOB_APPROVED", "JOB", jobId, job.getTitle());
            notifyRecruiterOfModeration(job, true, null);
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Job approved", "moderationStatus", job.getModerationStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to approve job: " + e.getMessage()));
        }
    }

    @PutMapping("/jobs/{jobId}/reject")
    public ResponseEntity<Map<String, Object>> rejectJob(@PathVariable Long jobId,
                                                          @RequestBody Map<String, String> payload,
                                                          HttpServletRequest request) {
        try {
            String reason = payload.get("reason");
            JobPosting job = jobPostingService.rejectJobPosting(jobId, reason);
            adminAuditService.record(actingAdmin(request), "JOB_REJECTED", "JOB", jobId, job.getTitle());
            notifyRecruiterOfModeration(job, false, reason);
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Job rejected", "moderationStatus", job.getModerationStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to reject job: " + e.getMessage()));
        }
    }

    private void notifyRecruiterOfModeration(JobPosting job, boolean approved, String reason) {
        if (job.getRecruiter() == null || job.getRecruiter().getUser() == null) {
            return;
        }
        User recruiterUser = job.getRecruiter().getUser();
        if (approved) {
            notificationService.sendJobPostingApproved(recruiterUser.getEmail(), recruiterUser.getFullName(), job.getTitle());
        } else {
            notificationService.sendJobPostingRejected(recruiterUser.getEmail(), recruiterUser.getFullName(), job.getTitle(), reason);
        }
    }

    @GetMapping("/company-reviews/pending")
    public ResponseEntity<List<com.jobera.entity.CompanyReview>> getPendingCompanyReviews() {
        return ResponseEntity.ok(companyReviewService.getPendingReviews());
    }

    @PutMapping("/company-reviews/{reviewId}/approve")
    public ResponseEntity<Map<String, Object>> approveCompanyReview(@PathVariable Long reviewId, HttpServletRequest request) {
        try {
            com.jobera.entity.CompanyReview review = companyReviewService.approveReview(reviewId);
            adminAuditService.record(actingAdmin(request), "COMPANY_REVIEW_APPROVED", "COMPANY_REVIEW", reviewId,
                review.getRecruiter().getCompanyName());
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Review approved", "moderationStatus", review.getModerationStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to approve review: " + e.getMessage()));
        }
    }

    @PutMapping("/company-reviews/{reviewId}/reject")
    public ResponseEntity<Map<String, Object>> rejectCompanyReview(@PathVariable Long reviewId,
                                                                    @RequestBody Map<String, String> payload,
                                                                    HttpServletRequest request) {
        try {
            String reason = payload.get("reason");
            com.jobera.entity.CompanyReview review = companyReviewService.rejectReview(reviewId, reason);
            adminAuditService.record(actingAdmin(request), "COMPANY_REVIEW_REJECTED", "COMPANY_REVIEW", reviewId,
                review.getRecruiter().getCompanyName());
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Review rejected", "moderationStatus", review.getModerationStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to reject review: " + e.getMessage()));
        }
    }

    @GetMapping("/interview-experiences/pending")
    public ResponseEntity<List<com.jobera.entity.InterviewExperience>> getPendingInterviewExperiences() {
        return ResponseEntity.ok(interviewExperienceService.getPendingExperiences());
    }

    @PutMapping("/interview-experiences/{experienceId}/approve")
    public ResponseEntity<Map<String, Object>> approveInterviewExperience(@PathVariable Long experienceId, HttpServletRequest request) {
        try {
            com.jobera.entity.InterviewExperience experience = interviewExperienceService.approveExperience(experienceId);
            adminAuditService.record(actingAdmin(request), "INTERVIEW_EXPERIENCE_APPROVED", "INTERVIEW_EXPERIENCE", experienceId,
                experience.getRecruiter().getCompanyName());
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Interview experience approved", "moderationStatus", experience.getModerationStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to approve interview experience: " + e.getMessage()));
        }
    }

    @PutMapping("/interview-experiences/{experienceId}/reject")
    public ResponseEntity<Map<String, Object>> rejectInterviewExperience(@PathVariable Long experienceId,
                                                                          @RequestBody Map<String, String> payload,
                                                                          HttpServletRequest request) {
        try {
            String reason = payload.get("reason");
            com.jobera.entity.InterviewExperience experience = interviewExperienceService.rejectExperience(experienceId, reason);
            adminAuditService.record(actingAdmin(request), "INTERVIEW_EXPERIENCE_REJECTED", "INTERVIEW_EXPERIENCE", experienceId,
                experience.getRecruiter().getCompanyName());
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Interview experience rejected", "moderationStatus", experience.getModerationStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to reject interview experience: " + e.getMessage()));
        }
    }

    @GetMapping("/audit-log")
    public ResponseEntity<List<Map<String, Object>>> getAuditLog() {
        List<Map<String, Object>> entries = adminAuditService.recent().stream()
            .map(this::createAuditLogResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(entries);
    }

    private Map<String, Object> createAuditLogResponse(AdminAuditLog entry) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", entry.getId());
        response.put("adminEmail", entry.getAdminEmail());
        response.put("action", entry.getAction());
        response.put("targetType", entry.getTargetType());
        response.put("targetId", entry.getTargetId());
        response.put("targetDescription", entry.getTargetDescription());
        response.put("createdAt", entry.getCreatedAt());
        return response;
    }
    
    @GetMapping("/applications")
    public ResponseEntity<List<Map<String, Object>>> getAllApplications() {
        try {
            List<Application> applications = applicationRepository.findAll();
            List<Map<String, Object>> applicationDetails = applications.stream().map(app -> {
                Map<String, Object> details = new HashMap<>();
                details.put("id", app.getId());
                details.put("applicationDate", app.getApplicationDate());
                details.put("status", app.getStatus());
                details.put("coverLetter", app.getCoverLetter());
                details.put("applicantSkills", app.getApplicantSkills());
                details.put("applicantExperience", app.getApplicantExperience());
                details.put("applicantEducation", app.getApplicantEducation());
                
                if (app.getUser() != null) {
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("id", app.getUser().getId());
                    userInfo.put(EMAIL_KEY, app.getUser().getEmail());
                    userInfo.put(FULL_NAME_KEY, app.getUser().getFullName());
                    details.put("applicant", userInfo);
                } else {
                    details.put("applicantName", app.getApplicantName());
                    details.put("applicantEmail", app.getApplicantEmail());
                }
                
                if (app.getJobPosting() != null) {
                    Map<String, Object> jobInfo = new HashMap<>();
                    jobInfo.put("id", app.getJobPosting().getId());
                    jobInfo.put("title", app.getJobPosting().getTitle());
                    jobInfo.put("company", app.getJobPosting().getCompany());
                    details.put("job", jobInfo);
                }
                
                return details;
            }).collect(Collectors.toList());
            
            return ResponseEntity.ok(applicationDetails);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(List.of());
        }
    }

    // ---------------------------------------------------------------------------------------
    // CSV exports - a recruiter could already export their own applicants to CSV for one job;
    // admins had no equivalent way to pull users/jobs/applications platform-wide for offline
    // reporting at all.
    // ---------------------------------------------------------------------------------------

    @GetMapping("/export/users.csv")
    public ResponseEntity<byte[]> exportUsersCsv() {
        StringBuilder csv = new StringBuilder("ID,Full Name,Email,Role,Created At,Last Login\n");
        for (User user : userRepository.findAll()) {
            csv.append(user.getId()).append(',')
                .append(CsvField.escape(user.getFullName())).append(',')
                .append(CsvField.escape(user.getEmail())).append(',')
                .append(CsvField.escape(user.getRole())).append(',')
                .append(user.getCreatedAt() != null ? user.getCreatedAt() : "").append(',')
                .append(user.getLastLogin() != null ? user.getLastLogin() : "").append('\n');
        }
        return csvResponse(csv.toString(), "users.csv");
    }

    @GetMapping("/export/jobs.csv")
    public ResponseEntity<byte[]> exportJobsCsv() {
        StringBuilder csv = new StringBuilder("ID,Title,Company,Location,Job Type,Experience Level,Moderation Status,Active,Posted Date\n");
        for (JobPosting job : jobPostingRepository.findAll()) {
            csv.append(job.getId()).append(',')
                .append(CsvField.escape(job.getTitle())).append(',')
                .append(CsvField.escape(job.getCompany())).append(',')
                .append(CsvField.escape(job.getLocation())).append(',')
                .append(CsvField.escape(job.getJobType())).append(',')
                .append(CsvField.escape(job.getExperienceLevel())).append(',')
                .append(CsvField.escape(job.getModerationStatus())).append(',')
                .append(Boolean.TRUE.equals(job.getIsActive())).append(',')
                .append(job.getPostedDate() != null ? job.getPostedDate() : "").append('\n');
        }
        return csvResponse(csv.toString(), "jobs.csv");
    }

    @GetMapping("/export/applications.csv")
    public ResponseEntity<byte[]> exportApplicationsCsv() {
        StringBuilder csv = new StringBuilder("ID,Applicant Name,Applicant Email,Job Title,Company,Status,Match %,Application Date\n");
        for (Application application : applicationRepository.findAll()) {
            String name = application.getUser() != null ? application.getUser().getFullName() : application.getApplicantName();
            String email = application.getUser() != null ? application.getUser().getEmail() : application.getApplicantEmail();
            JobPosting job = application.getJobPosting();
            csv.append(application.getId()).append(',')
                .append(CsvField.escape(name)).append(',')
                .append(CsvField.escape(email)).append(',')
                .append(CsvField.escape(job != null ? job.getTitle() : "")).append(',')
                .append(CsvField.escape(job != null ? job.getCompany() : "")).append(',')
                .append(CsvField.escape(application.getStatus())).append(',')
                .append(application.getMatchPercentage() != null ? application.getMatchPercentage() : 0).append(',')
                .append(application.getApplicationDate() != null ? application.getApplicationDate() : "").append('\n');
        }
        return csvResponse(csv.toString(), "applications.csv");
    }

    private ResponseEntity<byte[]> csvResponse(String csv, String filename) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}