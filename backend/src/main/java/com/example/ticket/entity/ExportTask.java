package com.example.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步导出任务。
 *
 * <p>为什么要有这张表：导出的活可能跑几十秒（10 万行），
 * 如果放在 HTTP 请求里跑，用户得一直等着，超时了还得重来。
 * 现在请求只建一条任务就返回，用户在页面上看进度，好了再下载。
 *
 * <p>这张表也是"任务状态机"的最小实现：PENDING → RUNNING → SUCCESS/FAILED。
 * 没有回退，失败就是失败，用户重新发起一个任务即可（比设计重试简单且够用）。
 */
@Data
@TableName("t_export_task")
public class ExportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务号，给用户看的短标识 */
    private String taskNo;

    /** 发起人，下载时校验归属要用 */
    private Long userId;

    /** PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    /** 查询条件的 JSON 快照，冻结用户点导出那一刻的筛选条件 */
    private String params;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private Integer rowCount;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
