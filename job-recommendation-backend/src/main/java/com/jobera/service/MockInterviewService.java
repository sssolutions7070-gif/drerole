package com.jobera.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jobera.entity.JobPosting;
import com.jobera.entity.MockInterviewQuestion;
import com.jobera.entity.MockInterviewSession;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.MockInterviewQuestionRepository;
import com.jobera.repository.MockInterviewSessionRepository;

/** AI mock-interview practice: a candidate starts a session for a specific job, gets a handful of
 *  AI-generated questions based on that job's title/description/skills, and answers them one at a
 *  time, each scored and given feedback by the same AI before moving on. */
@Service
public class MockInterviewService {

    public static class JobNotFoundException extends RuntimeException {
        public JobNotFoundException(Long jobPostingId) {
            super("Job posting not found: " + jobPostingId);
        }
    }

    public static class NotConfiguredException extends RuntimeException {
        public NotConfiguredException() {
            super("AI mock-interview practice is not configured on this server");
        }
    }

    public static class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(Long sessionId) {
            super("Mock interview session not found: " + sessionId);
        }
    }

    public static class QuestionNotFoundException extends RuntimeException {
        public QuestionNotFoundException(Long questionId) {
            super("Mock interview question not found: " + questionId);
        }
    }

    public static class NotYourSessionException extends RuntimeException {
        public NotYourSessionException() {
            super("This mock interview session belongs to a different candidate");
        }
    }

    private final MockInterviewSessionRepository sessionRepository;
    private final MockInterviewQuestionRepository questionRepository;
    private final JobPostingRepository jobPostingRepository;
    private final AiInterviewClient aiInterviewClient;
    private final int questionCount;

    public MockInterviewService(MockInterviewSessionRepository sessionRepository,
                                 MockInterviewQuestionRepository questionRepository,
                                 JobPostingRepository jobPostingRepository,
                                 AiInterviewClient aiInterviewClient,
                                 @Value("${app.ai.mock-interview-question-count:5}") int questionCount) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.aiInterviewClient = aiInterviewClient;
        this.questionCount = questionCount;
    }

    public MockInterviewSession startSession(Long candidateUserId, Long jobPostingId) {
        if (!aiInterviewClient.isConfigured()) {
            throw new NotConfiguredException();
        }
        JobPosting job = jobPostingRepository.findById(jobPostingId)
            .orElseThrow(() -> new JobNotFoundException(jobPostingId));

        List<String> questions = aiInterviewClient.generateQuestions(
            job.getTitle(), job.getDescription(), job.getRequiredSkills(), questionCount);

        MockInterviewSession session = sessionRepository.save(
            new MockInterviewSession(candidateUserId, jobPostingId, job.getTitle()));

        int number = 1;
        for (String questionText : questions) {
            questionRepository.save(new MockInterviewQuestion(session.getId(), number++, questionText));
        }

        return session;
    }

    public List<MockInterviewSession> listSessions(Long candidateUserId) {
        return sessionRepository.findByCandidateUserIdOrderByCreatedAtDesc(candidateUserId);
    }

    private MockInterviewSession requireOwnSession(Long sessionId, Long callerId) {
        MockInterviewSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (!callerId.equals(session.getCandidateUserId())) {
            throw new NotYourSessionException();
        }
        return session;
    }

    public List<MockInterviewQuestion> getQuestions(Long sessionId, Long callerId) {
        requireOwnSession(sessionId, callerId);
        return questionRepository.findBySessionIdOrderByQuestionNumberAsc(sessionId);
    }

    public MockInterviewQuestion submitAnswer(Long sessionId, Long questionId, Long callerId, String answerText) {
        MockInterviewSession session = requireOwnSession(sessionId, callerId);
        MockInterviewQuestion question = questionRepository.findById(questionId)
            .filter(q -> q.getSessionId().equals(sessionId))
            .orElseThrow(() -> new QuestionNotFoundException(questionId));

        var evaluation = aiInterviewClient.evaluateAnswer(session.getJobTitle(), question.getQuestionText(), answerText);

        question.setAnswerText(answerText);
        question.setScore((Integer) evaluation.get("score"));
        question.setFeedback((String) evaluation.get("feedback"));
        question.setAnsweredAt(LocalDateTime.now());
        MockInterviewQuestion saved = questionRepository.save(question);

        if (questionRepository.countBySessionIdAndAnswerTextIsNull(sessionId) == 0) {
            session.setStatus("COMPLETED");
            sessionRepository.save(session);
        }

        return saved;
    }
}
