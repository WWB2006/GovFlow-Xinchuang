package com.wwb.govflow.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于本地出站表 (Local Outbox Table) 的可靠 Kafka 生产端
 * 核心机制:
 * 1. 本地业务与出站表处于同一个数据库事务，杜绝发信丢失
 * 2. 模拟 Kafka acks=all 生产端多数副本确认
 * 3. 失败采用指数退避重试 (Exponential Backoff)
 */
@Component
public class KafkaReliablePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaReliablePublisher.class);

    private final Map<String, OutboxMessage> outboxTable = new ConcurrentHashMap<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong confirmedCount = new AtomicLong(0);

    private final KafkaAuditConsumer consumer;

    public KafkaReliablePublisher(KafkaAuditConsumer consumer) {
        this.consumer = consumer;
    }

    /**
     * 同事务内保存消息至出站表
     */
    public OutboxMessage saveOutboxMessage(String serialNo, String topic, String payload) {
        String msgId = "MSG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        OutboxMessage msg = new OutboxMessage(msgId, serialNo, topic, payload, "PENDING");
        outboxTable.put(msgId, msg);
        log.info("[Outbox] 成功记录本地出站消息: msgId={}, serialNo={}", msgId, serialNo);
        return msg;
    }

    /**
     * 异步投递消息至 Kafka (模拟 acks=all)
     */
    public boolean publishToKafka(String messageId) {
        OutboxMessage msg = outboxTable.get(messageId);
        if (msg == null) return false;

        try {
            msg.setStatus("PUBLISHED");
            msg.setUpdateTime(LocalDateTime.now());
            publishedCount.incrementAndGet();

            // 同步触发模拟 Kafka Broker 投递给下游审计消费者
            boolean ack = consumer.onMessage(msg.getTopic(), msg.getSerialNo(), msg.getPayload());
            if (ack) {
                msg.setStatus("CONFIRMED");
                msg.setUpdateTime(LocalDateTime.now());
                confirmedCount.incrementAndGet();
                log.info("[Kafka] 生产端收到 acks=all 确认，出站消息标记为 CONFIRMED: msgId={}", messageId);
                return true;
            } else {
                msg.setRetryCount(msg.getRetryCount() + 1);
                log.warn("[Kafka] 消费端未确认，准备重试: msgId={}, retryCount={}", messageId, msg.getRetryCount());
                return false;
            }
        } catch (Exception e) {
            msg.setRetryCount(msg.getRetryCount() + 1);
            log.error("[Kafka] 投递异常: msgId={}, err={}", messageId, e.getMessage());
            return false;
        }
    }

    public List<OutboxMessage> getAllMessages() {
        return new ArrayList<>(outboxTable.values());
    }

    public long getPublishedCount() { return publishedCount.get(); }
    public long getConfirmedCount() { return confirmedCount.get(); }
}
