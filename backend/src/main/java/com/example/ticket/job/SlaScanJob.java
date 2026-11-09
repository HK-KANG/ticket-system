package com.example.ticket.job;

import com.example.ticket.common.RedisLockHelper;
import com.example.ticket.service.SlaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * SLA 超时兜底扫描。
 *
 * <p>为什么有了 MQ 延迟消息还要扫库：TTL+死信那套延迟队列有“队头阻塞”——
 * RabbitMQ 只检查队头消息是否过期，前面压着一条 8 小时后到期的，后面 30 分钟到期的也得等。
 * 所以 MQ 负责“尽量准时”，扫描负责“保证不漏”，间隔 30 秒，最多晚 30 秒升级。
 *
 * <p>多实例防重：每个分片一把 Redis 锁，谁抢到谁扫。3 个实例时理论上可以三片并行，
 * 挂了实例也能由别的实例接手（锁 25 秒自动过期）。
 */
@Component
@Slf4j
public class SlaScanJob {

    private static final String LOCK_PREFIX = "job:sla-scan:lock:";

    private final SlaService slaService;
    private final RedisLockHelper lockHelper;

    public SlaScanJob(SlaService slaService, RedisLockHelper lockHelper) {
        this.slaService = slaService;
        this.lockHelper = lockHelper;
    }

    @Value("${ticket.sla.shard-total:1}")
    private int shardTotal;

    @Value("${ticket.sla.lock-seconds:25}")
    private long lockSeconds;

    @Value("${ticket.sla.batch-limit:200}")
    private int batchLimit;

    /** 应用启动 15 秒后开始，之后每 30 秒一次（上一轮跑完再计时，不会堆积） */
    @Scheduled(fixedDelayString = "${ticket.sla.scan-interval-ms:30000}", initialDelay = 15000)
    public void scheduledScan() {
        int n = scanOnce();
        if (n > 0) {
            log.info("SLA 定时扫描：本轮升级 {} 张工单", n);
        }
    }

    /**
     * 立即扫描一轮，返回本轮升级数量。手动触发接口也调它，便于验收。
     */
    public int scanOnce() {
        int total = 0;
        for (int shard = 0; shard < Math.max(1, shardTotal); shard++) {
            String key = LOCK_PREFIX + shard;
            String token = lockHelper.tryLock(key, Duration.ofSeconds(lockSeconds));
            if (token == null) {
                // 别的实例正在扫这个分片，跳过是对的——重复扫也不会重复升级（条件更新兜底），
                // 但白干一场没必要
                continue;
            }
            try {
                total += slaService.escalateOverdueShard(shard, Math.max(1, shardTotal), batchLimit);
            } catch (Exception e) {
                log.error("SLA 扫描分片失败 shard={}", shard, e);
            } finally {
                lockHelper.unlock(key, token);
            }
        }
        return total;
    }
}
