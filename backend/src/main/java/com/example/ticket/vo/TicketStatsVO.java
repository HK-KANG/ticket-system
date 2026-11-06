package com.example.ticket.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工单统计。
 *
 * <p>现在是实时聚合查询，数据量大了会慢——Phase 6 会改成定时预聚合 + 缓存，
 * 并记录改造前后的耗时对比。这里先做成基线。
 */
@Data
public class TicketStatsVO {

    /** 总工单数 */
    private long total;

    /** 按状态分组计数，如 {PENDING: 3, CLOSED: 10} */
    private Map<String, Long> statusCount;

    /** 按分类分组计数 */
    private Map<String, Long> categoryCount;

    /** 按优先级分组计数 */
    private Map<String, Long> priorityCount;

    /** SLA 达成率（已关闭且未超时的比例），0-100 的百分数 */
    private double slaAchieveRate;

    /** 已超时工单数 */
    private long overdueCount;

    /** 近 7 天每天新增工单数，供折线图用 */
    private List<DailyCount> dailyTrend;

    /** 单日计数 */
    @Data
    public static class DailyCount {
        private String date;
        private long count;
    }
}
