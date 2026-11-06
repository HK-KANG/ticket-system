package com.example.ticket.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 关闭工单请求。
 */
@Data
public class TicketCloseRequest {

    /** 处理结果 / 关闭说明，会写进流转日志 */
    @Size(max = 500, message = "关闭说明最长 500 字")
    private String resolution;
}
