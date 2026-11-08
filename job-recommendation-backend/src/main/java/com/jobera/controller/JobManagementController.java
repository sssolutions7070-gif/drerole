package com.jobera.controller;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.service.JobPostingService;
import com.jobera.service.RecruiterService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recruiters/jobs")
public class JobManagementController {
    private static final Logger log = LoggerFactory.getLogger(JobManagementController.class);


    private final JobPostingService jobPostingService;
    private final RecruiterService recruiterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobManagementController(JobPostingService jobPostingService, RecruiterService recruiterService) {
        this.jobPostingService = jobPostingService;
        this.recruiterService = recruiterService;
    }

    /** The request body sends screeningQuestions as a real nested JSON array (not a
     *  pre-stringified string like requiredSkills/preferredSkills) - cleaner for the frontend to
     *  produce - so this re-serializes it to the JSON-string form JobPosting's column stores. */
    private String serializeScreeningQuestions(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            log.error("Failed to serialize screeningQuestions: " + e.getMessage());
            return null;
        }
    }
    
    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody Map<String, Object> request) {
        try {
            log.debug("=== CREATE JOB REQUEST ===");
            log.debug("Request data: " + request);
            
            Long recruiterId = Long.valueOf(request.get("recruiterId").toString());
            String title = (String) request.get("title");
            String company = (String) request.get("company");
            String description = (String) request.get("description");
            String requiredSkills = (String) request.get("requiredSkills");
            String preferredSkills = (String) request.get("preferredSkills");
            String location = (String) request.get("location");
            String salaryRange = (String) request.get("salaryRange");
            String jobType = (String) request.get("jobType");
            String experienceLevel = (String) request.get("experienceLevel");
            java.time.LocalDateTime scheduledFor = request.get("scheduledFor") != null && !request.get("scheduledFor").toString().isBlank()
                ? java.time.LocalDateTime.parse(request.get("scheduledFor").toString())
                : null;
            String screeningQuestions = serializeScreeningQuestions(request.get("screeningQuestions"));

            log.debug("Recruiter ID: " + recruiterId);
            log.debug("Title: " + title);
            log.debug("Company: " + company);

            JobPosting job = jobPostingService.createJobPosting(
                recruiterId, title, company, description, requiredSkills,
                preferredSkills, location, salaryRange, jobType, experienceLevel, scheduledFor, screeningQuestions
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Job posted successfully");
            response.put("job", createJobResponse(job));
            
            log.debug("✅ Job created successfully with ID: " + job.getId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ ERROR creating job: " + e.getMessage());
            log.error("Unexpected error", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create job: " + e.getMessage()));
        }
    }
    
    @PutMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> updateJob(
            @PathVariable Long jobId, 
            @RequestBody Map<String, Object> request) {
        try {
            Long recruiterId = Long.valueOf(request.get("recruiterId").toString());
            String title = (String) request.get("title");
            String description = (String) request.get("description");
            String requiredSkills = (String) request.get("requiredSkills");
            String preferredSkills = (String) request.get("preferredSkills");
            String location = (String) request.get("location");
            String salaryRange = (String) request.get("salaryRange");
            String jobType = (String) request.get("jobType");
            String experienceLevel = (String) request.get("experienceLevel");
            Boolean isActive = (Boolean) request.get("isActive");
            String screeningQuestions = serializeScreeningQuestions(request.get("screeningQuestions"));

            JobPosting job = jobPostingService.updateJobPosting(
                jobId, recruiterId, title, description, requiredSkills,
                preferredSkills, location, salaryRange, jobType, experienceLevel, isActive, screeningQuestions
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Job updated successfully");
            response.put("job", createJobResponse(job));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to update job: " + e.getMessage()));
        }
    }
    
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> deleteJob(
            @PathVariable Long jobId, 
            @RequestParam Long recruiterId) {
        try {
            jobPostingService.deleteJobPosting(jobId, recruiterId);
            return ResponseEntity.ok(Map.of("message", "Job deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete job: " + e.getMessage()));
        }
    }
    
    @GetMapping("/recruiter/{recruiterId}")
    public ResponseEntity<List<JobPosting>> getRecruiterJobs(@PathVariable Long recruiterId) {
        try {
            log.debug("=== GET RECRUITER JOBS ===");
            log.debug("Recruiter ID: " + recruiterId);
            
            List<JobPosting> jobs = jobPostingService.getJobsByRecruiter(recruiterId);
            
            log.debug("✅ Found " + jobs.size() + " jobs for recruiter " + recruiterId);
            
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            log.error("❌ ERROR fetching recruiter jobs: " + e.getMessage());
            log.error("Unexpected error", e);
            return ResponseEntity.ok(List.of());
        }
    }
    
    // ENHANCED VERSION - FIXED
    @GetMapping("/{jobId}/applications")
    public ResponseEntity<Map<String, Object>> getJobApplications(
            @PathVariable Long jobId, 
            @RequestParam Long recruiterId) {
        try {
            log.debug("=== GET JOB APPLICATIONS - ENHANCED ===");
            log.debug("Job ID: " + jobId);
            log.debug("Recruiter ID: " + recruiterId);
            
            List<Application> applications = jobPostingService.getApplicationsForJob(jobId, recruiterId);
            
            log.debug("✅ Final applications count: " + applications.size());
            
            Map<String, Object> response = new HashMap<>();
            response.put("applications", applications.stream().map(this::createEnhancedApplicationResponse).toList());
            response.put("totalApplications", applications.size());
            response.put("jobId", jobId);
            response.put("recruiterId", recruiterId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ ERROR fetching applications: " + e.getMessage());
            log.error("Unexpected error", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch applications: " + e.getMessage()));
        }
    }
    
    @PostMapping("/{jobId}/duplicate")
    public ResponseEntity<Map<String, Object>> duplicateJob(
            @PathVariable Long jobId,
            @RequestBody Map<String, Object> request) {
        try {
            Long recruiterId = Long.valueOf(request.get("recruiterId").toString());
            JobPosting copy = jobPostingService.duplicateJobPosting(jobId, recruiterId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Job duplicated successfully");
            response.put("job", createJobResponse(copy));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to duplicate job: " + e.getMessage()));
        }
    }

    @PutMapping("/{jobId}/featured")
    public ResponseEntity<Map<String, Object>> setFeatured(
            @PathVariable Long jobId,
            @RequestBody Map<String, Object> request) {
        try {
            Long recruiterId = Long.valueOf(request.get("recruiterId").toString());
            boolean featured = Boolean.parseBoolean(String.valueOf(request.get("featured")));
            JobPosting job = jobPostingService.setFeatured(jobId, recruiterId, featured);

            Map<String, Object> response = new HashMap<>();
            response.put("message", featured ? "Job featured" : "Job unfeatured");
            response.put("job", createJobResponse(job));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{jobId}/applications/export")
    public ResponseEntity<byte[]> exportApplications(
            @PathVariable Long jobId,
            @RequestParam Long recruiterId) {
        try {
            List<Application> applications = jobPostingService.getApplicationsForJob(jobId, recruiterId);
            byte[] csv = buildApplicantsCsv(applications).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"applicants_job_" + jobId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
        } catch (Exception e) {
            log.error("❌ ERROR exporting applications: " + e.getMessage());
            return ResponseEntity.badRequest()
                .body(("Failed to export applications: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private String buildApplicantsCsv(List<Application> applications) {
        StringBuilder csv = new StringBuilder();
        csv.append("Name,Email,Phone,Status,Match %,Applied Date,Skills\n");
        for (Application application : applications) {
            String name = application.getUser() != null ? application.getUser().getFullName() : application.getApplicantName();
            String email = application.getUser() != null ? application.getUser().getEmail() : application.getApplicantEmail();
            String phone = application.getUser() != null ? application.getUser().getPhone() : application.getApplicantPhone();
            csv.append(csvField(name)).append(',')
                .append(csvField(email)).append(',')
                .append(csvField(phone)).append(',')
                .append(csvField(application.getStatus())).append(',')
                .append(application.getMatchPercentage() != null ? application.getMatchPercentage() : 0).append(',')
                .append(application.getApplicationDate() != null ? application.getApplicationDate() : "").append(',')
                .append(csvField(application.getApplicantSkills())).append('\n');
        }
        return csv.toString();
    }

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private Map<String, Object> createJobResponse(JobPosting job) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", job.getId());
        response.put("title", job.getTitle());
        response.put("company", job.getCompany());
        response.put("description", job.getDescription());
        response.put("requiredSkills", job.getRequiredSkills());
        response.put("preferredSkills", job.getPreferredSkills());
        response.put("location", job.getLocation());
        response.put("salaryRange", job.getSalaryRange());
        response.put("jobType", job.getJobType());
        response.put("experienceLevel", job.getExperienceLevel());
        response.put("postedDate", job.getPostedDate());
        response.put("isActive", job.getIsActive());
        response.put("featured", job.getFeatured());
        response.put("scheduledFor", job.getScheduledFor());
        response.put("screeningQuestions", job.getScreeningQuestions());
        response.put("moderationStatus", job.getModerationStatus());
        response.put("rejectionReason", job.getRejectionReason());

        if (job.getRecruiter() != null) {
            response.put("recruiterId", job.getRecruiter().getId());
            response.put("recruiterCompany", job.getRecruiter().getCompanyName());
        }
        
        return response;
    }
    
    // ENHANCED APPLICATION RESPONSE WITH MORE DETAILS
    private Map<String, Object> createEnhancedApplicationResponse(Application application) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", application.getId());
        response.put("applicationDate", application.getApplicationDate());
        response.put("status", application.getStatus());
        response.put("coverLetter", application.getCoverLetter());
        response.put("applicantSkills", application.getApplicantSkills());
        response.put("applicantExperience", application.getApplicantExperience());
        response.put("applicantEducation", application.getApplicantEducation());
        
        // Enhanced applicant information with fallbacks
        if (application.getUser() != null) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", application.getUser().getId());
            userInfo.put("fullName", application.getUser().getFullName());
            userInfo.put("email", application.getUser().getEmail());
            userInfo.put("phone", application.getUser().getPhone());
            response.put("applicant", userInfo);
            
            // Also set direct fields for easier frontend access
            response.put("applicantName", application.getUser().getFullName());
            response.put("applicantEmail", application.getUser().getEmail());
            response.put("applicantPhone", application.getUser().getPhone());
        } else {
            // Fallback to stored applicant info
            response.put("applicantName", application.getApplicantName());
            response.put("applicantEmail", application.getApplicantEmail());
            response.put("applicantPhone", application.getApplicantPhone());
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("fullName", application.getApplicantName());
            userInfo.put("email", application.getApplicantEmail());
            userInfo.put("phone", application.getApplicantPhone());
            response.put("applicant", userInfo);
        }
        
        // Job information
        if (application.getJobPosting() != null) {
            Map<String, Object> jobInfo = new HashMap<>();
            jobInfo.put("id", application.getJobPosting().getId());
            jobInfo.put("title", application.getJobPosting().getTitle());
            jobInfo.put("company", application.getJobPosting().getCompany());
            response.put("job", jobInfo);
        }
        
        return response;
    }
}