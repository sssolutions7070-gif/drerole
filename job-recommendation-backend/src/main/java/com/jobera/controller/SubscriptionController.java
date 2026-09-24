package com.jobera.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.service.StripeClient;
import com.jobera.service.SubscriptionService;

/** Recruiter subscription tiers: check status, start a Stripe Checkout upgrade, cancel, and the
 *  webhook Stripe itself calls back on. The first three are scoped to one recruiter via
 *  AccessPolicy's ownRecruiterId() check; the webhook is public but signature-verified instead. */
@RestController
public class SubscriptionController {

    private static final String ERROR_KEY = "error";

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/api/recruiters/{recruiterId}/subscription")
    public ResponseEntity<?> getStatus(@PathVariable Long recruiterId) {
        try {
            return ResponseEntity.ok(subscriptionService.getStatus(recruiterId));
        } catch (SubscriptionService.RecruiterNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping("/api/recruiters/{recruiterId}/subscription/checkout")
    public ResponseEntity<?> checkout(@PathVariable Long recruiterId) {
        try {
            return ResponseEntity.ok(Map.of("url", subscriptionService.createCheckoutSession(recruiterId)));
        } catch (SubscriptionService.RecruiterNotFoundException | SubscriptionService.NotConfiguredException
                 | StripeClient.StripeException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping("/api/recruiters/{recruiterId}/subscription/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long recruiterId) {
        try {
            subscriptionService.cancelSubscription(recruiterId);
            return ResponseEntity.ok(Map.of("message", "Subscription cancelled"));
        } catch (SubscriptionService.RecruiterNotFoundException | SubscriptionService.NoActiveSubscriptionException
                 | StripeClient.StripeException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping("/api/webhooks/stripe")
    public ResponseEntity<?> stripeWebhook(@RequestHeader(value = "Stripe-Signature", required = false) String signature,
                                            @RequestBody String payload) {
        try {
            subscriptionService.handleWebhook(payload, signature);
            return ResponseEntity.ok(Map.of("received", true));
        } catch (SubscriptionService.InvalidWebhookSignatureException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
