package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import com.jobera.entity.MockInterviewQuestion;
import com.jobera.entity.MockInterviewSession;
import com.jobera.service.AiInterviewClient;
import com.jobera.service.MockInterviewService;

@ExtendWith(MockitoExtension.class)
class MockInterviewControllerTest {

    @Mock MockInterviewService mockInterviewService;

    MockInterviewController controller;

    @BeforeEach
    void setUp() {
        controller = new MockInterviewController(mockInterviewService);
    }

    @Test
    void startingASessionReturnsItWithQuestions() {
        MockInterviewSession session = new MockInterviewSession(1L, 100L, "Backend Engineer");
        session.setId(500L);
        when(mockInterviewService.startSession(1L, 100L)).thenReturn(session);
        when(mockInterviewService.getQuestions(500L, 1L)).thenReturn(List.of(new MockInterviewQuestion(500L, 1, "Q1?")));

        ResponseEntity<?> response = controller.start(1L, Map.of("jobId", 100));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void startingWithoutAJobIdIsRejected() {
        ResponseEntity<?> response = controller.start(1L, Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void startingWithANonNumericJobIdIsRejected() {
        ResponseEntity<?> response = controller.start(1L, Map.of("jobId", "not-a-number"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void startingWhenNotConfiguredReturnsBadRequest() {
        when(mockInterviewService.startSession(1L, 100L)).thenThrow(new MockInterviewService.NotConfiguredException());

        ResponseEntity<?> response = controller.start(1L, Map.of("jobId", 100));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void startingForAMissingJobReturnsBadRequest() {
        when(mockInterviewService.startSession(1L, 100L)).thenThrow(new MockInterviewService.JobNotFoundException(100L));

        ResponseEntity<?> response = controller.start(1L, Map.of("jobId", 100));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void listingSessionsReturnsWhatTheServiceProvides() {
        List<MockInterviewSession> sessions = List.of(new MockInterviewSession(1L, 100L, "Backend Engineer"));
        when(mockInterviewService.listSessions(1L)).thenReturn(sessions);

        ResponseEntity<List<MockInterviewSession>> response = controller.list(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(sessions, response.getBody());
    }

    @Test
    void gettingASessionReturnsItsQuestions() {
        List<MockInterviewQuestion> questions = List.of(new MockInterviewQuestion(500L, 1, "Q1?"));
        when(mockInterviewService.getQuestions(500L, 1L)).thenReturn(questions);

        ResponseEntity<?> response = controller.get(1L, 500L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void gettingSomeoneElsesSessionReturnsBadRequest() {
        when(mockInterviewService.getQuestions(500L, 1L)).thenThrow(new MockInterviewService.NotYourSessionException());

        ResponseEntity<?> response = controller.get(1L, 500L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void answeringAQuestionReturnsTheUpdatedQuestion() {
        MockInterviewQuestion answered = new MockInterviewQuestion(500L, 1, "Q1?");
        answered.setScore(4);
        when(mockInterviewService.submitAnswer(500L, 9000L, 1L, "My answer")).thenReturn(answered);

        ResponseEntity<?> response = controller.answer(1L, 500L, 9000L, Map.of("answer", "My answer"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(answered, response.getBody());
    }

    @Test
    void answeringWithoutAnAnswerIsRejected() {
        ResponseEntity<?> response = controller.answer(1L, 500L, 9000L, Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void answeringWhenTheAiServiceFailsReturnsBadRequest() {
        when(mockInterviewService.submitAnswer(anyLong(), anyLong(), anyLong(), any()))
            .thenThrow(new AiInterviewClient.AiInterviewException("Failed to reach the AI interview service"));

        ResponseEntity<?> response = controller.answer(1L, 500L, 9000L, Map.of("answer", "My answer"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
