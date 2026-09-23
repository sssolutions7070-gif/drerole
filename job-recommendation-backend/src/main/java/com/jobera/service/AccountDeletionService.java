package com.jobera.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobera.entity.Application;
import com.jobera.entity.DreamRoleTestAssignment;
import com.jobera.entity.JobRating;
import com.jobera.entity.TestAssignment;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.AuthTokenRepository;
import com.jobera.repository.DreamRoleTestAssignmentRepository;
import com.jobera.repository.ExternalCandidateRepository;
import com.jobera.repository.InternshipApplicationRepository;
import com.jobera.repository.InternshipRatingRepository;
import com.jobera.repository.JobRatingRepository;
import com.jobera.repository.MessageRepository;
import com.jobera.repository.PasswordResetTokenRepository;
import com.jobera.repository.ProctoringViolationRepository;
import com.jobera.repository.SavedJobRepository;
import com.jobera.repository.TestAssignmentRepository;
import com.jobera.repository.UserRepository;
import com.jobera.util.PasswordEncoder;

/**
 * Self-service "delete my account". Scoped to candidate accounts only (roleId 1): a recruiter's
 * account has job postings other candidates have real applications against, and safely tearing
 * that down (or deciding what should survive) is a bigger, separate decision than this covers -
 * recruiters and admins go through support instead.
 *
 * User carries {@code cascade = ALL, orphanRemoval = true} on resumes/applications/skills/
 * education/experience/certifications, so deleting the User row handles those automatically.
 * Everything else that holds a hard (non-nullable) foreign key to the user, or to an application
 * of theirs, has to be cleaned up first or the delete would fail with a constraint violation -
 * that's most of what this class does. ExternalCandidate.user is the one optional link: it's a
 * recruiter's own record of a bulk-uploaded candidate, so it's detached, not deleted.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);
    private static final Long CANDIDATE_ROLE_ID = 1L;

    public static class CannotDeleteAccountException extends RuntimeException {
        public CannotDeleteAccountException(String message) {
            super(message);
        }
    }

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final TestAssignmentRepository testAssignmentRepository;
    private final DreamRoleTestAssignmentRepository dreamRoleTestAssignmentRepository;
    private final ProctoringViolationRepository proctoringViolationRepository;
    private final MessageRepository messageRepository;
    private final SavedJobRepository savedJobRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuthTokenRepository authTokenRepository;
    private final JobRatingRepository jobRatingRepository;
    private final JobRatingService jobRatingService;
    private final InternshipRatingRepository internshipRatingRepository;
    private final InternshipApplicationRepository internshipApplicationRepository;
    private final ExternalCandidateRepository externalCandidateRepository;
    private final PasswordEncoder passwordEncoder = new PasswordEncoder();

    public AccountDeletionService(UserRepository userRepository,
                                   ApplicationRepository applicationRepository,
                                   TestAssignmentRepository testAssignmentRepository,
                                   DreamRoleTestAssignmentRepository dreamRoleTestAssignmentRepository,
                                   ProctoringViolationRepository proctoringViolationRepository,
                                   MessageRepository messageRepository,
                                   SavedJobRepository savedJobRepository,
                                   PasswordResetTokenRepository passwordResetTokenRepository,
                                   AuthTokenRepository authTokenRepository,
                                   JobRatingRepository jobRatingRepository,
                                   JobRatingService jobRatingService,
                                   InternshipRatingRepository internshipRatingRepository,
                                   InternshipApplicationRepository internshipApplicationRepository,
                                   ExternalCandidateRepository externalCandidateRepository) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.testAssignmentRepository = testAssignmentRepository;
        this.dreamRoleTestAssignmentRepository = dreamRoleTestAssignmentRepository;
        this.proctoringViolationRepository = proctoringViolationRepository;
        this.messageRepository = messageRepository;
        this.savedJobRepository = savedJobRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.authTokenRepository = authTokenRepository;
        this.jobRatingRepository = jobRatingRepository;
        this.jobRatingService = jobRatingService;
        this.internshipRatingRepository = internshipRatingRepository;
        this.internshipApplicationRepository = internshipApplicationRepository;
        this.externalCandidateRepository = externalCandidateRepository;
    }

    @Transactional
    public void deleteMyAccount(Long userId, String currentPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CannotDeleteAccountException("Account not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CannotDeleteAccountException("Password is incorrect");
        }

        if (!CANDIDATE_ROLE_ID.equals(user.getRoleId())) {
            throw new CannotDeleteAccountException(
                "Recruiter and admin accounts can't be self-deleted yet - contact support.");
        }

        // Test assignments (and their proctoring violations / legacy face-verification rows)
        // hold a non-nullable FK to the application, so they must go before the applications do.
        for (Application application : applicationRepository.findByUserId(userId)) {
            for (TestAssignment assignment : testAssignmentRepository.findByApplicationId(application.getId())) {
                proctoringViolationRepository.deleteByTestAssignmentId(assignment.getId());
                testAssignmentRepository.deleteFaceVerificationsByAssignmentId(assignment.getId());
            }
            testAssignmentRepository.deleteByApplicationId(application.getId());

            for (DreamRoleTestAssignment assignment : dreamRoleTestAssignmentRepository.findByApplicationId(application.getId())) {
                dreamRoleTestAssignmentRepository.deleteFaceVerificationsByAssignmentId(assignment.getId());
            }
            dreamRoleTestAssignmentRepository.deleteByApplicationId(application.getId());
        }

        // Job ratings go through the service so the job's cached average/count stay correct,
        // rather than a bulk delete that would leave them stale.
        for (JobRating rating : jobRatingRepository.findByUserId(userId)) {
            jobRatingService.deleteRating(rating.getId());
        }

        messageRepository.deleteBySenderIdOrRecipientId(userId, userId);
        savedJobRepository.deleteByUserId(userId);
        passwordResetTokenRepository.deleteByUserId(userId);
        authTokenRepository.deleteByUserId(userId);
        internshipRatingRepository.deleteByUserId(userId);
        internshipApplicationRepository.deleteByUserId(userId);
        externalCandidateRepository.detachUser(userId);

        // Cascades to Resume, Application, UserSkill, UserEducation, UserExperience,
        // UserCertification via the User entity's own cascade = ALL, orphanRemoval = true.
        userRepository.delete(user);
        log.info("Deleted account {} at the user's own request", userId);
    }
}
