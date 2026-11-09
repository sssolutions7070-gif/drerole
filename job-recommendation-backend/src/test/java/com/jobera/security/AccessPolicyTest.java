package com.jobera.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jobera.entity.Application;
import com.jobera.entity.AptitudeTest;
import com.jobera.entity.CompanyReview;
import com.jobera.entity.DreamRoleTest;
import com.jobera.entity.DreamRoleTestAssignment;
import com.jobera.entity.ExternalCandidate;
import com.jobera.entity.ExternalJobPosting;
import com.jobera.entity.JobPosting;
import com.jobera.entity.JobRating;
import com.jobera.entity.Recruiter;
import com.jobera.entity.TestAssignment;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.AptitudeTestRepository;
import com.jobera.repository.DreamRoleTestAssignmentRepository;
import com.jobera.repository.DreamRoleTestRepository;
import com.jobera.repository.ExternalCandidateRepository;
import com.jobera.repository.ExternalJobPostingRepository;
import com.jobera.repository.InterviewRoundRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.JobRatingRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.CompanyReviewRepository;
import com.jobera.repository.RecruiterTeamMemberRepository;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.TestAssignmentRepository;

class AccessPolicyTest {

    static final Map<String, String[]> NO_PARAMS = Map.of();

    RecruiterRepository recruiterRepository = mock(RecruiterRepository.class);
    JobPostingRepository jobRepository = mock(JobPostingRepository.class);
    ExternalJobPostingRepository externalJobRepository = mock(ExternalJobPostingRepository.class);
    ExternalCandidateRepository externalCandidateRepository = mock(ExternalCandidateRepository.class);
    AptitudeTestRepository aptitudeTestRepository = mock(AptitudeTestRepository.class);
    DreamRoleTestRepository dreamRoleTestRepository = mock(DreamRoleTestRepository.class);
    ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    TestAssignmentRepository assignmentRepository = mock(TestAssignmentRepository.class);
    DreamRoleTestAssignmentRepository dreamRoleAssignmentRepository = mock(DreamRoleTestAssignmentRepository.class);
    JobRatingRepository ratingRepository = mock(JobRatingRepository.class);
    ResumeRepository resumeRepository = mock(ResumeRepository.class);
    InterviewRoundRepository interviewRoundRepository = mock(InterviewRoundRepository.class);
    RecruiterTeamMemberRepository recruiterTeamMemberRepository = mock(RecruiterTeamMemberRepository.class);
    CompanyReviewRepository companyReviewRepository = mock(CompanyReviewRepository.class);

    AccessPolicy policy;

    User admin = user(1, 2);
    User candidate = user(10, 1);
    User otherCandidate = user(11, 1);
    User recruiterUser = user(20, 3);
    User otherRecruiterUser = user(21, 3);
    Recruiter recruiter = recruiter(200, recruiterUser);
    Recruiter otherRecruiter = recruiter(201, otherRecruiterUser);

    static User user(long id, long roleId) {
        User user = new User();
        user.setId(id);
        user.setRoleId(roleId);
        return user;
    }

    static Recruiter recruiter(long id, User user) {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(id);
        recruiter.setUser(user);
        return recruiter;
    }

    @BeforeEach
    void setUp() {
        policy = new AccessPolicy(recruiterRepository, jobRepository, externalJobRepository, externalCandidateRepository,
            aptitudeTestRepository, dreamRoleTestRepository, applicationRepository, assignmentRepository,
            dreamRoleAssignmentRepository, ratingRepository, resumeRepository, interviewRoundRepository,
            recruiterTeamMemberRepository, companyReviewRepository);
        lenient().when(recruiterRepository.findByUserId(20L)).thenReturn(Optional.of(recruiter));
        lenient().when(recruiterRepository.findByUserId(21L)).thenReturn(Optional.of(otherRecruiter));
        lenient().when(recruiterRepository.findByUserId(10L)).thenReturn(Optional.empty());
    }

    boolean allowed(User user, String method, String path) {
        return policy.isAllowed(user, method, path, NO_PARAMS);
    }

    // ----------------------------------------------------------------- fixtures

    JobPosting jobOwnedBy(long id, Recruiter owner) {
        JobPosting job = new JobPosting();
        job.setId(id);
        job.setRecruiter(owner);
        lenient().when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        return job;
    }

    Application applicationFor(long id, User applicant, JobPosting job) {
        Application application = new Application();
        application.setId(id);
        application.setUser(applicant);
        application.setJobPosting(job);
        lenient().when(applicationRepository.findById(id)).thenReturn(Optional.of(application));
        return application;
    }

    com.jobera.entity.InterviewRound roundFor(long id, long applicationId) {
        com.jobera.entity.InterviewRound round = new com.jobera.entity.InterviewRound(
            applicationId, 1, "Phone screen", "PHONE_SCREEN", "room-" + id, null);
        round.setId(id);
        lenient().when(interviewRoundRepository.findById(id)).thenReturn(Optional.of(round));
        return round;
    }

    CompanyReview companyReviewOfCompany(long id, Recruiter owner) {
        CompanyReview review = new CompanyReview();
        review.setId(id);
        review.setRecruiter(owner);
        lenient().when(companyReviewRepository.findById(id)).thenReturn(Optional.of(review));
        return review;
    }

    AptitudeTest aptitudeTestOwnedBy(long id, Recruiter owner) {
        AptitudeTest test = new AptitudeTest();
        test.setId(id);
        test.setRecruiter(owner);
        lenient().when(aptitudeTestRepository.findById(id)).thenReturn(Optional.of(test));
        return test;
    }

    TestAssignment aptitudeAssignment(long id, AptitudeTest test, Application application) {
        TestAssignment assignment = new TestAssignment();
        assignment.setId(id);
        assignment.setTest(test);
        assignment.setApplication(application);
        lenient().when(assignmentRepository.findById(id)).thenReturn(Optional.of(assignment));
        return assignment;
    }

    // -------------------------------------------------------------------- roles

    @Test
    void unknownRoutesAreDeniedEvenForAdmins() {
        assertFalse(policy.hasRule("GET", "/api/does-not-exist"));
        assertFalse(allowed(admin, "GET", "/api/does-not-exist"));
    }

    @Test
    void adminEndpointsAreOnlyForAdmins() {
        assertTrue(allowed(admin, "GET", "/api/admin/users"));
        assertFalse(allowed(candidate, "GET", "/api/admin/users"));
        assertFalse(allowed(recruiterUser, "DELETE", "/api/admin/jobs/3"));
    }

    @Test
    void anyLoggedInUserCanBrowseJobs() {
        assertTrue(allowed(candidate, "GET", "/api/jobs"));
        assertTrue(allowed(candidate, "GET", "/api/jobs/search"));
        assertTrue(allowed(candidate, "GET", "/api/internships"));
    }

    @Test
    void anyLoggedInUserCanManageTheirOwnGoogleCalendarConnection() {
        assertTrue(allowed(candidate, "GET", "/api/calendar/google/connect"));
        assertTrue(allowed(recruiterUser, "GET", "/api/calendar/google/status"));
        assertTrue(allowed(candidate, "DELETE", "/api/calendar/google"));
    }

    @Test
    void candidatesCannotUseRecruiterEndpoints() {
        assertFalse(allowed(candidate, "POST", "/api/aptitude-tests/create"));
        assertFalse(allowed(candidate, "POST", "/api/dream-role-tests/create"));
        assertFalse(allowed(candidate, "POST", "/api/recruiters/jobs"));
        assertFalse(allowed(candidate, "POST", "/api/external-jobs/create"));
        assertFalse(allowed(candidate, "GET", "/api/external-jobs/recruiter/200"));
        assertTrue(allowed(recruiterUser, "POST", "/api/aptitude-tests/create"));
    }

    @Test
    void anyRecruiterCanBrowseOpenToWorkCandidatesButCandidatesCannot() {
        assertTrue(allowed(recruiterUser, "GET", "/api/candidates/discover"));
        assertTrue(allowed(otherRecruiterUser, "GET", "/api/candidates/discover"));
        assertFalse(allowed(candidate, "GET", "/api/candidates/discover"));

        assertTrue(allowed(recruiterUser, "GET", "/api/candidates/discover/ai-search"));
        assertFalse(allowed(candidate, "GET", "/api/candidates/discover/ai-search"));
    }

    // ---------------------------------------------------------- "own data" rules

    @Test
    void usersCanOnlyTouchTheirOwnUserScopedResources() {
        assertTrue(allowed(candidate, "GET", "/api/applications/user/10"));
        assertFalse(allowed(candidate, "GET", "/api/applications/user/11"));
        assertTrue(allowed(candidate, "GET", "/api/applications/user/10/insights"));
        assertFalse(allowed(candidate, "GET", "/api/applications/user/11/insights"));
        assertTrue(allowed(admin, "GET", "/api/applications/user/11/insights"));
        assertTrue(allowed(candidate, "POST", "/api/jobs/5/apply/10"));
        assertFalse(allowed(candidate, "POST", "/api/jobs/5/apply/11"));
        assertTrue(allowed(candidate, "GET", "/api/resumes/user/10/latest"));
        assertFalse(allowed(candidate, "GET", "/api/resumes/user/11/latest"));
        assertTrue(allowed(candidate, "PUT", "/api/users/10/profile"));
        assertFalse(allowed(candidate, "PUT", "/api/users/11/profile"));
        assertFalse(allowed(candidate, "DELETE", "/api/users/11/skills/4"));
        assertTrue(allowed(admin, "GET", "/api/applications/user/11"));
    }

    @Test
    void userIdQueryParameterMustBeTheCaller() {
        assertFalse(policy.isAllowed(candidate, "GET", "/api/jobs", Map.of("userId", new String[] {"11"})));
        assertTrue(policy.isAllowed(candidate, "GET", "/api/jobs", Map.of("userId", new String[] {"10"})));
    }

    @Test
    void ratingsCanOnlyBeDeletedByTheirAuthor() {
        JobRating rating = new JobRating();
        rating.setId(9L);
        rating.setUser(candidate);
        lenient().when(ratingRepository.findById(9L)).thenReturn(Optional.of(rating));

        assertTrue(allowed(candidate, "DELETE", "/api/jobs/ratings/9"));
        assertFalse(allowed(otherCandidate, "DELETE", "/api/jobs/ratings/9"));
    }

    // ---------------------------------------------------------- recruiter scoping

    @Test
    void recruiterIdInThePathMustBelongToTheCaller() {
        assertTrue(allowed(recruiterUser, "GET", "/api/aptitude-tests/recruiter/200"));
        assertFalse(allowed(recruiterUser, "GET", "/api/aptitude-tests/recruiter/201"));
        assertFalse(allowed(candidate, "GET", "/api/aptitude-tests/recruiter/200"));
        assertTrue(allowed(recruiterUser, "GET", "/api/applications/recruiter/200/by-status"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/applications/recruiter/200/stats"));
        assertTrue(allowed(admin, "GET", "/api/applications/recruiter/200/stats"));
    }

    @Test
    void recruiterIdQueryParameterMustBelongToTheCaller() {
        JobPosting job = jobOwnedBy(5, recruiter);
        applicationFor(9, candidate, job);

        assertTrue(policy.isAllowed(recruiterUser, "PUT", "/api/applications/9/shortlist",
            Map.of("recruiterId", new String[] {"200"})));
        assertFalse(policy.isAllowed(recruiterUser, "PUT", "/api/applications/9/shortlist",
            Map.of("recruiterId", new String[] {"201"})));
        assertFalse(policy.isAllowed(recruiterUser, "POST", "/api/external-jobs/create",
            Map.of("recruiterId", new String[] {"201"})));
    }

    @Test
    void jobsCanOnlyBeManagedByTheirRecruiter() {
        jobOwnedBy(5, recruiter);

        assertTrue(allowed(recruiterUser, "PUT", "/api/recruiters/jobs/5"));
        assertFalse(allowed(otherRecruiterUser, "PUT", "/api/recruiters/jobs/5"));
        assertFalse(allowed(otherRecruiterUser, "DELETE", "/api/recruiters/jobs/5"));
        assertFalse(allowed(candidate, "GET", "/api/recruiters/jobs/5/applications"));
        assertTrue(allowed(recruiterUser, "GET", "/api/resume-matching/job/5/applications/sorted"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/resume-matching/job/5/applications/sorted"));
    }

    @Test
    void aTeamMemberGetsTheSameAccessAsTheOwningRecruiter() {
        // ownsRecruiterId is the single chokepoint every recruiter-owned-resource check funnels
        // through, so a team member should pass everywhere the owner does - jobs, talent pool,
        // subscription, tests - without any of those routes needing to know team accounts exist.
        jobOwnedBy(5, recruiter);
        when(recruiterTeamMemberRepository.existsByRecruiterIdAndUserId(200L, 30L)).thenReturn(true);
        User teamMember = user(30, 3);

        assertTrue(allowed(teamMember, "PUT", "/api/recruiters/jobs/5"));
        assertTrue(allowed(teamMember, "GET", "/api/recruiters/200/talent-pool"));
        assertTrue(allowed(teamMember, "GET", "/api/recruiters/200/team"));
        assertFalse(allowed(teamMember, "GET", "/api/recruiters/201/talent-pool"));

        User nonMember = user(31, 3);
        assertFalse(allowed(nonMember, "GET", "/api/recruiters/200/talent-pool"));
    }

    @Test
    void apiKeyAndWebhookRoutesAreScopedToTheOwningRecruiter() {
        // Route-level access is the same ownRecruiterId check as everything else; the
        // owner-only restriction on actually minting a key/webhook is enforced in the service.
        assertTrue(allowed(recruiterUser, "GET", "/api/recruiters/200/api-keys"));
        assertTrue(allowed(recruiterUser, "POST", "/api/recruiters/200/api-keys"));
        assertTrue(allowed(recruiterUser, "DELETE", "/api/recruiters/200/api-keys/5"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/recruiters/200/api-keys"));

        assertTrue(allowed(recruiterUser, "GET", "/api/recruiters/200/webhooks"));
        assertTrue(allowed(recruiterUser, "POST", "/api/recruiters/200/webhooks"));
        assertTrue(allowed(recruiterUser, "DELETE", "/api/recruiters/200/webhooks/5"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/recruiters/200/webhooks"));

        assertTrue(allowed(recruiterUser, "GET", "/api/recruiters/200/verification"));
        assertTrue(allowed(recruiterUser, "POST", "/api/recruiters/200/verification"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/recruiters/200/verification"));
    }

    @Test
    void proposingInterviewSlotsIsRecruiterOnlyAndConfirmingIsOpenToBothParticipants() {
        JobPosting job = jobOwnedBy(5, recruiter);
        applicationFor(9, candidate, job);
        roundFor(7, 9);

        assertTrue(allowed(recruiterUser, "POST", "/api/applications/9/interview-rounds/propose"));
        assertFalse(allowed(candidate, "POST", "/api/applications/9/interview-rounds/propose"));
        assertFalse(allowed(otherRecruiterUser, "POST", "/api/applications/9/interview-rounds/propose"));

        assertTrue(allowed(candidate, "POST", "/api/interview-rounds/7/confirm-slot"));
        assertTrue(allowed(recruiterUser, "POST", "/api/interview-rounds/7/confirm-slot"));
        assertFalse(allowed(otherCandidate, "POST", "/api/interview-rounds/7/confirm-slot"));
    }

    @Test
    void interviewRoundFeedbackIsOpenToBothParticipantsButNotOutsiders() {
        JobPosting job = jobOwnedBy(5, recruiter);
        applicationFor(9, candidate, job);
        roundFor(7, 9);

        assertTrue(allowed(candidate, "POST", "/api/interview-rounds/7/feedback"));
        assertTrue(allowed(recruiterUser, "POST", "/api/interview-rounds/7/feedback"));
        assertFalse(allowed(otherCandidate, "POST", "/api/interview-rounds/7/feedback"));

        assertTrue(allowed(candidate, "GET", "/api/interview-rounds/7/feedback"));
        assertTrue(allowed(recruiterUser, "GET", "/api/interview-rounds/7/feedback"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/interview-rounds/7/feedback"));
    }

    @Test
    void directMessageThreadsAreLimitedToTheRecruiterAndTheNamedCandidate() {
        assertTrue(allowed(recruiterUser, "GET", "/api/recruiters/200/direct-messages/10"));
        assertTrue(allowed(recruiterUser, "POST", "/api/recruiters/200/direct-messages/10"));
        assertTrue(allowed(candidate, "GET", "/api/recruiters/200/direct-messages/10"));
        assertTrue(allowed(candidate, "POST", "/api/recruiters/200/direct-messages/10"));

        assertFalse(allowed(otherRecruiterUser, "GET", "/api/recruiters/200/direct-messages/10"));
        assertFalse(allowed(otherCandidate, "GET", "/api/recruiters/200/direct-messages/10"));

        assertTrue(allowed(recruiterUser, "GET", "/api/recruiters/200/direct-messages"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/recruiters/200/direct-messages"));
    }

    @Test
    void onlyTheOwningRecruiterCanReadAptitudeTestQuestions() {
        aptitudeTestOwnedBy(3, recruiter);

        assertTrue(allowed(recruiterUser, "GET", "/api/aptitude-tests/3/questions"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/aptitude-tests/3/questions"));
        assertFalse(allowed(candidate, "GET", "/api/aptitude-tests/3/questions"));
        assertFalse(allowed(candidate, "POST", "/api/aptitude-tests/3/add-multiple-questions"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/aptitude-tests/test/3/results/unscheduled"));
    }

    @Test
    void dreamRoleTestsAreScopedToTheirRecruiterToo() {
        DreamRoleTest test = new DreamRoleTest();
        test.setId(4L);
        test.setRecruiter(recruiter);
        lenient().when(dreamRoleTestRepository.findById(4L)).thenReturn(Optional.of(test));

        assertTrue(allowed(recruiterUser, "POST", "/api/dream-role-tests/4/add-technical-questions"));
        assertFalse(allowed(otherRecruiterUser, "POST", "/api/dream-role-tests/4/add-technical-questions"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/dream-role-tests/test/4/results/unscheduled"));
    }

    @Test
    void externalJobsAndCandidatesAreScopedToTheirRecruiter() {
        ExternalJobPosting externalJob = new ExternalJobPosting();
        externalJob.setId(4L);
        externalJob.setRecruiter(recruiter);
        lenient().when(externalJobRepository.findById(4L)).thenReturn(Optional.of(externalJob));
        ExternalCandidate externalCandidate = new ExternalCandidate();
        externalCandidate.setId(8L);
        externalCandidate.setJobPosting(externalJob);
        lenient().when(externalCandidateRepository.findById(8L)).thenReturn(Optional.of(externalCandidate));

        assertTrue(allowed(recruiterUser, "GET", "/api/external-jobs/4/all-candidates"));
        assertTrue(allowed(recruiterUser, "DELETE", "/api/external-jobs/4"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/external-jobs/4/all-candidates"));
        assertFalse(allowed(otherRecruiterUser, "DELETE", "/api/external-jobs/4"));
        assertTrue(allowed(recruiterUser, "GET", "/api/external-jobs/candidates/8/analysis"));
        assertFalse(allowed(otherRecruiterUser, "POST", "/api/external-jobs/candidates/8/schedule-interview"));
        assertFalse(allowed(candidate, "GET", "/api/external-jobs/4/stats"));
    }

    @Test
    void credentialFilesAreOnlyDownloadableByTheOwningRecruiter() {
        ExternalJobPosting externalJob = new ExternalJobPosting();
        externalJob.setId(4L);
        externalJob.setRecruiter(recruiter);
        lenient().when(externalJobRepository.findById(4L)).thenReturn(Optional.of(externalJob));

        assertTrue(allowed(recruiterUser, "GET", "/api/external-jobs/download-credentials/credentials_job_4_1700000000.csv"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/external-jobs/download-credentials/credentials_job_4_1700000000.csv"));
        assertFalse(allowed(recruiterUser, "GET", "/api/external-jobs/download-credentials/secrets.properties"));
    }

    // ----------------------------------------------------------- applications

    @Test
    void applicationsAreVisibleToTheApplicantAndTheOwningRecruiterOnly() {
        JobPosting job = jobOwnedBy(5, recruiter);
        applicationFor(9, candidate, job);

        assertTrue(allowed(candidate, "GET", "/api/aptitude-tests/application/9"));
        assertTrue(allowed(recruiterUser, "GET", "/api/resumes/download/9"));
        assertFalse(allowed(otherCandidate, "GET", "/api/resumes/download/9"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/resumes/download/9"));
        assertFalse(allowed(otherCandidate, "GET", "/api/resume-matching/application/9/resume-analysis"));
    }

    @Test
    void onlyTheOwningRecruiterCanChangeApplicationStatus() {
        JobPosting job = jobOwnedBy(5, recruiter);
        applicationFor(9, candidate, job);

        assertTrue(allowed(recruiterUser, "PUT", "/api/applications/9/reject"));
        assertFalse(allowed(otherRecruiterUser, "PUT", "/api/applications/9/reject"));
        assertFalse(allowed(candidate, "PUT", "/api/applications/9/send-offer"));
    }

    @Test
    void withdrawingAnApplicationIsOpenToBothParticipantsAtTheRouteLevel() {
        // The service enforces "candidate only"; the route itself allows either participant,
        // same layering as offer accept/decline and interview-round slot confirmation.
        JobPosting job = jobOwnedBy(5, recruiter);
        applicationFor(9, candidate, job);

        assertTrue(allowed(candidate, "POST", "/api/applications/9/withdraw"));
        assertTrue(allowed(recruiterUser, "POST", "/api/applications/9/withdraw"));
        assertFalse(allowed(otherCandidate, "POST", "/api/applications/9/withdraw"));
        assertFalse(allowed(otherRecruiterUser, "POST", "/api/applications/9/withdraw"));
    }

    @Test
    void recruitersOwnExternalApplicationsThroughTheTestsTheyAssigned() {
        Application external = applicationFor(30, candidate, null);
        AptitudeTest test = aptitudeTestOwnedBy(3, recruiter);
        TestAssignment assignment = aptitudeAssignment(70, test, external);
        lenient().when(assignmentRepository.findByApplicationId(30L)).thenReturn(List.of(assignment));

        assertTrue(allowed(recruiterUser, "PUT", "/api/applications/30/schedule-interview"));
        assertFalse(allowed(otherRecruiterUser, "PUT", "/api/applications/30/schedule-interview"));
    }

    @Test
    void recruitersOwnExternalApplicationsThroughTheExternalCandidateRecord() {
        Application external = applicationFor(31, candidate, null);
        ExternalJobPosting externalJob = new ExternalJobPosting();
        externalJob.setId(4L);
        externalJob.setRecruiter(recruiter);
        ExternalCandidate externalCandidate = new ExternalCandidate();
        externalCandidate.setJobPosting(externalJob);
        lenient().when(externalCandidateRepository.findAllByUserId(10L)).thenReturn(List.of(externalCandidate));

        assertTrue(allowed(recruiterUser, "PUT", "/api/applications/31/shortlist"));
        assertFalse(allowed(otherRecruiterUser, "PUT", "/api/applications/31/shortlist"));
        assertTrue(external.getId() == 31L);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void onlyTheApplicantCanStartTheirAssignment() {
        JobPosting job = jobOwnedBy(5, recruiter);
        Application application = applicationFor(9, candidate, job);
        aptitudeAssignment(70, aptitudeTestOwnedBy(3, recruiter), application);

        assertTrue(allowed(candidate, "GET", "/api/aptitude-tests/start/70"));
        assertFalse(allowed(otherCandidate, "GET", "/api/aptitude-tests/start/70"));
        assertFalse(allowed(recruiterUser, "GET", "/api/aptitude-tests/start/70"));
    }

    @Test
    void testResultsAreVisibleToTheApplicantAndTheTestOwnerOnly() {
        JobPosting job = jobOwnedBy(5, recruiter);
        Application application = applicationFor(9, candidate, job);
        aptitudeAssignment(70, aptitudeTestOwnedBy(3, recruiter), application);

        assertTrue(allowed(candidate, "GET", "/api/aptitude-tests/results/70"));
        assertTrue(allowed(recruiterUser, "GET", "/api/aptitude-tests/results/70"));
        assertFalse(allowed(otherCandidate, "GET", "/api/aptitude-tests/results/70"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/aptitude-tests/results/70"));
    }

    @Test
    void onlyTheReviewedCompanyCanReplyToItsOwnReview() {
        companyReviewOfCompany(60, recruiter);

        assertTrue(allowed(recruiterUser, "POST", "/api/recruiters/200/company-reviews/60/reply"));
        assertFalse(allowed(otherRecruiterUser, "POST", "/api/recruiters/201/company-reviews/60/reply"));
        assertFalse(allowed(candidate, "POST", "/api/recruiters/200/company-reviews/60/reply"));
    }

    @Test
    void proctoringViolationsAreVisibleToTheTestOwnerOnlyNotTheApplicantWhoTookIt() {
        JobPosting job = jobOwnedBy(5, recruiter);
        Application application = applicationFor(9, candidate, job);
        aptitudeAssignment(70, aptitudeTestOwnedBy(3, recruiter), application);

        assertTrue(allowed(recruiterUser, "GET", "/api/aptitude-tests/70/violations"));
        assertFalse(allowed(candidate, "GET", "/api/aptitude-tests/70/violations"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/aptitude-tests/70/violations"));
        assertFalse(allowed(otherCandidate, "GET", "/api/aptitude-tests/70/violations"));
    }

    @Test
    void dreamRoleAssignmentsFollowTheSameRules() {
        JobPosting job = jobOwnedBy(5, recruiter);
        Application application = applicationFor(9, candidate, job);
        DreamRoleTest test = new DreamRoleTest();
        test.setId(4L);
        test.setRecruiter(recruiter);
        DreamRoleTestAssignment assignment = new DreamRoleTestAssignment();
        assignment.setId(80L);
        assignment.setDreamRoleTest(test);
        assignment.setApplication(application);
        lenient().when(dreamRoleAssignmentRepository.findById(80L)).thenReturn(Optional.of(assignment));

        assertTrue(allowed(candidate, "GET", "/api/dream-role-tests/start/80"));
        assertFalse(allowed(otherCandidate, "GET", "/api/dream-role-tests/start/80"));
        assertTrue(allowed(recruiterUser, "GET", "/api/dream-role-tests/results/80"));
        assertFalse(allowed(otherRecruiterUser, "GET", "/api/dream-role-tests/results/80"));
    }

    @Test
    void proctoringViolationsCannotBePostedByRegularUsers() {
        assertFalse(allowed(candidate, "POST", "/api/aptitude-tests/70/violations"));
        assertFalse(allowed(recruiterUser, "POST", "/api/aptitude-tests/70/violations"));
    }

    // -------------------------------------------------------------- body checks

    boolean bodyAllowed(User user, String method, String path, Map<String, Object> body) {
        return policy.isBodyAllowed(user, method, path, NO_PARAMS, body);
    }

    @Test
    void recruiterIdInTheBodyMustBelongToTheCaller() {
        assertTrue(bodyAllowed(recruiterUser, "POST", "/api/aptitude-tests/create", Map.of("recruiterId", 200)));
        assertFalse(bodyAllowed(recruiterUser, "POST", "/api/aptitude-tests/create", Map.of("recruiterId", 201)));
        assertFalse(bodyAllowed(recruiterUser, "POST", "/api/recruiters/jobs", Map.of("recruiterId", "201")));
        assertTrue(bodyAllowed(admin, "POST", "/api/recruiters/jobs", Map.of("recruiterId", 201)));
    }

    @Test
    void userIdInTheBodyMustBeTheCaller() {
        assertTrue(bodyAllowed(candidate, "POST", "/api/recruiters/register", Map.of("userId", 10)));
        assertFalse(bodyAllowed(candidate, "POST", "/api/recruiters/register", Map.of("userId", 11)));
    }

    @Test
    void submittingSomeoneElsesAssignmentIsRejected() {
        JobPosting job = jobOwnedBy(5, recruiter);
        Application application = applicationFor(9, candidate, job);
        aptitudeAssignment(70, aptitudeTestOwnedBy(3, recruiter), application);

        assertTrue(bodyAllowed(candidate, "POST", "/api/aptitude-tests/submit", Map.of("assignmentId", 70)));
        assertFalse(bodyAllowed(otherCandidate, "POST", "/api/aptitude-tests/submit", Map.of("assignmentId", 70)));
        assertFalse(bodyAllowed(candidate, "POST", "/api/aptitude-tests/submit", Map.of("assignmentId", 71)));
    }

    @Test
    void assigningTestsRequiresOwningTheTestAndTheApplication() {
        JobPosting job = jobOwnedBy(5, recruiter);
        applicationFor(9, candidate, job);
        aptitudeTestOwnedBy(3, recruiter);
        aptitudeTestOwnedBy(6, otherRecruiter);

        assertTrue(bodyAllowed(recruiterUser, "POST", "/api/aptitude-tests/assign", Map.of("testId", 3, "applicationId", 9)));
        assertFalse(bodyAllowed(recruiterUser, "POST", "/api/aptitude-tests/assign", Map.of("testId", 6, "applicationId", 9)));
        assertFalse(bodyAllowed(otherRecruiterUser, "POST", "/api/aptitude-tests/assign", Map.of("testId", 6, "applicationId", 9)));
    }

    @Test
    void assigningTestsToExternalCandidatesRequiresOwningBoth() {
        ExternalJobPosting externalJob = new ExternalJobPosting();
        externalJob.setId(4L);
        externalJob.setRecruiter(recruiter);
        ExternalCandidate externalCandidate = new ExternalCandidate();
        externalCandidate.setId(8L);
        externalCandidate.setJobPosting(externalJob);
        lenient().when(externalCandidateRepository.findById(8L)).thenReturn(Optional.of(externalCandidate));
        aptitudeTestOwnedBy(3, recruiter);
        aptitudeTestOwnedBy(6, otherRecruiter);

        assertTrue(bodyAllowed(recruiterUser, "POST", "/api/external-jobs/assign-test", Map.of("candidateId", 8, "testId", 3)));
        assertFalse(bodyAllowed(recruiterUser, "POST", "/api/external-jobs/assign-test", Map.of("candidateId", 8, "testId", 6)));
        assertFalse(bodyAllowed(otherRecruiterUser, "POST", "/api/external-jobs/assign-test", Map.of("candidateId", 8, "testId", 6)));
    }
}
