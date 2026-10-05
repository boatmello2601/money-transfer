package com.bank.money_transfer.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class TransferRateLimiter {
    private static final int MAX_REQUESTS = 10;
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final String KEY_PREFIX = "ratelimit:transfer:";

    private final StringRedisTemplate redisTemplate;

    public TransferRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RateLimitResult checkAndIncrement(Long accountId) {
        String key = KEY_PREFIX + accountId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1L) {
            redisTemplate.expire(key, WINDOW);
        }

        if (count != null && count > MAX_REQUESTS) {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            long retryAfter = (ttl != null && ttl > 0) ? ttl : WINDOW.getSeconds();
            return RateLimitResult.exceeded(retryAfter);
        }
        return RateLimitResult.allowed();
    }

    public static class RateLimitResult {
        private final boolean limited;
        private final long retryAfterSeconds;

        private RateLimitResult(boolean limited, long retryAfterSeconds) {
            this.limited = limited;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public static RateLimitResult allowed() {
            return new RateLimitResult(false, 0);
        }

        public static RateLimitResult exceeded(long retryAfterSeconds) {
            return new RateLimitResult(true, retryAfterSeconds);
        }

        public boolean isLimited() { return limited; }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }
}
