package com.jobera.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobera.entity.Recruiter;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.util.StripeSignatureVerifier;

/**
 * Recruiter subscription tiers, backed by Stripe Checkout + webhooks for the paid tier. FREE is
 * the default for every recruiter and needs no Stripe involvement; upgrading opens a
 * Stripe-hosted Checkout page, and Stripe's webhook (not the browser redirect back) is the
 * source of truth for when the subscription actually becomes active, same as any standard
 * Stripe integration - the customer can close the tab before the redirect fires, but the webhook
 * always arrives.
 */
@Service
public class SubscriptionService {

    public static final String FREE_TIER = "FREE";
    public static final String PRO_TIER = "PRO";

    public static class RecruiterNotFoundException extends RuntimeException {
        public RecruiterNotFoundException(Long recruiterId) {
            super("Recruiter not found: " + recruiterId);
        }
    }

    public static class NotConfiguredException extends RuntimeException {
        public NotConfiguredException() {
            super("Subscriptions are not configured on this server");
        }
    }

    public static class NoActiveSubscriptionException extends RuntimeException {
        public NoActiveSubscriptionException(Long recruiterId) {
            super("Recruiter " + recruiterId + " has no active subscription to cancel");
        }
    }

    public static class InvalidWebhookSignatureException extends RuntimeException {
        public InvalidWebhookSignatureException() {
            super("Invalid Stripe webhook signature");
        }
    }

    public static class SubscriptionLimitException extends RuntimeException {
        public SubscriptionLimitException(int limit) {
            super("The free plan is limited to " + limit + " active job posting" + (limit == 1 ? "" : "s")
                + " - upgrade to Pro to post more");
        }
    }

    private final RecruiterRepository recruiterRepository;
    private final JobPostingRepository jobPostingRepository;
    private final StripeClient stripeClient;
    private final StripeSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String priceIdPro;
    private final String webhookSecret;
    private final String frontendUrl;
    private final int freeTierJobLimit;

    public SubscriptionService(RecruiterRepository recruiterRepository,
                                JobPostingRepository jobPostingRepository,
                                StripeClient stripeClient,
                                StripeSignatureVerifier signatureVerifier,
                                @Value("${app.stripe.price-id-pro:}") String priceIdPro,
                                @Value("${app.stripe.webhook-secret:}") String webhookSecret,
                                @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
                                @Value("${app.subscription.free-tier-job-limit:3}") int freeTierJobLimit) {
        this.recruiterRepository = recruiterRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.stripeClient = stripeClient;
        this.signatureVerifier = signatureVerifier;
        this.priceIdPro = priceIdPro;
        this.webhookSecret = webhookSecret;
        this.frontendUrl = frontendUrl;
        this.freeTierJobLimit = freeTierJobLimit;
    }

    private Recruiter requireRecruiter(Long recruiterId) {
        return recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new RecruiterNotFoundException(recruiterId));
    }

    public Map<String, Object> getStatus(Long recruiterId) {
        Recruiter recruiter = requireRecruiter(recruiterId);
        long activeJobCount = jobPostingRepository.countByRecruiterIdAndIsActiveTrue(recruiterId);

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("tier", recruiter.getSubscriptionTier());
        status.put("status", recruiter.getSubscriptionStatus());
        status.put("activeJobCount", activeJobCount);
        status.put("freeTierJobLimit", freeTierJobLimit);
        status.put("configured", stripeClient.isConfigured());
        return status;
    }

    public String createCheckoutSession(Long recruiterId) {
        if (!stripeClient.isConfigured() || priceIdPro.isBlank()) {
            throw new NotConfiguredException();
        }
        Recruiter recruiter = requireRecruiter(recruiterId);
        String successUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/recruiter/dashboard")
            .queryParam("subscription", "success").build().encode().toUriString();
        String cancelUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/recruiter/dashboard")
            .queryParam("subscription", "cancelled").build().encode().toUriString();

        Map<String, Object> session = stripeClient.createCheckoutSession(
            recruiter.getUser().getEmail(), priceIdPro, String.valueOf(recruiterId), successUrl, cancelUrl);
        Object url = session.get("url");
        if (url == null) {
            throw new StripeClient.StripeException("Stripe did not return a checkout URL");
        }
        return url.toString();
    }

    public void cancelSubscription(Long recruiterId) {
        Recruiter recruiter = requireRecruiter(recruiterId);
        if (recruiter.getStripeSubscriptionId() == null) {
            throw new NoActiveSubscriptionException(recruiterId);
        }
        stripeClient.cancelSubscription(recruiter.getStripeSubscriptionId());
        recruiter.setSubscriptionTier(FREE_TIER);
        recruiter.setSubscriptionStatus("CANCELED");
        recruiterRepository.save(recruiter);
    }

    /** Verifies and applies a Stripe webhook event. Unrecognized event types are ignored - Stripe
     *  sends many more events than this integration needs to react to. */
    public void handleWebhook(String payload, String signatureHeader) {
        if (!signatureVerifier.isValid(payload, signatureHeader, webhookSecret)) {
            throw new InvalidWebhookSignatureException();
        }

        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed webhook payload");
        }

        String type = event.path("type").asText("");
        JsonNode object = event.path("data").path("object");

        switch (type) {
            case "checkout.session.completed" -> onCheckoutCompleted(object);
            case "customer.subscription.updated" -> onSubscriptionUpdated(object);
            case "customer.subscription.deleted" -> onSubscriptionDeleted(object);
            default -> { /* not an event this integration reacts to */ }
        }
    }

    private void onCheckoutCompleted(JsonNode session) {
        String recruiterIdRaw = session.path("client_reference_id").asText(null);
        if (recruiterIdRaw == null) {
            return;
        }
        Optional<Recruiter> recruiterOpt = recruiterRepository.findById(Long.valueOf(recruiterIdRaw));
        if (recruiterOpt.isEmpty()) {
            return;
        }
        Recruiter recruiter = recruiterOpt.get();
        recruiter.setStripeCustomerId(session.path("customer").asText(null));
        recruiter.setStripeSubscriptionId(session.path("subscription").asText(null));
        recruiter.setSubscriptionTier(PRO_TIER);
        recruiter.setSubscriptionStatus("ACTIVE");
        recruiterRepository.save(recruiter);
    }

    private void onSubscriptionUpdated(JsonNode subscription) {
        String subscriptionId = subscription.path("id").asText(null);
        String status = subscription.path("status").asText(null);
        recruiterRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(recruiter -> {
            recruiter.setSubscriptionStatus(status == null ? null : status.toUpperCase());
            recruiter.setSubscriptionTier("canceled".equalsIgnoreCase(status) ? FREE_TIER : PRO_TIER);
            recruiterRepository.save(recruiter);
        });
    }

    private void onSubscriptionDeleted(JsonNode subscription) {
        String subscriptionId = subscription.path("id").asText(null);
        recruiterRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(recruiter -> {
            recruiter.setSubscriptionTier(FREE_TIER);
            recruiter.setSubscriptionStatus("CANCELED");
            recruiter.setStripeSubscriptionId(null);
            recruiterRepository.save(recruiter);
        });
    }

    /** Called before creating a new job posting - FREE tier recruiters are capped at a small
     *  number of simultaneously active postings; PRO (or any other paid tier) is unlimited. */
    public void enforceActiveJobLimit(Recruiter recruiter) {
        if (!FREE_TIER.equals(recruiter.getSubscriptionTier())) {
            return;
        }
        long activeCount = jobPostingRepository.countByRecruiterIdAndIsActiveTrue(recruiter.getId());
        if (activeCount >= freeTierJobLimit) {
            throw new SubscriptionLimitException(freeTierJobLimit);
        }
    }
}
