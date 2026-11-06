package com.example.ticket.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工单转派请求。
 */
@Data
public class TicketTransferRequest {

    /** 转给谁 */
    @NotNull(message = "目标处理人不能为空")
    private Long toHandlerId;

    /** 转派原因，写进流转日志和派单记录 */
    @Size(max = 20, message = "原因最长 20 字")
    private String reason = "TRANSFER";
}
