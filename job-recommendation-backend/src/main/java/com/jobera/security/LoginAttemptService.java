package com.jobera.security;

import java.time.Clock;

import org.springframework.stereotype.Service;

/** In-memory brute-force protection: too many failed logins per account or per client address block further attempts. */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES_PER_ACCOUNT = 5;
    static final int MAX_FAILURES_PER_ADDRESS = 30;
    static final long WINDOW_MILLIS = 15 * 60 * 1000L;

    private final SlidingWindowLimiter accountLimiter;
    private final SlidingWindowLimiter addressLimiter;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.accountLimiter = new SlidingWindowLimiter(clock, WINDOW_MILLIS);
        this.addressLimiter = new SlidingWindowLimiter(clock, WINDOW_MILLIS);
    }

    public boolean isBlocked(String email, String address) {
        return accountLimiter.count(accountKey(email)) >= MAX_FAILURES_PER_ACCOUNT
            || addressLimiter.count(addressKey(address)) >= MAX_FAILURES_PER_ADDRESS;
    }

    /** Minutes until the block on this account or address clears (at least 1). */
    public long retryAfterMinutes(String email, String address) {
        return Math.max(
            accountLimiter.retryAfterMinutes(accountKey(email)),
            addressLimiter.retryAfterMinutes(addressKey(address)));
    }

    public void recordFailure(String email, String address) {
        accountLimiter.record(accountKey(email));
        addressLimiter.record(addressKey(address));
    }

    public void recordSuccess(String email) {
        accountLimiter.clear(accountKey(email));
    }

    private static String accountKey(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static String addressKey(String address) {
        return address == null ? "" : address;
    }
}
