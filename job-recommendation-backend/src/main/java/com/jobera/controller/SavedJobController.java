package com.jobera.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.dto.JobPostingDTO;
import com.jobera.service.SavedJobService;

/** Candidates bookmarking jobs to revisit later. Covered by the existing
 *  "/api/users/{userId}/**" self-ownership rule in AccessPolicy - no new rule needed. */
@RestController
@RequestMapping("/api/users/{userId}/saved-jobs")
public class SavedJobController {

    private static final String MESSAGE_KEY = "message";
    private static final String ERROR_KEY = "error";

    private final SavedJobService savedJobService;

    public SavedJobController(SavedJobService savedJobService) {
        this.savedJobService = savedJobService;
    }

    @GetMapping
    public ResponseEntity<List<JobPostingDTO>> getSavedJobs(@PathVariable Long userId) {
        List<JobPostingDTO> jobs = savedJobService.getSavedJobs(userId).stream()
            .map(JobPostingDTO::new)
            .toList();
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{jobId}/status")
    public ResponseEntity<Map<String, Object>> isSaved(@PathVariable Long userId, @PathVariable Long jobId) {
        return ResponseEntity.ok(Map.of("saved", savedJobService.isJobSaved(userId, jobId)));
    }

    @PostMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> saveJob(@PathVariable Long userId, @PathVariable Long jobId) {
        try {
            savedJobService.saveJob(userId, jobId);
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Job saved"));
        } catch (SavedJobService.JobPostingNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> unsaveJob(@PathVariable Long userId, @PathVariable Long jobId) {
        savedJobService.unsaveJob(userId, jobId);
        return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Job removed from saved list"));
    }
}
