package com.contextcraft.portal.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Lightweight sliding/fixed-window rate limiter using Redis.
 */
@Service
public class RateLimitingService {

    private final StringRedisTemplate redisTemplate;

    public RateLimitingService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks if a request is allowed under the rate limit.
     *
     * @param key           Unique rate limit key (e.g., ip address, chat id, username)
     * @param maxRequests   Maximum requests allowed in the duration window
     * @param windowSeconds Window duration in seconds
     * @return true if request is allowed, false if rate limited
     */
    public boolean isAllowed(String key, int maxRequests, int windowSeconds) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) return true;

            if (count == 1) {
                redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            } else {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl == null || ttl < 0) {
                    redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
                }
            }

            return count <= maxRequests;
        } catch (Exception e) {
            // Fallback to allow request if Redis fails to avoid blocking users
            return true;
        }
    }
}
