package com.example.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SLA 超时规则，对应 t_sla_rule。
 * 创建工单时按 (category, priority) 唯一命中一条，用 sla_minutes 算出 sla_deadline。
 */
@Data
@TableName("t_sla_rule")
public class SlaRule {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类 */
    private String category;

    /** 优先级 */
    private String priority;

    /** 响应时限（分钟） */
    private Integer slaMinutes;

    /** 超时后升级到哪个技能组 */
    private String escalateToGroup;

    /** 是否启用：1 启用 / 0 停用 */
    private Integer enabled;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;
}
