package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.jobera.entity.DreamRoleTest;
import com.jobera.entity.DreamRoleTestAssignment;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.DreamRoleTestAssignmentRepository;
import com.jobera.repository.DreamRoleTestQuestionRepository;
import com.jobera.repository.DreamRoleTestRepository;
import com.jobera.repository.QuestionBankRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.service.AiInterviewClient;
import com.jobera.service.SkillBadgeService;

@ExtendWith(MockitoExtension.class)
class DreamRoleTestControllerTest {

    @Mock DreamRoleTestRepository dreamRoleTestRepository;
    @Mock DreamRoleTestQuestionRepository questionRepository;
    @Mock DreamRoleTestAssignmentRepository assignmentRepository;
    @Mock QuestionBankRepository questionBankRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock RecruiterRepository recruiterRepository;
    @Mock SkillBadgeService skillBadgeService;
    @Mock AiInterviewClient aiInterviewClient;

    DreamRoleTestController controller;

    @BeforeEach
    void setUp() {
        controller = new DreamRoleTestController(dreamRoleTestRepository, questionRepository,
            assignmentRepository, questionBankRepository, applicationRepository, recruiterRepository,
            skillBadgeService, aiInterviewClient);
    }

    private Application application(long id, String status, String name) {
        Application application = new Application();
        application.setId(id);
        application.setStatus(status);
        application.setApplicantName(name);
        application.setApplicantEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
        return application;
    }

    private DreamRoleTestAssignment assignment(long id, Application application) {
        DreamRoleTest test = new DreamRoleTest();
        test.setId(4L);
        test.setTestName("Backend Role");
        test.setPassingScore(60);

        DreamRoleTestAssignment assignment = new DreamRoleTestAssignment();
        assignment.setId(id);
        assignment.setDreamRoleTest(test);
        assignment.setApplication(application);
        assignment.setStatus("IN_PROGRESS");
        assignment.setDeadline(LocalDateTime.now().plusHours(2));
        assignment.setQuestions("[{\"id\":\"q1\",\"correctAnswer\":\"A\"}]");
        return assignment;
    }

    @Test
    void terminatedSubmissionFailsTheTestWithoutGrading() {
        Application application = application(9, "TEST_ASSIGNED", "Ravi Kumar");
        DreamRoleTestAssignment assignment = assignment(6, application);
        when(assignmentRepository.findById(6L)).thenReturn(Optional.of(assignment));

        ResponseEntity<Map<String, Object>> response = controller.submitDreamRoleTest(
            Map.of("assignmentId", 6, "terminated", true, "terminationReason", "Phone detected"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().get("terminated"));
        assertEquals("FAILED_PROCTORING", assignment.getStatus());
        assertEquals(Boolean.FALSE, assignment.getPassed());
        assertEquals("TEST_FAILED", application.getStatus());
    }

    @Test
    void submissionWithoutAnswersIsGradedAsZeroInsteadOfCrashing() {
        Application application = application(9, "TEST_ASSIGNED", "Ravi Kumar");
        DreamRoleTestAssignment assignment = assignment(6, application);
        when(assignmentRepository.findById(6L)).thenReturn(Optional.of(assignment));

        ResponseEntity<Map<String, Object>> response = controller.submitDreamRoleTest(Map.of("assignmentId", 6));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, response.getBody().get("percentage"));
        assertEquals(false, response.getBody().get("passed"));
        assertEquals("COMPLETED", assignment.getStatus());
        assertEquals("TEST_FAILED", application.getStatus());
    }

    @Test
    void passingSubmissionMarksTheApplicationAsPassed() {
        Application application = application(9, "TEST_ASSIGNED", "Ravi Kumar");
        DreamRoleTestAssignment assignment = assignment(6, application);
        when(assignmentRepository.findById(6L)).thenReturn(Optional.of(assignment));

        ResponseEntity<Map<String, Object>> response = controller.submitDreamRoleTest(
            Map.of("assignmentId", 6, "answers", Map.of("q1", "A")));

        assertEquals(100, response.getBody().get("percentage"));
        assertEquals(true, response.getBody().get("passed"));
        assertEquals("TEST_PASSED", application.getStatus());
    }

    @Test
    void submissionWithoutAnApplicationDoesNotCrash() {
        DreamRoleTestAssignment assignment = assignment(6, null);
        when(assignmentRepository.findById(6L)).thenReturn(Optional.of(assignment));

        ResponseEntity<Map<String, Object>> response = controller.submitDreamRoleTest(
            Map.of("assignmentId", 6, "answers", Map.of("q1", "A")));

        assertEquals(200, response.getStatusCode().value());
        assertNull(assignment.getApplication());
    }

    @Test
    void failedProctoringAssignmentsCannotBeStartedOrResubmitted() {
        DreamRoleTestAssignment assignment = assignment(6, application(9, "TEST_FAILED", "Ravi Kumar"));
        assignment.setStatus("FAILED_PROCTORING");
        when(assignmentRepository.findById(6L)).thenReturn(Optional.of(assignment));

        assertEquals(400, controller.submitDreamRoleTest(Map.of("assignmentId", 6)).getStatusCode().value());
        assertEquals(400, controller.startDreamRoleTest(6L).getStatusCode().value());
    }

    @Test
    void unscheduledResultsExcludeCandidatesWhoseInterviewIsAlreadyScheduled() {
        DreamRoleTestAssignment waiting = assignment(1, application(1, "TEST_PASSED", "Waiting Candidate"));
        DreamRoleTestAssignment scheduled = assignment(2, application(2, "INTERVIEW_SCHEDULED", "Scheduled Candidate"));
        when(assignmentRepository.findByDreamRoleTestId(4L)).thenReturn(List.of(waiting, scheduled));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results =
            (List<Map<String, Object>>) controller.getUnscheduledResults(4L).getBody().get("results");

        assertEquals(1, results.size());
        assertEquals("Waiting Candidate", results.get(0).get("candidateName"));
    }

    @Test
    void generateTechnicalQuestionsDelegatesToAiClient() {
        Map<String, Object> generatedQ = Map.of(
            "question", "What is React Virtual DOM?",
            "optionA", "Server only",
            "optionB", "Diffing engine",
            "optionC", "CSS parser",
            "optionD", "Database",
            "correctAnswer", "B",
            "explanation", "Diffs UI trees",
            "difficultyLevel", "Medium"
        );
        when(aiInterviewClient.generateTechnicalQuestions("Frontend Dev", "Build UI", "React", "CSE", 10))
            .thenReturn(List.of(generatedQ));

        Map<String, Object> req = Map.of(
            "jobTitle", "Frontend Dev",
            "jobDescription", "Build UI",
            "skills", "React",
            "targetBranch", "CSE",
            "count", 10
        );

        ResponseEntity<Map<String, Object>> response = controller.generateTechnicalQuestions(req);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().get("count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) response.getBody().get("questions");
        assertEquals("What is React Virtual DOM?", questions.get(0).get("question"));
    }
}
