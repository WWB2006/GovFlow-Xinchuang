package com.wwb.govflow.controller;

import com.wwb.govflow.idempotent.IdempotencyTokenManager;
import com.wwb.govflow.lock.GovDistributedLock;
import com.wwb.govflow.outbox.KafkaReliablePublisher;
import com.wwb.govflow.service.GovApprovalService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Api(tags = "3. 控制台监控聚合接口")
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin
public class DashboardController {

    private final GovDistributedLock distributedLock;
    private final IdempotencyTokenManager tokenManager;
    private final KafkaReliablePublisher kafkaPublisher;
    private final GovApprovalService approvalService;

    public DashboardController(GovDistributedLock distributedLock,
                               IdempotencyTokenManager tokenManager,
                               KafkaReliablePublisher kafkaPublisher,
                               GovApprovalService approvalService) {
        this.distributedLock = distributedLock;
        this.tokenManager = tokenManager;
        this.kafkaPublisher = kafkaPublisher;
        this.approvalService = approvalService;
    }

    @ApiOperation(value = "获取控制台聚合指标大盘")
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return Map.of(
                "totalRecords", approvalService.getAllRecords().size(),
                "activeLocks", distributedLock.getActiveLockCount(),
                "totalLockAcquired", distributedLock.getTotalLockAcquired(),
                "watchdogRenewals", distributedLock.getTotalWatchdogRenewals(),
                "tokensIssued", tokenManager.getTotalTokensIssued(),
                "duplicateBlocked", tokenManager.getDuplicateBlockedCount(),
                "outboxMessages", kafkaPublisher.getAllMessages().size(),
                "kafkaPublished", kafkaPublisher.getPublishedCount(),
                "kafkaConfirmed", kafkaPublisher.getConfirmedCount()
        );
    }
}
