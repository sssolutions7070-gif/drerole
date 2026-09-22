// src/main/java/com/jobera/controller/AptitudeTestController.java
package com.jobera.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobera.entity.AptitudeTest;
import com.jobera.entity.Application;
import com.jobera.entity.ProctoringViolation;
import com.jobera.entity.Recruiter;
import com.jobera.entity.TestAssignment;
import com.jobera.entity.TestQuestion;
import com.jobera.entity.User;
import com.jobera.repository.AptitudeTestRepository;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.ProctoringViolationRepository;
import com.jobera.repository.DreamRoleTestAssignmentRepository;
import com.jobera.entity.DreamRoleTestAssignment;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.TestAssignmentRepository;
import com.jobera.repository.TestQuestionRepository;

@RestController
@RequestMapping("/api/aptitude-tests")
public class AptitudeTestController {
    
    private static final String ERROR_KEY = "error";
    private static final String MESSAGE_KEY = "message";
    private static final String TEST_ID_KEY = "testId";
    private static final String APPLICATION_ID_KEY = "applicationId";
    private static final String ASSIGNMENT_ID_KEY = "assignmentId";
    private static final String QUESTIONS_KEY = "questions";
    private static final String ASSIGNMENT_KEY = "assignment";
    private static final String ASSIGNMENTS_KEY = "assignments";
    private static final String STATUS_KEY = "status";
    private static final String ANSWERS_KEY = "answers";
    
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_FAILED_PROCTORING = "FAILED_PROCTORING";
    
    private static final String APPLICATION_STATUS_TEST_ASSIGNED = "TEST_ASSIGNED";
    private static final String APPLICATION_STATUS_TEST_PASSED = "TEST_PASSED";
    private static final String APPLICATION_STATUS_TEST_FAILED = "TEST_FAILED";
    private static final String APPLICATION_STATUS_INTERVIEW_SCHEDULED = "INTERVIEW_SCHEDULED";
    
    private final AptitudeTestRepository testRepository;
    private final TestQuestionRepository questionRepository;
    private final TestAssignmentRepository assignmentRepository;
    private final ApplicationRepository applicationRepository;
    private final RecruiterRepository recruiterRepository;
    private final ProctoringViolationRepository violationRepository;
    private final DreamRoleTestAssignmentRepository dreamRoleAssignmentRepository;
    private final ObjectMapper objectMapper;

    public AptitudeTestController(
            AptitudeTestRepository testRepository,
            TestQuestionRepository questionRepository,
            TestAssignmentRepository assignmentRepository,
            ApplicationRepository applicationRepository,
            RecruiterRepository recruiterRepository,
            ProctoringViolationRepository violationRepository,
            DreamRoleTestAssignmentRepository dreamRoleAssignmentRepository) {
        this.testRepository = testRepository;
        this.questionRepository = questionRepository;
        this.assignmentRepository = assignmentRepository;
        this.applicationRepository = applicationRepository;
        this.recruiterRepository = recruiterRepository;
        this.violationRepository = violationRepository;
        this.dreamRoleAssignmentRepository = dreamRoleAssignmentRepository;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createTest(@RequestBody Map<String, Object> request) {
        try {
            Long recruiterId = Long.valueOf(request.get("recruiterId").toString());
            String testName = (String) request.get("testName");
            String description = (String) request.get("description");
            Integer durationMinutes = Integer.valueOf(request.get("durationMinutes").toString());
            Integer passingScore = Integer.valueOf(request.get("passingScore").toString());
            
            Optional<Recruiter> recruiter = recruiterRepository.findById(recruiterId);
            if (recruiter.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Recruiter not found"));
            }
            
            AptitudeTest test = new AptitudeTest();
            test.setRecruiter(recruiter.get());
            test.setTestName(testName);
            test.setDescription(description);
            test.setDurationMinutes(durationMinutes);
            test.setPassingScore(passingScore);
            test.setTotalQuestions(0);
            test.setIsActive(true);
            
            AptitudeTest savedTest = testRepository.save(test);
            
            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE_KEY, "Test created successfully");
            response.put("test", createTestResponse(savedTest));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to create test: " + e.getMessage()));
        }
    }

    @GetMapping("/recruiter/{recruiterId}")
    public ResponseEntity<List<Map<String, Object>>> getRecruiterTests(@PathVariable Long recruiterId) {
        try {
            List<AptitudeTest> tests = testRepository.findByRecruiterId(recruiterId);
            List<Map<String, Object>> response = tests.stream()
                .map(this::createTestResponse)
                .collect(Collectors.toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/{testId}/add-multiple-questions")
    public ResponseEntity<Map<String, Object>> addMultipleQuestions(
            @PathVariable Long testId,
            @RequestBody List<Map<String, Object>> questions) {
        try {
            Optional<AptitudeTest> testOpt = testRepository.findById(testId);
            if (testOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Test not found"));
            }
            
            AptitudeTest test = testOpt.get();
            
            long currentCount = questionRepository.countByTestId(testId);
            if (currentCount + questions.size() > 40) {
                return ResponseEntity.badRequest().body(Map.of(
                    ERROR_KEY, "Cannot add " + questions.size() + " questions. Max 40 allowed. Current: " + currentCount
                ));
            }
            
            int added = 0;
            for (Map<String, Object> q : questions) {
                TestQuestion question = new TestQuestion();
                question.setTest(test);
                question.setQuestion((String) q.get("question"));
                question.setOptionA((String) q.get("optionA"));
                question.setOptionB((String) q.get("optionB"));
                question.setOptionC((String) q.get("optionC"));
                question.setOptionD((String) q.get("optionD"));
                question.setCorrectAnswer((String) q.get("correctAnswer"));
                question.setExplanation((String) q.get("explanation"));
                question.setCategory((String) q.get("category"));
                question.setDifficultyLevel(Integer.valueOf(q.get("difficultyLevel").toString()));
                
                questionRepository.save(question);
                added++;
            }
            
            test.setTotalQuestions((int) questionRepository.countByTestId(testId));
            testRepository.save(test);
            
            return ResponseEntity.ok(Map.of(
                MESSAGE_KEY, added + " questions added successfully",
                "totalQuestions", test.getTotalQuestions()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to add questions: " + e.getMessage()));
        }
    }

    @PostMapping("/assign")
    public ResponseEntity<Map<String, Object>> assignTest(@RequestBody Map<String, Object> request) {
        try {
            Long testId = Long.valueOf(request.get(TEST_ID_KEY).toString());
            Long applicationId = Long.valueOf(request.get(APPLICATION_ID_KEY).toString());
            Integer deadlineHours = Integer.valueOf(request.get("deadlineHours").toString());
            
            Optional<AptitudeTest> testOpt = testRepository.findById(testId);
            if (testOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Test not found"));
            }
            
            Optional<Application> appOpt = applicationRepository.findById(applicationId);
            if (appOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Application not found"));
            }
            
            Optional<TestAssignment> existing = assignmentRepository.findByApplicationIdAndTestId(applicationId, testId);
            if (existing.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Test already assigned to this candidate"));
            }
            
            TestAssignment assignment = new TestAssignment();
            assignment.setTest(testOpt.get());
            assignment.setApplication(appOpt.get());
            assignment.setAssignedAt(LocalDateTime.now());
            assignment.setDeadline(LocalDateTime.now().plusHours(deadlineHours));
            assignment.setStatus(STATUS_PENDING);
            assignment.setTotalQuestions(testOpt.get().getTotalQuestions());
            assignment.setProctoringActive(false);
            assignment.setProctoringTerminated(false);
            assignment.setWarningCount(0);
            assignment.setNoFaceViolations(0);
            assignment.setMultipleFacesViolations(0);
            assignment.setMobileDetectedViolations(0);
            assignment.setLookingAwayViolations(0);
            
            TestAssignment saved = assignmentRepository.save(assignment);
            
            Application application = appOpt.get();
            application.setStatus(APPLICATION_STATUS_TEST_ASSIGNED);
            applicationRepository.save(application);
            
            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE_KEY, "Test assigned successfully");
            response.put(ASSIGNMENT_KEY, createAssignmentResponse(saved));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to assign test: " + e.getMessage()));
        }
    }

    @GetMapping("/application/{applicationId}")
    public ResponseEntity<Map<String, Object>> getApplicationTest(@PathVariable Long applicationId) {
        try {
            List<TestAssignment> assignments = assignmentRepository.findByApplicationId(applicationId);
            
            if (assignments.isEmpty()) {
                return ResponseEntity.ok(Map.of("hasTest", false));
            }
            
            TestAssignment latest = assignments.get(assignments.size() - 1);
            
            Map<String, Object> response = new HashMap<>();
            response.put("hasTest", true);
            response.put(ASSIGNMENT_KEY, createAssignmentResponse(latest));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to get test: " + e.getMessage()));
        }
    }

    @GetMapping("/start/{assignmentId}")
    public ResponseEntity<Map<String, Object>> startTest(@PathVariable String assignmentId) {
        try {
            String numericId = assignmentId.replaceAll("[^0-9]", "");
            Long id = Long.parseLong(numericId);
            
            Optional<TestAssignment> assignmentOpt = assignmentRepository.findById(id);
            if (assignmentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Assignment not found: " + assignmentId));
            }
            
            TestAssignment assignment = assignmentOpt.get();
            
            if (LocalDateTime.now().isAfter(assignment.getDeadline())) {
                assignment.setStatus(STATUS_EXPIRED);
                assignmentRepository.save(assignment);
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Test deadline has passed"));
            }
            
            if (STATUS_COMPLETED.equals(assignment.getStatus()) || 
                STATUS_FAILED_PROCTORING.equals(assignment.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Test already completed or failed"));
            }
            
            if (STATUS_IN_PROGRESS.equals(assignment.getStatus()) && assignment.getStartedAt() != null) {
                Map<String, Object> response = new HashMap<>();
                response.put(ASSIGNMENT_KEY, createAssignmentResponse(assignment));
                response.put(QUESTIONS_KEY, getTestQuestionsForCandidate(assignment.getTest().getId()));
                
                if (assignment.getAnswers() != null && !assignment.getAnswers().isEmpty()) {
                    try {
                        Map<String, String> existingAnswers = objectMapper.readValue(
                            assignment.getAnswers(), 
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}
                        );
                        response.put("existingAnswers", existingAnswers);
                    } catch (Exception e) {
                        response.put("existingAnswers", null);
                    }
                } else {
                    response.put("existingAnswers", null);
                }
                
                return ResponseEntity.ok(response);
            }
            
            assignment.setStartedAt(LocalDateTime.now());
            assignment.setStatus(STATUS_IN_PROGRESS);
            assignment.setProctoringActive(true);
            assignment.setProctoringStartedAt(LocalDateTime.now());
            assignment.setWarningCount(0);
            assignment.setNoFaceViolations(0);
            assignment.setMultipleFacesViolations(0);
            assignment.setMobileDetectedViolations(0);
            assignment.setLookingAwayViolations(0);
            assignmentRepository.save(assignment);
            
            Map<String, Object> response = new HashMap<>();
            response.put(ASSIGNMENT_KEY, createAssignmentResponse(assignment));
            response.put(QUESTIONS_KEY, getTestQuestionsForCandidate(assignment.getTest().getId()));
            response.put("existingAnswers", null);
            
            return ResponseEntity.ok(response);
            
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Invalid assignment ID format: " + assignmentId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to start test: " + e.getMessage()));
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitTest(@RequestBody Map<String, Object> request) {
        try {
            Long assignmentId = Long.valueOf(request.get(ASSIGNMENT_ID_KEY).toString());
            @SuppressWarnings("unchecked")
            Map<String, String> answers = (Map<String, String>) request.get(ANSWERS_KEY);
            
            Optional<TestAssignment> assignmentOpt = assignmentRepository.findById(assignmentId);
            if (assignmentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Assignment not found"));
            }
            
            TestAssignment assignment = assignmentOpt.get();

            if (STATUS_COMPLETED.equals(assignment.getStatus()) ||
                STATUS_FAILED_PROCTORING.equals(assignment.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Test already submitted or failed"));
            }

            if (Boolean.parseBoolean(String.valueOf(request.get("terminated")))) {
                Object reason = request.get("terminationReason");
                markProctoringTerminated(assignment, reason != null ? reason.toString() : "Proctoring violation");
                Map<String, Object> terminatedResponse = new HashMap<>();
                terminatedResponse.put(MESSAGE_KEY, "Test terminated due to proctoring violations");
                terminatedResponse.put("terminated", true);
                terminatedResponse.put("passed", false);
                terminatedResponse.put("score", 0);
                return ResponseEntity.ok(terminatedResponse);
            }

            assignment.setProctoringActive(false);
            assignment.setProctoringEndedAt(LocalDateTime.now());

            if (LocalDateTime.now().isAfter(assignment.getDeadline())) {
                assignment.setStatus(STATUS_EXPIRED);
                assignmentRepository.save(assignment);
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Test deadline has passed"));
            }
            
            List<TestQuestion> questions = questionRepository.findByTestId(assignment.getTest().getId());
            
            int correctCount = 0;
            Map<String, String> answersMap = answers != null ? answers : new HashMap<>();
            
            for (TestQuestion q : questions) {
                String userAnswer = answersMap.get(q.getId().toString());
                if (userAnswer != null && userAnswer.equalsIgnoreCase(q.getCorrectAnswer())) {
                    correctCount++;
                }
            }
            
            int totalQuestions = questions.size();
            int score = correctCount;
            int percentage = totalQuestions > 0 ? (score * 100) / totalQuestions : 0;
            int passingScore = assignment.getTest().getPassingScore();
            boolean passed = percentage >= passingScore;
            
            assignment.setCompletedAt(LocalDateTime.now());
            assignment.setStatus(STATUS_COMPLETED);
            assignment.setScore(score);
            assignment.setCorrectAnswers(correctCount);
            assignment.setTotalQuestions(totalQuestions);
            assignment.setPassed(passed);
            
            if (answers != null) {
                assignment.setAnswers(objectMapper.writeValueAsString(answers));
            }
            
            assignmentRepository.save(assignment);
            
            Application application = assignment.getApplication();
            if (application != null) {
                if (passed) {
                    application.setStatus(APPLICATION_STATUS_TEST_PASSED);
                } else {
                    application.setStatus(APPLICATION_STATUS_TEST_FAILED);
                }
                applicationRepository.save(application);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE_KEY, "Test submitted successfully");
            response.put("score", score);
            response.put("totalQuestions", totalQuestions);
            response.put("percentage", percentage);
            response.put("passed", passed);
            response.put("passingScore", passingScore);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to submit test: " + e.getMessage()));
        }
    }

    @GetMapping("/{testId}/questions")
    public ResponseEntity<Map<String, Object>> getTestQuestions(@PathVariable Long testId) {
        if (!testRepository.existsById(testId)) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Test not found: " + testId));
        }
        List<Map<String, Object>> questions = questionRepository.findByTestId(testId).stream()
            .map(this::createQuestionResponse)
            .collect(Collectors.toList());
        Map<String, Object> response = new HashMap<>();
        response.put(QUESTIONS_KEY, questions);
        response.put("totalQuestions", questions.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/recruiter/{recruiterId}")
    public ResponseEntity<Map<String, Object>> getRecruiterTestStats(@PathVariable Long recruiterId) {
        List<TestAssignment> aptitude = assignmentRepository.findByRecruiterId(recruiterId);
        List<DreamRoleTestAssignment> dreamRole = dreamRoleAssignmentRepository.findByRecruiterId(recruiterId);
        
        int totalAssigned = aptitude.size() + dreamRole.size();
        int completed = 0;
        int passed = 0;
        double percentageSum = 0;
        
        for (TestAssignment a : aptitude) {
            if (STATUS_COMPLETED.equals(a.getStatus())) {
                completed++;
                percentageSum += a.getPercentage();
                if (Boolean.TRUE.equals(a.getPassed())) passed++;
            }
        }
        for (DreamRoleTestAssignment a : dreamRole) {
            if (STATUS_COMPLETED.equals(a.getStatus())) {
                completed++;
                percentageSum += a.getPercentage();
                if (Boolean.TRUE.equals(a.getPassed())) passed++;
            }
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAssigned", totalAssigned);
        stats.put("completed", completed);
        stats.put("passed", passed);
        stats.put("averageScore", completed > 0 ? Math.round(percentageSum / completed) : 0);
        stats.put("passRate", completed > 0 ? Math.round(passed * 100.0 / completed) : 0);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/recruiter/{recruiterId}/results")
    public ResponseEntity<Map<String, Object>> getRecruiterTestResults(@PathVariable Long recruiterId) {
        List<Map<String, Object>> assignments = new ArrayList<>();

        for (TestAssignment a : assignmentRepository.findByRecruiterId(recruiterId)) {
            if (a.getApplication() == null) continue;
            Map<String, Object> row = createAssignmentResponse(a);
            row.put("assignmentId", a.getId());
            row.put("candidateName", a.getApplication().getSafeApplicantName());
            row.put("candidateEmail", a.getApplication().getSafeApplicantEmail());
            assignments.add(row);
        }
        for (DreamRoleTestAssignment a : dreamRoleAssignmentRepository.findByRecruiterId(recruiterId)) {
            if (a.getApplication() == null) continue;
            Map<String, Object> row = createDreamRoleAssignmentSummary(a);
            row.put("assignmentId", a.getId());
            row.put("candidateName", a.getApplication().getSafeApplicantName());
            row.put("candidateEmail", a.getApplication().getSafeApplicantEmail());
            assignments.add(row);
        }

        assignments.sort((x, y) -> {
            Object cx = x.get("completedAt");
            Object cy = y.get("completedAt");
            if (cx == null && cy == null) return 0;
            if (cx == null) return 1;
            if (cy == null) return -1;
            return cy.toString().compareTo(cx.toString());
        });

        return ResponseEntity.ok(Map.of(ASSIGNMENTS_KEY, assignments));
    }

    private Map<String, Object> createDreamRoleAssignmentSummary(DreamRoleTestAssignment assignment) {
        Map<String, Object> row = new HashMap<>();
        row.put("testName", assignment.getDreamRoleTest().getTestName());
        row.put(STATUS_KEY, assignment.getStatus());
        row.put("score", assignment.getScore() != null ? assignment.getScore() : 0);
        row.put("totalQuestions", assignment.getTotalQuestions() != null ? assignment.getTotalQuestions() : 0);
        row.put("percentage", assignment.getPercentage());
        row.put("passed", assignment.getPassed() != null ? assignment.getPassed() : false);
        row.put("completedAt", assignment.getCompletedAt());
        return row;
    }

    @GetMapping("/test/{testId}/results/unscheduled")
    public ResponseEntity<Map<String, Object>> getUnscheduledResults(@PathVariable Long testId) {
        List<Map<String, Object>> results = assignmentRepository.findByTestId(testId).stream()
            .filter(a -> a.getApplication() != null
                && !APPLICATION_STATUS_INTERVIEW_SCHEDULED.equals(a.getApplication().getStatus()))
            .map(a -> {
                Map<String, Object> row = createAssignmentResponse(a);
                row.put("candidateName", a.getApplication().getSafeApplicantName());
                row.put("candidateEmail", a.getApplication().getSafeApplicantEmail());
                return row;
            })
            .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("results", results));
    }

    @PostMapping("/{assignmentId}/violations")
    public ResponseEntity<Map<String, Object>> recordViolations(
            @PathVariable String assignmentId, @RequestBody Map<String, Object> request) {
        try {
            Long id = Long.parseLong(assignmentId.replaceAll("[^0-9]", ""));
            Optional<TestAssignment> assignmentOpt = assignmentRepository.findById(id);
            if (assignmentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Assignment not found: " + assignmentId));
            }

            TestAssignment assignment = assignmentOpt.get();
            if (STATUS_COMPLETED.equals(assignment.getStatus()) ||
                (STATUS_FAILED_PROCTORING.equals(assignment.getStatus())
                    && !violationRepository.findByTestAssignmentId(id).isEmpty())) {
                return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Assignment already finalized; violations ignored"));
            }

            int noFace = 0, multipleFaces = 0, mobile = 0, lookingAway = 0;
            Object violationsObj = request.get("violations");
            if (violationsObj instanceof List<?> violationList) {
                for (Object item : violationList) {
                    if (!(item instanceof Map<?, ?> v)) continue;
                    String type = String.valueOf(v.get("type"));
                    String message = v.get("message") != null ? v.get("message").toString() : type;
                    boolean critical = !Boolean.FALSE.equals(v.get("critical"));

                    ProctoringViolation violation = new ProctoringViolation(assignment, type, message, critical);
                    violationRepository.save(violation);

                    String normalized = type.toLowerCase();
                    if (normalized.contains("multiple")) multipleFaces++;
                    else if (normalized.contains("mobile") || normalized.contains("phone")) mobile++;
                    else if (normalized.contains("away")) lookingAway++;
                    else if (normalized.contains("no_face") || normalized.contains("no face")) noFace++;
                }
            }

            assignment.setNoFaceViolations(noFace);
            assignment.setMultipleFacesViolations(multipleFaces);
            assignment.setMobileDetectedViolations(mobile);
            assignment.setLookingAwayViolations(lookingAway);
            assignment.setWarningCount(noFace + multipleFaces + mobile + lookingAway);

            if (Boolean.TRUE.equals(request.get("terminated"))) {
                Object reason = request.get("termination_reason");
                markProctoringTerminated(assignment, reason != null ? reason.toString() : "Proctoring violation");
            } else {
                assignmentRepository.save(assignment);
            }

            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Violations recorded"));

        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Invalid assignment ID format: " + assignmentId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to record violations: " + e.getMessage()));
        }
    }

    private void markProctoringTerminated(TestAssignment assignment, String reason) {
        LocalDateTime now = LocalDateTime.now();
        String trimmedReason = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        assignment.setProctoringActive(false);
        assignment.setProctoringEndedAt(now);
        assignment.setProctoringTerminated(true);
        assignment.setProctoringTerminationReason(trimmedReason);
        assignment.setCompletedAt(now);
        assignment.setStatus(STATUS_FAILED_PROCTORING);
        assignment.setScore(0);
        assignment.setCorrectAnswers(0);
        assignment.setPassed(false);
        assignmentRepository.save(assignment);

        Application application = assignment.getApplication();
        if (application != null) {
            application.setStatus(APPLICATION_STATUS_TEST_FAILED);
            applicationRepository.save(application);
        }
    }

    @GetMapping("/results/{assignmentId}")
    public ResponseEntity<Map<String, Object>> getTestResults(@PathVariable Long assignmentId) {
        try {
            Optional<TestAssignment> assignmentOpt = assignmentRepository.findById(assignmentId);
            if (assignmentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Assignment not found"));
            }
            
            TestAssignment assignment = assignmentOpt.get();
            Application application = assignment.getApplication();
            User candidate = application != null ? application.getUser() : null;
            
            Map<String, Object> response = new HashMap<>();
            response.put(ASSIGNMENT_KEY, createAssignmentResponse(assignment));
            response.put("candidateName", candidate != null ? candidate.getFullName() : "Unknown");
            response.put("candidateEmail", candidate != null ? candidate.getEmail() : "Unknown");
            
            String jobTitle = "Unknown Job";
            if (application != null) {
                if (application.getJobPosting() != null) {
                    jobTitle = application.getJobPosting().getTitle();
                } else if (application.getCoverLetter() != null && application.getCoverLetter().contains("External Job:")) {
                    String match = application.getCoverLetter().replaceFirst(".*External Job:\\s*([^\\n]+).*", "$1");
                    if (!match.equals(application.getCoverLetter())) {
                        String[] parts = match.split(" at ");
                        jobTitle = parts.length >= 2 ? parts[0].trim() : match;
                    }
                }
            }
            response.put("jobTitle", jobTitle);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Failed to get results: " + e.getMessage()));
        }
    }

    private Map<String, Object> createTestResponse(AptitudeTest test) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", test.getId());
        response.put("testName", test.getTestName());
        response.put("description", test.getDescription());
        response.put("durationMinutes", test.getDurationMinutes());
        response.put("totalQuestions", test.getTotalQuestions());
        response.put("passingScore", test.getPassingScore());
        response.put("isActive", test.getIsActive());
        response.put("createdAt", test.getCreatedAt());
        return response;
    }

    private Map<String, Object> createQuestionResponse(TestQuestion question) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", question.getId());
        response.put("question", question.getQuestion());
        response.put("optionA", question.getOptionA());
        response.put("optionB", question.getOptionB());
        response.put("optionC", question.getOptionC());
        response.put("optionD", question.getOptionD());
        response.put("correctAnswer", question.getCorrectAnswer());
        response.put("explanation", question.getExplanation());
        response.put("category", question.getCategory());
        response.put("difficultyLevel", question.getDifficultyLevel());
        return response;
    }

    private Map<String, Object> createAssignmentResponse(TestAssignment assignment) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", assignment.getId());
        response.put(TEST_ID_KEY, assignment.getTest().getId());
        response.put("testName", assignment.getTest().getTestName());
        response.put(APPLICATION_ID_KEY, assignment.getApplication().getId());
        response.put("assignedAt", assignment.getAssignedAt());
        response.put("startedAt", assignment.getStartedAt());
        response.put("completedAt", assignment.getCompletedAt());
        response.put("deadline", assignment.getDeadline());
        response.put(STATUS_KEY, assignment.getStatus());
        response.put("score", assignment.getScore() != null ? assignment.getScore() : 0);
        response.put("correctAnswers", assignment.getCorrectAnswers() != null ? assignment.getCorrectAnswers() : 0);
        response.put("totalQuestions", assignment.getTotalQuestions() != null ? assignment.getTotalQuestions() : 0);
        response.put("passed", assignment.getPassed() != null ? assignment.getPassed() : false);
        response.put("percentage", assignment.getPercentage());
        response.put("passingScore", assignment.getTest().getPassingScore());
        
        response.put("proctoringActive", assignment.getProctoringActive());
        response.put("proctoringTerminated", assignment.getProctoringTerminated());
        response.put("terminationReason", assignment.getProctoringTerminationReason());
        response.put("warningCount", assignment.getWarningCount());
        response.put("noFaceViolations", assignment.getNoFaceViolations());
        response.put("multipleFacesViolations", assignment.getMultipleFacesViolations());
        response.put("mobileDetectedViolations", assignment.getMobileDetectedViolations());
        
        // FIXED: Get job info from multiple sources
        String jobTitle = "";
        String companyName = "";
        
        Application app = assignment.getApplication();
        if (app != null) {
            if (assignment.getExternalJobTitle() != null && !assignment.getExternalJobTitle().isEmpty()) {
                jobTitle = assignment.getExternalJobTitle();
                companyName = assignment.getExternalCompanyName() != null ? assignment.getExternalCompanyName() : "";
            }
            else if (app.getJobPosting() != null) {
                jobTitle = app.getJobPosting().getTitle();
                companyName = app.getJobPosting().getCompany();
            }
            else if (app.getCoverLetter() != null && app.getCoverLetter().contains("External Job:")) {
                String coverLetter = app.getCoverLetter();
                String match = coverLetter.replaceFirst(".*External Job:\\s*([^\\n]+).*", "$1");
                if (!match.equals(coverLetter)) {
                    String[] parts = match.split(" at ");
                    if (parts.length >= 2) {
                        jobTitle = parts[0].trim();
                        companyName = parts[1].trim();
                    } else {
                        jobTitle = match;
                        companyName = "External Recruiter";
                    }
                } else {
                    jobTitle = "External Job Opportunity";
                    companyName = "External Recruiter";
                }
            }
            
            response.put("jobTitle", jobTitle);
            response.put("companyName", companyName);
            response.put("applicantName", app.getSafeApplicantName());
            response.put("applicantEmail", app.getSafeApplicantEmail());
        }
        
        return response;
    }

    private List<Map<String, Object>> getTestQuestionsForCandidate(Long testId) {
        List<TestQuestion> questions = questionRepository.findByTestId(testId);
        return questions.stream()
            .map(q -> {
                Map<String, Object> qMap = new HashMap<>();
                qMap.put("id", q.getId());
                qMap.put("question", q.getQuestion());
                qMap.put("optionA", q.getOptionA());
                qMap.put("optionB", q.getOptionB());
                qMap.put("optionC", q.getOptionC());
                qMap.put("optionD", q.getOptionD());
                qMap.put("category", q.getCategory());
                return qMap;
            })
            .collect(Collectors.toList());
    }
}