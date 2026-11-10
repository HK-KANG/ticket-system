package com.example.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ticket.common.BizException;
import com.example.ticket.common.ForbiddenException;
import com.example.ticket.common.NotFoundException;
import com.example.ticket.common.ResultCode;
import com.example.ticket.config.RabbitConfig;
import com.example.ticket.dto.ExportRequest;
import com.example.ticket.entity.ExportTask;
import com.example.ticket.mapper.ExportTaskMapper;
import com.example.ticket.mq.ExportMessage;
import com.example.ticket.security.LoginUser;
import com.example.ticket.security.UserContext;
import com.example.ticket.vo.ExportTaskVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 导出任务的生命周期管理。
 *
 * <p>请求进来只做三件事：落一条任务、投一条 MQ、立刻返回。
 * 真正的查库写文件在 {@link TicketExcelExporter} 里，由消费者触发。
 */
@Service
@Slf4j
public class ExportService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RND = new SecureRandom();

    private final ExportTaskMapper taskMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ticket.export.max-rows:50000}")
    private int maxRows;

    public ExportService(ExportTaskMapper taskMapper, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建导出任务。
     *
     * <p>发 MQ 失败的处理：把任务直接标成 FAILED 并告诉用户"请重试"，
     * 而不是留在 PENDING 让前端永远转圈——用户看到的每个状态都必须有终点。
     */
    @Transactional(rollbackFor = Exception.class)
    public ExportTaskVO create(ExportRequest req) {
        LoginUser user = UserContext.require();

        ExportTask task = new ExportTask();
        task.setTaskNo("EXP" + LocalDateTime.now().format(NO_FMT)
                + String.format("%04d", RND.nextInt(10000)));
        task.setUserId(user.userId());
        task.setStatus("PENDING");
        try {
            task.setParams(objectMapper.writeValueAsString(req == null ? new ExportRequest() : req));
        } catch (Exception e) {
            task.setParams("{}");
        }
        taskMapper.insert(task);

        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXPORT_EXCHANGE,
                    RabbitConfig.EXPORT_ROUTING_KEY, new ExportMessage(task.getId()));
            log.info("导出任务已入队 taskNo={} id={}", task.getTaskNo(), task.getId());
        } catch (Exception e) {
            log.error("导出任务投递 MQ 失败 id={}", task.getId(), e);
            fail(task.getId(), "任务排队失败，请重试");
            throw new BizException(ResultCode.BIZ_ERROR, "导出任务创建失败，请稍后重试");
        }
        return ExportTaskVO.from(task);
    }

    /**
     * 抢占任务：PENDING → RUNNING。返回 true 表示由本次调用负责生成文件。
     *
     * <p>用条件更新而不是"先查后改"：MQ 可能重复投递同一条消息，
     * 两个消费者同时处理的概率虽然低，但一旦发生就会写出两个文件、状态互相覆盖。
     * 条件更新保证只有第一个能把 PENDING 改成 RUNNING。
     */
    public boolean claim(Long taskId) {
        LambdaUpdateWrapper<ExportTask> uw = Wrappers.<ExportTask>lambdaUpdate()
                .eq(ExportTask::getId, taskId)
                .eq(ExportTask::getStatus, "PENDING")
                .set(ExportTask::getStatus, "RUNNING");
        return taskMapper.update(null, uw) > 0;
    }

    public void markSuccess(Long taskId, String fileName, Path path, long size, int rows) {
        ExportTask t = new ExportTask();
        t.setId(taskId);
        t.setStatus("SUCCESS");
        t.setFileName(fileName);
        t.setFilePath(path.toAbsolutePath().toString());
        t.setFileSize(size);
        t.setRowCount(rows);
        taskMapper.updateById(t);
    }

    public void fail(Long taskId, String msg) {
        ExportTask t = new ExportTask();
        t.setId(taskId);
        t.setStatus("FAILED");
        t.setErrorMsg(msg != null && msg.length() > 480 ? msg.substring(0, 480) : msg);
        taskMapper.updateById(t);
    }

    public ExportTask findById(Long id) {
        return taskMapper.selectById(id);
    }

    /** 我的导出记录，最新的在前 */
    public List<ExportTaskVO> listMine(int limit) {
        LoginUser user = UserContext.require();
        int n = Math.min(Math.max(limit, 1), 50);
        List<ExportTask> list = taskMapper.selectList(new LambdaQueryWrapper<ExportTask>()
                .eq(ExportTask::getUserId, user.userId())
                .orderByDesc(ExportTask::getId)
                .last("LIMIT " + n));
        return list.stream().map(ExportTaskVO::from).toList();
    }

    /**
     * 下载前校验：只能下自己的；管理员可以下任何人的。
     */
    public ExportTask requireDownloadable(Long id) {
        LoginUser user = UserContext.require();
        ExportTask t = taskMapper.selectById(id);
        if (t == null) {
            throw new NotFoundException("导出任务不存在");
        }
        if (!"ADMIN".equals(user.role()) && !user.userId().equals(t.getUserId())) {
            throw new ForbiddenException("只能下载自己发起的导出");
        }
        if (!"SUCCESS".equals(t.getStatus())) {
            throw new BizException(ResultCode.BIZ_ERROR, "文件还没生成好");
        }
        if (t.getFilePath() == null || !Files.exists(Path.of(t.getFilePath()))) {
            throw new NotFoundException("文件已过期或被清理，请重新导出");
        }
        return t;
    }

    public int maxRows() {
        return maxRows;
    }
}
