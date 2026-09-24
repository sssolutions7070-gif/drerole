package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.JobOffer;
import com.jobera.entity.JobPosting;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobOfferRepository;

@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    @Mock JobOfferRepository jobOfferRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock RealtimeNotifier realtimeNotifier;

    OfferService service;

    User candidate;
    User recruiterUser;
    Application application;

    @BeforeEach
    void setUp() {
        service = new OfferService(jobOfferRepository, applicationRepository, realtimeNotifier);

        candidate = new User("candidate@example.com", "hash", "Candidate");
        candidate.setId(1L);
        recruiterUser = new User("recruiter@example.com", "hash", "Recruiter");
        recruiterUser.setId(2L);

        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        recruiter.setUser(recruiterUser);

        JobPosting job = new JobPosting();
        job.setRecruiter(recruiter);

        application = new Application();
        application.setId(1000L);
        application.setUser(candidate);
        application.setJobPosting(job);

        lenient().when(applicationRepository.findById(1000L)).thenReturn(Optional.of(application));
        lenient().when(jobOfferRepository.save(any(JobOffer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sendingAnOfferMarksTheApplicationAndNotifiesTheCandidate() {
        when(jobOfferRepository.findByApplicationId(1000L)).thenReturn(Optional.empty());

        JobOffer offer = service.sendOffer(1000L, "Backend Engineer", "$120,000/year", LocalDate.of(2026, 6, 1), "Equity: 0.1%");

        assertEquals("SENT", offer.getStatus());
        assertEquals("Backend Engineer", offer.getTitle());
        assertEquals("OFFER_SENT", application.getStatus());
        verify(realtimeNotifier).notifyUser(eq(1L), eq("OFFER_SENT"), any());
    }

    @Test
    void sendingAnOfferForAMissingApplicationFails() {
        when(applicationRepository.findById(1000L)).thenReturn(Optional.empty());

        assertThrows(OfferService.ApplicationNotFoundException.class, () ->
            service.sendOffer(1000L, "Title", "$1", LocalDate.now(), null));
    }

    @Test
    void resendingAnAcceptedOfferFails() {
        JobOffer existing = new JobOffer();
        existing.setStatus("ACCEPTED");
        when(jobOfferRepository.findByApplicationId(1000L)).thenReturn(Optional.of(existing));

        assertThrows(OfferService.OfferAlreadyAcceptedException.class, () ->
            service.sendOffer(1000L, "Title", "$1", LocalDate.now(), null));
    }

    @Test
    void gettingAMissingOfferFails() {
        when(jobOfferRepository.findByApplicationId(1000L)).thenReturn(Optional.empty());

        assertThrows(OfferService.OfferNotFoundException.class, () -> service.getOffer(1000L));
    }

    @Test
    void theCandidateCanAcceptAPendingOffer() {
        JobOffer offer = new JobOffer();
        offer.setApplicationId(1000L);
        offer.setStatus("SENT");
        when(jobOfferRepository.findByApplicationId(1000L)).thenReturn(Optional.of(offer));

        JobOffer accepted = service.acceptOffer(1000L, candidate.getId(), "Candidate Person", "1.2.3.4");

        assertEquals("ACCEPTED", accepted.getStatus());
        assertEquals("Candidate Person", accepted.getCandidateSignatureName());
        assertEquals("1.2.3.4", accepted.getCandidateSignatureIp());
        assertNotNull(accepted.getSignedAt());
        assertEquals("OFFER_ACCEPTED", application.getStatus());
        verify(realtimeNotifier).notifyUser(eq(2L), eq("OFFER_ACCEPTED"), any());
    }

    @Test
    void theRecruiterCannotAcceptTheirOwnOffer() {
        assertThrows(OfferService.NotTheCandidateException.class, () ->
            service.acceptOffer(1000L, recruiterUser.getId(), "Recruiter Person", "1.2.3.4"));
    }

    @Test
    void acceptingAnAlreadyAcceptedOfferFails() {
        JobOffer offer = new JobOffer();
        offer.setStatus("ACCEPTED");
        when(jobOfferRepository.findByApplicationId(1000L)).thenReturn(Optional.of(offer));

        assertThrows(OfferService.OfferNotPendingException.class, () ->
            service.acceptOffer(1000L, candidate.getId(), "Candidate Person", "1.2.3.4"));
    }

    @Test
    void theCandidateCanDeclineAPendingOffer() {
        JobOffer offer = new JobOffer();
        offer.setStatus("SENT");
        when(jobOfferRepository.findByApplicationId(1000L)).thenReturn(Optional.of(offer));

        JobOffer declined = service.declineOffer(1000L, candidate.getId(), "Accepted another offer");

        assertEquals("DECLINED", declined.getStatus());
        assertEquals("Accepted another offer", declined.getDeclineReason());
        assertEquals("OFFER_DECLINED", application.getStatus());
        verify(realtimeNotifier).notifyUser(eq(2L), eq("OFFER_DECLINED"), any());
    }

    @Test
    void theRecruiterCannotDeclineTheOfferTheySent() {
        assertThrows(OfferService.NotTheCandidateException.class, () ->
            service.declineOffer(1000L, recruiterUser.getId(), "reason"));
    }

    @Test
    void withdrawingAPendingOfferSucceeds() {
        JobOffer offer = new JobOffer();
        offer.setApplicationId(1000L);
        offer.setStatus("SENT");
        when(jobOfferRepository.findByApplicationId(1000L)).thenReturn(Optional.of(offer));

        JobOffer withdrawn = service.withdrawOffer(1000L);

        assertEquals("WITHDRAWN", withdrawn.getStatus());
        verify(realtimeNotifier).notifyUser(eq(1L), eq("OFFER_WITHDRAWN"), any());
    }

    @Test
    void withdrawingAnAcceptedOfferFails() {
        JobOffer offer = new JobOffer();
        offer.setStatus("ACCEPTED");
        when(jobOfferRepository.findByApplicationId(1000L)).thenReturn(Optional.of(offer));

        assertThrows(OfferService.OfferAlreadyAcceptedException.class, () -> service.withdrawOffer(1000L));
    }
}
