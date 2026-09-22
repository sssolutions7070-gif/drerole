package com.jobera.security;

import java.time.Clock;

import org.springframework.stereotype.Service;

/** Caps password-reset requests so the endpoint can't be used to spam an inbox or brute-force accounts. */
@Service
public class PasswordResetRateLimiter {

    static final int MAX_REQUESTS_PER_EMAIL = 3;
    static final long WINDOW_MILLIS = 60 * 60 * 1000L;

    private final SlidingWindowLimiter limiter;

    public PasswordResetRateLimiter() {
        this(Clock.systemUTC());
    }

    PasswordResetRateLimiter(Clock clock) {
        this.limiter = new SlidingWindowLimiter(clock, WINDOW_MILLIS);
    }

    public boolean isBlocked(String email) {
        return limiter.count(key(email)) >= MAX_REQUESTS_PER_EMAIL;
    }

    public void recordRequest(String email) {
        limiter.record(key(email));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
