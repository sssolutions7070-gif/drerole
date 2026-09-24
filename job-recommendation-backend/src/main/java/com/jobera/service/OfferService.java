package com.jobera.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jobera.entity.Application;
import com.jobera.entity.JobOffer;
import com.jobera.entity.Webhook;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobOfferRepository;

/** A job offer for one application: sent by the recruiter, then accepted (with a typed-name,
 *  IP-stamped click-to-sign) or declined by the candidate. Deliberately not integrating a real
 *  e-signature provider (DocuSign etc.) - a recorded name + IP + timestamp is the same level of
 *  assurance most small applications use for this, without adding an external dependency. */
@Service
public class OfferService {

    public static class ApplicationNotFoundException extends RuntimeException {
        public ApplicationNotFoundException(Long applicationId) {
            super("Application not found: " + applicationId);
        }
    }

    public static class OfferNotFoundException extends RuntimeException {
        public OfferNotFoundException(Long applicationId) {
            super("No offer has been sent for application " + applicationId);
        }
    }

    public static class OfferAlreadyAcceptedException extends RuntimeException {
        public OfferAlreadyAcceptedException(Long applicationId) {
            super("This offer has already been accepted and can no longer be changed");
        }
    }

    public static class OfferNotPendingException extends RuntimeException {
        public OfferNotPendingException() {
            super("This offer is no longer pending a response");
        }
    }

    public static class NotTheCandidateException extends RuntimeException {
        public NotTheCandidateException() {
            super("Only the candidate this offer was sent to can respond to it");
        }
    }

    private final JobOfferRepository jobOfferRepository;
    private final ApplicationRepository applicationRepository;
    private final RealtimeNotifier realtimeNotifier;
    private final NotificationFeedService notificationFeedService;
    private final WebhookService webhookService;

    public OfferService(JobOfferRepository jobOfferRepository, ApplicationRepository applicationRepository,
                         RealtimeNotifier realtimeNotifier, NotificationFeedService notificationFeedService,
                         WebhookService webhookService) {
        this.jobOfferRepository = jobOfferRepository;
        this.applicationRepository = applicationRepository;
        this.realtimeNotifier = realtimeNotifier;
        this.notificationFeedService = notificationFeedService;
        this.webhookService = webhookService;
    }

    private Long candidateUserId(Application application) {
        return application.getUser() != null ? application.getUser().getId() : null;
    }

    private Long recruiterUserId(Application application) {
        if (application.getJobPosting() == null || application.getJobPosting().getRecruiter() == null) {
            return null;
        }
        return Optional.ofNullable(application.getJobPosting().getRecruiter().getUser())
            .map(u -> u.getId())
            .orElse(null);
    }

    private Application requireApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
    }

    public JobOffer sendOffer(Long applicationId, String title, String salaryOffered, LocalDate startDate,
                               String additionalTerms) {
        Application application = requireApplication(applicationId);
        JobOffer offer = jobOfferRepository.findByApplicationId(applicationId).orElseGet(JobOffer::new);
        if ("ACCEPTED".equals(offer.getStatus())) {
            throw new OfferAlreadyAcceptedException(applicationId);
        }

        offer.setApplicationId(applicationId);
        offer.setTitle(title);
        offer.setSalaryOffered(salaryOffered);
        offer.setStartDate(startDate);
        offer.setAdditionalTerms(additionalTerms);
        offer.setStatus("SENT");
        offer.setCandidateSignatureName(null);
        offer.setCandidateSignatureIp(null);
        offer.setSignedAt(null);
        offer.setDeclineReason(null);
        offer.setUpdatedAt(LocalDateTime.now());
        JobOffer saved = jobOfferRepository.save(offer);

        application.sendOffer();
        applicationRepository.save(application);

        realtimeNotifier.notifyUser(candidateUserId(application), "OFFER_SENT", Map.of(
            "applicationId", applicationId, "title", title));
        notificationFeedService.record(candidateUserId(application), "OFFER_SENT",
            "You've received an offer: " + title,
            "Review the offer details and respond.", "/dashboard");

        return saved;
    }

    public JobOffer getOffer(Long applicationId) {
        return jobOfferRepository.findByApplicationId(applicationId)
            .orElseThrow(() -> new OfferNotFoundException(applicationId));
    }

    public JobOffer acceptOffer(Long applicationId, Long callerId, String signatureName, String signatureIp) {
        Application application = requireApplication(applicationId);
        if (!callerId.equals(candidateUserId(application))) {
            throw new NotTheCandidateException();
        }
        JobOffer offer = getOffer(applicationId);
        if (!"SENT".equals(offer.getStatus())) {
            throw new OfferNotPendingException();
        }

        offer.setStatus("ACCEPTED");
        offer.setCandidateSignatureName(signatureName);
        offer.setCandidateSignatureIp(signatureIp);
        offer.setSignedAt(LocalDateTime.now());
        JobOffer saved = jobOfferRepository.save(offer);

        application.setStatus("OFFER_ACCEPTED");
        applicationRepository.save(application);

        realtimeNotifier.notifyUser(recruiterUserId(application), "OFFER_ACCEPTED", Map.of(
            "applicationId", applicationId, "signedBy", signatureName));
        notificationFeedService.record(recruiterUserId(application), "OFFER_ACCEPTED",
            "Offer accepted",
            signatureName + " signed and accepted the offer.", "/recruiter/dashboard");
        if (application.getJobPosting() != null && application.getJobPosting().getRecruiter() != null) {
            webhookService.dispatch(application.getJobPosting().getRecruiter().getId(), Webhook.EVENT_OFFER_ACCEPTED, Map.of(
                "applicationId", applicationId, "signedBy", signatureName));
        }

        return saved;
    }

    public JobOffer declineOffer(Long applicationId, Long callerId, String reason) {
        Application application = requireApplication(applicationId);
        if (!callerId.equals(candidateUserId(application))) {
            throw new NotTheCandidateException();
        }
        JobOffer offer = getOffer(applicationId);
        if (!"SENT".equals(offer.getStatus())) {
            throw new OfferNotPendingException();
        }

        offer.setStatus("DECLINED");
        offer.setDeclineReason(reason);
        JobOffer saved = jobOfferRepository.save(offer);

        application.setStatus("OFFER_DECLINED");
        applicationRepository.save(application);

        realtimeNotifier.notifyUser(recruiterUserId(application), "OFFER_DECLINED", Map.of(
            "applicationId", applicationId));
        notificationFeedService.record(recruiterUserId(application), "OFFER_DECLINED",
            "Offer declined",
            "The candidate declined the offer.", "/recruiter/dashboard");

        return saved;
    }

    public JobOffer withdrawOffer(Long applicationId) {
        JobOffer offer = getOffer(applicationId);
        if ("ACCEPTED".equals(offer.getStatus())) {
            throw new OfferAlreadyAcceptedException(applicationId);
        }
        offer.setStatus("WITHDRAWN");
        JobOffer saved = jobOfferRepository.save(offer);

        applicationRepository.findById(applicationId).ifPresent(application -> {
            realtimeNotifier.notifyUser(candidateUserId(application), "OFFER_WITHDRAWN", Map.of(
                "applicationId", applicationId));
            notificationFeedService.record(candidateUserId(application), "OFFER_WITHDRAWN",
                "Offer withdrawn",
                "The recruiter withdrew the offer.", "/dashboard");
        });

        return saved;
    }
}
