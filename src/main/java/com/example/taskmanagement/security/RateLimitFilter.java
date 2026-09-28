package com.example.taskmanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiting per client IP. Runs before authentication, so a rejected
 * request never reaches the password hasher or the database.
 *
 * In-memory buckets are per instance; running several replicas would need a shared
 * store (e.g. Redis) or limiting at the gateway.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final long IDLE_EVICTION_NANOS = 10L * 60 * 1_000_000_000;

    private final int capacity;
    private final double refillPerSecond;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(int capacity, double refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            long now = System.nanoTime();
            buckets.values().removeIf(b -> now - b.lastRefill > IDLE_EVICTION_NANOS);
        }

        Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), k -> new Bucket(capacity));
        long remaining;
        long retryAfterSeconds = 0;
        boolean allowed;
        synchronized (bucket) {
            bucket.refill(capacity, refillPerSecond);
            allowed = bucket.tokens >= 1;
            if (allowed) {
                bucket.tokens -= 1;
            } else {
                retryAfterSeconds = (long) Math.ceil((1 - bucket.tokens) / refillPerSecond);
            }
            remaining = (long) Math.floor(bucket.tokens);
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        if (!allowed) {
            response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
            JsonErrorWriter.write(response, 429, "Too many requests. Retry after " + Math.max(1, retryAfterSeconds) + "s.");
            return;
        }
        filterChain.doFilter(request, response);
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
