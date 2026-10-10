package com.example.taskmanagement.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * The same token bucket, kept in Redis, so every instance of the API takes tokens from one
 * shared bucket per client.
 *
 * Reading the bucket, refilling it, taking a token and writing it back run as one Lua script.
 * As separate commands, two instances could both read "1 token left" and both let a request
 * through. The script also uses Redis's clock, so instances whose clocks differ still agree.
 *
 * If Redis can't be reached the request is allowed: a limiter that is down should not take the
 * API down with it.
 */
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> TAKE_TOKEN = new DefaultRedisScript<>("""
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

            local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'at')
            local tokens = tonumber(bucket[1]) or capacity
            local at = tonumber(bucket[2]) or now
            tokens = math.min(capacity, tokens + (now - at) / 1000 * refill)

            local allowed = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'at', tostring(now))
            -- a bucket nobody has used for long enough to be full again can be forgotten
            redis.call('PEXPIRE', KEYS[1], math.ceil(capacity / refill * 1000) + 1000)
            return {allowed, tostring(tokens)}
            """, List.class);

    private final StringRedisTemplate redis;

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Decision tryConsume(String clientKey, int capacity, double refillPerSecond) {
        try {
            List<?> result = redis.execute(TAKE_TOKEN, List.of("ratelimit:" + clientKey),
                    String.valueOf(capacity), String.valueOf(refillPerSecond));
            boolean allowed = ((Number) result.get(0)).longValue() == 1;
            double tokens = Double.parseDouble(String.valueOf(result.get(1)));
            long retryAfterSeconds = allowed ? 0 : (long) Math.ceil((1 - tokens) / refillPerSecond);
            return new Decision(allowed, (long) Math.floor(tokens), retryAfterSeconds);
        } catch (RuntimeException e) {
            log.warn("Rate limiter can't reach Redis, allowing the request: {}", e.getMessage());
            return new Decision(true, capacity, 0);
        }
    }
}
