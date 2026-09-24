package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.jobera.service.StripeClient.StripeException;

@ExtendWith(MockitoExtension.class)
class StripeClientTest {

    @Mock RestTemplate restTemplate;

    StripeClient configured;
    StripeClient unconfigured;

    @BeforeEach
    void setUp() {
        configured = new StripeClient(restTemplate, "sk_test_123");
        unconfigured = new StripeClient(restTemplate, "");
    }

    @Test
    void isConfiguredReflectsWhetherASecretKeyIsSet() {
        assertTrue(configured.isConfigured());
        assertFalse(unconfigured.isConfigured());
    }

    @Test
    void createCheckoutSessionSendsBasicAuthAndReturnsTheResponseBody() {
        lenient().when(restTemplate.postForObject(eq("https://api.stripe.com/v1/checkout/sessions"), any(), eq(Map.class)))
            .thenReturn(Map.of("id", "cs_123", "url", "https://checkout.stripe.com/pay/cs_123"));

        Map<String, Object> session = configured.createCheckoutSession(
            "recruiter@example.com", "price_123", "42", "https://app/success", "https://app/cancel");

        assertEquals("https://checkout.stripe.com/pay/cs_123", session.get("url"));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate).postForObject(any(String.class), entityCaptor.capture(), eq(Map.class));
        String authHeader = entityCaptor.getValue().getHeaders().getFirst("Authorization");
        assertTrue(authHeader.startsWith("Basic "));
    }

    @Test
    void aNetworkFailureCreatingACheckoutSessionIsWrappedInStripeException() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenThrow(new RestClientException("timeout"));

        assertThrows(StripeException.class, () ->
            configured.createCheckoutSession("a@b.com", "price_1", "1", "https://s", "https://c"));
    }

    @Test
    void anEmptyCheckoutResponseFails() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class))).thenReturn(null);

        assertThrows(StripeException.class, () ->
            configured.createCheckoutSession("a@b.com", "price_1", "1", "https://s", "https://c"));
    }

    @Test
    void cancelSubscriptionCallsDeleteAndReturnsTheResponseBody() {
        lenient().when(restTemplate.exchange(eq("https://api.stripe.com/v1/subscriptions/sub_123"),
                eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("id", "sub_123", "status", "canceled")));

        Map<String, Object> result = configured.cancelSubscription("sub_123");

        assertEquals("canceled", result.get("status"));
    }

    @Test
    void aNetworkFailureCancellingIsWrappedInStripeException() {
        lenient().when(restTemplate.exchange(any(String.class), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Map.class)))
            .thenThrow(new RestClientException("boom"));

        assertThrows(StripeException.class, () -> configured.cancelSubscription("sub_123"));
    }
}
