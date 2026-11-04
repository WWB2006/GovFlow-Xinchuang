package com.wwb.govflow.controller;

import com.wwb.govflow.idempotent.IdempotencyTokenManager;
import com.wwb.govflow.service.GovApprovalService;
import com.wwb.govflow.workflow.ApprovalRecord;
import com.wwb.govflow.workflow.ApprovalStatus;
import com.wwb.govflow.workflow.AppointmentSlot;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Api(tags = "1. 政务事项协同与审批流转接口 (Knife4j 规范)")
@RestController
@RequestMapping("/api/gov")
@CrossOrigin
public class GovApprovalController {

    private final GovApprovalService approvalService;
    private final IdempotencyTokenManager tokenManager;

    public GovApprovalController(GovApprovalService approvalService, IdempotencyTokenManager tokenManager) {
        this.approvalService = approvalService;
        this.tokenManager = tokenManager;
    }

    @ApiOperation(value = "获取在线申报幂等性令牌 (防前端抖动与重复点击)")
    @GetMapping("/token")
    public Map<String, Object> getIdempotencyToken() {
        String token = tokenManager.generateToken();
        return Map.of("code", 200, "token", token, "message", "令牌签发成功，有效期60秒");
    }

    @ApiOperation(value = "事项在线申报与号源争抢 (分布式锁 + CAS 配额扣减)")
    @PostMapping("/apply")
    public GovApprovalService.ApprovalResult apply(
            @ApiParam("号源时段ID") @RequestParam(defaultValue = "SLOT-20261105-AM") String slotId,
            @ApiParam("事项编码") @RequestParam(defaultValue = "ITEM-BIZ-01") String itemCode,
            @ApiParam("申报人姓名") @RequestParam(defaultValue = "张三") String applicantName,
            @ApiParam("身份证号") @RequestParam(defaultValue = "610113199801011234") String idCard,
            @ApiParam("幂等性Token") @RequestHeader(value = "X-Idempotency-Token", required = false) String idempToken
    ) {
        return approvalService.submitApproval(slotId, itemCode, applicantName, idCard, idempToken);
    }

    @ApiOperation(value = "工单审批状态机流转 (校验流转合法性与防越权)")
    @PostMapping("/transition")
    public Map<String, Object> transition(
            @ApiParam("业务流水号") @RequestParam String serialNo,
            @ApiParam("目标状态") @RequestParam ApprovalStatus targetStatus,
            @ApiParam("审查科室") @RequestParam(defaultValue = "政务审查一科") String auditDept,
            @ApiParam("办理意见") @RequestParam(defaultValue = "材料审核齐全，准予通过") String remark
    ) {
        boolean ok = approvalService.transitionStatus(serialNo, targetStatus, auditDept, remark);
        return Map.of("code", ok ? 200 : 400, "success", ok, "message", ok ? "状态流转成功" : "非法状态跨步流转！");
    }

    @ApiOperation(value = "按流水号查询办件详情")
    @GetMapping("/record/{serialNo}")
    public ApprovalRecord getRecord(@PathVariable String serialNo) {
        return approvalService.getRecord(serialNo);
    }

    @ApiOperation(value = "获取所有申报记录列表")
    @GetMapping("/records")
    public List<ApprovalRecord> getAllRecords() {
        return approvalService.getAllRecords();
    }

    @ApiOperation(value = "查询所有办理时段与号源配额")
    @GetMapping("/slots")
    public List<AppointmentSlot> getAllSlots() {
        return approvalService.getAllSlots();
    }
}
