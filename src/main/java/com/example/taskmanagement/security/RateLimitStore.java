package com.example.taskmanagement.security;

/** Where the token buckets are kept. */
public interface RateLimitStore {

    /** Takes one token from the client's bucket, if there is one. */
    Decision tryConsume(String clientKey, int capacity, double refillPerSecond);

    record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
    }
}
