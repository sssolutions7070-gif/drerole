package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.JobPosting;
import com.jobera.entity.SavedJob;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.SavedJobRepository;

@ExtendWith(MockitoExtension.class)
class SavedJobServiceTest {

    @Mock SavedJobRepository savedJobRepository;
    @Mock JobPostingRepository jobPostingRepository;

    SavedJobService service;

    @BeforeEach
    void setUp() {
        service = new SavedJobService(savedJobRepository, jobPostingRepository);
    }

    @Test
    void savingARealJobCreatesABookmark() {
        when(jobPostingRepository.existsById(10L)).thenReturn(true);
        when(savedJobRepository.findByUserIdAndJobPostingId(1L, 10L)).thenReturn(Optional.empty());
        when(savedJobRepository.save(any(SavedJob.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedJob result = service.saveJob(1L, 10L);

        assertEquals(1L, result.getUserId());
        assertEquals(10L, result.getJobPostingId());
    }

    @Test
    void savingAJobTwiceIsIdempotent() {
        SavedJob existing = new SavedJob(1L, 10L);
        when(jobPostingRepository.existsById(10L)).thenReturn(true);
        when(savedJobRepository.findByUserIdAndJobPostingId(1L, 10L)).thenReturn(Optional.of(existing));

        SavedJob result = service.saveJob(1L, 10L);

        assertEquals(existing, result);
        verify(savedJobRepository, never()).save(any());
    }

    @Test
    void savingANonExistentJobIsRejected() {
        when(jobPostingRepository.existsById(999L)).thenReturn(false);

        assertThrows(SavedJobService.JobPostingNotFoundException.class, () -> service.saveJob(1L, 999L));
        verify(savedJobRepository, never()).save(any());
    }

    @Test
    void unsavingIsIdempotentEvenIfNothingWasSaved() {
        service.unsaveJob(1L, 10L);

        verify(savedJobRepository, times(1)).deleteByUserIdAndJobPostingId(1L, 10L);
    }

    @Test
    void isJobSavedDelegatesToTheRepository() {
        when(savedJobRepository.existsByUserIdAndJobPostingId(1L, 10L)).thenReturn(true);

        assertTrue(service.isJobSaved(1L, 10L));
    }

    @Test
    void getSavedJobsSkipsBookmarksWhoseJobWasSinceDeleted() {
        SavedJob stillExists = new SavedJob(1L, 10L);
        SavedJob deletedJob = new SavedJob(1L, 20L);
        when(savedJobRepository.findByUserIdOrderBySavedAtDesc(1L)).thenReturn(List.of(stillExists, deletedJob));

        JobPosting job10 = new JobPosting();
        job10.setId(10L);
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(job10));
        when(jobPostingRepository.findById(20L)).thenReturn(Optional.empty());

        List<JobPosting> result = service.getSavedJobs(1L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
    }
}
