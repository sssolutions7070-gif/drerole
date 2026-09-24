package com.jobera.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class StripeSignatureVerifierTest {

    private static final String SECRET = "whsec_test_secret";

    private String sign(String timestamp, String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private String header(String timestamp, String payload, String secret) {
        return "t=" + timestamp + ",v1=" + sign(timestamp, payload, secret) + ",v0=irrelevant";
    }

    @Test
    void acceptsACorrectlySignedRecentPayload() {
        StripeSignatureVerifier verifier = new StripeSignatureVerifier();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String timestamp = String.valueOf(now.getEpochSecond());
        String payload = "{\"type\":\"checkout.session.completed\"}";

        assertTrue(verifier.isValid(payload, header(timestamp, payload, SECRET), SECRET, now));
    }

    @Test
    void rejectsASignatureComputedWithTheWrongSecret() {
        StripeSignatureVerifier verifier = new StripeSignatureVerifier();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String timestamp = String.valueOf(now.getEpochSecond());
        String payload = "{\"type\":\"checkout.session.completed\"}";

        assertFalse(verifier.isValid(payload, header(timestamp, payload, "wrong-secret"), SECRET, now));
    }

    @Test
    void rejectsATamperedPayload() {
        StripeSignatureVerifier verifier = new StripeSignatureVerifier();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String timestamp = String.valueOf(now.getEpochSecond());
        String signedPayload = "{\"type\":\"checkout.session.completed\"}";
        String tamperedPayload = "{\"type\":\"customer.subscription.deleted\"}";

        assertFalse(verifier.isValid(tamperedPayload, header(timestamp, signedPayload, SECRET), SECRET, now));
    }

    @Test
    void rejectsATimestampOutsideTheToleranceWindow() {
        StripeSignatureVerifier verifier = new StripeSignatureVerifier();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Instant tooOld = now.minusSeconds(600);
        String timestamp = String.valueOf(tooOld.getEpochSecond());
        String payload = "{\"type\":\"checkout.session.completed\"}";

        assertFalse(verifier.isValid(payload, header(timestamp, payload, SECRET), SECRET, now));
    }

    @Test
    void rejectsAMissingSignatureHeader() {
        StripeSignatureVerifier verifier = new StripeSignatureVerifier();

        assertFalse(verifier.isValid("{}", null, SECRET));
    }

    @Test
    void rejectsAHeaderWithNoV1Signature() {
        StripeSignatureVerifier verifier = new StripeSignatureVerifier();

        assertFalse(verifier.isValid("{}", "t=123456", SECRET));
    }

    @Test
    void rejectsWhenNoWebhookSecretIsConfigured() {
        StripeSignatureVerifier verifier = new StripeSignatureVerifier();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String timestamp = String.valueOf(now.getEpochSecond());
        String payload = "{}";

        assertFalse(verifier.isValid(payload, header(timestamp, payload, SECRET), ""));
    }
}
