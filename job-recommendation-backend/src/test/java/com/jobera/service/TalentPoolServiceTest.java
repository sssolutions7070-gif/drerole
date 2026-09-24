package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.CandidateNote;
import com.jobera.entity.JobPosting;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.CandidateNoteRepository;

@ExtendWith(MockitoExtension.class)
class TalentPoolServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock CandidateNoteRepository candidateNoteRepository;

    TalentPoolService service;

    User candidateA;
    User candidateB;

    @BeforeEach
    void setUp() {
        service = new TalentPoolService(applicationRepository, candidateNoteRepository);

        candidateA = new User("alice@example.com", "hash", "Alice Anderson");
        candidateA.setId(1L);
        candidateB = new User("bob@example.com", "hash", "Bob Baker");
        candidateB.setId(2L);

        lenient().when(candidateNoteRepository.findByRecruiterId(10L)).thenReturn(List.of());
        lenient().when(applicationRepository.findByRecruiterIdAndCandidateUserId(10L, 1L)).thenReturn(List.of());
        lenient().when(applicationRepository.findByRecruiterIdAndCandidateUserId(10L, 2L)).thenReturn(List.of());
    }

    @Test
    void listsEveryDistinctCandidateForTheRecruiter() {
        when(applicationRepository.findDistinctCandidatesByRecruiterId(10L)).thenReturn(List.of(candidateA, candidateB));

        List<Map<String, Object>> pool = service.getTalentPool(10L, null);

        assertEquals(2, pool.size());
    }

    @Test
    void searchMatchesByCandidateName() {
        when(applicationRepository.findDistinctCandidatesByRecruiterId(10L)).thenReturn(List.of(candidateA, candidateB));

        List<Map<String, Object>> pool = service.getTalentPool(10L, "alice");

        assertEquals(1, pool.size());
        assertEquals("Alice Anderson", pool.get(0).get("fullName"));
    }

    @Test
    void searchMatchesByEmailCaseInsensitively() {
        when(applicationRepository.findDistinctCandidatesByRecruiterId(10L)).thenReturn(List.of(candidateA, candidateB));

        List<Map<String, Object>> pool = service.getTalentPool(10L, "BOB@EXAMPLE");

        assertEquals(1, pool.size());
        assertEquals("Bob Baker", pool.get(0).get("fullName"));
    }

    @Test
    void searchMatchesByNoteTags() {
        when(applicationRepository.findDistinctCandidatesByRecruiterId(10L)).thenReturn(List.of(candidateA, candidateB));
        CandidateNote note = new CandidateNote();
        note.setCandidateUserId(1L);
        note.setTags("[\"strong-backend\"]");
        when(candidateNoteRepository.findByRecruiterId(10L)).thenReturn(List.of(note));

        List<Map<String, Object>> pool = service.getTalentPool(10L, "strong-backend");

        assertEquals(1, pool.size());
        assertEquals("Alice Anderson", pool.get(0).get("fullName"));
    }

    @Test
    void aCandidateWithNoNoteHasNullNotesAndTags() {
        when(applicationRepository.findDistinctCandidatesByRecruiterId(10L)).thenReturn(List.of(candidateA));

        Map<String, Object> entry = service.getTalentPool(10L, null).get(0);

        assertEquals(null, entry.get("notes"));
        assertEquals(null, entry.get("tags"));
    }

    @Test
    void applicationsForACandidateAreIncludedWithJobTitleAndStatus() {
        when(applicationRepository.findDistinctCandidatesByRecruiterId(10L)).thenReturn(List.of(candidateA));
        JobPosting job = new JobPosting();
        job.setTitle("Backend Engineer");
        Application application = new Application();
        application.setId(500L);
        application.setJobPosting(job);
        application.setStatus("SHORTLISTED");
        when(applicationRepository.findByRecruiterIdAndCandidateUserId(10L, 1L)).thenReturn(List.of(application));

        Map<String, Object> entry = service.getTalentPool(10L, null).get(0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> applications = (List<Map<String, Object>>) entry.get("applications");
        assertEquals(1, applications.size());
        assertEquals("Backend Engineer", applications.get(0).get("jobTitle"));
        assertEquals("SHORTLISTED", applications.get(0).get("status"));
    }

    @Test
    void upsertingANoteForANewCandidateCreatesOne() {
        when(candidateNoteRepository.findByRecruiterIdAndCandidateUserId(10L, 1L)).thenReturn(Optional.empty());
        when(candidateNoteRepository.save(any(CandidateNote.class))).thenAnswer(inv -> inv.getArgument(0));

        CandidateNote saved = service.upsertNote(10L, 1L, "Great communicator", "[\"backend\"]");

        assertEquals(10L, saved.getRecruiterId());
        assertEquals(1L, saved.getCandidateUserId());
        assertEquals("Great communicator", saved.getNotes());
    }

    @Test
    void upsertingANoteForAnExistingCandidateUpdatesItInPlace() {
        CandidateNote existing = new CandidateNote();
        existing.setId(99L);
        existing.setRecruiterId(10L);
        existing.setCandidateUserId(1L);
        existing.setNotes("Old note");
        when(candidateNoteRepository.findByRecruiterIdAndCandidateUserId(10L, 1L)).thenReturn(Optional.of(existing));
        when(candidateNoteRepository.save(any(CandidateNote.class))).thenAnswer(inv -> inv.getArgument(0));

        CandidateNote saved = service.upsertNote(10L, 1L, "Updated note", "[\"frontend\"]");

        assertEquals(99L, saved.getId());
        assertEquals("Updated note", saved.getNotes());
        verify(candidateNoteRepository).save(existing);
    }
}
