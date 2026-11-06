package com.example.ticket.common;

import lombok.Data;

import java.util.List;

/**
 * 分页返回结构。
 *
 * <p>为什么不直接返回 MyBatis-Plus 的 Page 对象：Page 里带了一堆前端用不到的内部字段
 * （optimizeCountSql、searchCount、orders 等），直接吐给前端既冗余又暴露实现细节。
 * 这里只保留四个字段。
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 总条数 */
    private long total;

    /** 当前页码，从 1 开始 */
    private long page;

    /** 每页条数 */
    private long size;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        PageResult<T> r = new PageResult<>();
        r.records = records;
        r.total = total;
        r.page = page;
        r.size = size;
        return r;
    }
}
