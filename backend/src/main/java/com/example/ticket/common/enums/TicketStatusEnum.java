package com.example.ticket.common.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 工单状态与合法流转表。
 *
 * <p>把「什么状态能到什么状态」集中在这里，是为了避免业务代码里散落
 * {@code if (status.equals("PENDING")) ...} 这种判断——那种写法一旦状态变多，
 * 没人能说清全系统到底允许哪些跳转。
 *
 * <p>非法流转由 {@link #canTransitTo} 判定，Service 层据此抛业务异常。
 */
public enum TicketStatusEnum {

    /** 待派单：刚创建，还没人接 */
    PENDING("待派单"),

    /** 已派单：已经指定了处理人，但还没开始处理 */
    ASSIGNED("已派单"),

    /** 处理中：处理人已接单 */
    PROCESSING("处理中"),

    /** 已解决：处理人标记完成，等客户确认 */
    RESOLVED("已解决"),

    /** 已关闭：流程终结 */
    CLOSED("已关闭"),

    /** 已升级：超时未响应，被系统升级到上级组 */
    ESCALATED("已升级");

    private final String label;

    TicketStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 合法流转表。
     *
     * 注意 PENDING 只能到 ASSIGNED：任务书要求「PENDING → CLOSED」必须报错，
     * 一张没人处理过的单子直接关掉等于把问题消失掉，业务上不允许。
     */
    public Set<TicketStatusEnum> allowedTargets() {
        return switch (this) {
            case PENDING -> EnumSet.of(ASSIGNED, ESCALATED);
            case ASSIGNED -> EnumSet.of(PROCESSING, CLOSED, ESCALATED);
            case PROCESSING -> EnumSet.of(RESOLVED, CLOSED, ESCALATED);
            case RESOLVED -> EnumSet.of(CLOSED, PROCESSING);
            case ESCALATED -> EnumSet.of(ASSIGNED, PROCESSING, CLOSED);
            // 终态：没有任何出边
            case CLOSED -> EnumSet.noneOf(TicketStatusEnum.class);
        };
    }

    /** 当前状态能否流转到目标状态 */
    public boolean canTransitTo(TicketStatusEnum target) {
        return allowedTargets().contains(target);
    }

    /** 是否终态（已关闭） */
    public boolean isFinal() {
        return this == CLOSED;
    }

    /** 按名称取枚举，非法值抛异常（宁可报错，也不要静默当成某个默认状态） */
    public static TicketStatusEnum of(String name) {
        for (TicketStatusEnum s : values()) {
            if (s.name().equalsIgnoreCase(name)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知工单状态: " + name);
    }
}
