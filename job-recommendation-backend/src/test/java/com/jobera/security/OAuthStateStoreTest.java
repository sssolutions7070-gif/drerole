package com.jobera.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class OAuthStateStoreTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }

        void advance(Duration duration) { now = now.plus(duration); }
    }

    @Test
    void issuedStateCanBeConsumedExactlyOnce() {
        OAuthStateStore store = new OAuthStateStore(new MutableClock());
        String state = store.issue();

        assertTrue(store.consume(state));
        assertFalse(store.consume(state));
    }

    @Test
    void unknownStateIsRejected() {
        OAuthStateStore store = new OAuthStateStore(new MutableClock());
        assertFalse(store.consume("never-issued"));
    }

    @Test
    void nullStateIsRejected() {
        OAuthStateStore store = new OAuthStateStore(new MutableClock());
        assertFalse(store.consume(null));
    }

    @Test
    void stateExpiresAfterTenMinutes() {
        MutableClock clock = new MutableClock();
        OAuthStateStore store = new OAuthStateStore(clock);
        String state = store.issue();

        clock.advance(Duration.ofMinutes(11));

        assertFalse(store.consume(state));
    }

    @Test
    void stateIsStillValidJustUnderTenMinutes() {
        MutableClock clock = new MutableClock();
        OAuthStateStore store = new OAuthStateStore(clock);
        String state = store.issue();

        clock.advance(Duration.ofMinutes(9));

        assertTrue(store.consume(state));
    }

    @Test
    void eachIssuedStateIsUnique() {
        OAuthStateStore store = new OAuthStateStore(new MutableClock());
        assertNotEquals(store.issue(), store.issue());
    }

    @Test
    void aUserBoundStateReturnsThatUserExactlyOnce() {
        OAuthStateStore store = new OAuthStateStore(new MutableClock());
        String state = store.issueForUser(42L);

        assertEquals(42L, store.consumeUserId(state).orElseThrow());
        assertTrue(store.consumeUserId(state).isEmpty());
    }

    @Test
    void aUserBoundStateExpiresJustLikeAnOrdinaryOne() {
        MutableClock clock = new MutableClock();
        OAuthStateStore store = new OAuthStateStore(clock);
        String state = store.issueForUser(42L);

        clock.advance(Duration.ofMinutes(11));

        assertTrue(store.consumeUserId(state).isEmpty());
    }

    @Test
    void aPlainLoginStateHasNoUserToConsume() {
        OAuthStateStore store = new OAuthStateStore(new MutableClock());
        String state = store.issue();

        assertTrue(store.consumeUserId(state).isEmpty());
    }
}
