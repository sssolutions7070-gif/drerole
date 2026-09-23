package com.jobera.util;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM for the one place this app needs a secret it can read back rather than only
 * verify - a TOTP seed. (Everything else sensitive - passwords, session tokens - is hashed
 * one-way; a TOTP seed has to be recovered in full to compute the current code for comparison,
 * so hashing it isn't an option, and storing it in plain text would defeat the point of 2FA if
 * the database were ever read.)
 */
public class AesGcmCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int KEY_BYTES = 32; // AES-256

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public AesGcmCipher(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("AES-256-GCM requires exactly a " + KEY_BYTES + "-byte key");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Decodes a base64-encoded 32-byte key, e.g. one generated with `openssl rand -base64 32`. */
    public static AesGcmCipher fromBase64Key(String base64Key) {
        return new AesGcmCipher(Base64.getDecoder().decode(base64Key));
    }

    /** Returns base64(iv || ciphertext+tag), self-contained so decrypt() needs nothing else. */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt value", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] ciphertext = new byte[combined.length - GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(combined, GCM_IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt value", e);
        }
    }
}
