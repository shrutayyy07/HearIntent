package com.hearintent.backend.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();

    /**
     * Returns true if the request identified by `key` is allowed under the
     * given limit within the given window, recording the attempt if allowed.
     */
    public boolean tryAcquire(String key, int maxRequests, long windowSeconds) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);

        Deque<Instant> timestamps = requestLog.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    public long remainingInWindow(String key, long windowSeconds) {
        Deque<Instant> timestamps = requestLog.get(key);
        if (timestamps == null || timestamps.isEmpty()) {
            return 0;
        }
        Instant oldest = timestamps.peekFirst();
        long elapsed = Instant.now().getEpochSecond() - oldest.getEpochSecond();
        return Math.max(0, windowSeconds - elapsed);
    }
}
