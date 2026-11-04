package com.wwb.govflow.workflow;

public enum ApprovalStatus {
    SUBMITTED("已提交"),
    PRE_CHECKING("窗口预审中"),
    DEPT_REVIEW("委办局科室审查"),
    APPROVED("审批通过已办结"),
    REJECTED("审批驳回已结案"),
    ARCHIVED("电子证照已归档");

    private final String description;

    ApprovalStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
