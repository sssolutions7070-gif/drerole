package com.jobera.security;

import java.time.Clock;

import org.springframework.stereotype.Service;

/** Caps how many accounts can be registered from one client address - every registration is by
 *  definition a brand-new email, so (unlike login/password-reset) this can only be keyed by
 *  address, to slow down signup spam / bot account farming rather than targeting one account. */
@Service
public class RegistrationRateLimiter {

    static final int MAX_REGISTRATIONS_PER_ADDRESS = 5;
    static final long WINDOW_MILLIS = 60 * 60 * 1000L;

    private final SlidingWindowLimiter limiter;

    public RegistrationRateLimiter() {
        this(Clock.systemUTC());
    }

    RegistrationRateLimiter(Clock clock) {
        this.limiter = new SlidingWindowLimiter(clock, WINDOW_MILLIS);
    }

    public boolean isBlocked(String address) {
        return limiter.count(key(address)) >= MAX_REGISTRATIONS_PER_ADDRESS;
    }

    public void recordAttempt(String address) {
        limiter.record(key(address));
    }

    public long retryAfterMinutes(String address) {
        return limiter.retryAfterMinutes(key(address));
    }

    private static String key(String address) {
        return address == null ? "" : address;
    }
}
