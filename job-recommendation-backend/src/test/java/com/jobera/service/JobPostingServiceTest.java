package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.ReferralRepository;
import com.jobera.repository.SavedJobRepository;
import com.jobera.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class JobPostingServiceTest {

    @Mock JobPostingRepository jobPostingRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock UserRepository userRepository;
    @Mock RecruiterRepository recruiterRepository;
    @Mock SavedJobRepository savedJobRepository;
    @Mock SubscriptionService subscriptionService;
    @Mock WebhookService webhookService;
    @Mock SilverMedalistService silverMedalistService;
    @Mock ReferralRepository referralRepository;

    JobPostingService service;

    @BeforeEach
    void setUp() {
        service = new JobPostingService(jobPostingRepository, applicationRepository, userRepository,
            recruiterRepository, savedJobRepository, subscriptionService, webhookService, silverMedalistService,
            referralRepository);
    }

    @Test
    void newJobPostingsStartPendingModeration() {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        when(recruiterRepository.findById(10L)).thenReturn(Optional.of(recruiter));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        JobPosting created = service.createJobPosting(10L, "Backend Developer", "Acme", "desc",
            "[\"java\"]", "[]", "Remote", "$100k", "Full-time", "Mid");

        assertEquals(JobPosting.MODERATION_PENDING, created.getModerationStatus());
    }

    @Test
    void creatingAJobIsBlockedWhenTheSubscriptionServiceRejectsIt() {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        when(recruiterRepository.findById(10L)).thenReturn(Optional.of(recruiter));
        org.mockito.Mockito.doThrow(new SubscriptionService.SubscriptionLimitException(3))
            .when(subscriptionService).enforceActiveJobLimit(recruiter);

        assertThrows(SubscriptionService.SubscriptionLimitException.class, () ->
            service.createJobPosting(10L, "Backend Developer", "Acme", "desc",
                "[\"java\"]", "[]", "Remote", "$100k", "Full-time", "Mid"));

        org.mockito.Mockito.verify(jobPostingRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void approvingAJobClearsAnyPriorRejectionReason() {
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setModerationStatus(JobPosting.MODERATION_REJECTED);
        job.setRejectionReason("Needs a real description");
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        JobPosting approved = service.approveJobPosting(1L);

        assertEquals(JobPosting.MODERATION_APPROVED, approved.getModerationStatus());
        assertNull(approved.getRejectionReason());
    }

    @Test
    void approvingAJobNotifiesPastNearMisses() {
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setModerationStatus(JobPosting.MODERATION_PENDING);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        service.approveJobPosting(1L);

        org.mockito.Mockito.verify(silverMedalistService).notifyMatchingPastCandidates(job);
    }

    @Test
    void approvingAJobSucceedsEvenWhenTheNotificationSweepFails() {
        JobPosting job = new JobPosting();
        job.setId(1L);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));
        when(silverMedalistService.notifyMatchingPastCandidates(any())).thenThrow(new RuntimeException("boom"));

        JobPosting approved = service.approveJobPosting(1L);

        assertEquals(JobPosting.MODERATION_APPROVED, approved.getModerationStatus());
    }

    @Test
    void duplicatingAJobCopiesItsContentAsAFreshPendingDraft() {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        JobPosting original = new JobPosting();
        original.setId(1L);
        original.setTitle("Backend Developer");
        original.setCompany("Acme");
        original.setDescription("desc");
        original.setRequiredSkills("java,spring");
        original.setPreferredSkills("docker");
        original.setLocation("Remote");
        original.setSalaryRange("$100k");
        original.setJobType("Full-time");
        original.setExperienceLevel("Mid");
        original.setRecruiter(recruiter);
        original.setModerationStatus(JobPosting.MODERATION_APPROVED);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(original));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        JobPosting copy = service.duplicateJobPosting(1L, 10L);

        assertEquals("Backend Developer (Copy)", copy.getTitle());
        assertEquals("java,spring", copy.getRequiredSkills());
        assertEquals(JobPosting.MODERATION_PENDING, copy.getModerationStatus());
    }

    @Test
    void duplicatingAJobRejectsANonOwningRecruiter() {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        JobPosting original = new JobPosting();
        original.setId(1L);
        original.setRecruiter(recruiter);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(original));

        assertThrows(JobPostingService.JobPostingException.class, () -> service.duplicateJobPosting(1L, 999L));
        org.mockito.Mockito.verify(jobPostingRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void rejectingAJobRecordsTheReason() {
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setModerationStatus(JobPosting.MODERATION_PENDING);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        JobPosting rejected = service.rejectJobPosting(1L, "Looks like a scam listing");

        assertEquals(JobPosting.MODERATION_REJECTED, rejected.getModerationStatus());
        assertEquals("Looks like a scam listing", rejected.getRejectionReason());
    }

    @Test
    void editingARejectedJobResubmitsItForReview() {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setRecruiter(recruiter);
        job.setModerationStatus(JobPosting.MODERATION_REJECTED);
        job.setRejectionReason("Vague description");
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        JobPosting updated = service.updateJobPosting(1L, 10L, "Backend Developer", "a much better description",
            "[\"java\"]", "[]", "Remote", "$100k", "Full-time", "Mid", null);

        assertEquals(JobPosting.MODERATION_PENDING, updated.getModerationStatus());
        assertNull(updated.getRejectionReason());
    }

    @Test
    void editingAnAlreadyApprovedJobDoesNotResetModeration() {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setRecruiter(recruiter);
        job.setModerationStatus(JobPosting.MODERATION_APPROVED);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        JobPosting updated = service.updateJobPosting(1L, 10L, "Backend Developer (fixed typo)", "desc",
            "[\"java\"]", "[]", "Remote", "$100k", "Full-time", "Mid", null);

        assertEquals(JobPosting.MODERATION_APPROVED, updated.getModerationStatus());
    }

    @Test
    void moderatingANonExistentJobFails() {
        when(jobPostingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(JobPostingService.JobPostingException.class, () -> service.approveJobPosting(999L));
        assertThrows(JobPostingService.JobPostingException.class, () -> service.rejectJobPosting(999L, "no"));
    }

    @Test
    void publicListingOnlyAsksTheRepositoryForApprovedJobs() {
        service.getActiveJobPostingsSortedByDate();

        org.mockito.Mockito.verify(jobPostingRepository)
            .findByIsActiveTrueAndModerationStatusOrderByFeaturedDescPostedDateDesc(JobPosting.MODERATION_APPROVED);
    }

    @Test
    void aProRecruiterCanFeatureTheirOwnJob() {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        recruiter.setSubscriptionTier(SubscriptionService.PRO_TIER);
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setRecruiter(recruiter);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        JobPosting featured = service.setFeatured(1L, 10L, true);

        assertEquals(true, featured.getFeatured());
    }

    @Test
    void aFreeTierRecruiterCannotFeatureAJob() {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        recruiter.setSubscriptionTier(SubscriptionService.FREE_TIER);
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setRecruiter(recruiter);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));

        assertThrows(JobPostingService.JobPostingException.class, () -> service.setFeatured(1L, 10L, true));
        org.mockito.Mockito.verify(jobPostingRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void settingFeaturedRejectsANonOwningRecruiter() {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setRecruiter(recruiter);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));

        assertThrows(JobPostingService.JobPostingException.class, () -> service.setFeatured(1L, 999L, true));
    }

    @Test
    void unfeaturingDoesNotRequireAProSubscription() {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        recruiter.setSubscriptionTier(SubscriptionService.FREE_TIER);
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setRecruiter(recruiter);
        job.setFeatured(true);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        JobPosting result = service.setFeatured(1L, 10L, false);

        assertEquals(false, result.getFeatured());
    }

    @Test
    void applyingDefaultsToADirectSource() {
        User user = new User();
        user.setId(5L);
        JobPosting job = new JobPosting();
        job.setId(1L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        service.applyForJob(5L, 1L, "cover", "[]", 2, "BS", null);

        org.mockito.Mockito.verify(applicationRepository).save(
            org.mockito.ArgumentMatchers.argThat(a -> Application.SOURCE_DIRECT.equals(a.getSource())));
    }

    @Test
    void applyingHonorsAnExplicitRecognizedSource() {
        User user = new User();
        user.setId(5L);
        JobPosting job = new JobPosting();
        job.setId(1L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        service.applyForJob(5L, 1L, "cover", "[]", 2, "BS", "silver_medalist");

        org.mockito.Mockito.verify(applicationRepository).save(
            org.mockito.ArgumentMatchers.argThat(a -> Application.SOURCE_SILVER_MEDALIST.equals(a.getSource())));
    }

    @Test
    void applyingFallsBackToReferralForAReferredAccountWithNoExplicitSource() {
        User user = new User();
        user.setId(5L);
        JobPosting job = new JobPosting();
        job.setId(1L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
        when(referralRepository.existsByReferredUserId(5L)).thenReturn(true);

        service.applyForJob(5L, 1L, "cover", "[]", 2, "BS", null);

        org.mockito.Mockito.verify(applicationRepository).save(
            org.mockito.ArgumentMatchers.argThat(a -> Application.SOURCE_REFERRAL.equals(a.getSource())));
    }

    @Test
    void applyingIgnoresAnUnrecognizedSourceValue() {
        User user = new User();
        user.setId(5L);
        JobPosting job = new JobPosting();
        job.setId(1L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(job));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        service.applyForJob(5L, 1L, "cover", "[]", 2, "BS", "not_a_real_source");

        org.mockito.Mockito.verify(applicationRepository).save(
            org.mockito.ArgumentMatchers.argThat(a -> Application.SOURCE_DIRECT.equals(a.getSource())));
    }

    @Test
    void pendingQueueAsksTheRepositoryForPendingJobs() {
        service.getPendingJobPostings();

        org.mockito.Mockito.verify(jobPostingRepository)
            .findByModerationStatusOrderByPostedDateDesc(JobPosting.MODERATION_PENDING);
    }
}
