package com.example.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单主表实体，对应 t_ticket。
 * 全系统的核心表，状态机与并发抢单都围绕它。
 */
@Data
@TableName("t_ticket")
public class Ticket {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务单号，对外展示，唯一 */
    private String ticketNo;

    /** 标题 */
    private String title;

    /** 问题描述 */
    private String content;

    /** 分类，对应 t_sla_rule.category */
    private String category;

    /** 优先级：P0 / P1 / P2 / P3 */
    private String priority;

    /** 状态：PENDING / ASSIGNED / PROCESSING / RESOLVED / CLOSED / ESCALATED */
    private String status;

    /** 创建人（客户）ID */
    private Long creatorId;

    /** 当前处理人（坐席）ID，未派单为 null */
    private Long handlerId;

    /** 所需技能组，抢单大厅按此过滤 */
    private String skillGroup;

    /** 乐观锁版本号。抢单时 UPDATE ... WHERE id=? AND version=?，
     *  影响行数为 0 说明被别人抢走了，据此判断抢单失败 */
    @Version
    private Integer version;

    /** SLA 截止时间，超时扫描任务按它判断 */
    private LocalDateTime slaDeadline;

    /** 接单时间 */
    private LocalDateTime acceptTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 是否已升级：0 否 / 1 是，防止重复升级 */
    private Integer escalateFlag;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;
}
