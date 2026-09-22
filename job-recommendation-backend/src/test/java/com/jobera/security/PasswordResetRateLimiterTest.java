package com.jobera.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class PasswordResetRateLimiterTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }

        void advance(Duration duration) { now = now.plus(duration); }
    }

    @Test
    void allowsUpToTheLimitThenBlocks() {
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(new MutableClock());

        limiter.recordRequest("a@b.com");
        limiter.recordRequest("a@b.com");
        assertFalse(limiter.isBlocked("a@b.com"));

        limiter.recordRequest("a@b.com");
        assertTrue(limiter.isBlocked("a@b.com"));
    }

    @Test
    void differentEmailsAreTrackedIndependently() {
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(new MutableClock());
        for (int i = 0; i < 3; i++) {
            limiter.recordRequest("a@b.com");
        }
        assertFalse(limiter.isBlocked("someone-else@b.com"));
    }

    @Test
    void blockExpiresAfterTheWindow() {
        MutableClock clock = new MutableClock();
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(clock);
        for (int i = 0; i < 3; i++) {
            limiter.recordRequest("a@b.com");
        }
        assertTrue(limiter.isBlocked("a@b.com"));

        clock.advance(Duration.ofMinutes(61));

        assertFalse(limiter.isBlocked("a@b.com"));
    }

    @Test
    void emailMatchingIgnoresCaseAndSpaces() {
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(new MutableClock());
        limiter.recordRequest("A@B.com");
        limiter.recordRequest(" a@b.com ");
        limiter.recordRequest("a@B.COM");

        assertTrue(limiter.isBlocked("a@b.com"));
    }
}
