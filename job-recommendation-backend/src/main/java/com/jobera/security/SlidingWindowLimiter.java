package com.jobera.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Counts events per key within a trailing time window; old events age out automatically. */
public class SlidingWindowLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(Clock clock, long windowMillis) {
        this.clock = clock;
        this.windowMillis = windowMillis;
    }

    public int count(String key) {
        Deque<Long> times = hits.get(key);
        if (times == null) {
            return 0;
        }
        long cutoff = clock.millis() - windowMillis;
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < cutoff) {
                times.pollFirst();
            }
            return times.size();
        }
    }

    public void record(String key) {
        if (hits.size() > MAX_TRACKED_KEYS) {
            evictExpired();
        }
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(clock.millis());
        }
    }

    public void clear(String key) {
        hits.remove(key);
    }

    /** Minutes until the oldest recorded hit for this key ages out of the window (at least 1). */
    public long retryAfterMinutes(String key) {
        long now = clock.millis();
        Deque<Long> times = hits.get(key);
        long oldest = now;
        if (times != null) {
            synchronized (times) {
                if (!times.isEmpty()) {
                    oldest = times.peekFirst();
                }
            }
        }
        return Math.max(1, (oldest + windowMillis - now + 59_999) / 60_000);
    }

    private void evictExpired() {
        long cutoff = clock.millis() - windowMillis;
        hits.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || entry.getValue().peekLast() < cutoff;
            }
        });
    }
}
