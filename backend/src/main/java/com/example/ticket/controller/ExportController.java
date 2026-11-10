package com.example.ticket.controller;

import com.example.ticket.common.RequireRole;
import com.example.ticket.common.Result;
import com.example.ticket.dto.ExportRequest;
import com.example.ticket.entity.ExportTask;
import com.example.ticket.service.ExportService;
import com.example.ticket.vo.ExportTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * 异步导出接口。
 *
 * <p>三个接口构成完整闭环：发起 → 查状态 → 下载。
 * 发起只返回任务号，不等文件生成——10 万行 Excel 要跑几秒到几十秒，
 * 同步接口早就超时了。
 */
@Tag(name = "导出", description = "异步导出工单为 Excel")
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @Operation(summary = "发起导出", description = "立即返回任务号，文件在后台生成；仅管理员")
    @RequireRole("ADMIN")
    @PostMapping("/ticket")
    public Result<ExportTaskVO> create(@RequestBody(required = false) ExportRequest req) {
        return Result.ok(exportService.create(req));
    }

    @Operation(summary = "我的导出记录", description = "按时间倒序，用于前端轮询进度")
    @GetMapping("/tasks")
    public Result<List<ExportTaskVO>> tasks(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.ok(exportService.listMine(limit));
    }

    @Operation(summary = "下载导出文件", description = "仅任务发起人或管理员可下载")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        ExportTask task = exportService.requireDownloadable(id);
        Path path = Path.of(task.getFilePath());
        Resource resource = new FileSystemResource(path);
        String encoded = URLEncoder.encode(task.getFileName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(task.getFileSize() == null ? 0 : task.getFileSize())
                .body(resource);
    }
}
