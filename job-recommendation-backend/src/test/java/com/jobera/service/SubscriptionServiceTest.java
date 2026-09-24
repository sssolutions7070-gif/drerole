package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.util.StripeSignatureVerifier;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock RecruiterRepository recruiterRepository;
    @Mock JobPostingRepository jobPostingRepository;
    @Mock StripeClient stripeClient;
    @Mock StripeSignatureVerifier signatureVerifier;

    SubscriptionService service;
    Recruiter recruiter;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(recruiterRepository, jobPostingRepository, stripeClient, signatureVerifier,
            "price_pro", "whsec_test", "http://localhost:3000", 3);

        User user = new User("recruiter@example.com", "hash", "Recruiter Person");
        user.setId(5L);
        recruiter = new Recruiter(user, "Acme Corp");
        recruiter.setId(42L);
    }

    @Test
    void statusReportsTierAndActiveJobCount() {
        when(recruiterRepository.findById(42L)).thenReturn(Optional.of(recruiter));
        when(jobPostingRepository.countByRecruiterIdAndIsActiveTrue(42L)).thenReturn(2L);
        lenient().when(stripeClient.isConfigured()).thenReturn(true);

        Map<String, Object> status = service.getStatus(42L);

        assertEquals("FREE", status.get("tier"));
        assertEquals(2L, status.get("activeJobCount"));
        assertEquals(3, status.get("freeTierJobLimit"));
    }

    @Test
    void statusForAMissingRecruiterFails() {
        when(recruiterRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(SubscriptionService.RecruiterNotFoundException.class, () -> service.getStatus(42L));
    }

    @Test
    void checkoutFailsWhenStripeIsNotConfigured() {
        when(stripeClient.isConfigured()).thenReturn(false);

        assertThrows(SubscriptionService.NotConfiguredException.class, () -> service.createCheckoutSession(42L));
        verify(recruiterRepository, never()).findById(any());
    }

    @Test
    void checkoutFailsWhenNoProPriceIdIsConfigured() {
        SubscriptionService noPriceConfigured = new SubscriptionService(recruiterRepository, jobPostingRepository,
            stripeClient, signatureVerifier, "", "whsec_test", "http://localhost:3000", 3);
        when(stripeClient.isConfigured()).thenReturn(true);

        assertThrows(SubscriptionService.NotConfiguredException.class, () -> noPriceConfigured.createCheckoutSession(42L));
    }

    @Test
    void checkoutReturnsTheSessionUrl() {
        when(stripeClient.isConfigured()).thenReturn(true);
        when(recruiterRepository.findById(42L)).thenReturn(Optional.of(recruiter));
        when(stripeClient.createCheckoutSession(eq("recruiter@example.com"), eq("price_pro"), eq("42"), anyString(), anyString()))
            .thenReturn(Map.of("id", "cs_1", "url", "https://checkout.stripe.com/pay/cs_1"));

        String url = service.createCheckoutSession(42L);

        assertEquals("https://checkout.stripe.com/pay/cs_1", url);
    }

    @Test
    void checkoutFailsWhenStripeDoesNotReturnAUrl() {
        when(stripeClient.isConfigured()).thenReturn(true);
        when(recruiterRepository.findById(42L)).thenReturn(Optional.of(recruiter));
        when(stripeClient.createCheckoutSession(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Map.of("id", "cs_1"));

        assertThrows(StripeClient.StripeException.class, () -> service.createCheckoutSession(42L));
    }

    @Test
    void cancellingAnActiveSubscriptionDowngradesToFree() {
        recruiter.setSubscriptionTier("PRO");
        recruiter.setSubscriptionStatus("ACTIVE");
        recruiter.setStripeSubscriptionId("sub_1");
        when(recruiterRepository.findById(42L)).thenReturn(Optional.of(recruiter));
        when(stripeClient.cancelSubscription("sub_1")).thenReturn(Map.of("status", "canceled"));

        service.cancelSubscription(42L);

        assertEquals("FREE", recruiter.getSubscriptionTier());
        assertEquals("CANCELED", recruiter.getSubscriptionStatus());
        verify(recruiterRepository).save(recruiter);
    }

    @Test
    void cancellingWithNoActiveSubscriptionFails() {
        when(recruiterRepository.findById(42L)).thenReturn(Optional.of(recruiter));

        assertThrows(SubscriptionService.NoActiveSubscriptionException.class, () -> service.cancelSubscription(42L));
        verify(stripeClient, never()).cancelSubscription(any());
    }

    @Test
    void anInvalidWebhookSignatureIsRejected() {
        when(signatureVerifier.isValid("{}", "bad-sig", "whsec_test")).thenReturn(false);

        assertThrows(SubscriptionService.InvalidWebhookSignatureException.class, () -> service.handleWebhook("{}", "bad-sig"));
        verify(recruiterRepository, never()).save(any());
    }

    @Test
    void checkoutCompletedActivatesProForTheReferencedRecruiter() {
        String payload = "{\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
            + "\"client_reference_id\":\"42\",\"customer\":\"cus_1\",\"subscription\":\"sub_1\"}}}";
        when(signatureVerifier.isValid(payload, "sig", "whsec_test")).thenReturn(true);
        when(recruiterRepository.findById(42L)).thenReturn(Optional.of(recruiter));

        service.handleWebhook(payload, "sig");

        assertEquals("PRO", recruiter.getSubscriptionTier());
        assertEquals("ACTIVE", recruiter.getSubscriptionStatus());
        assertEquals("cus_1", recruiter.getStripeCustomerId());
        assertEquals("sub_1", recruiter.getStripeSubscriptionId());
        verify(recruiterRepository).save(recruiter);
    }

    @Test
    void checkoutCompletedForAnUnknownRecruiterIsSilentlyIgnored() {
        String payload = "{\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
            + "\"client_reference_id\":\"999\",\"customer\":\"cus_1\",\"subscription\":\"sub_1\"}}}";
        when(signatureVerifier.isValid(payload, "sig", "whsec_test")).thenReturn(true);
        when(recruiterRepository.findById(999L)).thenReturn(Optional.empty());

        service.handleWebhook(payload, "sig");

        verify(recruiterRepository, never()).save(any());
    }

    @Test
    void subscriptionUpdatedRefreshesStatus() {
        recruiter.setStripeSubscriptionId("sub_1");
        recruiter.setSubscriptionTier("PRO");
        String payload = "{\"type\":\"customer.subscription.updated\",\"data\":{\"object\":{"
            + "\"id\":\"sub_1\",\"status\":\"past_due\"}}}";
        when(signatureVerifier.isValid(payload, "sig", "whsec_test")).thenReturn(true);
        when(recruiterRepository.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(recruiter));

        service.handleWebhook(payload, "sig");

        assertEquals("PAST_DUE", recruiter.getSubscriptionStatus());
        assertEquals("PRO", recruiter.getSubscriptionTier());
    }

    @Test
    void subscriptionUpdatedToCanceledDowngradesTheTier() {
        recruiter.setStripeSubscriptionId("sub_1");
        recruiter.setSubscriptionTier("PRO");
        String payload = "{\"type\":\"customer.subscription.updated\",\"data\":{\"object\":{"
            + "\"id\":\"sub_1\",\"status\":\"canceled\"}}}";
        when(signatureVerifier.isValid(payload, "sig", "whsec_test")).thenReturn(true);
        when(recruiterRepository.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(recruiter));

        service.handleWebhook(payload, "sig");

        assertEquals("FREE", recruiter.getSubscriptionTier());
    }

    @Test
    void subscriptionDeletedResetsToFree() {
        recruiter.setStripeSubscriptionId("sub_1");
        recruiter.setSubscriptionTier("PRO");
        recruiter.setSubscriptionStatus("ACTIVE");
        String payload = "{\"type\":\"customer.subscription.deleted\",\"data\":{\"object\":{\"id\":\"sub_1\"}}}";
        when(signatureVerifier.isValid(payload, "sig", "whsec_test")).thenReturn(true);
        when(recruiterRepository.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(recruiter));

        service.handleWebhook(payload, "sig");

        assertEquals("FREE", recruiter.getSubscriptionTier());
        assertEquals("CANCELED", recruiter.getSubscriptionStatus());
        assertEquals(null, recruiter.getStripeSubscriptionId());
    }

    @Test
    void anUnrecognizedEventTypeIsIgnoredWithoutError() {
        String payload = "{\"type\":\"invoice.payment_failed\",\"data\":{\"object\":{}}}";
        when(signatureVerifier.isValid(payload, "sig", "whsec_test")).thenReturn(true);

        service.handleWebhook(payload, "sig");

        verify(recruiterRepository, never()).save(any());
    }

    @Test
    void freeTierRecruiterUnderTheLimitCanPostAJob() {
        when(jobPostingRepository.countByRecruiterIdAndIsActiveTrue(42L)).thenReturn(2L);

        service.enforceActiveJobLimit(recruiter);
    }

    @Test
    void freeTierRecruiterAtTheLimitIsBlocked() {
        when(jobPostingRepository.countByRecruiterIdAndIsActiveTrue(42L)).thenReturn(3L);

        assertThrows(SubscriptionService.SubscriptionLimitException.class, () -> service.enforceActiveJobLimit(recruiter));
    }

    @Test
    void proTierRecruiterHasNoLimit() {
        recruiter.setSubscriptionTier("PRO");

        service.enforceActiveJobLimit(recruiter);

        verify(jobPostingRepository, never()).countByRecruiterIdAndIsActiveTrue(any());
    }
}
