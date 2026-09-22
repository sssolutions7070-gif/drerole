// src/main/java/com/jobera/controller/ExternalJobController.java
package com.jobera.controller;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.jobera.entity.ExternalCandidate;
import com.jobera.entity.ExternalJobPosting;
import com.jobera.service.ExternalJobService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/external-jobs")
public class ExternalJobController {
    private static final Logger log = LoggerFactory.getLogger(ExternalJobController.class);

    
    private static final String ERROR = "error";
    private final ExternalJobService externalJobService;
    
    public ExternalJobController(ExternalJobService externalJobService) {
        this.externalJobService = externalJobService;
    }
    
    // ========== JOB POSTING MANAGEMENT ==========
    
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createExternalJob(
            @RequestParam Long recruiterId,
            @RequestBody Map<String, Object> jobData) {
        try {
            ExternalJobPosting job = externalJobService.createExternalJobPosting(recruiterId, jobData);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "External job created successfully");
            response.put("job", job);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR, e.getMessage()));
        }
    }
    
    @GetMapping("/recruiter/{recruiterId}")
    public ResponseEntity<List<ExternalJobPosting>> getRecruiterExternalJobs(@PathVariable Long recruiterId) {
        try {
            List<ExternalJobPosting> jobs = externalJobService.getRecruiterExternalJobs(recruiterId);
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }
    
    @GetMapping("/{jobId}")
    public ResponseEntity<ExternalJobPosting> getExternalJob(@PathVariable Long jobId) {
        try {
            return externalJobService.getExternalJobById(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> deleteExternalJob(
            @PathVariable Long jobId,
            @RequestParam Long recruiterId) {
        try {
            Map<String, Object> result = externalJobService.deleteExternalJob(jobId, recruiterId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR, e.getMessage()));
        }
    }
    
    // ========== RESUME UPLOAD AND CANDIDATE MANAGEMENT ==========
    
    @PostMapping("/{jobId}/upload-resumes")
    public ResponseEntity<Map<String, Object>> uploadResumes(
            @PathVariable Long jobId,
            @RequestParam("files") List<MultipartFile> files) {
        try {
            log.debug("=".repeat(60));
            log.debug("UPLOAD RESUMES REQUEST RECEIVED");
            log.debug("=".repeat(60));
            log.debug("Job ID: " + jobId);
            log.debug("Number of files: " + files.size());
            log.debug("Files: " + files.stream().map(f -> f.getOriginalFilename()).limit(10).toList());
            
            if (files.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(ERROR, "No files provided"));
            }
            
            long startTime = System.currentTimeMillis();
            
            Map<String, Object> result = externalJobService.uploadAndAnalyzeResumes(jobId, files);
            
            long endTime = System.currentTimeMillis();
            long duration = (endTime - startTime) / 1000;
            
            result.put("processingTimeSeconds", duration);
            
            log.debug("=".repeat(60));
            log.debug("UPLOAD COMPLETED");
            log.debug("Time taken: " + duration + " seconds");
            log.debug("Successful: " + result.get("successfulUploads"));
            log.error("Failed: " + result.get("failedUploads"));
            log.debug("=".repeat(60));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error in upload: " + e.getMessage());
            log.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(ERROR, "Upload failed: " + e.getMessage()));
        }
    }
    
    @GetMapping("/{jobId}/upload-status")
    public ResponseEntity<Map<String, Object>> getUploadStatus(@PathVariable Long jobId) {
        try {
            Map<String, Object> status = externalJobService.getUploadStatus(jobId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR, e.getMessage()));
        }
    }
    
    @PostMapping("/{jobId}/select-candidates")
    public ResponseEntity<Map<String, Object>> selectTopCandidates(
            @PathVariable Long jobId,
            @RequestBody Map<String, Integer> request) {
        try {
            Integer numberOfCandidates = request.get("numberOfCandidates");
            Map<String, Object> result = externalJobService.selectTopCandidates(jobId, numberOfCandidates);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", result.get("message"));
            response.put("totalShortlisted", result.get("totalShortlisted"));
            response.put("newUsersCount", result.get("newUsersCount"));
            response.put("existingUsersCount", result.get("existingUsersCount"));
            response.put("downloadUrl", result.get("downloadUrl"));
            response.put("shortlistedCandidates", result.get("shortlistedCandidates"));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR, e.getMessage()));
        }
    }
    
    @GetMapping("/{jobId}/shortlisted-candidates")
    public ResponseEntity<List<ExternalCandidate>> getShortlistedCandidates(@PathVariable Long jobId) {
        try {
            List<ExternalCandidate> candidates = externalJobService.getShortlistedCandidates(jobId);
            return ResponseEntity.ok(candidates);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }
    
    @GetMapping("/{jobId}/all-candidates")
    public ResponseEntity<List<ExternalCandidate>> getAllCandidates(@PathVariable Long jobId) {
        try {
            List<ExternalCandidate> candidates = externalJobService.getAllCandidates(jobId);
            return ResponseEntity.ok(candidates);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }
    
    @GetMapping("/{jobId}/candidates-with-results")
    public ResponseEntity<List<Map<String, Object>>> getExternalCandidatesWithTestResults(@PathVariable Long jobId) {
        try {
            List<Map<String, Object>> candidates = externalJobService.getExternalCandidatesWithTestResults(jobId);
            return ResponseEntity.ok(candidates);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }
    
    @GetMapping("/candidates/{candidateId}/full-details")
    public ResponseEntity<Map<String, Object>> getExternalCandidateFullDetails(@PathVariable Long candidateId) {
        try {
            Map<String, Object> candidate = externalJobService.getExternalCandidateFullDetails(candidateId);
            return ResponseEntity.ok(candidate);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR, e.getMessage()));
        }
    }
    
    @GetMapping("/candidates/{candidateId}/analysis")
    public ResponseEntity<Map<String, Object>> getCandidateAnalysis(@PathVariable Long candidateId) {
        try {
            Map<String, Object> analysis = externalJobService.getCandidateAnalysis(candidateId);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR, e.getMessage()));
        }
    }
    
    // ========== TEST ASSIGNMENT FOR EXTERNAL CANDIDATES ==========
    
    @PostMapping("/assign-test")
    public ResponseEntity<Map<String, Object>> assignTestToExternalCandidate(@RequestBody Map<String, Object> request) {
        try {
            log.debug("=".repeat(60));
            log.debug("ASSIGN TEST TO EXTERNAL CANDIDATE");
            log.debug("=".repeat(60));
            
            Long candidateId = Long.valueOf(request.get("candidateId").toString());
            Long testId = Long.valueOf(request.get("testId").toString());
            Integer deadlineHours = Integer.valueOf(request.get("deadlineHours").toString());
            Boolean isDreamRoleTest = (Boolean) request.getOrDefault("isDreamRoleTest", false);
            
            log.debug("Candidate ID: " + candidateId);
            log.debug("Test ID: " + testId);
            log.debug("Deadline Hours: " + deadlineHours);
            log.debug("Is DreamRole Test: " + isDreamRoleTest);
            
            Map<String, Object> result = externalJobService.assignTestToExternalCandidate(
                candidateId, testId, deadlineHours, isDreamRoleTest);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error assigning test: " + e.getMessage());
            log.error("Unexpected error", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/recruiter/{recruiterId}/shortlisted-candidates-for-test")
    public ResponseEntity<List<Map<String, Object>>> getShortlistedCandidatesForTestAssignmentByRecruiter(
            @PathVariable Long recruiterId) {
        try {
            List<Map<String, Object>> candidates = externalJobService.getShortlistedCandidatesForTestAssignmentByRecruiter(recruiterId);
            log.debug("Found " + candidates.size() + " shortlisted external candidates for recruiter " + recruiterId);
            return ResponseEntity.ok(candidates);
        } catch (Exception e) {
            log.error("Error fetching shortlisted candidates: " + e.getMessage());
            log.error("Unexpected error", e);
            return ResponseEntity.ok(List.of());
        }
    }
    
    @GetMapping("/recruiter/{recruiterId}/jobs-with-stats")
    public ResponseEntity<List<Map<String, Object>>> getRecruiterExternalJobsWithStats(@PathVariable Long recruiterId) {
        try {
            List<Map<String, Object>> jobs = externalJobService.getRecruiterExternalJobsWithStats(recruiterId);
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }
    
    @GetMapping("/{jobId}/stats")
    public ResponseEntity<Map<String, Object>> getExternalJobStats(@PathVariable Long jobId) {
        try {
            Map<String, Object> stats = externalJobService.getExternalJobStats(jobId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR, e.getMessage()));
        }
    }
    
    @PostMapping("/candidates/{candidateId}/schedule-interview")
    public ResponseEntity<Map<String, Object>> scheduleInterviewForExternalCandidate(
            @PathVariable Long candidateId,
            @RequestBody Map<String, Object> request) {
        try {
            String interviewDateStr = (String) request.get("interviewDate");
            String interviewLocation = (String) request.get("interviewLocation");
            String notes = (String) request.get("notes");
            
            LocalDateTime interviewDate = LocalDateTime.parse(interviewDateStr);
            
            Map<String, Object> result = externalJobService.scheduleInterviewForExternalCandidate(
                candidateId, interviewDate, interviewLocation, notes);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR, e.getMessage()));
        }
    }
    
    @GetMapping("/download-credentials/{fileName}")
    public ResponseEntity<Resource> downloadCredentials(@PathVariable String fileName) {
        try {
            Path baseDir = Paths.get("uploads/external_resumes/").toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(fileName).normalize();
            if (!filePath.startsWith(baseDir) || !fileName.matches("credentials_job_[0-9]+_[0-9]+[.]csv")) {
                return ResponseEntity.badRequest().build();
            }
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}