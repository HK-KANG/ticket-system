package com.example.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ticket.common.enums.TicketStatusEnum;
import com.example.ticket.entity.Ticket;
import com.example.ticket.entity.TicketDispatch;
import com.example.ticket.entity.TicketLog;
import com.example.ticket.mapper.TicketDispatchMapper;
import com.example.ticket.mapper.TicketLogMapper;
import com.example.ticket.mapper.TicketMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SLA 超时升级。
 *
 * <p>两个入口，一套逻辑：
 * <ul>
 *   <li>{@link #escalateIfDue} —— MQ 延迟消息到点时调用（尽量准时）</li>
 *   <li>{@link #escalateOverdueShard} —— 定时扫描兜底（每 30 秒）</li>
 * </ul>
 * 两者都走“条件更新”，所以同时到达也只会有一次生效——幂等靠 SQL 条件，不靠调用方。
 *
 * <p>为什么用 {@link TransactionTemplate} 而不是在方法上标 {@code @Transactional}：
 * <ol>
 *   <li>扫描一次可能命中 200 张单，不能把 200 张单放进一个事务里——
 *       一张失败全部回滚，而且长事务会长时间占着行锁</li>
 *   <li>同类内部调用 {@code this.escalateIfDue(...)} 不经过 Spring 代理，
 *       方法上的 {@code @Transactional} 会静默失效（经典坑）。用 TransactionTemplate
 *       显式开事务，不依赖代理，行为可预期</li>
 * </ol>
 */
@Service
@Slf4j
public class SlaService {

    /** 系统操作人在日志表里的 ID，约定为 0 */
    private static final Long SYSTEM_OPERATOR = 0L;

    private final TicketMapper ticketMapper;
    private final TicketLogMapper logMapper;
    private final TicketDispatchMapper dispatchMapper;
    private final TransactionTemplate tx;

    /**
     * 当前实例端口，只用来打日志。
     *
     * <p>多实例部署时看日志必须能分辨"这条是哪个实例干的"，
     * 否则出现重复升级时根本没法定位。生产环境更规范的做法是给每个实例一个 instance-id，
     * 写进日志的 MDC（配合 traceId 一起）。
     */
    @Value("${server.port:8080}")
    private String serverPort;

    public SlaService(TicketMapper ticketMapper, TicketLogMapper logMapper,
                      TicketDispatchMapper dispatchMapper, TransactionTemplate tx) {
        this.ticketMapper = ticketMapper;
        this.logMapper = logMapper;
        this.dispatchMapper = dispatchMapper;
        this.tx = tx;
    }

    /**
     * 到期则升级。返回 true 表示这次调用真的把它升级了，false 表示不需要或已被别人升级。
     *
     * @param source MQ / SCAN / MANUAL，只用于写日志区分触发来源
     */
    public boolean escalateIfDue(Long ticketId, String source) {
        Boolean done = tx.execute(status -> doEscalate(ticketId, source));
        return Boolean.TRUE.equals(done);
    }

    /**
     * 扫描一个分片里所有已超时、未升级、且还在待派单/已派单的工单，逐张升级。
     *
     * <p>分片规则 {@code MOD(id, shardTotal) = shard}：多实例各扫一片，互不重叠。
     * 分片比全局锁好的地方是 3 个实例能并行干活，而不是只有一个在扫、另外两个干等。
     */
    public int escalateOverdueShard(int shard, int shardTotal, int limit) {
        List<Ticket> due = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .select(Ticket::getId)
                .lt(Ticket::getSlaDeadline, LocalDateTime.now())
                .eq(Ticket::getEscalateFlag, 0)
                .in(Ticket::getStatus, TicketStatusEnum.PENDING.name(), TicketStatusEnum.ASSIGNED.name())
                .apply("MOD(id, {0}) = {1}", shardTotal, shard)
                .last("LIMIT " + Math.max(1, limit)));

        int n = 0;
        for (Ticket t : due) {
            try {
                if (escalateIfDue(t.getId(), "SCAN")) {
                    n++;
                }
            } catch (Exception e) {
                // 单张失败不影响其他工单，下一轮扫描还会重试
                log.error("升级工单失败 ticketId={}", t.getId(), e);
            }
        }
        return n;
    }

    /**
     * 真正干活的私有方法，由 TransactionTemplate 包成一个独立事务。
     */
    private boolean doEscalate(Long ticketId, String source) {
        Ticket t = ticketMapper.selectById(ticketId);
        if (t == null) {
            return false;
        }
        // 先做廉价判断，命中才发 UPDATE，减少无谓写操作
        if (Integer.valueOf(1).equals(t.getEscalateFlag())
                || !(TicketStatusEnum.PENDING.name().equals(t.getStatus())
                     || TicketStatusEnum.ASSIGNED.name().equals(t.getStatus()))
                || t.getSlaDeadline() == null
                || t.getSlaDeadline().isAfter(LocalDateTime.now())) {
            return false;
        }

        // 条件更新：这就是幂等的全部。多个实例/消息同时到达，只有一个能把 rows 改成 1
        LambdaUpdateWrapper<Ticket> uw = Wrappers.<Ticket>lambdaUpdate()
                .eq(Ticket::getId, ticketId)
                .eq(Ticket::getEscalateFlag, 0)
                .in(Ticket::getStatus, TicketStatusEnum.PENDING.name(), TicketStatusEnum.ASSIGNED.name())
                .set(Ticket::getStatus, TicketStatusEnum.ESCALATED.name())
                .set(Ticket::getEscalateFlag, 1)
                .setSql("version = version + 1");

        if (ticketMapper.update(null, uw) == 0) {
            log.debug("工单已被其它实例升级 ticketId={}", ticketId);
            return false;
        }

        // 派单记录：to_handler_id 非空约束，未派单的用 0 表示“升级后仍待认领”
        TicketDispatch d = new TicketDispatch();
        d.setTicketId(ticketId);
        d.setFromHandlerId(t.getHandlerId());
        d.setToHandlerId(t.getHandlerId() == null ? 0L : t.getHandlerId());
        d.setReason("ESCALATE");
        dispatchMapper.insert(d);

        TicketLog lg = new TicketLog();
        lg.setTicketId(ticketId);
        lg.setFromStatus(t.getStatus());
        lg.setToStatus(TicketStatusEnum.ESCALATED.name());
        lg.setOperatorId(SYSTEM_OPERATOR);
        lg.setAction("ESCALATE");
        lg.setRemark("SLA 超时未处理，系统升级（来源 " + source + "）");
        logMapper.insert(lg);

        log.info("工单已升级 ticketId={} from={} source={} instance={}",
                ticketId, t.getStatus(), source, serverPort);
        return true;
    }
}
