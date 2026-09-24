package com.jobera.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Talks to Stripe's REST API directly with RestTemplate rather than adding the stripe-java SDK -
 * only two calls are needed (create a Checkout Session, cancel a subscription), both plain
 * form-encoded POSTs authenticated with HTTP Basic auth using the secret key as the username and
 * no password, exactly as Stripe's API docs describe: https://stripe.com/docs/api/authentication.
 */
@Service
public class StripeClient {

    public static class StripeException extends RuntimeException {
        public StripeException(String message) {
            super(message);
        }
    }

    private static final String API_BASE = "https://api.stripe.com/v1";

    private final RestTemplate restTemplate;
    private final String secretKey;

    public StripeClient(RestTemplate restTemplate, @Value("${app.stripe.secret-key:}") String secretKey) {
        this.restTemplate = restTemplate;
        this.secretKey = secretKey;
    }

    public boolean isConfigured() {
        return !secretKey.isBlank();
    }

    /** Creates a Stripe-hosted Checkout Session for a new subscription and returns its full response body (the caller reads "url" to redirect to, "id" for logging, etc). */
    public Map<String, Object> createCheckoutSession(String customerEmail, String priceId, String clientReferenceId,
                                                       String successUrl, String cancelUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("customer_email", customerEmail);
        form.add("client_reference_id", clientReferenceId);
        form.add("line_items[0][price]", priceId);
        form.add("line_items[0][quantity]", "1");
        form.add("success_url", successUrl);
        form.add("cancel_url", cancelUrl);
        return post(API_BASE + "/checkout/sessions", form);
    }

    /** Cancels a subscription immediately (not at period end) and returns Stripe's response body. */
    public Map<String, Object> cancelSubscription(String subscriptionId) {
        HttpHeaders headers = authHeaders();
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.exchange(API_BASE + "/subscriptions/" + subscriptionId,
                HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new StripeException("Stripe returned an empty response");
            }
            return body;
        } catch (RestClientException e) {
            throw new StripeException("Failed to cancel the subscription with Stripe");
        }
    }

    private Map<String, Object> post(String url, MultiValueMap<String, String> form) {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(url, new HttpEntity<>(form, headers), Map.class);
            if (body == null) {
                throw new StripeException("Stripe returned an empty response");
            }
            return body;
        } catch (RestClientException e) {
            throw new StripeException("Failed to reach Stripe");
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String credentials = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        headers.add(HttpHeaders.AUTHORIZATION, "Basic " + credentials);
        return headers;
    }
}
