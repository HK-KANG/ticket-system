package com.example.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单流转日志，对应 t_ticket_log。
 * 流水表：只增不改，所以没有 update_time 和 deleted。
 * 每张工单详情页的"流转记录"就是按 ticket_id 查这张表。
 */
@Data
@TableName("t_ticket_log")
public class TicketLog {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单 ID */
    private Long ticketId;

    /** 变更前状态，创建工单时为 null */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 操作人 ID，系统操作用 0 */
    private Long operatorId;

    /** 动作：CREATE / ACCEPT / TRANSFER / RESOLVE / CLOSE / ESCALATE */
    private String action;

    /** 备注，例如转派原因 */
    private String remark;

    /** 发生时间 */
    private LocalDateTime createTime;
}
