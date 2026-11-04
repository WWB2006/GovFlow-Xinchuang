package com.wwb.govflow.workflow;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 政务审批工作流状态机
 * 保证状态流转合规、杜绝跨步与重复终态操作
 */
@Component
public class ApprovalStateMachine {

    private final Map<ApprovalStatus, Set<ApprovalStatus>> legalTransitions = new EnumMap<>(ApprovalStatus.class);

    public ApprovalStateMachine() {
        // SUBMITTED -> PRE_CHECKING / REJECTED
        legalTransitions.put(ApprovalStatus.SUBMITTED, EnumSet.of(ApprovalStatus.PRE_CHECKING, ApprovalStatus.REJECTED));

        // PRE_CHECKING -> DEPT_REVIEW / REJECTED
        legalTransitions.put(ApprovalStatus.PRE_CHECKING, EnumSet.of(ApprovalStatus.DEPT_REVIEW, ApprovalStatus.REJECTED));

        // DEPT_REVIEW -> APPROVED / REJECTED
        legalTransitions.put(ApprovalStatus.DEPT_REVIEW, EnumSet.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED));

        // APPROVED -> ARCHIVED (归档)
        legalTransitions.put(ApprovalStatus.APPROVED, EnumSet.of(ApprovalStatus.ARCHIVED));

        // REJECTED / ARCHIVED 为终态，不可再向外流转
        legalTransitions.put(ApprovalStatus.REJECTED, Collections.emptySet());
        legalTransitions.put(ApprovalStatus.ARCHIVED, Collections.emptySet());
    }

    /**
     * 校验从 currentStatus 流转到 targetStatus 是否合法
     */
    public boolean canTransit(ApprovalStatus currentStatus, ApprovalStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        Set<ApprovalStatus> allowed = legalTransitions.get(currentStatus);
        return allowed != null && allowed.contains(targetStatus);
    }
}
