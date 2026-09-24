package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.CandidateNote;
import com.jobera.service.TalentPoolService;

@ExtendWith(MockitoExtension.class)
class TalentPoolControllerTest {

    @Mock TalentPoolService talentPoolService;

    TalentPoolController controller;

    @BeforeEach
    void setUp() {
        controller = new TalentPoolController(talentPoolService);
    }

    @Test
    void getTalentPoolReturnsWhatTheServiceProvides() {
        List<Map<String, Object>> pool = List.of(Map.of("candidateId", 1L, "fullName", "Alice"));
        when(talentPoolService.getTalentPool(10L, "alice")).thenReturn(pool);

        ResponseEntity<List<Map<String, Object>>> response = controller.getTalentPool(10L, "alice");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(pool, response.getBody());
    }

    @Test
    void upsertNoteDelegatesToTheServiceAndReturnsIt() {
        CandidateNote note = new CandidateNote();
        note.setRecruiterId(10L);
        note.setCandidateUserId(1L);
        when(talentPoolService.upsertNote(10L, 1L, "Great fit", "[\"backend\"]")).thenReturn(note);

        ResponseEntity<CandidateNote> response = controller.upsertNote(10L, 1L,
            Map.of("notes", "Great fit", "tags", "[\"backend\"]"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(note, response.getBody());
    }
}
