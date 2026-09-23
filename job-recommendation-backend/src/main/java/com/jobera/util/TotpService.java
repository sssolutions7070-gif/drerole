package com.jobera.util;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP (RFC 6238, built on the HOTP algorithm from RFC 4226) - the same scheme every standard
 * authenticator app (Google Authenticator, Authy, 1Password, etc.) implements. HMAC-SHA1 is part
 * of the spec itself, not a weaker choice made here; TOTP's security comes from the secret and
 * the short validity window, not the hash algorithm.
 */
public class TotpService {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int SECRET_BYTES = 20; // 160 bits, the size RFC 4226 recommends
    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int ALLOWED_DRIFT_STEPS = 1; // accepts the previous/current/next 30s window
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom random = new SecureRandom();

    /** A fresh random secret, base32-encoded so it can be typed by hand into an authenticator app. */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** The otpauth:// URI authenticator apps use to pre-fill an account when scanned or opened. */
    public String buildOtpAuthUri(String secret, String accountEmail, String issuer) {
        String label = urlEncode(issuer) + ":" + urlEncode(accountEmail);
        return "otpauth://totp/" + label
            + "?secret=" + secret
            + "&issuer=" + urlEncode(issuer)
            + "&digits=" + CODE_DIGITS
            + "&period=" + TIME_STEP_SECONDS;
    }

    /** The code valid right now for this secret - what a user's authenticator app would be showing. */
    public String currentCode(String secret) {
        return generateCode(secret, System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS);
    }

    /** True if {@code code} matches the secret at the current time step, or one step either side
     *  to tolerate clock drift between the server and the user's device. */
    public boolean verifyCode(String secret, String code) {
        if (code == null || !code.matches("\\d{" + CODE_DIGITS + "}")) {
            return false;
        }
        long currentStep = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;
        for (long drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
            if (code.equals(generateCode(secret, currentStep + drift))) {
                return true;
            }
        }
        return false;
    }

    String generateCode(String secret, long timeStep) {
        byte[] key = base32Decode(secret);
        byte[] counter = ByteBuffer.allocate(8).putLong(timeStep).array();

        byte[] hash;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            hash = mac.doFinal(counter);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute TOTP code", e);
        }

        // Dynamic truncation (RFC 4226 section 5.3).
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
            | ((hash[offset + 1] & 0xFF) << 16)
            | ((hash[offset + 2] & 0xFF) << 8)
            | (hash[offset + 3] & 0xFF);

        int otp = binary % (int) Math.pow(10, CODE_DIGITS);
        return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", otp);
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                result.append(BASE32_ALPHABET.charAt(index));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(BASE32_ALPHABET.charAt(index));
        }
        return result.toString();
    }

    private static byte[] base32Decode(String encoded) {
        String clean = encoded.trim().toUpperCase(Locale.ROOT).replace("=", "");
        int outputLength = clean.length() * 5 / 8;
        byte[] result = new byte[outputLength];
        int buffer = 0;
        int bitsLeft = 0;
        int outputIndex = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                continue; // ignore separators a user might have typed, e.g. spaces
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[outputIndex++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
