package com.example.ticket.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Excel 导出的一行。
 *
 * <p>单独一个类而不是直接用实体：实体里有 version/deleted 这类内部字段，
 * 导出的列应该由业务决定，而不是"表里有什么就导什么"。
 *
 * <p>时间字段用 String 而不是 LocalDateTime：EasyExcel 对 LocalDateTime 的默认格式
 * 依赖转换器注册，直接用字符串能保证 Excel 里看到的格式就是我们想要的。
 */
@Data
public class TicketExportRow {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExcelProperty("工单号")
    @ColumnWidth(20)
    private String ticketNo;

    @ExcelProperty("标题")
    @ColumnWidth(40)
    private String title;

    @ExcelProperty("分类")
    @ColumnWidth(10)
    private String category;

    @ExcelProperty("优先级")
    @ColumnWidth(10)
    private String priority;

    @ExcelProperty("状态")
    @ColumnWidth(10)
    private String status;

    @ExcelProperty("客户")
    @ColumnWidth(12)
    private String creatorName;

    @ExcelProperty("处理人")
    @ColumnWidth(12)
    private String handlerName;

    @ExcelProperty("技能组")
    @ColumnWidth(12)
    private String skillGroup;

    @ExcelProperty("SLA 截止")
    @ColumnWidth(20)
    private String slaDeadline;

    @ExcelProperty("创建时间")
    @ColumnWidth(20)
    private String createTime;

    @ExcelProperty("完成时间")
    @ColumnWidth(20)
    private String finishTime;

    /** 便于在 Excel 里一眼看出是否超时 */
    @ExcelProperty("是否超时")
    @ColumnWidth(10)
    private String overdue;

    /**
     * 格式化时间。
     *
     * <p>⚠️ 这里踩过一个坑：一开始用 {@code t.toString().substring(0,19)}，
     * 结果 {@code LocalDateTime.toString()} 在**秒为 0 时会省略秒**——
     * 2025-07-02T11:42:00 会输出成 "2025-07-02T11:42"（长度 16），
     * substring(0,19) 直接抛 StringIndexOutOfBoundsException。
     * 几万行数据里必然有这么几条，所以必须用格式化器，不能靠切片。
     */
    public static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(FMT);
    }
}
