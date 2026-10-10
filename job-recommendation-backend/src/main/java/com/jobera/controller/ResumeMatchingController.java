package com.jobera.controller;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.repository.ApplicationRepository;
import com.jobera.service.AiInterviewClient;
import com.jobera.service.JobPostingService;
import com.jobera.service.SkillGapAnalyzer;

@RestController
@RequestMapping("/api/resume-matching")
public class ResumeMatchingController {
    private static final Logger log = LoggerFactory.getLogger(ResumeMatchingController.class);

    // Rough minimum years of experience implied by each canonical experience level (see
    // JobPosting.canonicalizeExperienceLevel) - there's no numeric "years required" field on a
    // job posting, so this is the best available real comparison against the applicant's own
    // recorded years, rather than a fabricated verdict.
    private static final Map<String, Integer> MIN_YEARS_BY_LEVEL = Map.of(
        "Entry", 0, "Junior", 1, "Mid-level", 2, "Senior", 5, "Lead", 8, "Principal", 12
    );

    private final JobPostingService jobPostingService;
    private final AiInterviewClient aiInterviewClient;
    private final ApplicationRepository applicationRepository;

    @Autowired
    public ResumeMatchingController(JobPostingService jobPostingService, AiInterviewClient aiInterviewClient,
                                     ApplicationRepository applicationRepository) {
        this.jobPostingService = jobPostingService;
        this.aiInterviewClient = aiInterviewClient;
        this.applicationRepository = applicationRepository;
    }

    public ResumeMatchingController(JobPostingService jobPostingService, ApplicationRepository applicationRepository) {
        this(jobPostingService, null, applicationRepository);
    }
    
    @GetMapping("/job/{jobId}/applications/sorted")
    public ResponseEntity<Map<String, Object>> getApplicationsSortedByMatch(
            @PathVariable Long jobId,
            @RequestParam Long recruiterId) {
        try {
            log.debug("=== GET APPLICATIONS SORTED BY MATCH PERCENTAGE ===");
            log.debug("Job ID: " + jobId);
            log.debug("Recruiter ID: " + recruiterId);
            
            List<Application> applications = jobPostingService.getApplicationsForJob(jobId, recruiterId);
            
            // Sort applications by match percentage (descending)
            List<Application> sortedApplications = applications.stream()
                .sorted((a1, a2) -> {
                    Double score1 = a1.getMatchPercentage() != null ? a1.getMatchPercentage() : 0.0;
                    Double score2 = a2.getMatchPercentage() != null ? a2.getMatchPercentage() : 0.0;
                    return Double.compare(score2, score1);
                })
                .collect(Collectors.toList());
            
            log.debug("✅ Found " + sortedApplications.size() + " applications, sorted by match percentage");
            
            Map<String, Object> response = new HashMap<>();
            response.put("applications", sortedApplications.stream()
                .map(this::createEnhancedApplicationResponse)
                .collect(Collectors.toList()));
            response.put("totalApplications", sortedApplications.size());
            response.put("jobId", jobId);
            response.put("recruiterId", recruiterId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ ERROR fetching sorted applications: " + e.getMessage());
            log.error("Unexpected error", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to fetch applications: " + e.getMessage()));
        }
    }
    
    /** Real resume-match analysis for one application, read from the fields the resume matcher
     *  already recorded at apply time - this used to return the exact same hardcoded 85.5% /
     *  Java-Spring-MySQL analysis for every application regardless of what it actually contained. */
    @GetMapping("/application/{applicationId}/resume-analysis")
    public ResponseEntity<Map<String, Object>> getResumeAnalysis(@PathVariable Long applicationId) {
        try {
            Application application = applicationRepository.findById(applicationId).orElse(null);
            if (application == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> analysis = new HashMap<>();
            analysis.put("matchPercentage", application.getMatchPercentage() != null ? application.getMatchPercentage() : 0.0);
            analysis.put("strengths", SkillGapAnalyzer.parseSkillList(application.getMatchedSkills()));
            analysis.put("improvements", SkillGapAnalyzer.parseSkillList(application.getMissingSkills()));
            analysis.put("experienceMatch", experienceMatch(application));
            analysis.put("educationMatch", educationMatch(application));

            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to analyze resume: " + e.getMessage()));
        }
    }

    /** Compares the applicant's own recorded years of experience against the job's experience
     *  level - the only real target available, since a job posting has no numeric "years
     *  required" field. */
    private String experienceMatch(Application application) {
        Integer years = application.getApplicantExperience();
        if (years == null) {
            return "Not specified";
        }
        JobPosting job = application.getJobPosting();
        String level = job != null ? job.getExperienceLevel() : null;
        Integer requiredYears = (level != null) ? MIN_YEARS_BY_LEVEL.get(level) : null;
        if (requiredYears == null) {
            return years + (years == 1 ? " year of experience" : " years of experience");
        }
        if (years >= requiredYears + 2) {
            return "Exceeds requirement (" + years + "y vs " + requiredYears + "y+ for " + level + ")";
        }
        if (years >= requiredYears) {
            return "Meets requirement (" + years + "y vs " + requiredYears + "y+ for " + level + ")";
        }
        return "Below requirement (" + years + "y vs " + requiredYears + "y+ for " + level + ")";
    }

    /** There's no structured "required education" field on a job posting to compare against, so
     *  rather than inventing a qualitative verdict against nothing, this reports what the
     *  applicant actually recorded. */
    private String educationMatch(Application application) {
        String education = application.getApplicantEducation();
        return (education == null || education.isBlank()) ? "Not provided" : education;
    }

    @PostMapping("/tailor-resume")
    public ResponseEntity<Map<String, Object>> tailorResumeForJob(@RequestBody Map<String, Object> request) {
        try {
            String jobTitle = (String) request.get("jobTitle");
            String jobDescription = (String) request.get("jobDescription");

            @SuppressWarnings("unchecked")
            List<String> userSkills = (List<String>) request.get("userSkills");

            @SuppressWarnings("unchecked")
            List<String> missingSkills = (List<String>) request.get("missingSkills");

            int currentMatchScore = 0;
            if (request.get("currentMatchScore") != null) {
                currentMatchScore = ((Number) request.get("currentMatchScore")).intValue();
            }

            Map<String, Object> tailoring = (aiInterviewClient != null)
                ? aiInterviewClient.generateResumeTailoring(jobTitle, jobDescription, userSkills, missingSkills, currentMatchScore)
                : new AiInterviewClient(new RestTemplate(), "", "").generateFallbackResumeTailoring(jobTitle, jobDescription, userSkills, missingSkills, currentMatchScore);

            return ResponseEntity.ok(tailoring);
        } catch (Exception e) {
            log.error("Error tailoring resume: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to generate resume tailoring: " + e.getMessage()));
        }
    }

    @PostMapping("/generate-cover-letter")
    public ResponseEntity<Map<String, Object>> generateCoverLetter(@RequestBody Map<String, Object> request) {
        try {
            String candidateName = (String) request.get("candidateName");
            String jobTitle = (String) request.get("jobTitle");
            String company = (String) request.get("company");
            String jobDescription = (String) request.get("jobDescription");

            @SuppressWarnings("unchecked")
            List<String> userSkills = (List<String>) request.get("userSkills");

            Map<String, Object> result = (aiInterviewClient != null)
                ? aiInterviewClient.generateCoverLetter(candidateName, jobTitle, company, jobDescription, userSkills)
                : new AiInterviewClient(new RestTemplate(), "", "").generateFallbackCoverLetter(candidateName, jobTitle, company, userSkills);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error generating cover letter: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to generate a cover letter: " + e.getMessage()));
        }
    }

    @PostMapping("/career-roadmap")
    public ResponseEntity<Map<String, Object>> getCareerRoadmap(@RequestBody Map<String, Object> request) {
        try {
            String jobTitle = (String) request.get("jobTitle");
            String jobDescription = (String) request.get("jobDescription");

            @SuppressWarnings("unchecked")
            List<String> userSkills = (List<String>) request.get("userSkills");

            @SuppressWarnings("unchecked")
            List<String> missingSkills = (List<String>) request.get("missingSkills");

            int currentMatchScore = 0;
            if (request.get("currentMatchScore") != null) {
                currentMatchScore = ((Number) request.get("currentMatchScore")).intValue();
            }

            Map<String, Object> roadmap = (aiInterviewClient != null)
                ? aiInterviewClient.generateCareerRoadmap(jobTitle, jobDescription, userSkills, missingSkills, currentMatchScore)
                : new AiInterviewClient(new RestTemplate(), "", "").generateFallbackCareerRoadmap(jobTitle, jobDescription, userSkills, missingSkills, currentMatchScore);

            return ResponseEntity.ok(roadmap);
        } catch (Exception e) {
            log.error("Error generating career roadmap: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to generate career roadmap: " + e.getMessage()));
        }
    }
    
    private Map<String, Object> createEnhancedApplicationResponse(Application application) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", application.getId());
        response.put("applicationDate", application.getApplicationDate());
        response.put("status", application.getStatus());
        response.put("coverLetter", application.getCoverLetter());
        response.put("applicantSkills", application.getApplicantSkills());
        response.put("applicantExperience", application.getApplicantExperience());
        response.put("applicantEducation", application.getApplicantEducation());
        response.put("followUpCount", application.getFollowUpCount() != null ? application.getFollowUpCount() : 0);
        response.put("lastFollowUpAt", application.getLastFollowUpAt());
        
        // Resume matching data
        response.put("matchPercentage", application.getMatchPercentage() != null ? 
            Math.round(application.getMatchPercentage() * 100.0) / 100.0 : 0.0);
        response.put("matchedSkills", application.getMatchedSkills());
        response.put("missingSkills", application.getMissingSkills());
        response.put("resumeFilePath", application.getResumeFilePath());
        response.put("hasResume", application.getResumeFilePath() != null && !application.getResumeFilePath().isEmpty());
        
        // Applicant information
        if (application.getUser() != null) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", application.getUser().getId());
            userInfo.put("fullName", application.getUser().getFullName());
            userInfo.put("email", application.getUser().getEmail());
            userInfo.put("phone", application.getUser().getPhone());
            response.put("applicant", userInfo);
            
            response.put("applicantName", application.getUser().getFullName());
            response.put("applicantEmail", application.getUser().getEmail());
            response.put("applicantPhone", application.getUser().getPhone());
        } else {
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