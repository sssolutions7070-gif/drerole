package com.jobera.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.entity.CandidateNote;
import com.jobera.service.TalentPoolService;

/** A recruiter's talent pool: every candidate who has ever applied to any of their postings,
 *  searchable, with private notes/tags independent of any one job's lifecycle. Scoped by
 *  AccessPolicy's existing ownRecruiterId() check. */
@RestController
public class TalentPoolController {

    private final TalentPoolService talentPoolService;

    public TalentPoolController(TalentPoolService talentPoolService) {
        this.talentPoolService = talentPoolService;
    }

    @GetMapping("/api/recruiters/{recruiterId}/talent-pool")
    public ResponseEntity<List<Map<String, Object>>> getTalentPool(@PathVariable Long recruiterId,
                                                                     @RequestParam(required = false) String search) {
        return ResponseEntity.ok(talentPoolService.getTalentPool(recruiterId, search));
    }

    @PutMapping("/api/recruiters/{recruiterId}/talent-pool/{candidateUserId}/note")
    public ResponseEntity<CandidateNote> upsertNote(@PathVariable Long recruiterId, @PathVariable Long candidateUserId,
                                                     @RequestBody Map<String, String> payload) {
        CandidateNote note = talentPoolService.upsertNote(recruiterId, candidateUserId,
            payload.get("notes"), payload.get("tags"));
        return ResponseEntity.ok(note);
    }
}
