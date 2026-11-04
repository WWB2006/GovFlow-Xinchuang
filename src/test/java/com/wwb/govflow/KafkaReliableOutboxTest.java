package com.wwb.govflow;

import com.wwb.govflow.outbox.KafkaAuditConsumer;
import com.wwb.govflow.outbox.KafkaReliablePublisher;
import com.wwb.govflow.outbox.OutboxMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Kafka 本地出站表事务一致性与手动提交位移测试")
public class KafkaReliableOutboxTest {

    @Autowired
    private KafkaReliablePublisher publisher;

    @Autowired
    private KafkaAuditConsumer consumer;

    @Test
    @DisplayName("出站表测试：事务内落库 PENDING 并在模拟 Kafka acks=all 确认后流转为 CONFIRMED")
    void testOutboxLifecycle() {
        String serialNo = "GOV-TEST-" + System.currentTimeMillis();
        OutboxMessage msg = publisher.saveOutboxMessage(serialNo, "gov.approval.audit", "{\"action\":\"TEST\"}");
        assertNotNull(msg);
        assertEquals("PENDING", msg.getStatus());

        boolean ok = publisher.publishToKafka(msg.getMessageId());
        assertTrue(ok);
        assertEquals("CONFIRMED", msg.getStatus());
        assertTrue(publisher.getConfirmedCount() >= 1);
    }

    @Test
    @DisplayName("消费端幂等测试：相同 serialNo 消息被重复投递时，手动位移确认且不重复入库")
    void testConsumerDeduplication() {
        String testSerialNo = "GOV-IDEMP-" + System.currentTimeMillis();

        // 第一次投递消费
        boolean firstAck = consumer.onMessage("gov.approval.audit", testSerialNo, "{\"payload\":\"FIRST\"}");
        assertTrue(firstAck);

        // 第二次重复投递同一流水号
        boolean secondAck = consumer.onMessage("gov.approval.audit", testSerialNo, "{\"payload\":\"REPEAT\"}");
        assertTrue(secondAck); // 依然返回 ACK 防止 Kafka 阻塞
        assertTrue(consumer.getDuplicateDroppedCount() >= 1);
    }
}
