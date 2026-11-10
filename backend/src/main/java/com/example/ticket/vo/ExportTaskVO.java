package com.example.ticket.vo;

import com.example.ticket.entity.ExportTask;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导出任务展示对象（不含 filePath，前端不需要也不该知道服务端路径）。
 */
@Data
public class ExportTaskVO {

    private Long id;

    private String taskNo;

    private String status;

    /** 状态中文，省得前端再维护一份字典 */
    private String statusLabel;

    private Integer rowCount;

    private Long fileSize;

    private String fileName;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static ExportTaskVO from(ExportTask t) {
        ExportTaskVO v = new ExportTaskVO();
        v.setId(t.getId());
        v.setTaskNo(t.getTaskNo());
        v.setStatus(t.getStatus());
        v.setStatusLabel(label(t.getStatus()));
        v.setRowCount(t.getRowCount());
        v.setFileSize(t.getFileSize());
        v.setFileName(t.getFileName());
        v.setErrorMsg(t.getErrorMsg());
        v.setCreateTime(t.getCreateTime());
        v.setUpdateTime(t.getUpdateTime());
        return v;
    }

    private static String label(String s) {
        return switch (s == null ? "" : s) {
            case "PENDING" -> "排队中";
            case "RUNNING" -> "生成中";
            case "SUCCESS" -> "已完成";
            case "FAILED" -> "失败";
            default -> s;
        };
    }
}
