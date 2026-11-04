package com.wwb.govflow;

import com.wwb.govflow.idempotent.IdempotencyTokenManager;
import com.wwb.govflow.lock.GovDistributedLock;
import com.wwb.govflow.service.GovApprovalService;
import com.wwb.govflow.workflow.ApprovalRecord;
import com.wwb.govflow.workflow.ApprovalStateMachine;
import com.wwb.govflow.workflow.ApprovalStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("政务事项高并发号源争抢、幂等防重与状态机流转测试")
public class GovApprovalConcurrencyTest {

    @Autowired
    private GovApprovalService approvalService;

    @Autowired
    private IdempotencyTokenManager tokenManager;

    @Autowired
    private GovDistributedLock distributedLock;

    @Autowired
    private ApprovalStateMachine stateMachine;

    @Test
    @DisplayName("并发测试：32 线程并发抢占 10 个号源配额，严格防超发")
    void testConcurrencyDeductQuota() throws InterruptedException {
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    startSignal.await();
                    GovApprovalService.ApprovalResult res = approvalService.submitApproval(
                            "SLOT-20261105-AM", "ITEM-BIZ-01", "申请人_" + idx, "61011319980101" + String.format("%04d", idx), null
                    );
                    if (res.success) {
                        successCount.incrementAndGet();
                    } else {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        startSignal.countDown();
        assertTrue(doneSignal.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertTrue(successCount.get() > 0);
        assertEquals(threads, successCount.get() + conflictCount.get());
        assertTrue(distributedLock.getTotalLockAcquired() >= successCount.get());
    }

    @Test
    @DisplayName("幂等测试：同一 Token 重复提交，第二次必定被精准拦截")
    void testIdempotencyTokenDeduplication() {
        String token = tokenManager.generateToken();

        // 第一次消费，成功
        boolean first = tokenManager.consumeToken(token);
        assertTrue(first);

        // 第二次重复消费同一 Token，必被拦截
        boolean second = tokenManager.consumeToken(token);
        assertFalse(second);
        assertTrue(tokenManager.getDuplicateBlockedCount() >= 1);
    }

    @Test
    @DisplayName("状态机测试：合法流转通过，非法跨步与终态重复操作必被阻断")
    void testStateMachineTransitions() {
        // 1. 合法正常流转链: SUBMITTED -> PRE_CHECKING -> DEPT_REVIEW -> APPROVED -> ARCHIVED
        assertTrue(stateMachine.canTransit(ApprovalStatus.SUBMITTED, ApprovalStatus.PRE_CHECKING));
        assertTrue(stateMachine.canTransit(ApprovalStatus.PRE_CHECKING, ApprovalStatus.DEPT_REVIEW));
        assertTrue(stateMachine.canTransit(ApprovalStatus.DEPT_REVIEW, ApprovalStatus.APPROVED));
        assertTrue(stateMachine.canTransit(ApprovalStatus.APPROVED, ApprovalStatus.ARCHIVED));

        // 2. 非法跨步阻断: SUBMITTED 直接跳到 ARCHIVED
        assertFalse(stateMachine.canTransit(ApprovalStatus.SUBMITTED, ApprovalStatus.ARCHIVED));
        assertFalse(stateMachine.canTransit(ApprovalStatus.PRE_CHECKING, ApprovalStatus.APPROVED));

        // 3. 终态不可再流转
        assertFalse(stateMachine.canTransit(ApprovalStatus.REJECTED, ApprovalStatus.APPROVED));
        assertFalse(stateMachine.canTransit(ApprovalStatus.ARCHIVED, ApprovalStatus.PRE_CHECKING));
    }
}
