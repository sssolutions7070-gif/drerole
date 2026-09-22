package com.jobera.util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing with PBKDF2-HMAC-SHA256 (random salt, stored as
 * "pbkdf2_sha256$iterations$salt$hash"). Hashes created by the previous
 * single-round SHA-256 scheme are still verified so existing users can log in;
 * callers should re-encode them when {@link #needsUpgrade(String)} is true.
 */
public class PasswordEncoder {

    private static final String PREFIX = "pbkdf2_sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int LEGACY_SALT_BYTES = 16;

    private final SecureRandom random = new SecureRandom();

    public String encode(CharSequence rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword, salt, ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
    }

    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        if (encodedPassword.startsWith(PREFIX + "$")) {
            return matchesPbkdf2(rawPassword, encodedPassword);
        }
        return matchesLegacySha256(rawPassword, encodedPassword);
    }

    public boolean needsUpgrade(String encodedPassword) {
        return encodedPassword == null || !encodedPassword.startsWith(PREFIX + "$");
    }

    private boolean matchesPbkdf2(CharSequence rawPassword, String encodedPassword) {
        String[] parts = encodedPassword.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, pbkdf2(rawPassword, salt, iterations));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean matchesLegacySha256(CharSequence rawPassword, String encodedPassword) {
        try {
            byte[] combined = Base64.getDecoder().decode(encodedPassword);
            if (combined.length <= LEGACY_SALT_BYTES) {
                return false;
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(combined, 0, LEGACY_SALT_BYTES);
            byte[] testHash = digest.digest(rawPassword.toString().getBytes());

            byte[] storedHash = new byte[combined.length - LEGACY_SALT_BYTES];
            System.arraycopy(combined, LEGACY_SALT_BYTES, storedHash, 0, storedHash.length);
            return MessageDigest.isEqual(storedHash, testHash);
        } catch (IllegalArgumentException e) {
            return false;
        } catch (NoSuchAlgorithmException e) {
            throw new PasswordEncodingException("Error verifying password", e);
        }
    }

    private byte[] pbkdf2(CharSequence rawPassword, byte[] salt, int iterations) {
        char[] chars = rawPassword.toString().toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new PasswordEncodingException("Error hashing password", e);
        } finally {
            spec.clearPassword();
        }
    }

    public static class PasswordEncodingException extends RuntimeException {
        public PasswordEncodingException(String message) {
            super(message);
        }

        public PasswordEncodingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
