package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

import com.jobera.entity.JobPosting;
import com.jobera.entity.MockInterviewQuestion;
import com.jobera.entity.MockInterviewSession;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.MockInterviewQuestionRepository;
import com.jobera.repository.MockInterviewSessionRepository;

@ExtendWith(MockitoExtension.class)
class MockInterviewServiceTest {

    @Mock MockInterviewSessionRepository sessionRepository;
    @Mock MockInterviewQuestionRepository questionRepository;
    @Mock JobPostingRepository jobPostingRepository;
    @Mock AiInterviewClient aiInterviewClient;

    MockInterviewService service;

    @BeforeEach
    void setUp() {
        service = new MockInterviewService(sessionRepository, questionRepository, jobPostingRepository, aiInterviewClient, 3);
    }

    @Test
    void startingASessionGeneratesQuestionsAndSavesThem() {
        JobPosting job = new JobPosting();
        job.setId(100L);
        job.setTitle("Backend Engineer");
        job.setDescription("Build APIs");
        job.setRequiredSkills("Java");
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.of(job));
        when(aiInterviewClient.isConfigured()).thenReturn(true);
        when(aiInterviewClient.generateQuestions("Backend Engineer", "Build APIs", "Java", 3))
            .thenReturn(List.of("Q1?", "Q2?", "Q3?"));
        MockInterviewSession saved = new MockInterviewSession(1L, 100L, "Backend Engineer");
        saved.setId(500L);
        when(sessionRepository.save(any(MockInterviewSession.class))).thenReturn(saved);
        when(questionRepository.save(any(MockInterviewQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        MockInterviewSession session = service.startSession(1L, 100L);

        assertEquals(500L, session.getId());
        verify(questionRepository, times(3)).save(any(MockInterviewQuestion.class));
    }

    @Test
    void startingASessionFailsWhenNotConfigured() {
        when(aiInterviewClient.isConfigured()).thenReturn(false);

        assertThrows(MockInterviewService.NotConfiguredException.class, () -> service.startSession(1L, 100L));
        verify(jobPostingRepository, never()).findById(any());
    }

    @Test
    void startingASessionForAMissingJobFails() {
        when(aiInterviewClient.isConfigured()).thenReturn(true);
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(MockInterviewService.JobNotFoundException.class, () -> service.startSession(1L, 100L));
    }

    @Test
    void listingSessionsDelegatesToTheRepository() {
        MockInterviewSession session = new MockInterviewSession(1L, 100L, "Backend Engineer");
        when(sessionRepository.findByCandidateUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(session));

        List<MockInterviewSession> sessions = service.listSessions(1L);

        assertEquals(1, sessions.size());
    }

    @Test
    void getQuestionsForSomeoneElsesSessionFails() {
        MockInterviewSession session = new MockInterviewSession(1L, 100L, "Backend Engineer");
        session.setId(500L);
        when(sessionRepository.findById(500L)).thenReturn(Optional.of(session));

        assertThrows(MockInterviewService.NotYourSessionException.class, () -> service.getQuestions(500L, 999L));
    }

    @Test
    void getQuestionsForAMissingSessionFails() {
        when(sessionRepository.findById(500L)).thenReturn(Optional.empty());

        assertThrows(MockInterviewService.SessionNotFoundException.class, () -> service.getQuestions(500L, 1L));
    }

    @Test
    void submittingAnAnswerScoresItAndSaves() {
        MockInterviewSession session = new MockInterviewSession(1L, 100L, "Backend Engineer");
        session.setId(500L);
        when(sessionRepository.findById(500L)).thenReturn(Optional.of(session));
        MockInterviewQuestion question = new MockInterviewQuestion(500L, 1, "How do you scale a database?");
        question.setId(9000L);
        when(questionRepository.findById(9000L)).thenReturn(Optional.of(question));
        when(aiInterviewClient.evaluateAnswer("Backend Engineer", "How do you scale a database?", "Sharding."))
            .thenReturn(Map.of("score", 4, "feedback", "Good, add more detail."));
        when(questionRepository.save(any(MockInterviewQuestion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.countBySessionIdAndAnswerTextIsNull(500L)).thenReturn(1L);

        MockInterviewQuestion answered = service.submitAnswer(500L, 9000L, 1L, "Sharding.");

        assertEquals(4, answered.getScore());
        assertEquals("Good, add more detail.", answered.getFeedback());
        assertEquals("Sharding.", answered.getAnswerText());
        verify(sessionRepository, never()).save(session);
    }

    @Test
    void answeringTheLastQuestionCompletesTheSession() {
        MockInterviewSession session = new MockInterviewSession(1L, 100L, "Backend Engineer");
        session.setId(500L);
        when(sessionRepository.findById(500L)).thenReturn(Optional.of(session));
        MockInterviewQuestion question = new MockInterviewQuestion(500L, 1, "Q?");
        question.setId(9000L);
        when(questionRepository.findById(9000L)).thenReturn(Optional.of(question));
        when(aiInterviewClient.evaluateAnswer(any(), any(), any())).thenReturn(Map.of("score", 3, "feedback", "OK"));
        when(questionRepository.save(any(MockInterviewQuestion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.countBySessionIdAndAnswerTextIsNull(500L)).thenReturn(0L);

        service.submitAnswer(500L, 9000L, 1L, "My answer");

        assertEquals("COMPLETED", session.getStatus());
        verify(sessionRepository).save(session);
    }

    @Test
    void submittingAnAnswerForAQuestionFromAnotherSessionFails() {
        MockInterviewSession session = new MockInterviewSession(1L, 100L, "Backend Engineer");
        session.setId(500L);
        when(sessionRepository.findById(500L)).thenReturn(Optional.of(session));
        MockInterviewQuestion question = new MockInterviewQuestion(999L, 1, "Q?");
        question.setId(9000L);
        when(questionRepository.findById(9000L)).thenReturn(Optional.of(question));

        assertThrows(MockInterviewService.QuestionNotFoundException.class, () ->
            service.submitAnswer(500L, 9000L, 1L, "Answer"));
    }
}
