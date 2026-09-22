package com.jobera.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/** In-memory brute-force protection: too many failed logins per account or per client address block further attempts. */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES_PER_ACCOUNT = 5;
    static final int MAX_FAILURES_PER_ADDRESS = 30;
    static final long WINDOW_MILLIS = 15 * 60 * 1000L;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String email, String address) {
        return count(accountKey(email)) >= MAX_FAILURES_PER_ACCOUNT
            || count(addressKey(address)) >= MAX_FAILURES_PER_ADDRESS;
    }

    /** Minutes until the oldest recorded failure ages out of the window (at least 1). */
    public long retryAfterMinutes(String email, String address) {
        long now = clock.millis();
        long oldest = now;
        for (String key : new String[] {accountKey(email), addressKey(address)}) {
            Deque<Long> times = failures.get(key);
            if (times != null) {
                synchronized (times) {
                    if (!times.isEmpty()) {
                        oldest = Math.min(oldest, times.peekFirst());
                    }
                }
            }
        }
        return Math.max(1, (oldest + WINDOW_MILLIS - now + 59_999) / 60_000);
    }

    public void recordFailure(String email, String address) {
        if (failures.size() > MAX_TRACKED_KEYS) {
            evictExpired();
        }
        add(accountKey(email));
        add(addressKey(address));
    }

    public void recordSuccess(String email) {
        failures.remove(accountKey(email));
    }

    private void add(String key) {
        Deque<Long> times = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(clock.millis());
        }
    }

    private int count(String key) {
        Deque<Long> times = failures.get(key);
        if (times == null) {
            return 0;
        }
        long cutoff = clock.millis() - WINDOW_MILLIS;
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < cutoff) {
                times.pollFirst();
            }
            return times.size();
        }
    }

    private void evictExpired() {
        long cutoff = clock.millis() - WINDOW_MILLIS;
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || entry.getValue().peekLast() < cutoff;
            }
        });
    }

    private static String accountKey(String email) {
        return "acct:" + (email == null ? "" : email.trim().toLowerCase());
    }

    private static String addressKey(String address) {
        return "addr:" + (address == null ? "" : address);
    }
}
