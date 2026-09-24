package com.jobera.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jobera.entity.Application;
import com.jobera.entity.CandidateNote;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.CandidateNoteRepository;

/** Every candidate a recruiter has ever received an application from, across all of their job
 *  postings, searchable and independent of any single posting's lifecycle - the "silver medalist"
 *  reuse case: someone who wasn't right for job A but is worth remembering for job B later. */
@Service
public class TalentPoolService {

    private final ApplicationRepository applicationRepository;
    private final CandidateNoteRepository candidateNoteRepository;

    public TalentPoolService(ApplicationRepository applicationRepository,
                              CandidateNoteRepository candidateNoteRepository) {
        this.applicationRepository = applicationRepository;
        this.candidateNoteRepository = candidateNoteRepository;
    }

    public List<Map<String, Object>> getTalentPool(Long recruiterId, String search) {
        List<User> candidates = applicationRepository.findDistinctCandidatesByRecruiterId(recruiterId);
        Map<Long, CandidateNote> notesByCandidateId = new LinkedHashMap<>();
        candidateNoteRepository.findByRecruiterId(recruiterId)
            .forEach(note -> notesByCandidateId.put(note.getCandidateUserId(), note));

        String needle = (search == null) ? "" : search.trim().toLowerCase();

        return candidates.stream()
            .filter(candidate -> matches(candidate, notesByCandidateId.get(candidate.getId()), needle))
            .map(candidate -> buildEntry(recruiterId, candidate, notesByCandidateId.get(candidate.getId())))
            .toList();
    }

    private boolean matches(User candidate, CandidateNote note, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        if (candidate.getFullName() != null && candidate.getFullName().toLowerCase().contains(needle)) {
            return true;
        }
        if (candidate.getEmail() != null && candidate.getEmail().toLowerCase().contains(needle)) {
            return true;
        }
        if (note == null) {
            return false;
        }
        return (note.getTags() != null && note.getTags().toLowerCase().contains(needle))
            || (note.getNotes() != null && note.getNotes().toLowerCase().contains(needle));
    }

    private Map<String, Object> buildEntry(Long recruiterId, User candidate, CandidateNote note) {
        List<Application> applications = applicationRepository
            .findByRecruiterIdAndCandidateUserId(recruiterId, candidate.getId());

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("candidateId", candidate.getId());
        entry.put("fullName", candidate.getFullName());
        entry.put("email", candidate.getEmail());
        entry.put("applications", applications.stream().map(app -> {
            Map<String, Object> appEntry = new LinkedHashMap<>();
            appEntry.put("applicationId", app.getId());
            appEntry.put("jobTitle", app.getJobPosting() != null ? app.getJobPosting().getTitle() : "External");
            appEntry.put("status", app.getStatus());
            appEntry.put("appliedAt", app.getApplicationDate());
            return appEntry;
        }).toList());
        entry.put("notes", note == null ? null : note.getNotes());
        entry.put("tags", note == null ? null : note.getTags());
        return entry;
    }

    public CandidateNote upsertNote(Long recruiterId, Long candidateUserId, String notes, String tags) {
        Optional<CandidateNote> existing = candidateNoteRepository.findByRecruiterIdAndCandidateUserId(recruiterId, candidateUserId);
        CandidateNote note = existing.orElseGet(CandidateNote::new);
        note.setRecruiterId(recruiterId);
        note.setCandidateUserId(candidateUserId);
        note.setNotes(notes);
        note.setTags(tags);
        note.setUpdatedAt(LocalDateTime.now());
        return candidateNoteRepository.save(note);
    }
}
