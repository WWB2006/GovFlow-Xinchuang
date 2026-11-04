package com.wwb.govflow.idempotent;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 接口幂等性 Token 校验器
 * 场景：政务窗口高并发申报、审批事项防重复提交
 */
@Component
public class IdempotencyTokenManager {

    private final Map<String, Long> tokenStore = new ConcurrentHashMap<>();
    private final AtomicLong totalTokensIssued = new AtomicLong(0);
    private final AtomicLong duplicateBlockedCount = new AtomicLong(0);

    private static final long TOKEN_TTL_MS = 60000; // 1分钟有效

    /**
     * 生成并签发唯一幂等性令牌
     */
    public String generateToken() {
        String token = "IDEMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        tokenStore.put(token, System.currentTimeMillis() + TOKEN_TTL_MS);
        totalTokensIssued.incrementAndGet();
        return token;
    }

    /**
     * 原子消费令牌。若令牌有效且未消费返回 true；已消费或不存在返回 false
     */
    public synchronized boolean consumeToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        Long expireAt = tokenStore.get(token);
        long now = System.currentTimeMillis();

        if (expireAt == null || expireAt < now) {
            duplicateBlockedCount.incrementAndGet();
            return false;
        }

        // 成功消费，立即失效
        tokenStore.remove(token);
        return true;
    }

    public long getTotalTokensIssued() {
        return totalTokensIssued.get();
    }

    public long getDuplicateBlockedCount() {
        return duplicateBlockedCount.get();
    }
}
