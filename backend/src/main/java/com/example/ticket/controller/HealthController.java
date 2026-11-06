package com.example.ticket.controller;

import com.example.ticket.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口。
 * Phase 0 用来验证三件事：接口能通、返回统一 Result、traceId 能串日志。
 */
@Slf4j
@Tag(name = "00-健康检查")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Operation(summary = "健康检查", description = "返回服务状态和时间，用于探活")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        // 这行日志会带上当前请求的 traceId，用来证明 MDC 链路打通
        log.info("健康检查被调用");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("app", "ticket-system");
        data.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return Result.ok(data);
    }
}
