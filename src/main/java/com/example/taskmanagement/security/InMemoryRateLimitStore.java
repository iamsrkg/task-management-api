package com.example.taskmanagement.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Buckets in this instance's memory. Correct for one instance; with several, each would allow
 * the full limit on its own, which is what RedisRateLimitStore is for.
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final long IDLE_EVICTION_NANOS = 10L * 60 * 1_000_000_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Decision tryConsume(String clientKey, int capacity, double refillPerSecond) {
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            long now = System.nanoTime();
            buckets.values().removeIf(b -> now - b.lastRefill > IDLE_EVICTION_NANOS);
        }
        Bucket bucket = buckets.computeIfAbsent(clientKey, k -> new Bucket(capacity));
        synchronized (bucket) {
            bucket.refill(capacity, refillPerSecond);
            boolean allowed = bucket.tokens >= 1;
            long retryAfterSeconds = 0;
            if (allowed) {
                bucket.tokens -= 1;
            } else {
                retryAfterSeconds = (long) Math.ceil((1 - bucket.tokens) / refillPerSecond);
            }
            return new Decision(allowed, (long) Math.floor(bucket.tokens), retryAfterSeconds);
        }
    }

    static final class Bucket {
        double tokens;
        long lastRefill = System.nanoTime();

        Bucket(int capacity) {
            this.tokens = capacity;
        }

        void refill(int capacity, double refillPerSecond) {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefill) / 1_000_000_000.0 * refillPerSecond);
            lastRefill = now;
        }
    }
}
