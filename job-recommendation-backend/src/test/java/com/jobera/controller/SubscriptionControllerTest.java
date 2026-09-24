package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.service.StripeClient;
import com.jobera.service.SubscriptionService;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock SubscriptionService subscriptionService;

    SubscriptionController controller;

    @BeforeEach
    void setUp() {
        controller = new SubscriptionController(subscriptionService);
    }

    @Test
    void getStatusReturnsWhatTheServiceProvides() {
        Map<String, Object> status = Map.of("tier", "FREE");
        when(subscriptionService.getStatus(42L)).thenReturn(status);

        ResponseEntity<?> response = controller.getStatus(42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(status, response.getBody());
    }

    @Test
    void getStatusForAMissingRecruiterReturnsBadRequest() {
        when(subscriptionService.getStatus(42L)).thenThrow(new SubscriptionService.RecruiterNotFoundException(42L));

        ResponseEntity<?> response = controller.getStatus(42L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void checkoutReturnsTheUrl() {
        when(subscriptionService.createCheckoutSession(42L)).thenReturn("https://checkout.stripe.com/pay/cs_1");

        ResponseEntity<?> response = controller.checkout(42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("url", "https://checkout.stripe.com/pay/cs_1"), response.getBody());
    }

    @Test
    void checkoutWhenNotConfiguredReturnsBadRequest() {
        when(subscriptionService.createCheckoutSession(42L)).thenThrow(new SubscriptionService.NotConfiguredException());

        ResponseEntity<?> response = controller.checkout(42L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void checkoutWrapsAStripeFailureAsBadRequest() {
        when(subscriptionService.createCheckoutSession(42L)).thenThrow(new StripeClient.StripeException("Failed to reach Stripe"));

        ResponseEntity<?> response = controller.checkout(42L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void cancelSucceeds() {
        ResponseEntity<?> response = controller.cancel(42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void cancelWithNoActiveSubscriptionReturnsBadRequest() {
        doThrow(new SubscriptionService.NoActiveSubscriptionException(42L)).when(subscriptionService).cancelSubscription(42L);

        ResponseEntity<?> response = controller.cancel(42L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void webhookWithAValidSignatureIsAcknowledged() {
        ResponseEntity<?> response = controller.stripeWebhook("valid-sig", "{}");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void webhookWithAnInvalidSignatureIsRejected() {
        doThrow(new SubscriptionService.InvalidWebhookSignatureException())
            .when(subscriptionService).handleWebhook("{}", "bad-sig");

        ResponseEntity<?> response = controller.stripeWebhook("bad-sig", "{}");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
