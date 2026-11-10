package com.example.ticket.dto;

import lombok.Data;

/**
 * 导出请求：复用列表页的筛选条件。
 *
 * <p>不继承 {@code TicketQuery} 是刻意的：列表分页要 page/size，
 * 导出不需要，混在一起容易让调用方传了 size 却以为能限制导出条数。
 * 实际限制在服务端配置里（上限 5 万行）。
 */
@Data
public class ExportRequest {

    private String category;

    private String status;

    private String priority;

    private Long handlerId;

    /** 创建时间起，格式 yyyy-MM-dd HH:mm:ss */
    private String startTime;

    /** 创建时间止 */
    private String endTime;

    /** 标题关键字 */
    private String keyword;
}
