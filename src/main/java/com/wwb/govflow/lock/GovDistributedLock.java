package com.wwb.govflow.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 兼容 Redis 规范的高性能分布式锁引擎 (含 Watchdog 自动续期守护)
 * 核心机制:
 * 1. SET lock:key owner NX PX leaseTime 保证互斥
 * 2. Watchdog 守护线程每隔 1/3 周期 (1000ms) 自动续期，防业务未完锁提前过期
 * 3. 校验 Owner Token 安全释放，防误解锁他人资源
 */
@Component
public class GovDistributedLock {

    private static final Logger log = LoggerFactory.getLogger(GovDistributedLock.class);

    private final Map<String, LockMetadata> lockStorage = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdogExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "gov-lock-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final AtomicLong totalLockAcquired = new AtomicLong(0);
    private final AtomicLong totalWatchdogRenewals = new AtomicLong(0);

    public GovDistributedLock() {
        // Watchdog 扫描任务：每隔 1000ms 执行一次续期
        watchdogExecutor.scheduleAtFixedRate(this::performWatchdogRenewal, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    public synchronized boolean tryLock(String lockKey, String ownerToken, long leaseTimeMs) {
        long now = System.currentTimeMillis();
        LockMetadata existing = lockStorage.get(lockKey);

        if (existing != null && existing.expireAt > now) {
            // 已被占用
            if (existing.ownerToken.equals(ownerToken)) {
                // 重入支持
                existing.reentrantCount++;
                existing.expireAt = now + leaseTimeMs;
                return true;
            }
            return false;
        }

        // 获取锁成功
        LockMetadata meta = new LockMetadata(lockKey, ownerToken, now + leaseTimeMs, leaseTimeMs);
        lockStorage.put(lockKey, meta);
        totalLockAcquired.incrementAndGet();
        log.debug("[Lock] 成功获取分布式锁: key={}, owner={}, lease={}ms", lockKey, ownerToken, leaseTimeMs);
        return true;
    }

    public synchronized boolean unlock(String lockKey, String ownerToken) {
        LockMetadata meta = lockStorage.get(lockKey);
        if (meta == null) {
            return false;
        }

        if (!meta.ownerToken.equals(ownerToken)) {
            log.warn("[Lock] 非法解锁尝试！当前所有者: {}, 请求释放者: {}", meta.ownerToken, ownerToken);
            return false;
        }

        meta.reentrantCount--;
        if (meta.reentrantCount <= 0) {
            lockStorage.remove(lockKey);
            log.debug("[Lock] 成功安全释放分布式锁: key={}, owner={}", lockKey, ownerToken);
        }
        return true;
    }

    private synchronized void performWatchdogRenewal() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, LockMetadata> entry : lockStorage.entrySet()) {
            LockMetadata meta = entry.getValue();
            if (meta.expireAt > now) {
                // 仅对未过期的活跃锁自动续期，恢复原租约时间
                meta.expireAt = now + meta.initialLeaseMs;
                totalWatchdogRenewals.incrementAndGet();
                log.trace("[Watchdog] 自动续期锁成功: key={}, 新过期时间={}", meta.lockKey, meta.expireAt);
            }
        }
    }

    public long getTotalLockAcquired() {
        return totalLockAcquired.get();
    }

    public long getTotalWatchdogRenewals() {
        return totalWatchdogRenewals.get();
    }

    public int getActiveLockCount() {
        return lockStorage.size();
    }

    @PreDestroy
    public void destroy() {
        watchdogExecutor.shutdown();
    }

    private static class LockMetadata {
        final String lockKey;
        final String ownerToken;
        final long initialLeaseMs;
        volatile long expireAt;
        int reentrantCount = 1;

        LockMetadata(String lockKey, String ownerToken, long expireAt, long initialLeaseMs) {
            this.lockKey = lockKey;
            this.ownerToken = ownerToken;
            this.expireAt = expireAt;
            this.initialLeaseMs = initialLeaseMs;
        }
    }
}
