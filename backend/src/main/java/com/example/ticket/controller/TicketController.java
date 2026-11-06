package com.example.ticket.controller;

import com.example.ticket.common.PageResult;
import com.example.ticket.common.RequireRole;
import com.example.ticket.common.Result;
import com.example.ticket.dto.TicketCloseRequest;
import com.example.ticket.dto.TicketCommentRequest;
import com.example.ticket.dto.TicketCreateRequest;
import com.example.ticket.dto.TicketQuery;
import com.example.ticket.dto.TicketTransferRequest;
import com.example.ticket.service.TicketService;
import com.example.ticket.vo.TicketDetailVO;
import com.example.ticket.vo.TicketStatsVO;
import com.example.ticket.vo.TicketVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单接口。
 *
 * <p>Controller 只做两件事：接参校验 + 调 Service。业务逻辑一律不写在这里，
 * 否则同一段规则会在多个接口里复制一遍。
 *
 * <p>数据范围（客户只看自己的、坐席只看本组和自己在办的）在 Service 层实现，
 * 因为它是业务规则，而且列表、详情、回复、关闭四个入口都要用同一套判断。
 */
@Tag(name = "工单", description = "创建、查询、详情、回复、转派、关闭、统计")
@RestController
@RequestMapping("/api/ticket")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * 创建工单。只有客户能提交（坐席和管理员不替客户提单）。
     */
    @Operation(summary = "创建工单", description = "requestId 相同则只创建一次（幂等）")
    @RequireRole("CUSTOMER")
    @PostMapping("/create")
    public Result<TicketVO> create(@Valid @RequestBody TicketCreateRequest req) {
        return Result.ok(ticketService.create(req));
    }

    /**
     * 分页查询。查询条件放 query string，六条件任意组合。
     */
    @Operation(summary = "分页查询", description = "结果按角色自动收窄：客户只看自己，坐席看本组待抢与自己在办")
    @GetMapping("/page")
    public Result<PageResult<TicketVO>> page(TicketQuery query) {
        return Result.ok(ticketService.page(query));
    }

    /**
     * 工单详情，含流转日志与回复。
     */
    @Operation(summary = "工单详情", description = "客户视角会自动过滤掉内部备注")
    @GetMapping("/{id}")
    public Result<TicketDetailVO> detail(@PathVariable Long id) {
        return Result.ok(ticketService.detail(id));
    }

    /**
     * 回复工单。坐席可发内部备注，客户只能发公开回复。
     */
    @Operation(summary = "回复工单", description = "internal=true 为内部备注，仅坐席可见")
    @PostMapping("/{id}/comment")
    public Result<Void> comment(@PathVariable Long id, @Valid @RequestBody TicketCommentRequest req) {
        ticketService.comment(id, req);
        return Result.ok();
    }

    /**
     * 转派工单。
     */
    @Operation(summary = "转派", description = "管理员可转任意单，坐席只能转自己手上的")
    @RequireRole({"AGENT", "ADMIN"})
    @PostMapping("/{id}/transfer")
    public Result<Void> transfer(@PathVariable Long id, @Valid @RequestBody TicketTransferRequest req) {
        ticketService.transfer(id, req);
        return Result.ok();
    }

    /**
     * 关闭工单。
     */
    @Operation(summary = "关闭工单", description = "待派单状态不允许直接关闭，会返回业务错误")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id, @Valid @RequestBody TicketCloseRequest req) {
        ticketService.close(id, req);
        return Result.ok();
    }

    /**
     * 工单统计，仅管理员。
     */
    @Operation(summary = "统计", description = "状态/分类/优先级分布、SLA 达成率、近 7 天趋势")
    @RequireRole("ADMIN")
    @GetMapping("/stats")
    public Result<TicketStatsVO> stats() {
        return Result.ok(ticketService.stats());
    }
}
