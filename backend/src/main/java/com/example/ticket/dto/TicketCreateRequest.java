package com.example.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建工单请求。
 */
@Data
public class TicketCreateRequest {

    /** 标题，一句话说清问题 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长 200 字")
    private String title;

    /** 问题描述 */
    @NotBlank(message = "问题描述不能为空")
    @Size(max = 5000, message = "描述最长 5000 字")
    private String content;

    /** 分类，必须能在 t_sla_rule 里找到规则才有截止时间 */
    @NotBlank(message = "分类不能为空")
    @Size(max = 50, message = "分类最长 50 字")
    private String category;

    /** 优先级：P0 紧急 / P1 高 / P2 中 / P3 低 */
    @NotBlank(message = "优先级不能为空")
    private String priority;

    /**
     * 幂等键，客户端生成（一般用 UUID）。
     * 同一个 requestId 重复提交只会创建一张工单——网络抖动、用户连点两次都靠它兜住。
     */
    @NotBlank(message = "requestId 不能为空")
    @Size(max = 64, message = "requestId 最长 64 字符")
    private String requestId;
}
