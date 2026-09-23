package com.jobera.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.jobera.entity.JobPosting;
import com.jobera.entity.SavedJob;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.SavedJobRepository;

@Service
public class SavedJobService {

    public static class JobPostingNotFoundException extends RuntimeException {
        public JobPostingNotFoundException(Long jobId) {
            super("Job posting not found: " + jobId);
        }
    }

    private final SavedJobRepository savedJobRepository;
    private final JobPostingRepository jobPostingRepository;

    public SavedJobService(SavedJobRepository savedJobRepository, JobPostingRepository jobPostingRepository) {
        this.savedJobRepository = savedJobRepository;
        this.jobPostingRepository = jobPostingRepository;
    }

    /** Idempotent: saving an already-saved job just returns the existing bookmark. */
    public SavedJob saveJob(Long userId, Long jobPostingId) {
        if (!jobPostingRepository.existsById(jobPostingId)) {
            throw new JobPostingNotFoundException(jobPostingId);
        }
        return savedJobRepository.findByUserIdAndJobPostingId(userId, jobPostingId)
            .orElseGet(() -> savedJobRepository.save(new SavedJob(userId, jobPostingId)));
    }

    /** Idempotent: unsaving a job that isn't saved is a no-op, not an error. */
    public void unsaveJob(Long userId, Long jobPostingId) {
        savedJobRepository.deleteByUserIdAndJobPostingId(userId, jobPostingId);
    }

    public boolean isJobSaved(Long userId, Long jobPostingId) {
        return savedJobRepository.existsByUserIdAndJobPostingId(userId, jobPostingId);
    }

    /** Most-recently-saved first; silently skips bookmarks whose job posting was since deleted. */
    public List<JobPosting> getSavedJobs(Long userId) {
        return savedJobRepository.findByUserIdOrderBySavedAtDesc(userId).stream()
            .map(saved -> jobPostingRepository.findById(saved.getJobPostingId()).orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}
