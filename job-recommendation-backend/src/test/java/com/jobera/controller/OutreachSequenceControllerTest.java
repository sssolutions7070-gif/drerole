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

import com.jobera.entity.OutreachSequence;
import com.jobera.service.OutreachSequenceService;

@ExtendWith(MockitoExtension.class)
class OutreachSequenceControllerTest {

    @Mock OutreachSequenceService outreachSequenceService;

    OutreachSequenceController controller;

    @BeforeEach
    void setUp() {
        controller = new OutreachSequenceController(outreachSequenceService);
    }

    @Test
    void startsASequence() {
        OutreachSequence saved = new OutreachSequence(1L, 10L, "[]");
        saved.setId(5L);
        when(outreachSequenceService.startSequence(1L, 10L, List.of(Map.of("delayDays", 0, "message", "Hi"))))
            .thenReturn(saved);

        ResponseEntity<?> response = controller.startSequence(1L, Map.of(
            "candidateUserId", 10,
            "steps", List.of(Map.of("delayDays", 0, "message", "Hi"))));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void returnsNotFoundForAnUnknownRecruiter() {
        when(outreachSequenceService.startSequence(404L, 10L, List.of(Map.of("delayDays", 0, "message", "Hi"))))
            .thenThrow(new OutreachSequenceService.RecruiterNotFoundException(404L));

        ResponseEntity<?> response = controller.startSequence(404L, Map.of(
            "candidateUserId", 10,
            "steps", List.of(Map.of("delayDays", 0, "message", "Hi"))));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void rejectsAnAlreadyActiveSequence() {
        when(outreachSequenceService.startSequence(1L, 10L, List.of(Map.of("delayDays", 0, "message", "Hi"))))
            .thenThrow(new OutreachSequenceService.SequenceAlreadyActiveException());

        ResponseEntity<?> response = controller.startSequence(1L, Map.of(
            "candidateUserId", 10,
            "steps", List.of(Map.of("delayDays", 0, "message", "Hi"))));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void listsSequencesForARecruiter() {
        when(outreachSequenceService.listForRecruiter(1L)).thenReturn(List.of(Map.of("id", 5L)));

        ResponseEntity<List<Map<String, Object>>> response = controller.listSequences(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void stopsASequence() {
        ResponseEntity<?> response = controller.stopSequence(1L, 5L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void returnsNotFoundWhenStoppingAnUnknownSequence() {
        org.mockito.Mockito.doThrow(new OutreachSequenceService.SequenceNotFoundException(5L))
            .when(outreachSequenceService).stopSequence(1L, 5L);

        ResponseEntity<?> response = controller.stopSequence(1L, 5L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
