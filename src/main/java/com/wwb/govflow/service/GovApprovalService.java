package com.wwb.govflow.service;

import com.wwb.govflow.idempotent.IdempotencyTokenManager;
import com.wwb.govflow.lock.GovDistributedLock;
import com.wwb.govflow.outbox.KafkaReliablePublisher;
import com.wwb.govflow.outbox.OutboxMessage;
import com.wwb.govflow.workflow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GovApprovalService {

    private static final Logger log = LoggerFactory.getLogger(GovApprovalService.class);

    private final GovDistributedLock distributedLock;
    private final IdempotencyTokenManager tokenManager;
    private final ApprovalStateMachine stateMachine;
    private final KafkaReliablePublisher kafkaPublisher;

    private final Map<String, ApprovalRecord> recordStorage = new ConcurrentHashMap<>();
    private final Map<String, AppointmentSlot> slotStorage = new ConcurrentHashMap<>();
    private final AtomicLong recordIdGenerator = new AtomicLong(1000);

    public GovApprovalService(GovDistributedLock distributedLock,
                              IdempotencyTokenManager tokenManager,
                              ApprovalStateMachine stateMachine,
                              KafkaReliablePublisher kafkaPublisher) {
        this.distributedLock = distributedLock;
        this.tokenManager = tokenManager;
        this.stateMachine = stateMachine;
        this.kafkaPublisher = kafkaPublisher;
    }

    @PostConstruct
    public void initDefaultSlots() {
        // 初始化默认政务办理号源配额
        slotStorage.put("SLOT-20261105-AM", new AppointmentSlot("SLOT-20261105-AM", "ITEM-BIZ-01", "2026-11-05", "上午", 50, 50, 1));
        slotStorage.put("SLOT-20261105-PM", new AppointmentSlot("SLOT-20261105-PM", "ITEM-BIZ-01", "2026-11-05", "下午", 50, 50, 1));
    }

    /**
     * 事项在线申报与号源抢占主业务链路 (分布式锁 + 幂等 + 本地出站表 + 异步Kafka)
     */
    public ApprovalResult submitApproval(String slotId, String itemCode, String applicantName, String idCard, String idempToken) {
        // 1. 幂等性 Token 校验防重复提交
        if (idempToken != null && !idempToken.trim().isEmpty()) {
            boolean validToken = tokenManager.consumeToken(idempToken);
            if (!validToken) {
                return new ApprovalResult(false, null, "DUPLICATE_SUBMISSION", "检测到重复提交或令牌已失效，已被幂等拦截！");
            }
        }

        AppointmentSlot slot = slotStorage.get(slotId);
        if (slot == null) {
            return new ApprovalResult(false, null, "SLOT_NOT_FOUND", "指定的政务办理时段号源不存在");
        }

        // 2. 分布式互斥锁争抢 (租约 3000ms + Watchdog 自动续期)
        String lockKey = "lock:gov:slot:" + slotId;
        String ownerToken = UUID.randomUUID().toString();
        boolean locked = distributedLock.tryLock(lockKey, ownerToken, 3000);

        if (!locked) {
            return new ApprovalResult(false, null, "LOCK_CONFLICT", "当前时段号源正被其他办事人员争抢，请稍后重试");
        }

        try {
            // 3. 配额原子扣减 (乐观锁防超发)
            boolean deducted = slot.deductQuota();
            if (!deducted) {
                return new ApprovalResult(false, null, "QUOTA_EXHAUSTED", "该时段号源已被预约完毕，无可分配配额");
            }

            // 4. 创建审批流初始记录 (SUBMITTED)
            String serialNo = "GOV-" + System.currentTimeMillis() + "-" + (recordIdGenerator.incrementAndGet() % 1000);
            String idCardMasked = idCard != null && idCard.length() >= 8 ?
                    idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4) : "6101**********1234";

            ApprovalRecord record = new ApprovalRecord(
                    recordIdGenerator.get(),
                    serialNo,
                    itemCode,
                    applicantName,
                    idCardMasked,
                    ApprovalStatus.SUBMITTED,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    "市民服务中心综窗科",
                    "网上申报成功，号源已锁定"
            );
            recordStorage.put(serialNo, record);

            // 5. 同事务记录本地出站表
            OutboxMessage outboxMsg = kafkaPublisher.saveOutboxMessage(
                    serialNo,
                    "gov.approval.audit",
                    "{\"serialNo\":\"" + serialNo + "\",\"itemCode\":\"" + itemCode + "\",\"status\":\"SUBMITTED\"}"
            );

            // 6. 异步发布至 Kafka
            kafkaPublisher.publishToKafka(outboxMsg.getMessageId());

            return new ApprovalResult(true, serialNo, "SUCCESS", "政务事项申报成功，流水号已生成！");
        } finally {
            // 7. 安全释放分布式锁
            distributedLock.unlock(lockKey, ownerToken);
        }
    }

    /**
     * 审批流状态机流转
     */
    public boolean transitionStatus(String serialNo, ApprovalStatus targetStatus, String auditorDept, String remark) {
        ApprovalRecord record = recordStorage.get(serialNo);
        if (record == null) return false;

        if (!stateMachine.canTransit(record.getStatus(), targetStatus)) {
            log.warn("[Workflow] 非法状态流转尝试: 当前={}, 目标={}", record.getStatus(), targetStatus);
            return false;
        }

        record.setStatus(targetStatus);
        record.setUpdateTime(LocalDateTime.now());
        record.setAuditDept(auditorDept);
        record.setRemark(remark);

        // 发送流转变更通知
        OutboxMessage msg = kafkaPublisher.saveOutboxMessage(
                serialNo,
                "gov.approval.audit",
                "{\"serialNo\":\"" + serialNo + "\",\"status\":\"" + targetStatus.name() + "\"}"
        );
        kafkaPublisher.publishToKafka(msg.getMessageId());
        return true;
    }

    public ApprovalRecord getRecord(String serialNo) {
        return recordStorage.get(serialNo);
    }

    public List<ApprovalRecord> getAllRecords() {
        return new ArrayList<>(recordStorage.values());
    }

    public List<AppointmentSlot> getAllSlots() {
        return new ArrayList<>(slotStorage.values());
    }

    public static class ApprovalResult {
        public boolean success;
        public String serialNo;
        public String code;
        public String message;

        public ApprovalResult(boolean success, String serialNo, String code, String message) {
            this.success = success;
            this.serialNo = serialNo;
            this.code = code;
            this.message = message;
        }
    }
}
