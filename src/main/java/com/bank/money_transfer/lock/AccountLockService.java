package com.bank.money_transfer.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Component
public class AccountLockService {
    private static final Duration LOCK_TTL = Duration.ofMillis(5000);
    private static final String KEY_PREFIX = "lock:account:";

    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "  return redis.call('del', KEYS[1]) " +
                    "else " +
                    "  return 0 " +
                    "end";

    private final StringRedisTemplate redisTemplate;

    public AccountLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String acquireLock(Long accountId) {
        String key = KEY_PREFIX + accountId;
        String token = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    public void releaseLock(Long accountId, String token) {
        String key = KEY_PREFIX + accountId;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);
        redisTemplate.execute(script, Collections.singletonList(key), token);
    }
}
