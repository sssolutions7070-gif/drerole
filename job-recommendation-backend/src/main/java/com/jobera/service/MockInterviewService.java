package com.jobera.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private static final int QUESTIONS_TO_REVISIT = 5;

    /** Every past session's questions/answers already sit in the database (score, feedback,
     *  timestamp) but, until now, nothing ever rolled them up - each session was only ever shown
     *  once, right after finishing it, then effectively lost. This aggregates them into a score
     *  trend over time and the lowest-scoring answered questions across every session, so a
     *  candidate can actually see whether they're improving and what to revisit. */
    public Map<String, Object> getPracticeHistory(Long candidateUserId) {
        List<MockInterviewSession> sessions = sessionRepository.findByCandidateUserIdOrderByCreatedAtDesc(candidateUserId);

        List<Map<String, Object>> sessionSummaries = new ArrayList<>();
        List<Map<String, Object>> scoreTrend = new ArrayList<>();
        List<Map<String, Object>> allAnsweredRows = new ArrayList<>();

        long totalQuestionsAnswered = 0;
        double sumOfAllScores = 0;

        for (MockInterviewSession session : sessions) {
            List<MockInterviewQuestion> questions = questionRepository.findBySessionIdOrderByQuestionNumberAsc(session.getId());
            List<MockInterviewQuestion> answered = questions.stream()
                .filter(q -> q.getScore() != null)
                .collect(Collectors.toList());

            Double averageScore = answered.isEmpty() ? null
                : answered.stream().mapToInt(MockInterviewQuestion::getScore).average().getAsDouble();

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("sessionId", session.getId());
            summary.put("jobTitle", session.getJobTitle());
            summary.put("status", session.getStatus());
            summary.put("createdAt", session.getCreatedAt());
            summary.put("questionCount", questions.size());
            summary.put("answeredCount", answered.size());
            summary.put("averageScore", averageScore == null ? null : round1(averageScore));
            sessionSummaries.add(summary);

            if (averageScore != null) {
                Map<String, Object> trendPoint = new LinkedHashMap<>();
                trendPoint.put("sessionId", session.getId());
                trendPoint.put("jobTitle", session.getJobTitle());
                trendPoint.put("createdAt", session.getCreatedAt());
                trendPoint.put("averageScore", round1(averageScore));
                scoreTrend.add(trendPoint);

                totalQuestionsAnswered += answered.size();
                sumOfAllScores += answered.stream().mapToInt(MockInterviewQuestion::getScore).sum();
            }

            for (MockInterviewQuestion q : answered) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sessionId", session.getId());
                row.put("jobTitle", session.getJobTitle());
                row.put("questionText", q.getQuestionText());
                row.put("answerText", q.getAnswerText());
                row.put("score", q.getScore());
                row.put("feedback", q.getFeedback());
                row.put("answeredAt", q.getAnsweredAt());
                allAnsweredRows.add(row);
            }
        }

        // sessionSummaries/sessions are newest-first (matches listSessions); the trend reads
        // more naturally oldest-to-newest for charting, so reverse just that one.
        List<Map<String, Object>> chronologicalTrend = new ArrayList<>(scoreTrend);
        java.util.Collections.reverse(chronologicalTrend);

        List<Map<String, Object>> questionsToRevisit = allAnsweredRows.stream()
            .sorted(Comparator.comparingInt(row -> (Integer) row.get("score")))
            .limit(QUESTIONS_TO_REVISIT)
            .collect(Collectors.toList());

        Map<String, Object> history = new LinkedHashMap<>();
        history.put("totalSessions", sessions.size());
        history.put("totalQuestionsAnswered", totalQuestionsAnswered);
        history.put("overallAverageScore", totalQuestionsAnswered > 0 ? round1(sumOfAllScores / totalQuestionsAnswered) : null);
        history.put("sessions", sessionSummaries);
        history.put("scoreTrend", chronologicalTrend);
        history.put("questionsToRevisit", questionsToRevisit);
        return history;
    }

    private static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
