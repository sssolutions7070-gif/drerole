package com.jobera.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TotpServiceTest {

    private final TotpService totp = new TotpService();

    // The exact ASCII secret "12345678901234567890" from RFC 4226 Appendix D / RFC 6238 Appendix B,
    // base32-encoded (RFC 4648) - this is the standard fixture practically every TOTP library's own
    // test suite uses, so matching it here is a real cross-check against the spec, not just
    // internal self-consistency.
    private static final String RFC_TEST_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesTheOfficialRfc4226HotpTestVectors() {
        // RFC 4226 Appendix D: HOTP(secret, counter) for counters 0-9.
        String[] expected = {
            "755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489"
        };
        for (int counter = 0; counter < expected.length; counter++) {
            assertEquals(expected[counter], totp.generateCode(RFC_TEST_SECRET, counter),
                "counter " + counter);
        }
    }

    @Test
    void matchesTheRfc6238TotpVectorAtASpecificTimestamp() {
        // RFC 6238 Appendix B: at T=59s (time step 0, 30s steps), the 8-digit TOTP is 94287082;
        // the low 6 digits (which is what this codebase actually generates) are 287082, exactly
        // HOTP counter 1 from the 4226 vector - T=59 / 30 = 1.
        assertEquals("287082", totp.generateCode(RFC_TEST_SECRET, 59L / 30));
    }

    @Test
    void generatedSecretsAreDistinctAndBase32() {
        String a = totp.generateSecret();
        String b = totp.generateSecret();

        assertNotEquals(a, b);
        assertTrue(a.matches("[A-Z2-7]+"), "expected base32 alphabet only, got: " + a);
    }

    @Test
    void aCodeGeneratedRightNowVerifiesSuccessfully() {
        String secret = totp.generateSecret();
        long currentStep = System.currentTimeMillis() / 1000L / 30;
        String code = totp.generateCode(secret, currentStep);

        assertTrue(totp.verifyCode(secret, code));
    }

    @Test
    void toleratesOneStepOfClockDriftEitherWay() {
        String secret = totp.generateSecret();
        long currentStep = System.currentTimeMillis() / 1000L / 30;

        assertTrue(totp.verifyCode(secret, totp.generateCode(secret, currentStep - 1)));
        assertTrue(totp.verifyCode(secret, totp.generateCode(secret, currentStep + 1)));
    }

    @Test
    void rejectsACodeOutsideTheDriftWindow() {
        String secret = totp.generateSecret();
        long currentStep = System.currentTimeMillis() / 1000L / 30;

        assertFalse(totp.verifyCode(secret, totp.generateCode(secret, currentStep - 2)));
        assertFalse(totp.verifyCode(secret, totp.generateCode(secret, currentStep + 2)));
    }

    @Test
    void rejectsMalformedCodesWithoutThrowing() {
        String secret = totp.generateSecret();

        assertFalse(totp.verifyCode(secret, null));
        assertFalse(totp.verifyCode(secret, ""));
        assertFalse(totp.verifyCode(secret, "12345")); // too short
        assertFalse(totp.verifyCode(secret, "1234567")); // too long
        assertFalse(totp.verifyCode(secret, "12345a")); // not all digits
    }

    @Test
    void otpAuthUriIncludesTheAccountIssuerAndSecret() {
        String uri = totp.buildOtpAuthUri("ABCD1234", "user@example.com", "DreamRole");

        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret=ABCD1234"));
        assertTrue(uri.contains("issuer=DreamRole"));
        assertTrue(uri.contains("user%40example.com"));
    }
}
