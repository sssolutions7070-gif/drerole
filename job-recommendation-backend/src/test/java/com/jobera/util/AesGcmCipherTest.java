package com.jobera.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class AesGcmCipherTest {

    private String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void decryptRecoversExactlyWhatWasEncrypted() {
        AesGcmCipher cipher = AesGcmCipher.fromBase64Key(randomKey());

        String encrypted = cipher.encrypt("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");

        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", cipher.decrypt(encrypted));
    }

    @Test
    void encryptingTheSameValueTwiceProducesDifferentCiphertext() {
        // A fresh random IV each time, so identical plaintexts don't reveal a pattern.
        AesGcmCipher cipher = AesGcmCipher.fromBase64Key(randomKey());

        String first = cipher.encrypt("same-secret");
        String second = cipher.encrypt("same-secret");

        assertNotEquals(first, second);
        assertEquals("same-secret", cipher.decrypt(first));
        assertEquals("same-secret", cipher.decrypt(second));
    }

    @Test
    void decryptingWithTheWrongKeyFails() {
        AesGcmCipher encryptor = AesGcmCipher.fromBase64Key(randomKey());
        AesGcmCipher wrongKeyDecryptor = AesGcmCipher.fromBase64Key(randomKey());

        String encrypted = encryptor.encrypt("a secret value");

        assertThrows(IllegalStateException.class, () -> wrongKeyDecryptor.decrypt(encrypted));
    }

    @Test
    void tamperedCiphertextFailsToDecrypt() {
        // GCM's authentication tag must catch this - it's what makes it authenticated encryption,
        // not just confidentiality.
        AesGcmCipher cipher = AesGcmCipher.fromBase64Key(randomKey());
        String encrypted = cipher.encrypt("a secret value");

        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 0x01; // flip a bit near the end, inside the auth tag
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void rejectsAKeyOfTheWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmCipher(new byte[16])); // AES-128 length, not accepted here
        assertThrows(IllegalArgumentException.class, () -> new AesGcmCipher(null));
    }
}
