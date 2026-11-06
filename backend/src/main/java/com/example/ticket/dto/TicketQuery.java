package com.example.ticket.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单分页查询条件。
 *
 * <p>六个条件可任意组合，全部为空时按角色返回可见范围内的全部工单。
 * 注意：creatorId 不在这里——它必须由服务端从 token 里取，
 * 让前端传 creatorId 等于让用户自己决定能看谁的数据。
 */
@Data
public class TicketQuery {

    /** 页码，从 1 开始 */
    private Long page = 1L;

    /** 每页条数，服务端限制上限 100 */
    private Long size = 10L;

    /** 分类 */
    private String category;

    /** 状态 */
    private String status;

    /** 优先级 */
    private String priority;

    /** 处理人 ID（管理员按坐席筛选时用） */
    private Long handlerId;

    /** 创建时间起（含） */
    private LocalDateTime createTimeStart;

    /** 创建时间止（含） */
    private LocalDateTime createTimeEnd;

    /** 关键词，模糊匹配标题或单号 */
    private String keyword;
}
