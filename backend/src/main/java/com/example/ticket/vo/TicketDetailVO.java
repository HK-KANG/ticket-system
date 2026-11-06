package com.example.ticket.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单详情：列表项 + 描述 + 流转日志 + 回复。
 *
 * <p>详情页一次请求就够，不做"点开详情再拉日志、再拉回复"三次请求——
 * 移动网络下三次往返的耗时用户能感觉到。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TicketDetailVO extends TicketVO {

    /** 问题描述（列表页不返回，详情才给，避免列表接口返回大量文本） */
    private String content;

    /** 流转日志，按时间正序 */
    private List<LogItem> logs;

    /** 回复列表，按时间正序；客户视角已过滤掉内部备注 */
    private List<CommentItem> comments;

    /** 一条流转记录 */
    @Data
    public static class LogItem {
        private String fromStatus;
        private String toStatus;
        private String action;
        private String operatorName;
        private String remark;
        private LocalDateTime createTime;
    }

    /** 一条回复 */
    @Data
    public static class CommentItem {
        private Long id;
        private Long userId;
        private String userName;
        private String content;
        /** 1 = 内部备注，仅坐席可见 */
        private Integer isInternal;
        private LocalDateTime createTime;
    }
}
