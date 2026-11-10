package com.example.ticket.common;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 游标分页结果。
 *
 * <p>和 {@link PageResult} 的区别：没有 total，也没有页码，只有"下一页从哪开始"。
 * 代价是不能跳页；收益是无论翻到多深，查询耗时都一样。
 *
 * <p>{@code nextCreateTime} + {@code nextId} 组成游标——只用时间不够，
 * 同一毫秒可能有多条工单，必须带上 id 才能唯一定位。
 */
@Data
public class CursorResult<T> {

    private List<T> records;

    /** 下一页的锚点时间，null 表示没有下一页 */
    private LocalDateTime nextCreateTime;

    /** 下一页的锚点 id，与 nextCreateTime 一起用 */
    private Long nextId;

    private boolean hasMore;

    public static <T> CursorResult<T> of(List<T> records, LocalDateTime nextTime, Long nextId, boolean hasMore) {
        CursorResult<T> r = new CursorResult<>();
        r.setRecords(records);
        r.setNextCreateTime(nextTime);
        r.setNextId(nextId);
        r.setHasMore(hasMore);
        return r;
    }
}
