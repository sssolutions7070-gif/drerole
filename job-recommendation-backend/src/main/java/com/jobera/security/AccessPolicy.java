package com.jobera.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

import com.jobera.entity.Application;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.AptitudeTestRepository;
import com.jobera.repository.CompanyReviewRepository;
import com.jobera.repository.DreamRoleTestAssignmentRepository;
import com.jobera.repository.DreamRoleTestRepository;
import com.jobera.repository.ExternalCandidateRepository;
import com.jobera.repository.ExternalJobPostingRepository;
import com.jobera.repository.InterviewRoundRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.JobRatingRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.RecruiterTeamMemberRepository;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.TestAssignmentRepository;

/**
 * Central, fail-closed authorization rules for every /api endpoint. A request is only
 * allowed when a rule matches it and that rule's check passes. Rules cover roles,
 * "the user in the URL is the caller" and ownership of recruiter-owned resources.
 */
@Component
@Transactional(readOnly = true)
public class AccessPolicy {

    private static final long ROLE_ADMIN = 2L;
    private static final long ROLE_RECRUITER = 3L;
    private static final Pattern CREDENTIALS_FILE = Pattern.compile("credentials_job_([0-9]+)_[0-9]+\\.csv");

    @FunctionalInterface
    interface Check {
        boolean allow(AccessContext ctx);
    }

    @FunctionalInterface
    interface BodyCheck {
        boolean allow(AccessContext ctx, Map<String, Object> body);
    }

    private record Rule(String method, String pattern, Check check, BodyCheck bodyCheck) {
    }

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<Rule> rules = new ArrayList<>();

    private final RecruiterRepository recruiterRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ExternalJobPostingRepository externalJobRepository;
    private final ExternalCandidateRepository externalCandidateRepository;
    private final AptitudeTestRepository aptitudeTestRepository;
    private final DreamRoleTestRepository dreamRoleTestRepository;
    private final ApplicationRepository applicationRepository;
    private final TestAssignmentRepository assignmentRepository;
    private final DreamRoleTestAssignmentRepository dreamRoleAssignmentRepository;
    private final JobRatingRepository jobRatingRepository;
    private final ResumeRepository resumeRepository;
    private final InterviewRoundRepository interviewRoundRepository;
    private final RecruiterTeamMemberRepository recruiterTeamMemberRepository;
    private final CompanyReviewRepository companyReviewRepository;

    public AccessPolicy(RecruiterRepository recruiterRepository,
                        JobPostingRepository jobPostingRepository,
                        ExternalJobPostingRepository externalJobRepository,
                        ExternalCandidateRepository externalCandidateRepository,
                        AptitudeTestRepository aptitudeTestRepository,
                        DreamRoleTestRepository dreamRoleTestRepository,
                        ApplicationRepository applicationRepository,
                        TestAssignmentRepository assignmentRepository,
                        DreamRoleTestAssignmentRepository dreamRoleAssignmentRepository,
                        JobRatingRepository jobRatingRepository,
                        ResumeRepository resumeRepository,
                        InterviewRoundRepository interviewRoundRepository,
                        RecruiterTeamMemberRepository recruiterTeamMemberRepository,
                        CompanyReviewRepository companyReviewRepository) {
        this.recruiterRepository = recruiterRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.externalJobRepository = externalJobRepository;
        this.externalCandidateRepository = externalCandidateRepository;
        this.aptitudeTestRepository = aptitudeTestRepository;
        this.dreamRoleTestRepository = dreamRoleTestRepository;
        this.applicationRepository = applicationRepository;
        this.assignmentRepository = assignmentRepository;
        this.dreamRoleAssignmentRepository = dreamRoleAssignmentRepository;
        this.jobRatingRepository = jobRatingRepository;
        this.resumeRepository = resumeRepository;
        this.interviewRoundRepository = interviewRoundRepository;
        this.recruiterTeamMemberRepository = recruiterTeamMemberRepository;
        this.companyReviewRepository = companyReviewRepository;
        registerRules();
    }

    // ------------------------------------------------------------------ rules

    private void registerRules() {
        Check authenticated = c -> true;
        Check admin = c -> isAdmin(c.user());
        Check recruiter = c -> isRecruiter(c.user());

        // Everyone logged in
        rule(null, "/api/auth/me", authenticated);

        // Administration
        rule(null, "/api/admin/**", admin);

        // Jobs and ratings
        rule("GET", "/api/jobs", authenticated);
        rule("GET", "/api/jobs/search", authenticated);
        rule("GET", "/api/jobs/recommendations/skills", authenticated);
        rule("GET", "/api/jobs/recommendations/{userId}", self("userId"));
        rule("GET", "/api/jobs/recommendations/{userId}/detailed", self("userId"));
        rule("POST", "/api/jobs/{jobId}/apply-with-resume/{userId}", self("userId"));
        rule("POST", "/api/jobs/{jobId}/apply/{userId}", self("userId"));
        rule("POST", "/api/jobs/{jobId}/rate/{userId}", self("userId"));
        rule("GET", "/api/jobs/{jobId}/rating/user/{userId}", self("userId"));
        rule("GET", "/api/jobs/{jobId}/ratings", authenticated);
        rule("GET", "/api/jobs/{jobId}/rating/stats", authenticated);
        rule("DELETE", "/api/jobs/ratings/{ratingId}", this::ownsRating);

        // Companies - public GETs, same as job browsing (see AuthInterceptor); this rule only
        // matters if a token happens to be present, since the interceptor bypasses it otherwise.
        rule("GET", "/api/companies", authenticated);
        rule("GET", "/api/companies/{recruiterId}", authenticated);
        rule("GET", "/api/companies/{recruiterId}/reviews", authenticated);
        rule("GET", "/api/companies/{recruiterId}/interview-experiences", authenticated);
        rule("GET", "/api/jobs/{jobId}", authenticated);
        rule("GET", "/api/salary-insights", authenticated);
        rule("POST", "/api/salary-insights/negotiation-advice", authenticated);

        // Any recruiter can browse the open-to-work pool - it's not scoped to one company,
        // same as any recruiter being able to post a job in the first place.
        rule("GET", "/api/candidates/discover", recruiter);
        rule("GET", "/api/candidates/discover/ai-search", recruiter);
        rule("POST", "/api/candidates/{candidateUserId}/views", recruiter);

        // Internships
        rule("GET", "/api/internships", authenticated);
        rule("GET", "/api/internships/stats", authenticated);
        rule("GET", "/api/internships/recommendations/{userId}", self("userId"));
        rule("POST", "/api/internships/{internshipId}/apply/{userId}", self("userId"));
        rule("POST", "/api/internships/{internshipId}/rate/{userId}", self("userId"));
        rule("GET", "/api/internships/{internshipId}/rating/user/{userId}", self("userId"));

        // Recruiter profile and job management
        rule("POST", "/api/recruiters/register", authenticated);
        rule("GET", "/api/recruiters/user/{userId}", self("userId"));
        rule("PUT", "/api/recruiters/user/{userId}/profile", self("userId"));
        rule("POST", "/api/recruiters/user/{userId}/logo", self("userId"));
        rule("POST", "/api/recruiters/user/{userId}/banner", self("userId"));
        rule("POST", "/api/recruiters/jobs", recruiter);
        rule("POST", "/api/recruiters/jobs/suggest-screening-questions", recruiter);
        rule("GET", "/api/recruiters/jobs/recruiter/{recruiterId}", ownRecruiterId("recruiterId"));
        rule("GET", "/api/recruiters/{recruiterId}/team", ownRecruiterId("recruiterId"));
        rule("POST", "/api/recruiters/{recruiterId}/team", ownRecruiterId("recruiterId"));
        rule("DELETE", "/api/recruiters/{recruiterId}/team/{membershipId}", ownRecruiterId("recruiterId"));
        rule("GET", "/api/recruiters/{recruiterId}/api-keys", ownRecruiterId("recruiterId"));
        rule("POST", "/api/recruiters/{recruiterId}/api-keys", ownRecruiterId("recruiterId"));
        rule("DELETE", "/api/recruiters/{recruiterId}/api-keys/{keyId}", ownRecruiterId("recruiterId"));
        rule("GET", "/api/recruiters/{recruiterId}/webhooks", ownRecruiterId("recruiterId"));
        rule("POST", "/api/recruiters/{recruiterId}/webhooks", ownRecruiterId("recruiterId"));
        rule("DELETE", "/api/recruiters/{recruiterId}/webhooks/{webhookId}", ownRecruiterId("recruiterId"));
        rule("POST", "/api/recruiters/{recruiterId}/webhooks/{webhookId}/test", ownRecruiterId("recruiterId"));
        rule("POST", "/api/recruiters/{recruiterId}/company-reviews/{reviewId}/reply", c -> ownsCompanyReview(c, "reviewId"));
        rule("GET", "/api/recruiters/{recruiterId}/verification", ownRecruiterId("recruiterId"));
        rule("POST", "/api/recruiters/{recruiterId}/verification", ownRecruiterId("recruiterId"));
        rule("GET", "/api/recruiters/{recruiterId}/direct-messages", ownRecruiterId("recruiterId"));
        rule("GET", "/api/recruiters/{recruiterId}/direct-messages/{candidateUserId}", participatesInDirectThread());
        rule("POST", "/api/recruiters/{recruiterId}/direct-messages/{candidateUserId}", participatesInDirectThread());
        rule("POST", "/api/recruiters/{recruiterId}/outreach-sequences", ownRecruiterId("recruiterId"));
        rule("GET", "/api/recruiters/{recruiterId}/outreach-sequences", ownRecruiterId("recruiterId"));
        rule("DELETE", "/api/recruiters/{recruiterId}/outreach-sequences/{sequenceId}", ownRecruiterId("recruiterId"));
        rule("GET", "/api/recruiters/{recruiterId}/talent-pool", ownRecruiterId("recruiterId"));
        rule("PUT", "/api/recruiters/{recruiterId}/talent-pool/{candidateUserId}/note", ownRecruiterId("recruiterId"));
        rule("GET", "/api/recruiters/{recruiterId}/saved-searches", ownRecruiterId("recruiterId"));
        rule("POST", "/api/recruiters/{recruiterId}/saved-searches", ownRecruiterId("recruiterId"));
        rule("DELETE", "/api/recruiters/{recruiterId}/saved-searches/{searchId}", ownRecruiterId("recruiterId"));
        rule("PUT", "/api/recruiters/{recruiterId}/saved-searches/{searchId}/alert", ownRecruiterId("recruiterId"));
        rule("POST", "/api/recruiters/{recruiterId}/saved-searches/{searchId}/check", ownRecruiterId("recruiterId"));
        rule("GET", "/api/recruiters/{recruiterId}/subscription", ownRecruiterId("recruiterId"));
        rule("POST", "/api/recruiters/{recruiterId}/subscription/checkout", ownRecruiterId("recruiterId"));
        rule("POST", "/api/recruiters/{recruiterId}/subscription/cancel", ownRecruiterId("recruiterId"));
        rule("PUT", "/api/recruiters/jobs/{jobId}", ownsJob("jobId"));
        rule("DELETE", "/api/recruiters/jobs/{jobId}", ownsJob("jobId"));
        rule("GET", "/api/recruiters/jobs/{jobId}/applications", ownsJob("jobId"));
        rule("GET", "/api/recruiters/jobs/{jobId}/applications/export", ownsJob("jobId"));
        rule("POST", "/api/recruiters/jobs/{jobId}/applications/bulk-message", ownsJob("jobId"));
        rule("PUT", "/api/recruiters/jobs/{jobId}/featured", ownsJob("jobId"));
        rule("POST", "/api/recruiters/jobs/{jobId}/duplicate", ownsJob("jobId"));

        // Application tracking
        rule("GET", "/api/applications/user/{userId}", self("userId"));
        rule("GET", "/api/applications/user/{userId}/insights", self("userId"));
        rule("GET", "/api/applications/job/{jobId}", ownsJob("jobId"));
        rule("GET", "/api/applications/recruiter/{recruiterId}/**", ownRecruiterId("recruiterId"));
        rule("PUT", "/api/applications/{applicationId}/*", c -> recruiterManagesApplication(c, "applicationId"));
        rule("POST", "/api/applications/{applicationId}/withdraw", c -> participatesInApplication(c, "applicationId"));
        rule("GET", "/api/applications/{applicationId}/rejection-explanation", c -> participatesInApplication(c, "applicationId"));
        rule("POST", "/api/applications/{applicationId}/follow-up", c -> recruiterManagesApplication(c, "applicationId"));

        // Messaging between a candidate and the recruiter behind their application
        rule("GET", "/api/applications/{applicationId}/messages", c -> participatesInApplication(c, "applicationId"));
        rule("POST", "/api/applications/{applicationId}/messages", c -> participatesInApplication(c, "applicationId"));
        rule("PUT", "/api/applications/{applicationId}/messages/read", c -> participatesInApplication(c, "applicationId"));
        rule("GET", "/api/messages/unread-count/{userId}", self("userId"));

        // Interview pipeline, scoped to one application - the recruiter schedules each round,
        // either side can list the pipeline, join a round or check its status
        rule("POST", "/api/applications/{applicationId}/interview-rounds", c -> recruiterManagesApplication(c, "applicationId"));
        rule("POST", "/api/applications/{applicationId}/interview-rounds/propose", c -> recruiterManagesApplication(c, "applicationId"));
        rule("POST", "/api/interview-rounds/{roundId}/confirm-slot", c -> participatesInRound(c, "roundId"));
        rule("GET", "/api/applications/{applicationId}/interview-rounds", c -> participatesInApplication(c, "applicationId"));
        rule("GET", "/api/interview-rounds/{roundId}/join", c -> participatesInRound(c, "roundId"));
        rule("GET", "/api/interview-rounds/{roundId}/calendar.ics", c -> participatesInRound(c, "roundId"));
        rule("PUT", "/api/interview-rounds/{roundId}/status", c -> participatesInRound(c, "roundId"));
        rule("PUT", "/api/interview-rounds/{roundId}/reschedule", c -> recruiterManagesRound(c, "roundId"));
        rule("POST", "/api/interview-rounds/{roundId}/recording", c -> participatesInRound(c, "roundId"));
        rule("GET", "/api/interview-rounds/{roundId}/recording", c -> participatesInRound(c, "roundId"));
        rule("GET", "/api/interview-rounds/{roundId}/recording-insights", c -> participatesInRound(c, "roundId"));
        rule("POST", "/api/interview-rounds/{roundId}/feedback", c -> participatesInRound(c, "roundId"));
        rule("GET", "/api/interview-rounds/{roundId}/feedback", c -> participatesInRound(c, "roundId"));
        rule("POST", "/api/interview-rounds/{roundId}/copilot-probes", c -> recruiterManagesRound(c, "roundId"));

        // Structured interview scorecards - recruiter-only, a candidate never sees the internal
        // rubric filled in about them
        rule("PUT", "/api/interview-rounds/{roundId}/scorecard", c -> recruiterManagesRound(c, "roundId"));
        rule("POST", "/api/interview-rounds/{roundId}/scorecard/generate-draft", c -> recruiterManagesRound(c, "roundId"));
        rule("GET", "/api/interview-rounds/{roundId}/scorecard", c -> recruiterManagesRound(c, "roundId"));
        rule("GET", "/api/applications/{applicationId}/scorecards", c -> recruiterManagesApplication(c, "applicationId"));

        // Collaborative hiring comments - internal team notes, never shown to the candidate
        rule("POST", "/api/applications/{applicationId}/comments", c -> recruiterManagesApplication(c, "applicationId"));
        rule("GET", "/api/applications/{applicationId}/comments", c -> recruiterManagesApplication(c, "applicationId"));
        rule("DELETE", "/api/applications/{applicationId}/comments/{commentId}", c -> recruiterManagesApplication(c, "applicationId"));

        // Google Calendar sync - connecting/disconnecting/checking status is about the caller's
        // own account, not any particular job/application, so just "logged in" like /api/auth/me.
        // The actual OAuth callback (/api/calendar/google/callback) is public - see
        // AuthInterceptor.PUBLIC_PATHS - since Google redirects the bare browser there.
        rule("GET", "/api/calendar/google/connect", authenticated);
        rule("GET", "/api/calendar/google/status", authenticated);
        rule("DELETE", "/api/calendar/google", authenticated);

        // Job offers, scoped to one application - the recruiter sends/withdraws it, either
        // participant can view it, and the service layer double-checks that only the candidate
        // themselves can accept or decline (not the recruiter who sent it)
        rule("POST", "/api/applications/{applicationId}/offer", c -> recruiterManagesApplication(c, "applicationId"));
        rule("GET", "/api/applications/{applicationId}/offer", c -> participatesInApplication(c, "applicationId"));
        rule("GET", "/api/applications/{applicationId}/offer/letter", c -> participatesInApplication(c, "applicationId"));
        rule("POST", "/api/applications/{applicationId}/offer/accept", c -> participatesInApplication(c, "applicationId"));
        rule("POST", "/api/applications/{applicationId}/offer/decline", c -> participatesInApplication(c, "applicationId"));
        rule("POST", "/api/applications/{applicationId}/offer/withdraw", c -> recruiterManagesApplication(c, "applicationId"));

        // Aptitude tests
        rule("POST", "/api/aptitude-tests/create", recruiter);
        rule("GET", "/api/aptitude-tests/recruiter/{recruiterId}", ownRecruiterId("recruiterId"));
        rule("GET", "/api/aptitude-tests/recruiter/{recruiterId}/results", ownRecruiterId("recruiterId"));
        rule("GET", "/api/aptitude-tests/stats/recruiter/{recruiterId}", ownRecruiterId("recruiterId"));
        rule("POST", "/api/aptitude-tests/assign", recruiter, this::checkAptitudeAssignBody);
        rule("GET", "/api/aptitude-tests/application/{applicationId}", c -> participatesInApplication(c, "applicationId"));
        rule("GET", "/api/aptitude-tests/start/{assignmentId}", c -> isAptitudeApplicant(c, "assignmentId"));
        rule("POST", "/api/aptitude-tests/submit", authenticated,
            (c, body) -> isAptitudeApplicant(c.user(), AccessContext.toId(body.get("assignmentId"))));
        rule("GET", "/api/aptitude-tests/results/{assignmentId}", c -> participatesInAptitudeAssignment(c, "assignmentId"));
        rule("GET", "/api/aptitude-tests/{assignmentId}/violations", c -> ownsAptitudeAssignment(c, "assignmentId"));
        rule("GET", "/api/aptitude-tests/test/{testId}/results/unscheduled", c -> ownsAptitudeTest(c, "testId"));
        rule("POST", "/api/aptitude-tests/{assignmentId}/violations", admin);
        rule("GET", "/api/aptitude-tests/{testId}/questions", c -> ownsAptitudeTest(c, "testId"));
        rule("POST", "/api/aptitude-tests/{testId}/add-multiple-questions", c -> ownsAptitudeTest(c, "testId"));

        // DreamRole tests
        rule("POST", "/api/dream-role-tests/create", recruiter);
        rule("POST", "/api/dream-role-tests/generate-questions", recruiter);
        rule("GET", "/api/dream-role-tests/branches/stats", recruiter);
        rule("GET", "/api/dream-role-tests/recruiter/{recruiterId}", ownRecruiterId("recruiterId"));
        rule("POST", "/api/dream-role-tests/assign", recruiter, this::checkDreamRoleAssignBody);
        rule("GET", "/api/dream-role-tests/application/{applicationId}", c -> participatesInApplication(c, "applicationId"));
        rule("GET", "/api/dream-role-tests/start/{assignmentId}", c -> isDreamRoleApplicant(c, "assignmentId"));
        rule("POST", "/api/dream-role-tests/submit", authenticated,
            (c, body) -> isDreamRoleApplicant(c.user(), AccessContext.toId(body.get("assignmentId"))));
        rule("GET", "/api/dream-role-tests/results/{assignmentId}", c -> participatesInDreamRoleAssignment(c, "assignmentId"));
        rule("GET", "/api/dream-role-tests/test/{testId}/results/unscheduled", c -> ownsDreamRoleTest(c, "testId"));
        rule("POST", "/api/dream-role-tests/{testId}/add-technical-questions", c -> ownsDreamRoleTest(c, "testId"));

        // External hiring
        rule("POST", "/api/external-jobs/create", recruiter);
        rule("POST", "/api/external-jobs/assign-test", recruiter, this::checkExternalAssignBody);
        rule("GET", "/api/external-jobs/download-credentials/{fileName}", this::ownsCredentialsFile);
        rule("GET", "/api/external-jobs/recruiter/{recruiterId}", ownRecruiterId("recruiterId"));
        rule("GET", "/api/external-jobs/recruiter/{recruiterId}/**", ownRecruiterId("recruiterId"));
        rule(null, "/api/external-jobs/candidates/{candidateId}/**", c -> ownsExternalCandidate(c, "candidateId"));
        rule(null, "/api/external-jobs/{jobId}", c -> ownsExternalJob(c, "jobId"));
        rule(null, "/api/external-jobs/{jobId}/*", c -> ownsExternalJob(c, "jobId"));

        // Resumes
        rule("POST", "/api/resumes/upload/{userId}", self("userId"));
        rule("GET", "/api/resumes/user/{userId}", self("userId"));
        rule("GET", "/api/resumes/user/{userId}/latest", self("userId"));
        rule("GET", "/api/resumes/download/{applicationId}", c -> participatesInApplication(c, "applicationId"));
        rule("GET", "/api/resumes/{resumeId}/analysis", this::ownsResume);
        rule("GET", "/api/resumes/{resumeId}/quality", this::ownsResume);
        rule("GET", "/api/resume-matching/job/{jobId}/applications/sorted", ownsJob("jobId"));
        rule("GET", "/api/resume-matching/application/{applicationId}/resume-analysis",
            c -> participatesInApplication(c, "applicationId"));
        rule("POST", "/api/resume-matching/tailor-resume", authenticated);
        rule("POST", "/api/resume-matching/career-roadmap", authenticated);
        rule("POST", "/api/resume-matching/generate-cover-letter", authenticated);

        // Own profile
        rule(null, "/api/users/{userId}/**", self("userId"));
    }

    private void rule(String method, String pattern, Check check) {
        rules.add(new Rule(method, pattern, check, null));
    }

    private void rule(String method, String pattern, Check check, BodyCheck bodyCheck) {
        rules.add(new Rule(method, pattern, check, bodyCheck));
    }

    // ------------------------------------------------------------- entry points

    public boolean hasRule(String method, String path) {
        return findRule(method, path).isPresent();
    }

    /** Decides whether {@code user} may call {@code method path}; unknown routes are denied. */
    public boolean isAllowed(User user, String method, String path, Map<String, String[]> params) {
        Optional<Rule> rule = findRule(method, path);
        if (rule.isEmpty()) {
            return false;
        }
        AccessContext ctx = context(user, rule.get(), path, params);
        if (!rule.get().check().allow(ctx)) {
            return false;
        }
        Long recruiterParam = ctx.param("recruiterId");
        if (recruiterParam != null && !ownsRecruiterId(user, recruiterParam)) {
            return false;
        }
        Long userParam = ctx.param("userId");
        return userParam == null || isAdmin(user) || Objects.equals(user.getId(), userParam);
    }

    /** Validates identifiers carried in a JSON request body (recruiterId, userId, testId, ...). */
    public boolean isBodyAllowed(User user, String method, String path, Map<String, String[]> params,
                                 Map<String, Object> body) {
        if (isAdmin(user)) {
            return true;
        }
        Optional<Rule> rule = findRule(method, path);
        if (rule.isEmpty()) {
            return false;
        }
        AccessContext ctx = context(user, rule.get(), path, params);
        if (body.containsKey("recruiterId") && !ownsRecruiterId(user, AccessContext.toId(body.get("recruiterId")))) {
            return false;
        }
        if (body.containsKey("userId") && !Objects.equals(user.getId(), AccessContext.toId(body.get("userId")))) {
            return false;
        }
        return rule.get().bodyCheck() == null || rule.get().bodyCheck().allow(ctx, body);
    }

    private Optional<Rule> findRule(String method, String path) {
        return rules.stream()
            .filter(r -> (r.method() == null || r.method().equalsIgnoreCase(method)) && matcher.match(r.pattern(), path))
            .findFirst();
    }

    private AccessContext context(User user, Rule rule, String path, Map<String, String[]> params) {
        return new AccessContext(user, matcher.extractUriTemplateVariables(rule.pattern(), path), params);
    }

    // ------------------------------------------------------------------- roles

    public boolean isAdmin(User user) {
        return user != null && Objects.equals(user.getRoleId(), ROLE_ADMIN);
    }

    public boolean isRecruiter(User user) {
        return isAdmin(user) || (user != null && Objects.equals(user.getRoleId(), ROLE_RECRUITER));
    }

    private Optional<Recruiter> recruiterOf(User user) {
        return user == null ? Optional.empty() : recruiterRepository.findByUserId(user.getId());
    }

    private boolean ownsRecruiterId(User user, Long recruiterId) {
        if (recruiterId == null) {
            return false;
        }
        return isAdmin(user) || recruiterOf(user).map(r -> r.getId().equals(recruiterId)).orElse(false)
            || (user != null && recruiterTeamMemberRepository.existsByRecruiterIdAndUserId(recruiterId, user.getId()));
    }

    private boolean isRecruiterEntity(User user, Recruiter owner) {
        return owner != null && ownsRecruiterId(user, owner.getId());
    }

    // ------------------------------------------------------------ rule factories

    private Check self(String var) {
        return c -> isAdmin(c.user()) || Objects.equals(c.user().getId(), c.var(var));
    }

    private Check ownRecruiterId(String var) {
        return c -> ownsRecruiterId(c.user(), c.var(var));
    }

    /** Either side of a direct-message thread: the recruiter (owner or team member) or the
     *  candidate the thread is with - nobody else. */
    private Check participatesInDirectThread() {
        Check recruiterSide = ownRecruiterId("recruiterId");
        Check candidateSide = self("candidateUserId");
        return c -> recruiterSide.allow(c) || candidateSide.allow(c);
    }

    /** The recruiter being path-scoped must be the company the review is actually about - not
     *  just any recruiter, which ownRecruiterId alone would allow. */
    private boolean ownsCompanyReview(AccessContext c, String var) {
        Long reviewId = c.var(var);
        return reviewId != null && isRecruiter(c.user()) && companyReviewRepository.findById(reviewId)
            .map(r -> isRecruiterEntity(c.user(), r.getRecruiter()))
            .orElse(false);
    }

    private Check ownsJob(String var) {
        return c -> isAdmin(c.user()) || (isRecruiter(c.user()) && c.var(var) != null
            && jobPostingRepository.findById(c.var(var))
                .map(job -> isRecruiterEntity(c.user(), job.getRecruiter())).orElse(false));
    }

    // ------------------------------------------------------- resource ownership

    private boolean ownsExternalJob(AccessContext c, String var) {
        return ownsExternalJobId(c.user(), c.var(var));
    }

    private boolean ownsExternalJobId(User user, Long jobId) {
        return isAdmin(user) || (isRecruiter(user) && jobId != null
            && externalJobRepository.findById(jobId).map(j -> isRecruiterEntity(user, j.getRecruiter())).orElse(false));
    }

    private boolean ownsExternalCandidate(AccessContext c, String var) {
        return ownsExternalCandidateId(c.user(), c.var(var));
    }

    private boolean ownsExternalCandidateId(User user, Long candidateId) {
        return isAdmin(user) || (isRecruiter(user) && candidateId != null
            && externalCandidateRepository.findById(candidateId)
                .map(cand -> cand.getJobPosting() != null && isRecruiterEntity(user, cand.getJobPosting().getRecruiter()))
                .orElse(false));
    }

    private boolean ownsCredentialsFile(AccessContext c) {
        Matcher m = CREDENTIALS_FILE.matcher(c.vars().getOrDefault("fileName", ""));
        return m.matches() && ownsExternalJobId(c.user(), Long.valueOf(m.group(1)));
    }

    private boolean ownsAptitudeTest(AccessContext c, String var) {
        return ownsAptitudeTestId(c.user(), c.var(var));
    }

    private boolean ownsAptitudeTestId(User user, Long testId) {
        return isAdmin(user) || (isRecruiter(user) && testId != null
            && aptitudeTestRepository.findById(testId).map(t -> isRecruiterEntity(user, t.getRecruiter())).orElse(false));
    }

    private boolean ownsDreamRoleTest(AccessContext c, String var) {
        return ownsDreamRoleTestId(c.user(), c.var(var));
    }

    private boolean ownsDreamRoleTestId(User user, Long testId) {
        return isAdmin(user) || (isRecruiter(user) && testId != null
            && dreamRoleTestRepository.findById(testId).map(t -> isRecruiterEntity(user, t.getRecruiter())).orElse(false));
    }

    private boolean ownsRating(AccessContext c) {
        Long ratingId = c.var("ratingId");
        return isAdmin(c.user()) || (ratingId != null && jobRatingRepository.findById(ratingId)
            .map(r -> r.getUser() != null && Objects.equals(r.getUser().getId(), c.user().getId())).orElse(false));
    }

    private boolean ownsResume(AccessContext c) {
        Long resumeId = c.var("resumeId");
        return isAdmin(c.user()) || (resumeId != null && resumeRepository.findById(resumeId)
            .map(r -> r.getUser() != null && Objects.equals(r.getUser().getId(), c.user().getId())).orElse(false));
    }

    /** True when a recruiter's job, or a test/external listing they own, is behind this application. */
    private boolean recruiterOwnsApplication(User user, Application application) {
        if (application == null || !isRecruiter(user)) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        if (application.getJobPosting() != null) {
            return isRecruiterEntity(user, application.getJobPosting().getRecruiter());
        }
        Long applicationId = application.getId();
        boolean viaAptitude = assignmentRepository.findByApplicationId(applicationId).stream()
            .anyMatch(a -> a.getTest() != null && isRecruiterEntity(user, a.getTest().getRecruiter()));
        boolean viaDreamRole = dreamRoleAssignmentRepository.findByApplicationId(applicationId).stream()
            .anyMatch(a -> a.getDreamRoleTest() != null && isRecruiterEntity(user, a.getDreamRoleTest().getRecruiter()));
        if (viaAptitude || viaDreamRole) {
            return true;
        }
        return application.getUser() != null && externalCandidateRepository
            .findAllByUserId(application.getUser().getId()).stream()
            .anyMatch(cand -> cand.getJobPosting() != null
                && isRecruiterEntity(user, cand.getJobPosting().getRecruiter()));
    }

    private boolean recruiterManagesApplication(AccessContext c, String var) {
        Long id = c.var(var);
        return id != null && applicationRepository.findById(id)
            .map(app -> recruiterOwnsApplication(c.user(), app)).orElse(false);
    }

    private boolean recruiterManagesRound(AccessContext c, String var) {
        Long id = c.var(var);
        return id != null && interviewRoundRepository.findById(id)
            .flatMap(round -> applicationRepository.findById(round.getApplicationId()))
            .map(app -> recruiterOwnsApplication(c.user(), app)).orElse(false);
    }

    private boolean participatesInRound(AccessContext c, String var) {
        Long id = c.var(var);
        if (id == null) {
            return false;
        }
        return isAdmin(c.user()) || interviewRoundRepository.findById(id)
            .flatMap(round -> applicationRepository.findById(round.getApplicationId()))
            .map(app -> (app.getUser() != null && Objects.equals(app.getUser().getId(), c.user().getId()))
                || recruiterOwnsApplication(c.user(), app))
            .orElse(false);
    }

    private boolean participatesInApplication(AccessContext c, String var) {
        Long id = c.var(var);
        if (id == null) {
            return false;
        }
        return isAdmin(c.user()) || applicationRepository.findById(id)
            .map(app -> (app.getUser() != null && Objects.equals(app.getUser().getId(), c.user().getId()))
                || recruiterOwnsApplication(c.user(), app))
            .orElse(false);
    }

    private boolean isAptitudeApplicant(AccessContext c, String var) {
        return isAptitudeApplicant(c.user(), c.var(var));
    }

    private boolean isAptitudeApplicant(User user, Long assignmentId) {
        return isAdmin(user) || (assignmentId != null && assignmentRepository.findById(assignmentId)
            .map(a -> a.getApplication() != null && a.getApplication().getUser() != null
                && Objects.equals(a.getApplication().getUser().getId(), user.getId()))
            .orElse(false));
    }

    private boolean participatesInAptitudeAssignment(AccessContext c, String var) {
        Long id = c.var(var);
        return isAptitudeApplicant(c.user(), id) || (id != null && assignmentRepository.findById(id)
            .map(a -> a.getTest() != null && isRecruiter(c.user()) && isRecruiterEntity(c.user(), a.getTest().getRecruiter()))
            .orElse(false));
    }

    /** Recruiter-only, unlike participatesInAptitudeAssignment: the candidate who took the test
     *  never sees their own proctoring violation log, so they can't learn what the detector does
     *  and doesn't catch for next time. */
    private boolean ownsAptitudeAssignment(AccessContext c, String var) {
        Long id = c.var(var);
        return id != null && isRecruiter(c.user()) && assignmentRepository.findById(id)
            .map(a -> a.getTest() != null && isRecruiterEntity(c.user(), a.getTest().getRecruiter()))
            .orElse(false);
    }

    private boolean isDreamRoleApplicant(AccessContext c, String var) {
        return isDreamRoleApplicant(c.user(), c.var(var));
    }

    private boolean isDreamRoleApplicant(User user, Long assignmentId) {
        return isAdmin(user) || (assignmentId != null && dreamRoleAssignmentRepository.findById(assignmentId)
            .map(a -> a.getApplication() != null && a.getApplication().getUser() != null
                && Objects.equals(a.getApplication().getUser().getId(), user.getId()))
            .orElse(false));
    }

    private boolean participatesInDreamRoleAssignment(AccessContext c, String var) {
        Long id = c.var(var);
        return isDreamRoleApplicant(c.user(), id) || (id != null && dreamRoleAssignmentRepository.findById(id)
            .map(a -> a.getDreamRoleTest() != null && isRecruiter(c.user())
                && isRecruiterEntity(c.user(), a.getDreamRoleTest().getRecruiter()))
            .orElse(false));
    }

    // -------------------------------------------------------------- body checks

    private boolean checkAptitudeAssignBody(AccessContext c, Map<String, Object> body) {
        return ownsAptitudeTestId(c.user(), AccessContext.toId(body.get("testId")))
            && canManageApplicationId(c.user(), AccessContext.toId(body.get("applicationId")));
    }

    private boolean checkDreamRoleAssignBody(AccessContext c, Map<String, Object> body) {
        return ownsDreamRoleTestId(c.user(), AccessContext.toId(body.get("testId")))
            && canManageApplicationId(c.user(), AccessContext.toId(body.get("applicationId")));
    }

    private boolean checkExternalAssignBody(AccessContext c, Map<String, Object> body) {
        Long testId = AccessContext.toId(body.get("testId"));
        return ownsExternalCandidateId(c.user(), AccessContext.toId(body.get("candidateId")))
            && (ownsAptitudeTestId(c.user(), testId) || ownsDreamRoleTestId(c.user(), testId));
    }

    private boolean canManageApplicationId(User user, Long applicationId) {
        return applicationId != null && applicationRepository.findById(applicationId)
            .map(app -> recruiterOwnsApplication(user, app)).orElse(false);
    }
}
