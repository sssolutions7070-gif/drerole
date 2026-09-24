package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.Application;
import com.jobera.entity.AptitudeTest;
import com.jobera.entity.DreamRoleTest;
import com.jobera.entity.DreamRoleTestAssignment;
import com.jobera.entity.ProctoringViolation;
import com.jobera.entity.TestAssignment;
import com.jobera.entity.TestQuestion;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.AptitudeTestRepository;
import com.jobera.repository.DreamRoleTestAssignmentRepository;
import com.jobera.repository.ProctoringViolationRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.TestAssignmentRepository;
import com.jobera.repository.TestQuestionRepository;
import com.jobera.service.SkillBadgeService;

@ExtendWith(MockitoExtension.class)
class AptitudeTestControllerTest {

    @Mock AptitudeTestRepository testRepository;
    @Mock TestQuestionRepository questionRepository;
    @Mock TestAssignmentRepository assignmentRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock RecruiterRepository recruiterRepository;
    @Mock ProctoringViolationRepository violationRepository;
    @Mock DreamRoleTestAssignmentRepository dreamRoleAssignmentRepository;
    @Mock SkillBadgeService skillBadgeService;

    AptitudeTestController controller;

    @BeforeEach
    void setUp() {
        controller = new AptitudeTestController(testRepository, questionRepository, assignmentRepository,
            applicationRepository, recruiterRepository, violationRepository, dreamRoleAssignmentRepository,
            skillBadgeService);
    }

    private AptitudeTest test() {
        AptitudeTest test = new AptitudeTest();
        test.setId(3L);
        test.setTestName("Logic Test");
        test.setPassingScore(60);
        return test;
    }

    private Application application(long id, String status, String name) {
        Application application = new Application();
        application.setId(id);
        application.setStatus(status);
        application.setApplicantName(name);
        application.setApplicantEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
        return application;
    }

    private TestAssignment assignment(long id, Application application) {
        TestAssignment assignment = new TestAssignment();
        assignment.setId(id);
        assignment.setTest(test());
        assignment.setApplication(application);
        assignment.setStatus("IN_PROGRESS");
        assignment.setDeadline(LocalDateTime.now().plusHours(2));
        return assignment;
    }

    private TestQuestion question(long id, String correct) {
        TestQuestion question = new TestQuestion();
        question.setId(id);
        question.setCorrectAnswer(correct);
        return question;
    }

    @Test
    void terminatedSubmissionFailsTheTestWithoutGrading() {
        Application application = application(9, "TEST_ASSIGNED", "Asha Rao");
        TestAssignment assignment = assignment(5, application);
        when(assignmentRepository.findById(5L)).thenReturn(Optional.of(assignment));

        ResponseEntity<Map<String, Object>> response = controller.submitTest(
            Map.of("assignmentId", 5, "terminated", true, "terminationReason", "Tab switch detected"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().get("terminated"));
        assertEquals("FAILED_PROCTORING", assignment.getStatus());
        assertEquals(Boolean.FALSE, assignment.getPassed());
        assertEquals(0, assignment.getScore());
        assertEquals(Boolean.TRUE, assignment.getProctoringTerminated());
        assertEquals("Tab switch detected", assignment.getProctoringTerminationReason());
        assertEquals("TEST_FAILED", application.getStatus());
        verify(questionRepository, never()).findByTestId(any());
    }

    @Test
    void normalSubmissionIsGradedAgainstThePassingScore() {
        Application application = application(9, "TEST_ASSIGNED", "Asha Rao");
        TestAssignment assignment = assignment(5, application);
        when(assignmentRepository.findById(5L)).thenReturn(Optional.of(assignment));
        when(questionRepository.findByTestId(3L)).thenReturn(List.of(question(1, "A"), question(2, "B")));

        ResponseEntity<Map<String, Object>> response = controller.submitTest(
            Map.of("assignmentId", 5, "answers", Map.of("1", "A", "2", "C")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(50, response.getBody().get("percentage"));
        assertEquals(false, response.getBody().get("passed"));
        assertEquals("COMPLETED", assignment.getStatus());
        assertEquals("TEST_FAILED", application.getStatus());
    }

    @Test
    void finishedAssignmentsCannotBeSubmittedAgain() {
        TestAssignment assignment = assignment(5, application(9, "TEST_FAILED", "Asha Rao"));
        assignment.setStatus("FAILED_PROCTORING");
        when(assignmentRepository.findById(5L)).thenReturn(Optional.of(assignment));

        ResponseEntity<Map<String, Object>> response = controller.submitTest(Map.of("assignmentId", 5));

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void violationsFromTheProctoringServiceAreStoredAndTerminateTheTest() {
        Application application = application(9, "TEST_ASSIGNED", "Asha Rao");
        TestAssignment assignment = assignment(5, application);
        when(assignmentRepository.findById(5L)).thenReturn(Optional.of(assignment));

        Map<String, Object> body = Map.of(
            "terminated", true,
            "termination_reason", "2 people detected during test",
            "violations", List.of(
                Map.of("type", "multiple_faces", "message", "2 people detected during test", "critical", true),
                Map.of("type", "mobile_phone", "message", "Mobile phone detected during test", "critical", true)));

        ResponseEntity<Map<String, Object>> response = controller.recordViolations("5", body);

        assertEquals(200, response.getStatusCode().value());
        verify(violationRepository, times(2)).save(any(ProctoringViolation.class));
        assertEquals(1, assignment.getMultipleFacesViolations());
        assertEquals(1, assignment.getMobileDetectedViolations());
        assertEquals(2, assignment.getWarningCount());
        assertEquals("FAILED_PROCTORING", assignment.getStatus());
        assertEquals("TEST_FAILED", application.getStatus());
    }

    @Test
    void violationsForACompletedAssignmentAreIgnored() {
        TestAssignment assignment = assignment(5, application(9, "TEST_PASSED", "Asha Rao"));
        assignment.setStatus("COMPLETED");
        when(assignmentRepository.findById(5L)).thenReturn(Optional.of(assignment));

        ResponseEntity<Map<String, Object>> response = controller.recordViolations("5",
            Map.of("terminated", true, "violations", List.of(Map.of("type", "no_face", "message", "x"))));

        assertEquals(200, response.getStatusCode().value());
        verify(violationRepository, never()).save(any());
        assertEquals("COMPLETED", assignment.getStatus());
    }

    @Test
    void violationsForAnUnknownAssignmentReturnBadRequest() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.recordViolations("99", Map.of());

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void recruiterStatsCombineAptitudeAndDreamRoleAssignments() {
        TestAssignment strong = assignment(1, application(1, "TEST_PASSED", "A One"));
        strong.setStatus("COMPLETED");
        strong.setScore(8);
        strong.setTotalQuestions(10);
        strong.setPassed(true);
        TestAssignment weak = assignment(2, application(2, "TEST_FAILED", "B Two"));
        weak.setStatus("COMPLETED");
        weak.setScore(4);
        weak.setTotalQuestions(10);
        weak.setPassed(false);
        TestAssignment pending = assignment(3, application(3, "TEST_ASSIGNED", "C Three"));
        pending.setStatus("PENDING");

        DreamRoleTestAssignment dreamRole = new DreamRoleTestAssignment();
        dreamRole.setDreamRoleTest(new DreamRoleTest());
        dreamRole.setStatus("COMPLETED");
        dreamRole.setScore(10);
        dreamRole.setTotalQuestions(10);
        dreamRole.setPassed(true);

        when(assignmentRepository.findByRecruiterId(1L)).thenReturn(List.of(strong, weak, pending));
        when(dreamRoleAssignmentRepository.findByRecruiterId(1L)).thenReturn(List.of(dreamRole));

        Map<String, Object> stats = controller.getRecruiterTestStats(1L).getBody();

        assertEquals(4, stats.get("totalAssigned"));
        assertEquals(3, stats.get("completed"));
        assertEquals(2, stats.get("passed"));
        assertEquals(73, ((Number) stats.get("averageScore")).intValue());
        assertEquals(67, ((Number) stats.get("passRate")).intValue());
    }

    @Test
    void recruiterStatsAreZeroWhenNothingIsAssigned() {
        Map<String, Object> stats = controller.getRecruiterTestStats(1L).getBody();

        assertEquals(0, stats.get("totalAssigned"));
        assertEquals(0, ((Number) stats.get("passRate")).intValue());
    }

    @Test
    void unscheduledResultsExcludeCandidatesWhoseInterviewIsAlreadyScheduled() {
        TestAssignment waiting = assignment(1, application(1, "TEST_PASSED", "Waiting Candidate"));
        TestAssignment scheduled = assignment(2, application(2, "INTERVIEW_SCHEDULED", "Scheduled Candidate"));
        when(assignmentRepository.findByTestId(3L)).thenReturn(List.of(waiting, scheduled));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results =
            (List<Map<String, Object>>) controller.getUnscheduledResults(3L).getBody().get("results");

        assertEquals(1, results.size());
        assertEquals("Waiting Candidate", results.get(0).get("candidateName"));
        assertEquals("waiting.candidate@example.com", results.get(0).get("candidateEmail"));
        assertNotNull(results.get(0).get("applicationId"));
    }

    @Test
    void questionsOfAnUnknownTestReturnBadRequest() {
        when(testRepository.existsById(42L)).thenReturn(false);

        assertEquals(400, controller.getTestQuestions(42L).getStatusCode().value());
    }

    @Test
    void questionsOfATestAreListed() {
        when(testRepository.existsById(3L)).thenReturn(true);
        when(questionRepository.findByTestId(3L)).thenReturn(List.of(question(1, "A"), question(2, "B")));

        Map<String, Object> body = controller.getTestQuestions(3L).getBody();

        assertEquals(2, body.get("totalQuestions"));
        assertTrue(body.containsKey("questions"));
        assertFalse(((List<?>) body.get("questions")).isEmpty());
    }
}
