package com.example.ticket.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticket.dto.ExportRequest;
import com.example.ticket.entity.Ticket;
import com.example.ticket.entity.User;
import com.example.ticket.mapper.TicketMapper;
import com.example.ticket.mapper.UserMapper;
import com.example.ticket.vo.TicketExportRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 真正干导出活的类：分批查库 + 边查边写 Excel。
 *
 * <p>为什么必须分批：10 万工单一次性查进内存，JVM 直接 OOM。
 * 这里每批 2000 行，用 {@code id > lastId} 的游标翻页
 * （不是 LIMIT offset——Phase 6 实测过，offset 越翻越慢）。
 *
 * <p>为什么不用 EasyExcel 的 doWrite 一次性写：那也要先把 List 攒齐。
 * 用 ExcelWriter 手动 write 多批，内存里始终只有一批数据。
 */
@Component
@Slf4j
public class TicketExcelExporter {

    private static final int BATCH = 2000;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TicketMapper ticketMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Value("${ticket.export.dir:./data/exports}")
    private String exportDir;

    @Value("${ticket.export.max-rows:50000}")
    private int maxRows;

    public TicketExcelExporter(TicketMapper ticketMapper, UserMapper userMapper, ObjectMapper objectMapper) {
        this.ticketMapper = ticketMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * @return 写出的文件路径与行数
     */
    public Exported export(String paramsJson, Long taskId) throws IOException {
        ExportRequest cond = StringUtils.hasText(paramsJson)
                ? objectMapper.readValue(paramsJson, ExportRequest.class)
                : new ExportRequest();

        Path dir = Paths.get(exportDir);
        Files.createDirectories(dir);
        Path file = dir.resolve("tickets-" + taskId + ".xlsx");

        int total = 0;
        try (ExcelWriter writer = EasyExcel.write(file.toFile(), TicketExportRow.class).build()) {
            WriteSheet sheet = EasyExcel.writerSheet("工单列表").build();
            long lastId = 0L;
            while (total < maxRows) {
                List<Ticket> batch = fetchBatch(lastId, cond);
                if (batch.isEmpty()) {
                    break;
                }
                writer.write(toRows(batch), sheet);
                lastId = batch.get(batch.size() - 1).getId();
                total += batch.size();
                if (batch.size() < BATCH) {
                    break;
                }
            }
        }
        log.info("导出完成 taskId={} rows={} file={} size={}KB",
                taskId, total, file.getFileName(), Files.size(file) / 1024);
        return new Exported(file, total, Files.size(file));
    }

    /** 按 id 游标取一批，条件沿用用户点导出时的快照 */
    private List<Ticket> fetchBatch(long lastId, ExportRequest c) {
        LambdaQueryWrapper<Ticket> w = new LambdaQueryWrapper<Ticket>().gt(Ticket::getId, lastId);
        if (StringUtils.hasText(c.getCategory())) {
            w.eq(Ticket::getCategory, c.getCategory());
        }
        if (StringUtils.hasText(c.getStatus())) {
            w.eq(Ticket::getStatus, c.getStatus());
        }
        if (StringUtils.hasText(c.getPriority())) {
            w.eq(Ticket::getPriority, c.getPriority());
        }
        if (c.getHandlerId() != null) {
            w.eq(Ticket::getHandlerId, c.getHandlerId());
        }
        if (StringUtils.hasText(c.getStartTime())) {
            w.ge(Ticket::getCreateTime, LocalDateTime.parse(c.getStartTime(), FMT));
        }
        if (StringUtils.hasText(c.getEndTime())) {
            w.le(Ticket::getCreateTime, LocalDateTime.parse(c.getEndTime(), FMT));
        }
        if (StringUtils.hasText(c.getKeyword())) {
            w.and(x -> x.like(Ticket::getTitle, c.getKeyword()).or().like(Ticket::getTicketNo, c.getKeyword()));
        }
        w.orderByAsc(Ticket::getId).last("LIMIT " + BATCH);
        return ticketMapper.selectList(w);
    }

    private List<TicketExportRow> toRows(List<Ticket> batch) {
        Map<Long, String> names = userNames(batch);
        List<TicketExportRow> rows = new ArrayList<>(batch.size());
        LocalDateTime now = LocalDateTime.now();
        for (Ticket t : batch) {
            TicketExportRow r = new TicketExportRow();
            r.setTicketNo(t.getTicketNo());
            r.setTitle(t.getTitle());
            r.setCategory(t.getCategory());
            r.setPriority(t.getPriority());
            r.setStatus(t.getStatus());
            r.setCreatorName(names.getOrDefault(t.getCreatorId(), ""));
            r.setHandlerName(t.getHandlerId() == null ? "" : names.getOrDefault(t.getHandlerId(), ""));
            r.setSkillGroup(t.getSkillGroup());
            r.setSlaDeadline(TicketExportRow.fmt(t.getSlaDeadline()));
            r.setCreateTime(TicketExportRow.fmt(t.getCreateTime()));
            r.setFinishTime(TicketExportRow.fmt(t.getFinishTime()));
            boolean overdue = t.getSlaDeadline() != null
                    && (t.getFinishTime() == null ? now.isAfter(t.getSlaDeadline())
                                                  : t.getFinishTime().isAfter(t.getSlaDeadline()));
            r.setOverdue(overdue ? "是" : "否");
            rows.add(r);
        }
        return rows;
    }

    /** 一批 2000 行里可能涉及很多用户，一次 IN 查完，不要循环里查 */
    private Map<Long, String> userNames(List<Ticket> batch) {
        List<Long> ids = new ArrayList<>();
        for (Ticket t : batch) {
            if (t.getCreatorId() != null) {
                ids.add(t.getCreatorId());
            }
            if (t.getHandlerId() != null) {
                ids.add(t.getHandlerId());
            }
        }
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (User u : userMapper.selectBatchIds(ids.stream().distinct().toList())) {
            map.put(u.getId(), u.getRealName());
        }
        return map;
    }

    public record Exported(Path path, int rows, long size) {
    }
}
