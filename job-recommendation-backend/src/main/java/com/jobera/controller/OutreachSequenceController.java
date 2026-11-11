package com.jobera.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.entity.OutreachSequence;
import com.jobera.service.OutreachSequenceService;

/** A recruiter queues/lists/cancels multi-step outreach campaigns to sourced candidates - see
 *  OutreachSequenceService. Gated by AccessPolicy's ownRecruiterId check, same as talent pool
 *  and saved searches. */
@RestController
public class OutreachSequenceController {

    private static final String ERROR_KEY = "error";

    private final OutreachSequenceService outreachSequenceService;

    public OutreachSequenceController(OutreachSequenceService outreachSequenceService) {
        this.outreachSequenceService = outreachSequenceService;
    }

    @PostMapping("/api/recruiters/{recruiterId}/outreach-sequences")
    public ResponseEntity<?> startSequence(@PathVariable Long recruiterId, @RequestBody Map<String, Object> payload) {
        try {
            Long candidateUserId = Long.valueOf(payload.get("candidateUserId").toString());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps = (List<Map<String, Object>>) payload.get("steps");

            OutreachSequence sequence = outreachSequenceService.startSequence(recruiterId, candidateUserId, steps);
            return ResponseEntity.status(HttpStatus.CREATED).body(sequence);
        } catch (OutreachSequenceService.RecruiterNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (OutreachSequenceService.InvalidStepsException
                 | OutreachSequenceService.SequenceAlreadyActiveException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/api/recruiters/{recruiterId}/outreach-sequences")
    public ResponseEntity<List<Map<String, Object>>> listSequences(@PathVariable Long recruiterId) {
        return ResponseEntity.ok(outreachSequenceService.listForRecruiter(recruiterId));
    }

    @DeleteMapping("/api/recruiters/{recruiterId}/outreach-sequences/{sequenceId}")
    public ResponseEntity<?> stopSequence(@PathVariable Long recruiterId, @PathVariable Long sequenceId) {
        try {
            outreachSequenceService.stopSequence(recruiterId, sequenceId);
            return ResponseEntity.noContent().build();
        } catch (OutreachSequenceService.SequenceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
