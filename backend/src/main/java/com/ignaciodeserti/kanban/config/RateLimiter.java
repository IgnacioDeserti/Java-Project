package com.ignaciodeserti.kanban.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

/**
 * A minimal in-memory sliding-window rate limiter, keyed by caller-supplied strings (typically
 * "ip:route"). Good enough for a single-instance deployment; a multi-instance deployment would need
 * a shared store (e.g. Redis) instead.
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** Returns true if the call is allowed and records it; false if the limit is already hit. */
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        Instant now = Instant.now();
        Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(now.minus(window))) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxAttempts) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
