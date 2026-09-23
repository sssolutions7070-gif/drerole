package com.jobera.security;

import java.time.Clock;

import org.springframework.stereotype.Service;

/** Brute-force protection for the 2FA code step of login - a 6-digit TOTP code is only 1-in-a-
 *  million per guess, but that's not enough on its own without also limiting how many guesses
 *  are allowed. Keyed by user ID rather than email/address like LoginAttemptService, since by
 *  this point the account is already known (the password was already correct). */
@Service
public class TwoFactorAttemptService {

    static final int MAX_FAILURES = 5;
    static final long WINDOW_MILLIS = 15 * 60 * 1000L;

    private final SlidingWindowLimiter limiter;

    public TwoFactorAttemptService() {
        this(Clock.systemUTC());
    }

    TwoFactorAttemptService(Clock clock) {
        this.limiter = new SlidingWindowLimiter(clock, WINDOW_MILLIS);
    }

    public boolean isBlocked(Long userId) {
        return limiter.count(key(userId)) >= MAX_FAILURES;
    }

    public long retryAfterMinutes(Long userId) {
        return limiter.retryAfterMinutes(key(userId));
    }

    public void recordFailure(Long userId) {
        limiter.record(key(userId));
    }

    public void recordSuccess(Long userId) {
        limiter.clear(key(userId));
    }

    private static String key(Long userId) {
        return userId == null ? "" : userId.toString();
    }
}
