package com.wwb.govflow.workflow;

import java.time.LocalDateTime;

public class ApprovalRecord {
    private Long id;
    private String serialNo;
    private String itemCode;
    private String applicantName;
    private String idCardMasked;
    private ApprovalStatus status;
    private LocalDateTime applyTime;
    private LocalDateTime updateTime;
    private String auditDept;
    private String remark;

    public ApprovalRecord() {}

    public ApprovalRecord(Long id, String serialNo, String itemCode, String applicantName, String idCardMasked, ApprovalStatus status, LocalDateTime applyTime, LocalDateTime updateTime, String auditDept, String remark) {
        this.id = id;
        this.serialNo = serialNo;
        this.itemCode = itemCode;
        this.applicantName = applicantName;
        this.idCardMasked = idCardMasked;
        this.status = status;
        this.applyTime = applyTime;
        this.updateTime = updateTime;
        this.auditDept = auditDept;
        this.remark = remark;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }

    public String getIdCardMasked() { return idCardMasked; }
    public void setIdCardMasked(String idCardMasked) { this.idCardMasked = idCardMasked; }

    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }

    public LocalDateTime getApplyTime() { return applyTime; }
    public void setApplyTime(LocalDateTime applyTime) { this.applyTime = applyTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getAuditDept() { return auditDept; }
    public void setAuditDept(String auditDept) { this.auditDept = auditDept; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
