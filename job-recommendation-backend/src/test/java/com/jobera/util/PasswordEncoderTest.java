package com.jobera.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;

public class PasswordEncoderTest {

    private final PasswordEncoder encoder = new PasswordEncoder();

    public static String legacyHash(String password) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        byte[] hash = digest.digest(password.getBytes());
        byte[] combined = new byte[salt.length + hash.length];
        System.arraycopy(salt, 0, combined, 0, salt.length);
        System.arraycopy(hash, 0, combined, salt.length, hash.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    @Test
    void encodedPasswordMatchesOnlyTheOriginal() {
        String encoded = encoder.encode("Correct-Horse-9");
        assertTrue(encoder.matches("Correct-Horse-9", encoded));
        assertFalse(encoder.matches("correct-horse-9", encoded));
    }

    @Test
    void samePasswordProducesDifferentHashes() {
        assertNotEquals(encoder.encode("secret"), encoder.encode("secret"));
    }

    @Test
    void newHashesUsePbkdf2Format() {
        String encoded = encoder.encode("secret");
        assertTrue(encoded.startsWith("pbkdf2_sha256$"));
        assertEquals(4, encoded.split("\\$").length);
        assertFalse(encoder.needsUpgrade(encoded));
    }

    @Test
    void legacySha256HashesStillVerifyAndAreFlaggedForUpgrade() throws Exception {
        String legacy = legacyHash("old-password");
        assertTrue(encoder.matches("old-password", legacy));
        assertFalse(encoder.matches("wrong", legacy));
        assertTrue(encoder.needsUpgrade(legacy));
    }

    @Test
    void malformedOrMissingHashesNeverMatch() {
        assertFalse(encoder.matches("x", null));
        assertFalse(encoder.matches(null, "abc"));
        assertFalse(encoder.matches("x", "not base64 !!"));
        assertFalse(encoder.matches("x", "QUJD"));
        assertFalse(encoder.matches("x", "pbkdf2_sha256$abc$$"));
        assertFalse(encoder.matches("x", "pbkdf2_sha256$1"));
    }
}
