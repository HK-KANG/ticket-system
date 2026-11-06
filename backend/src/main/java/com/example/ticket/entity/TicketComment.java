package com.example.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单回复，对应 t_ticket_comment。
 * 流水表。is_internal = 1 表示坐席内部备注，客户侧接口必须过滤掉。
 */
@Data
@TableName("t_ticket_comment")
public class TicketComment {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单 ID */
    private Long ticketId;

    /** 发言人 ID */
    private Long userId;

    /** 回复内容 */
    private String content;

    /** 是否内部备注：1 仅坐席可见 / 0 客户可见 */
    private Integer isInternal;

    /** 发言时间 */
    private LocalDateTime createTime;
}
