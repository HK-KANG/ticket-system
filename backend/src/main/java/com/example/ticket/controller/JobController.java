package com.example.ticket.controller;

import com.example.ticket.common.RequireRole;
import com.example.ticket.common.Result;
import com.example.ticket.job.SlaScanJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维/测试用的手动触发接口。
 *
 * <p>定时任务本身不需要接口，这个接口存在的意义是：验收时不用等 30 秒的下一个周期，
 * 也不用去翻日志，直接调一下就知道这轮升级了几张。
 */
@Tag(name = "定时任务", description = "手动触发后台任务，便于验收")
@RestController
@RequestMapping("/api/job")
public class JobController {

    private final SlaScanJob slaScanJob;

    public JobController(SlaScanJob slaScanJob) {
        this.slaScanJob = slaScanJob;
    }

    @Operation(summary = "立即执行一次 SLA 超时扫描", description = "返回本轮升级的工单数量")
    @RequireRole("ADMIN")
    @PostMapping("/sla-scan")
    public Result<Integer> slaScan() {
        return Result.ok(slaScanJob.scanOnce());
    }
}
