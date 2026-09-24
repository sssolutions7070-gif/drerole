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

import com.jobera.entity.JobPosting;
import com.jobera.entity.Recruiter;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.RecruiterRepository;
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

    JobPostingService service;

    @BeforeEach
    void setUp() {
        service = new JobPostingService(jobPostingRepository, applicationRepository, userRepository,
            recruiterRepository, savedJobRepository, subscriptionService);
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
            .findByIsActiveTrueAndModerationStatusOrderByPostedDateDesc(JobPosting.MODERATION_APPROVED);
    }

    @Test
    void pendingQueueAsksTheRepositoryForPendingJobs() {
        service.getPendingJobPostings();

        org.mockito.Mockito.verify(jobPostingRepository)
            .findByModerationStatusOrderByPostedDateDesc(JobPosting.MODERATION_PENDING);
    }
}
