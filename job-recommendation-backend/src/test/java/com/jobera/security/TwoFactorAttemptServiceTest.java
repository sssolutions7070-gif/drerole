package com.jobera.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TwoFactorAttemptServiceTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }

        void advance(Duration duration) { now = now.plus(duration); }
    }

    @Test
    void blocksAfterTheMaximumNumberOfFailures() {
        TwoFactorAttemptService service = new TwoFactorAttemptService(new MutableClock());

        for (int i = 0; i < TwoFactorAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure(1L);
        }
        assertFalse(service.isBlocked(1L));

        service.recordFailure(1L);

        assertTrue(service.isBlocked(1L));
    }

    @Test
    void doesNotBlockADifferentUser() {
        TwoFactorAttemptService service = new TwoFactorAttemptService(new MutableClock());

        for (int i = 0; i < TwoFactorAttemptService.MAX_FAILURES; i++) {
            service.recordFailure(1L);
        }

        assertTrue(service.isBlocked(1L));
        assertFalse(service.isBlocked(2L));
    }

    @Test
    void successClearsPriorFailures() {
        TwoFactorAttemptService service = new TwoFactorAttemptService(new MutableClock());
        for (int i = 0; i < TwoFactorAttemptService.MAX_FAILURES; i++) {
            service.recordFailure(1L);
        }
        assertTrue(service.isBlocked(1L));

        service.recordSuccess(1L);

        assertFalse(service.isBlocked(1L));
    }

    @Test
    void oldFailuresAgeOutOfTheWindow() {
        MutableClock clock = new MutableClock();
        TwoFactorAttemptService service = new TwoFactorAttemptService(clock);

        for (int i = 0; i < TwoFactorAttemptService.MAX_FAILURES; i++) {
            service.recordFailure(1L);
        }
        assertTrue(service.isBlocked(1L));

        clock.advance(Duration.ofMillis(TwoFactorAttemptService.WINDOW_MILLIS + 1));

        assertFalse(service.isBlocked(1L));
    }

    @Test
    void retryAfterMinutesReportsTheRemainingTime() {
        MutableClock clock = new MutableClock();
        TwoFactorAttemptService service = new TwoFactorAttemptService(clock);
        for (int i = 0; i < TwoFactorAttemptService.MAX_FAILURES; i++) {
            service.recordFailure(1L);
        }
        clock.advance(Duration.ofMinutes(5));

        assertEquals(10, service.retryAfterMinutes(1L));
    }
}
