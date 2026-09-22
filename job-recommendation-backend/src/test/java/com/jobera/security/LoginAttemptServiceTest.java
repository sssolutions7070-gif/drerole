package com.jobera.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }

        void advance(Duration duration) { now = now.plus(duration); }
    }

    @Test
    void accountIsBlockedAfterFiveFailuresWithinTheWindow() {
        LoginAttemptService service = new LoginAttemptService(new MutableClock());
        for (int i = 0; i < 4; i++) {
            service.recordFailure("a@b.com", "10.0.0." + i);
        }
        assertFalse(service.isBlocked("a@b.com", "10.0.0.99"));

        service.recordFailure("a@b.com", "10.0.0.5");
        assertTrue(service.isBlocked("a@b.com", "10.0.0.99"));
    }

    @Test
    void emailMatchingIgnoresCaseAndSurroundingSpaces() {
        LoginAttemptService service = new LoginAttemptService(new MutableClock());
        for (int i = 0; i < 5; i++) {
            service.recordFailure(i % 2 == 0 ? "A@B.com" : " a@b.com ", "1.1.1.1");
        }
        assertTrue(service.isBlocked("a@b.COM", "2.2.2.2"));
    }

    @Test
    void otherAccountsAreNotAffected() {
        LoginAttemptService service = new LoginAttemptService(new MutableClock());
        for (int i = 0; i < 5; i++) {
            service.recordFailure("victim@b.com", "1.1.1.1");
        }
        assertFalse(service.isBlocked("someone-else@b.com", "9.9.9.9"));
    }

    @Test
    void blockExpiresOnceTheWindowHasPassed() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(clock);
        for (int i = 0; i < 5; i++) {
            service.recordFailure("a@b.com", "1.1.1.1");
        }
        assertTrue(service.isBlocked("a@b.com", "1.1.1.1"));

        clock.advance(Duration.ofMinutes(16));

        assertFalse(service.isBlocked("a@b.com", "1.1.1.1"));
    }

    @Test
    void successfulLoginClearsTheAccountCounter() {
        LoginAttemptService service = new LoginAttemptService(new MutableClock());
        for (int i = 0; i < 4; i++) {
            service.recordFailure("a@b.com", "1.1.1.1");
        }
        service.recordSuccess("a@b.com");
        service.recordFailure("a@b.com", "1.1.1.1");

        assertFalse(service.isBlocked("a@b.com", "2.2.2.2"));
    }

    @Test
    void oneAddressGuessingManyAccountsIsEventuallyBlocked() {
        LoginAttemptService service = new LoginAttemptService(new MutableClock());
        for (int i = 0; i < 30; i++) {
            service.recordFailure("user" + i + "@b.com", "6.6.6.6");
        }
        assertTrue(service.isBlocked("brand-new@b.com", "6.6.6.6"));
        assertFalse(service.isBlocked("brand-new@b.com", "7.7.7.7"));
    }

    @Test
    void retryAfterReportsTheRemainingMinutes() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(clock);
        for (int i = 0; i < 5; i++) {
            service.recordFailure("a@b.com", "1.1.1.1");
        }
        clock.advance(Duration.ofMinutes(5));

        assertEquals(10, service.retryAfterMinutes("a@b.com", "1.1.1.1"));
    }
}
