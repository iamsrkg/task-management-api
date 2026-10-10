package com.example.taskmanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Token-bucket rate limiting per client IP. Runs before authentication, so a rejected
 * request never reaches the password hasher or the database.
 *
 * Where the buckets live is up to the RateLimitStore: this instance's memory, or Redis when
 * several instances have to share one limit.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitStore store;
    private final int capacity;
    private final double refillPerSecond;

    public RateLimitFilter(int capacity, double refillPerSecond) {
        this(new InMemoryRateLimitStore(), capacity, refillPerSecond);
    }

    public RateLimitFilter(RateLimitStore store, int capacity, double refillPerSecond) {
        this.store = store;
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

        RateLimitStore.Decision decision = store.tryConsume(request.getRemoteAddr(), capacity, refillPerSecond);

        response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            long retryAfter = Math.max(1, decision.retryAfterSeconds());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            JsonErrorWriter.write(response, 429, "Too many requests. Retry after " + retryAfter + "s.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
