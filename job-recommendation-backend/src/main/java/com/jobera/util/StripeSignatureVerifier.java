package com.jobera.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Verifies a Stripe webhook's "Stripe-Signature" header by hand, the same way Stripe's own
 * documented algorithm works, rather than pulling in the stripe-java SDK just for this one check:
 * https://stripe.com/docs/webhooks/signatures. The header looks like
 * "t=1614556800,v1=5257a869...,v0=...": Stripe signs "{timestamp}.{raw body}" with HMAC-SHA256
 * using the webhook's signing secret, and a request without a valid v1 signature could be
 * anyone claiming to be Stripe. A Spring bean (unlike this package's other hand-rolled crypto
 * utilities) so SubscriptionService's tests can inject a mock instead of computing real HMACs.
 */
@Component
public class StripeSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long DEFAULT_TOLERANCE_SECONDS = 5 * 60;

    public boolean isValid(String payload, String signatureHeader, String webhookSecret) {
        return isValid(payload, signatureHeader, webhookSecret, Instant.now());
    }

    boolean isValid(String payload, String signatureHeader, String webhookSecret, Instant now) {
        if (payload == null || signatureHeader == null || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }

        String timestamp = null;
        String v1Signature = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("t".equals(kv[0])) {
                timestamp = kv[1];
            } else if ("v1".equals(kv[0])) {
                v1Signature = kv[1];
            }
        }
        if (timestamp == null || v1Signature == null) {
            return false;
        }

        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - timestampSeconds) > DEFAULT_TOLERANCE_SECONDS) {
            return false;
        }

        String signedPayload = timestamp + "." + payload;
        String expected = hmacHex(signedPayload, webhookSecret);
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), v1Signature.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacHex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute Stripe webhook signature", e);
        }
    }
}
