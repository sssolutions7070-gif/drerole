package com.jobera.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RegistrationRateLimiterTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }

        void advance(Duration duration) { now = now.plus(duration); }
    }

    @Test
    void allowsUpToTheLimitThenBlocks() {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(new MutableClock());

        for (int i = 0; i < 4; i++) {
            limiter.recordAttempt("1.2.3.4");
        }
        assertFalse(limiter.isBlocked("1.2.3.4"));

        limiter.recordAttempt("1.2.3.4");
        assertTrue(limiter.isBlocked("1.2.3.4"));
    }

    @Test
    void differentAddressesAreTrackedIndependently() {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(new MutableClock());
        for (int i = 0; i < 5; i++) {
            limiter.recordAttempt("1.2.3.4");
        }
        assertTrue(limiter.isBlocked("1.2.3.4"));
        assertFalse(limiter.isBlocked("5.6.7.8"));
    }

    @Test
    void blockExpiresAfterTheWindow() {
        MutableClock clock = new MutableClock();
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(clock);
        for (int i = 0; i < 5; i++) {
            limiter.recordAttempt("1.2.3.4");
        }
        assertTrue(limiter.isBlocked("1.2.3.4"));

        clock.advance(Duration.ofMinutes(61));

        assertFalse(limiter.isBlocked("1.2.3.4"));
    }
}
