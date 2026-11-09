package com.example.ticket.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 基于 Redis 的简单分布式锁。
 *
 * <p>只做两件事：tryLock 拿锁、unlock 放锁。抢单和定时任务都用它。
 *
 * <p>三个必须做对的点（面试常问）：
 * <ol>
 *   <li><b>必须设过期时间</b>：否则持锁进程崩溃后锁永不释放，这个资源就死锁了</li>
 *   <li><b>值必须是随机串</b>：用来判断"这把锁是不是我加的"</li>
 *   <li><b>释放必须用 Lua</b>：先 get 再 del 中间可能锁过期被别人拿走，
 *       那样会删掉别人的锁；Lua 在 Redis 内单线程执行，判断和删除是原子的</li>
 * </ol>
 *
 * <p>它不解决的问题（要知道边界）：锁过期但业务还没跑完（需要看门狗续期）、
 * Redis 主从切换时锁可能丢（Redlock 那套争议）。本项目都是短任务，用不上。
 */
@Component
public class RedisLockHelper {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisLockHelper(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 尝试加锁，不阻塞。
     *
     * @return 加锁成功返回持有凭证（释放时要传回来），失败返回 null
     */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    /**
     * 释放锁。只有值匹配才删，避免误删别人的锁。
     */
    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        redis.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
    }
}
