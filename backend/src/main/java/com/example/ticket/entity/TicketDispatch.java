package com.example.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派单记录，对应 t_ticket_dispatch。
 * 流水表，记录工单在坐席之间的每一次流转，用于详情页展示轨迹和统计转派率。
 */
@Data
@TableName("t_ticket_dispatch")
public class TicketDispatch {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单 ID */
    private Long ticketId;

    /** 原处理人 ID，首次派单为 null */
    private Long fromHandlerId;

    /** 新处理人 ID */
    private Long toHandlerId;

    /** 派单原因：AUTO / MANUAL / TRANSFER / ESCALATE */
    private String reason;

    /** 派单时间 */
    private LocalDateTime createTime;
}
