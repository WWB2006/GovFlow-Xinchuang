package com.wwb.govflow.outbox;

import java.time.LocalDateTime;

public class OutboxMessage {
    private String messageId;
    private String serialNo;
    private String topic;
    private String payload;
    private String status; // PENDING, PUBLISHED, CONFIRMED
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OutboxMessage() {}

    public OutboxMessage(String messageId, String serialNo, String topic, String payload, String status) {
        this.messageId = messageId;
        this.serialNo = serialNo;
        this.topic = topic;
        this.payload = payload;
        this.status = status;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdateTime() { return updatedAt; }
    public void setUpdateTime(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
