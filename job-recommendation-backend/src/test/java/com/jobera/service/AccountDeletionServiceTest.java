package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
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

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock UserRepository userRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock TestAssignmentRepository testAssignmentRepository;
    @Mock DreamRoleTestAssignmentRepository dreamRoleTestAssignmentRepository;
    @Mock ProctoringViolationRepository proctoringViolationRepository;
    @Mock MessageRepository messageRepository;
    @Mock SavedJobRepository savedJobRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock AuthTokenRepository authTokenRepository;
    @Mock JobRatingRepository jobRatingRepository;
    @Mock JobRatingService jobRatingService;
    @Mock InternshipRatingRepository internshipRatingRepository;
    @Mock InternshipApplicationRepository internshipApplicationRepository;
    @Mock ExternalCandidateRepository externalCandidateRepository;

    AccountDeletionService service;
    User candidate;
    static final String RAW_PASSWORD = "CorrectHorseBattery1!";

    @BeforeEach
    void setUp() {
        service = new AccountDeletionService(userRepository, applicationRepository, testAssignmentRepository,
            dreamRoleTestAssignmentRepository, proctoringViolationRepository, messageRepository,
            savedJobRepository, passwordResetTokenRepository, authTokenRepository, jobRatingRepository,
            jobRatingService, internshipRatingRepository, internshipApplicationRepository,
            externalCandidateRepository);

        candidate = new User("candidate@example.com", new PasswordEncoder().encode(RAW_PASSWORD), "Candidate");
        candidate.setId(1L);
        candidate.setRoleId(1L);
    }

    @Test
    void refusesWhenThePasswordIsWrong() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(candidate));

        assertThrows(AccountDeletionService.CannotDeleteAccountException.class,
            () -> service.deleteMyAccount(1L, "wrong-password"));

        verify(userRepository, never()).delete(any());
    }

    @Test
    void refusesToDeleteARecruiterAccount() {
        candidate.setRoleId(3L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(candidate));

        assertThrows(AccountDeletionService.CannotDeleteAccountException.class,
            () -> service.deleteMyAccount(1L, RAW_PASSWORD));

        verify(userRepository, never()).delete(any());
    }

    @Test
    void refusesWhenTheAccountDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountDeletionService.CannotDeleteAccountException.class,
            () -> service.deleteMyAccount(99L, RAW_PASSWORD));
    }

    @Test
    void deletesTestAssignmentsAndTheirProctoringViolationsBeforeTheApplication() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(candidate));

        Application application = new Application();
        application.setId(500L);
        when(applicationRepository.findByUserId(1L)).thenReturn(List.of(application));

        TestAssignment assignment = new TestAssignment();
        assignment.setId(700L);
        when(testAssignmentRepository.findByApplicationId(500L)).thenReturn(List.of(assignment));
        when(jobRatingRepository.findByUserId(1L)).thenReturn(List.of());

        service.deleteMyAccount(1L, RAW_PASSWORD);

        verify(proctoringViolationRepository).deleteByTestAssignmentId(700L);
        verify(testAssignmentRepository).deleteFaceVerificationsByAssignmentId(700L);
        verify(testAssignmentRepository).deleteByApplicationId(500L);
        verify(dreamRoleTestAssignmentRepository).deleteByApplicationId(500L);
    }

    @Test
    void deletesLegacyFaceVerificationRowsForDreamRoleAssignmentsToo() {
        // face_verifications has a live FK to dream_role_test_assignments even though no entity
        // in this codebase maps that table anymore - a stray row there must not block deletion.
        when(userRepository.findById(1L)).thenReturn(Optional.of(candidate));

        Application application = new Application();
        application.setId(500L);
        when(applicationRepository.findByUserId(1L)).thenReturn(List.of(application));
        when(testAssignmentRepository.findByApplicationId(500L)).thenReturn(List.of());

        com.jobera.entity.DreamRoleTestAssignment dreamRoleAssignment = new com.jobera.entity.DreamRoleTestAssignment();
        dreamRoleAssignment.setId(800L);
        when(dreamRoleTestAssignmentRepository.findByApplicationId(500L)).thenReturn(List.of(dreamRoleAssignment));
        when(jobRatingRepository.findByUserId(1L)).thenReturn(List.of());

        service.deleteMyAccount(1L, RAW_PASSWORD);

        verify(dreamRoleTestAssignmentRepository).deleteFaceVerificationsByAssignmentId(800L);
        verify(dreamRoleTestAssignmentRepository).deleteByApplicationId(500L);
    }

    @Test
    void deletesJobRatingsThroughTheServiceSoAggregatesStayCorrect() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(candidate));
        when(applicationRepository.findByUserId(1L)).thenReturn(List.of());

        JobRating rating = new JobRating();
        rating.setId(900L);
        when(jobRatingRepository.findByUserId(1L)).thenReturn(List.of(rating));

        service.deleteMyAccount(1L, RAW_PASSWORD);

        verify(jobRatingService).deleteRating(900L);
        verify(jobRatingRepository, never()).deleteById(anyLong());
    }

    @Test
    void cleansUpEveryDependentBeforeDeletingTheUserRow() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(candidate));
        when(applicationRepository.findByUserId(1L)).thenReturn(List.of());
        when(jobRatingRepository.findByUserId(1L)).thenReturn(List.of());

        service.deleteMyAccount(1L, RAW_PASSWORD);

        verify(messageRepository).deleteBySenderIdOrRecipientId(1L, 1L);
        verify(savedJobRepository).deleteByUserId(1L);
        verify(passwordResetTokenRepository).deleteByUserId(1L);
        verify(authTokenRepository).deleteByUserId(1L);
        verify(internshipRatingRepository).deleteByUserId(1L);
        verify(internshipApplicationRepository).deleteByUserId(1L);
        verify(externalCandidateRepository).detachUser(1L);
        verify(userRepository, times(1)).delete(candidate);
    }
}
