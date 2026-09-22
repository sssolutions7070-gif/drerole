package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Resume;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.UserRepository;

/** Covers ResumeService.pruneOldResumes, the per-user resume cap run after every upload. */
@ExtendWith(MockitoExtension.class)
class ResumeServicePruningTest {

    @Mock ResumeRepository resumeRepository;
    @Mock UserRepository userRepository;
    @Mock ResumeProcessingService resumeProcessingService;

    ResumeService service;

    private ResumeService newService() {
        return new ResumeService(resumeRepository, userRepository, resumeProcessingService);
    }

    private Resume resume(long id, int daysAgo) {
        Resume resume = new Resume();
        resume.setId(id);
        resume.setUploadDate(LocalDateTime.now().minusDays(daysAgo));
        resume.setFilePath("/uploads/" + id + "_resume.pdf");
        return resume;
    }

    @Test
    void keepsExactlyFiveResumesDeletingTheOldestFirst() {
        service = newService();
        // Oldest first, matching findByUserIdOrderByUploadDateAsc's contract.
        List<Resume> oldestFirst = new ArrayList<>(List.of(
            resume(1, 10), resume(2, 9), resume(3, 8), resume(4, 7), resume(5, 6), resume(6, 5), resume(7, 4)));
        when(resumeRepository.findByUserIdOrderByUploadDateAsc(42L)).thenReturn(oldestFirst);
        // Only the two oldest (ids 1 and 2) are expected to be pruned; deleteResume looks each up by id.
        when(resumeRepository.findById(1L)).thenReturn(Optional.of(oldestFirst.get(0)));
        when(resumeRepository.findById(2L)).thenReturn(Optional.of(oldestFirst.get(1)));

        service.pruneOldResumes(42L);

        ArgumentCaptor<Resume> deleted = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository, times(2)).delete(deleted.capture());
        List<Long> deletedIds = deleted.getAllValues().stream().map(Resume::getId).toList();
        assertEquals(List.of(1L, 2L), deletedIds);
        verify(resumeRepository, never()).delete(argThatId(3L));
    }

    @Test
    void doesNothingWhenAtOrBelowTheCap() {
        service = newService();
        List<Resume> fiveResumes = List.of(resume(1, 5), resume(2, 4), resume(3, 3), resume(4, 2), resume(5, 1));
        when(resumeRepository.findByUserIdOrderByUploadDateAsc(42L)).thenReturn(fiveResumes);

        service.pruneOldResumes(42L);

        verify(resumeRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNothingForAUserWithNoResumes() {
        service = newService();
        when(resumeRepository.findByUserIdOrderByUploadDateAsc(42L)).thenReturn(List.of());

        service.pruneOldResumes(42L);

        verify(resumeRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private Resume argThatId(long id) {
        return org.mockito.ArgumentMatchers.argThat(r -> r != null && r.getId() == id);
    }
}
