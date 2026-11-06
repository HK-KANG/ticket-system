package com.example.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ticket.common.BizException;
import com.example.ticket.common.NotFoundException;
import com.example.ticket.common.PageResult;
import com.example.ticket.common.ResultCode;
import com.example.ticket.common.enums.TicketStatusEnum;
import com.example.ticket.dto.TicketCloseRequest;
import com.example.ticket.dto.TicketCommentRequest;
import com.example.ticket.dto.TicketCreateRequest;
import com.example.ticket.dto.TicketQuery;
import com.example.ticket.dto.TicketTransferRequest;
import com.example.ticket.entity.Idempotent;
import com.example.ticket.entity.SlaRule;
import com.example.ticket.entity.Ticket;
import com.example.ticket.entity.TicketComment;
import com.example.ticket.entity.TicketDispatch;
import com.example.ticket.entity.TicketLog;
import com.example.ticket.entity.User;
import com.example.ticket.mapper.IdempotentMapper;
import com.example.ticket.mapper.SlaRuleMapper;
import com.example.ticket.mapper.TicketCommentMapper;
import com.example.ticket.mapper.TicketDispatchMapper;
import com.example.ticket.mapper.TicketLogMapper;
import com.example.ticket.mapper.TicketMapper;
import com.example.ticket.mapper.UserMapper;
import com.example.ticket.security.LoginUser;
import com.example.ticket.security.UserContext;
import com.example.ticket.vo.TicketDetailVO;
import com.example.ticket.vo.TicketStatsVO;
import com.example.ticket.vo.TicketVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工单主流程服务：创建、分页查询、详情、回复、转派、关闭、统计。
 *
 * <p>几条贯穿全类的原则：
 * <ol>
 *   <li><b>数据范围由服务端决定</b>：客户只能看自己创建的，坐席只能看自己处理的或本组待抢的，
 *       这个收窄在 SQL 条件里完成，不是查出来再过滤（后者会把不该看的数据读进内存）</li>
 *   <li><b>状态变更必须走状态机</b>：{@link TicketStatusEnum#canTransitTo} 判定，非法跳转直接抛异常</li>
 *   <li><b>状态变更必须写日志</b>：与主表更新在同一个事务里，要么都成功要么都回滚</li>
 * </ol>
 */
@Slf4j
@Service
public class TicketService {

    /** 单页最大条数，防止前端传 size=99999 拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 100L;

    /** 工单单号在 Redis 里的计数器前缀，按天分 key */
    private static final String NO_KEY_PREFIX = "ticket:no:";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TicketMapper ticketMapper;
    private final TicketLogMapper logMapper;
    private final TicketCommentMapper commentMapper;
    private final TicketDispatchMapper dispatchMapper;
    private final IdempotentMapper idempotentMapper;
    private final SlaRuleMapper slaRuleMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redis;

    public TicketService(TicketMapper ticketMapper, TicketLogMapper logMapper,
                         TicketCommentMapper commentMapper, TicketDispatchMapper dispatchMapper,
                         IdempotentMapper idempotentMapper, SlaRuleMapper slaRuleMapper,
                         UserMapper userMapper, StringRedisTemplate redis) {
        this.ticketMapper = ticketMapper;
        this.logMapper = logMapper;
        this.commentMapper = commentMapper;
        this.dispatchMapper = dispatchMapper;
        this.idempotentMapper = idempotentMapper;
        this.slaRuleMapper = slaRuleMapper;
        this.userMapper = userMapper;
        this.redis = redis;
    }

    // ==================================================================
    // 创建
    // ==================================================================

    /**
     * 创建工单。
     *
     * <p>幂等实现思路：先用 (biz_type + requestId) 往 t_idempotent 插一条记录，
     * 唯一索引冲突即说明这是重复请求，直接返回首次创建的那张工单。
     *
     * <p>为什么不能"先查再插"：两个并发请求可能同时查到"不存在"，然后各插一条。
     * 只有数据库唯一索引能在并发下真正保证唯一，Redis 的 setIfAbsent 虽然也是原子的，
     * 但它可能因为过期、被清理、主从延迟而不准，数据库是最后一道防线。
     */
    @Transactional(rollbackFor = Exception.class)
    public TicketVO create(TicketCreateRequest req) {
        LoginUser user = UserContext.require();
        String bizKey = "CREATE_TICKET:" + req.getRequestId();

        Idempotent record = new Idempotent();
        record.setBizKey(bizKey);
        record.setBizType("CREATE_TICKET");
        record.setExpireTime(LocalDateTime.now().plusHours(24));
        try {
            idempotentMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 重复请求：把它首次创建的工单原样返回，客户端看到的结果是一致的
            Idempotent exist = idempotentMapper.selectOne(
                    new LambdaQueryWrapper<Idempotent>().eq(Idempotent::getBizKey, bizKey));
            if (exist != null && StringUtils.hasText(exist.getResultSnapshot())) {
                Long ticketId = Long.valueOf(exist.getResultSnapshot());
                log.info("幂等命中 requestId={} 返回已有工单 id={}", req.getRequestId(), ticketId);
                Ticket existTicket = ticketMapper.selectById(ticketId);
                return toVOList(List.of(existTicket)).get(0);
            }
            // 首次请求还没提交完（拿到唯一索引冲突但快照还没写），让客户端稍后重试
            throw new BizException(ResultCode.BIZ_ERROR, "请求正在处理中，请勿重复提交");
        }

        Ticket ticket = new Ticket();
        ticket.setTicketNo(nextTicketNo());
        ticket.setTitle(req.getTitle());
        ticket.setContent(req.getContent());
        ticket.setCategory(req.getCategory());
        ticket.setPriority(req.getPriority());
        ticket.setStatus(TicketStatusEnum.PENDING.name());
        ticket.setCreatorId(user.userId());
        ticket.setHandlerId(null);
        // 技能组先按分类走默认规则；Phase 8 做 AI 分类后会按分类映射到更细的组
        ticket.setSkillGroup(resolveSkillGroup(req.getCategory(), req.getPriority()));
        ticket.setVersion(0);
        ticket.setEscalateFlag(0);
        ticket.setSlaDeadline(calcSlaDeadline(req.getCategory(), req.getPriority()));
        ticketMapper.insert(ticket);

        writeLog(ticket.getId(), null, TicketStatusEnum.PENDING, user.userId(), "CREATE", "提交工单");

        // 回写结果快照，供重复请求直接返回
        Idempotent update = new Idempotent();
        update.setId(record.getId());
        update.setResultSnapshot(String.valueOf(ticket.getId()));
        idempotentMapper.updateById(update);

        log.info("创建工单 no={} id={} creator={} sla={}",
                ticket.getTicketNo(), ticket.getId(), user.userId(), ticket.getSlaDeadline());
        return toVOList(List.of(ticket)).get(0);
    }

    // ==================================================================
    // 查询
    // ==================================================================

    /**
     * 分页查询，六条件任意组合。
     *
     * <p>这里用的是普通 offset 分页（LIMIT offset, size），第 5000 页会很慢——
     * 这正是 Phase 6 要压测和优化的基线，现在不提前优化。
     */
    public PageResult<TicketVO> page(TicketQuery q) {
        LoginUser user = UserContext.require();
        long pageNo = q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage();
        long pageSize = q.getSize() == null || q.getSize() < 1 ? 10 : Math.min(q.getSize(), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Ticket> w = new LambdaQueryWrapper<>();
        applyRoleScope(w, user);
        w.eq(StringUtils.hasText(q.getCategory()), Ticket::getCategory, q.getCategory())
                .eq(StringUtils.hasText(q.getStatus()), Ticket::getStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getPriority()), Ticket::getPriority, q.getPriority())
                .eq(q.getHandlerId() != null, Ticket::getHandlerId, q.getHandlerId())
                .ge(q.getCreateTimeStart() != null, Ticket::getCreateTime, q.getCreateTimeStart())
                .le(q.getCreateTimeEnd() != null, Ticket::getCreateTime, q.getCreateTimeEnd())
                .and(StringUtils.hasText(q.getKeyword()), x -> x
                        .like(Ticket::getTitle, q.getKeyword())
                        .or()
                        .like(Ticket::getTicketNo, q.getKeyword()))
                .orderByDesc(Ticket::getCreateTime)
                .orderByDesc(Ticket::getId);

        Page<Ticket> p = ticketMapper.selectPage(new Page<>(pageNo, pageSize), w);
        return PageResult.of(toVOList(p.getRecords()), p.getTotal(), pageNo, pageSize);
    }

    /**
     * 工单详情（含流转日志与回复）。
     *
     * <p>可见性判断和数据范围的思路一致：客户只能看自己的，坐席只能看自己处理或本组待抢的。
     * 越权访问返回 404 而不是 403——不告诉对方"这张单存在但你没权限"，
     * 避免通过错误码探测别人工单是否存在。
     */
    public TicketDetailVO detail(Long id) {
        LoginUser user = UserContext.require();
        Ticket t = ticketMapper.selectById(id);
        if (t == null) {
            throw new NotFoundException("工单不存在");
        }
        if (!canView(t, user)) {
            throw new NotFoundException("工单不存在");
        }

        TicketDetailVO vo = new TicketDetailVO();
        copyToListFields(t, vo);
        vo.setContent(t.getContent());

        List<TicketLog> logs = logMapper.selectList(new LambdaQueryWrapper<TicketLog>()
                .eq(TicketLog::getTicketId, id)
                .orderByAsc(TicketLog::getCreateTime)
                .orderByAsc(TicketLog::getId));

        List<TicketComment> comments = commentMapper.selectList(new LambdaQueryWrapper<TicketComment>()
                .eq(TicketComment::getTicketId, id)
                // 客户看不到内部备注：这个过滤必须在 SQL 里做，不能查出来再删
                .eq("CUSTOMER".equals(user.role()), TicketComment::getIsInternal, 0)
                .orderByAsc(TicketComment::getCreateTime)
                .orderByAsc(TicketComment::getId));

        Map<Long, String> names = userNames(logs.stream().map(TicketLog::getOperatorId)
                .collect(Collectors.toSet()));
        names.putAll(userNames(comments.stream().map(TicketComment::getUserId).collect(Collectors.toSet())));

        vo.setLogs(logs.stream().map(l -> {
            TicketDetailVO.LogItem item = new TicketDetailVO.LogItem();
            item.setFromStatus(l.getFromStatus());
            item.setToStatus(l.getToStatus());
            item.setAction(l.getAction());
            item.setRemark(l.getRemark());
            item.setOperatorName(names.getOrDefault(l.getOperatorId(), "系统"));
            item.setCreateTime(l.getCreateTime());
            return item;
        }).toList());

        vo.setComments(comments.stream().map(c -> {
            TicketDetailVO.CommentItem item = new TicketDetailVO.CommentItem();
            item.setId(c.getId());
            item.setUserId(c.getUserId());
            item.setContent(c.getContent());
            item.setIsInternal(c.getIsInternal());
            item.setUserName(names.getOrDefault(c.getUserId(), "未知用户"));
            item.setCreateTime(c.getCreateTime());
            return item;
        }).toList());

        return vo;
    }

    // ==================================================================
    // 状态变更
    // ==================================================================

    /**
     * 转派：把工单交给另一个坐席。
     *
     * <p>待派单的工单被指派后状态变为「已派单」；已经在处理中的工单转派，状态保持不变。
     * 每次转派都写一条派单记录 + 一条流转日志，详情页的轨迹才完整。
     */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long id, TicketTransferRequest req) {
        LoginUser user = UserContext.require();
        Ticket t = requireTicket(id);

        if (TicketStatusEnum.of(t.getStatus()).isFinal()) {
            throw new BizException(ResultCode.BIZ_ERROR, "工单已关闭，不能转派");
        }
        // 权限：管理员可以直接转；坐席只能转自己手上的单
        boolean isAdmin = "ADMIN".equals(user.role());
        boolean isOwner = user.userId().equals(t.getHandlerId());
        if (!isAdmin && !isOwner) {
            throw new BizException(ResultCode.FORBIDDEN, "只能转派自己处理的工单");
        }

        User target = userMapper.selectById(req.getToHandlerId());
        if (target == null || !"AGENT".equals(target.getRole())) {
            throw new BizException(ResultCode.PARAM_ERROR, "目标用户不存在或不是坐席");
        }
        if (target.getId().equals(t.getHandlerId())) {
            throw new BizException(ResultCode.BIZ_ERROR, "工单已经在这个人手上");
        }

        TicketStatusEnum from = TicketStatusEnum.of(t.getStatus());
        TicketStatusEnum to = from == TicketStatusEnum.PENDING ? TicketStatusEnum.ASSIGNED : from;
        if (!from.canTransitTo(to)) {
            throw new BizException(ResultCode.BIZ_ERROR,
                    "不允许从「" + from.getLabel() + "」转派");
        }

        Ticket update = new Ticket();
        update.setId(id);
        update.setHandlerId(target.getId());
        update.setStatus(to.name());
        update.setSkillGroup(target.getSkillGroup());
        ticketMapper.updateById(update);

        TicketDispatch d = new TicketDispatch();
        d.setTicketId(id);
        d.setFromHandlerId(t.getHandlerId());
        d.setToHandlerId(target.getId());
        d.setReason(StringUtils.hasText(req.getReason()) ? req.getReason() : "TRANSFER");
        dispatchMapper.insert(d);

        writeLog(id, from, to, user.userId(), "TRANSFER",
                "转派给 " + target.getRealName() + (StringUtils.hasText(req.getReason()) ? "（" + req.getReason() + "）" : ""));
    }

    /**
     * 回复工单。
     *
     * <p>内部备注只有坐席和管理员能发；客户即使请求里传了 internal=true，
     * 也会被服务端强制改成 false——权限判断永远在服务端，不能信任前端参数。
     */
    public void comment(Long id, TicketCommentRequest req) {
        LoginUser user = UserContext.require();
        Ticket t = requireTicket(id);
        if (!canView(t, user)) {
            throw new NotFoundException("工单不存在");
        }
        if (TicketStatusEnum.of(t.getStatus()).isFinal()) {
            throw new BizException(ResultCode.BIZ_ERROR, "工单已关闭，不能再回复");
        }

        boolean internal = Boolean.TRUE.equals(req.getInternal()) && !"CUSTOMER".equals(user.role());

        TicketComment c = new TicketComment();
        c.setTicketId(id);
        c.setUserId(user.userId());
        c.setContent(req.getContent());
        c.setIsInternal(internal ? 1 : 0);
        commentMapper.insert(c);
    }

    /**
     * 关闭工单。
     *
     * <p>谁能关：管理员、当前处理人、创建人（客户确认问题已解决）。
     * 一条从没被处理过的工单（待派单）不允许直接关闭——状态机会拦住。
     */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, TicketCloseRequest req) {
        LoginUser user = UserContext.require();
        Ticket t = requireTicket(id);

        boolean isAdmin = "ADMIN".equals(user.role());
        boolean isHandler = user.userId().equals(t.getHandlerId());
        boolean isCreator = user.userId().equals(t.getCreatorId());
        if (!isAdmin && !isHandler && !isCreator) {
            throw new BizException(ResultCode.FORBIDDEN, "无权关闭该工单");
        }

        TicketStatusEnum from = TicketStatusEnum.of(t.getStatus());
        TicketStatusEnum to = TicketStatusEnum.CLOSED;
        if (!from.canTransitTo(to)) {
            throw new BizException(ResultCode.BIZ_ERROR,
                    "不允许从「" + from.getLabel() + "」直接关闭，请先确认处理人已接手");
        }

        Ticket update = new Ticket();
        update.setId(id);
        update.setStatus(to.name());
        update.setFinishTime(LocalDateTime.now());
        ticketMapper.updateById(update);

        writeLog(id, from, to, user.userId(), "CLOSE",
                StringUtils.hasText(req.getResolution()) ? req.getResolution() : "关闭工单");
    }

    // ==================================================================
    // 统计
    // ==================================================================

    /**
     * 工单统计。当前是实时聚合，Phase 6 会改成预聚合表 + 缓存。
     */
    public TicketStatsVO stats() {
        TicketStatsVO vo = new TicketStatsVO();

        vo.setTotal(ticketMapper.selectCount(new LambdaQueryWrapper<>()));
        vo.setStatusCount(groupCount("status"));
        vo.setCategoryCount(groupCount("category"));
        vo.setPriorityCount(groupCount("priority"));
        vo.setOverdueCount(ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .lt(Ticket::getSlaDeadline, LocalDateTime.now())
                .notIn(Ticket::getStatus, TicketStatusEnum.CLOSED.name(), TicketStatusEnum.RESOLVED.name())));

        // SLA 达成率：已关闭的工单里，在截止时间前完成的比例
        long closed = ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getStatus, TicketStatusEnum.CLOSED.name()));
        long onTime = ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getStatus, TicketStatusEnum.CLOSED.name())
                .isNotNull(Ticket::getSlaDeadline)
                .apply("finish_time <= sla_deadline"));
        vo.setSlaAchieveRate(closed == 0 ? 100.0 : Math.round(onTime * 1000.0 / closed) / 10.0);

        vo.setDailyTrend(dailyTrend(7));
        return vo;
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    private Ticket requireTicket(Long id) {
        Ticket t = ticketMapper.selectById(id);
        if (t == null) {
            throw new NotFoundException("工单不存在");
        }
        return t;
    }

    /**
     * 按角色收窄可见范围。这是本项目最容易被写错的地方：
     * 如果让前端传 creatorId，改个参数就能看别人的工单。
     */
    private void applyRoleScope(LambdaQueryWrapper<Ticket> w, LoginUser user) {
        switch (user.role()) {
            case "CUSTOMER" -> w.eq(Ticket::getCreatorId, user.userId());
            case "AGENT" -> {
                User me = userMapper.selectById(user.userId());
                String group = me == null ? null : me.getSkillGroup();
                // 坐席可见 = 派给我的 或 本组还没人接的
                w.and(x -> x.eq(Ticket::getHandlerId, user.userId())
                        .or(y -> y.eq(Ticket::getSkillGroup, group)
                                .eq(Ticket::getStatus, TicketStatusEnum.PENDING.name())));
            }
            default -> {
                // ADMIN：不加限制，看全量
            }
        }
    }

    /** 单张工单的可见性判断，规则与列表收窄一致 */
    private boolean canView(Ticket t, LoginUser user) {
        return switch (user.role()) {
            case "CUSTOMER" -> user.userId().equals(t.getCreatorId());
            case "AGENT" -> {
                if (user.userId().equals(t.getHandlerId())) {
                    yield true;
                }
                User me = userMapper.selectById(user.userId());
                yield me != null
                        && me.getSkillGroup() != null
                        && me.getSkillGroup().equals(t.getSkillGroup())
                        && TicketStatusEnum.PENDING.name().equals(t.getStatus());
            }
            default -> true;
        };
    }

    /**
     * 生成工单号：TK + yyyyMMdd + 6 位当日序号。
     *
     * <p>序号用 Redis 的 INCR——它是单线程原子自增，天然解决并发下重号问题，
     * 比自己写 SELECT MAX(no)+1 安全（后者在并发下必重号）。
     * key 按天分开并设 48 小时过期，历史 key 自动清理，不需要额外清理任务。
     *
     * <p>Redis 不可用时降级为「时间戳尾数」，可能重号但唯一索引会兜住，
     * 保证系统不会因为缓存故障整个不可用。
     */
    private String nextTicketNo() {
        String date = LocalDate.now().format(DATE_FMT);
        try {
            String key = NO_KEY_PREFIX + date;
            Long seq = redis.opsForValue().increment(key);
            if (seq != null && seq == 1L) {
                redis.expire(key, Duration.ofHours(48));
            }
            return "TK" + date + String.format("%06d", seq == null ? 1 : seq);
        } catch (Exception e) {
            log.warn("Redis 生成单号失败，降级为时间戳：{}", e.getMessage());
            return "TK" + date + String.format("%06d", System.nanoTime() % 1000000);
        }
    }

    /**
     * 按（分类 + 优先级）查 SLA 规则算截止时间。
     * 找不到规则返回 null——宁可没有截止时间，也不要瞎猜一个数字。
     */
    private LocalDateTime calcSlaDeadline(String category, String priority) {
        SlaRule rule = slaRuleMapper.selectOne(new LambdaQueryWrapper<SlaRule>()
                .eq(SlaRule::getCategory, category)
                .eq(SlaRule::getPriority, priority)
                .eq(SlaRule::getEnabled, 1)
                .last("LIMIT 1"));
        if (rule == null) {
            log.warn("未匹配到 SLA 规则 category={} priority={}，该工单无截止时间", category, priority);
            return null;
        }
        return LocalDateTime.now().plusMinutes(rule.getSlaMinutes());
    }

    /** 根据分类与优先级推断技能组（Phase 8 的 AI 分类会替换这段规则） */
    private String resolveSkillGroup(String category, String priority) {
        SlaRule rule = slaRuleMapper.selectOne(new LambdaQueryWrapper<SlaRule>()
                .eq(SlaRule::getCategory, category)
                .eq(SlaRule::getPriority, priority)
                .eq(SlaRule::getEnabled, 1)
                .last("LIMIT 1"));
        if (rule != null && StringUtils.hasText(rule.getEscalateToGroup())) {
            return rule.getEscalateToGroup();
        }
        return "售后一组";
    }

    /** 写流转日志。状态变更与日志必须同事务，回滚时日志一起消失 */
    private void writeLog(Long ticketId, TicketStatusEnum from, TicketStatusEnum to,
                          Long operatorId, String action, String remark) {
        TicketLog l = new TicketLog();
        l.setTicketId(ticketId);
        l.setFromStatus(from == null ? null : from.name());
        l.setToStatus(to.name());
        l.setOperatorId(operatorId);
        l.setAction(action);
        l.setRemark(remark);
        logMapper.insert(l);
    }

    /** 批量查用户名，避免列表渲染时 N+1 查询 */
    private Map<Long, String> userNames(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        List<User> users = userMapper.selectBatchIds(ids);
        return users.stream().collect(Collectors.toMap(User::getId,
                u -> u.getRealName() == null ? u.getUsername() : u.getRealName(), (a, b) -> a));
    }

    /** 实体列表转 VO 列表，补上创建人/处理人姓名与超时标记 */
    private List<TicketVO> toVOList(List<Ticket> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> ids = new java.util.HashSet<>();
        list.forEach(t -> {
            ids.add(t.getCreatorId());
            if (t.getHandlerId() != null) {
                ids.add(t.getHandlerId());
            }
        });
        Map<Long, String> names = userNames(ids);

        return list.stream().map(t -> {
            TicketVO vo = TicketVO.from(t);
            copyToListFields(t, vo);
            vo.setCreatorName(names.get(t.getCreatorId()));
            vo.setHandlerName(t.getHandlerId() == null ? null : names.get(t.getHandlerId()));
            vo.setStatusLabel(TicketStatusEnum.of(t.getStatus()).getLabel());
            boolean active = !TicketStatusEnum.of(t.getStatus()).isFinal();
            vo.setOverdue(t.getSlaDeadline() != null && active
                    && t.getSlaDeadline().isBefore(LocalDateTime.now()));
            return vo;
        }).toList();
    }

    private void copyToListFields(Ticket t, TicketVO vo) {
        vo.setId(t.getId());
        vo.setTicketNo(t.getTicketNo());
        vo.setTitle(t.getTitle());
        vo.setCategory(t.getCategory());
        vo.setPriority(t.getPriority());
        vo.setStatus(t.getStatus());
        vo.setStatusLabel(TicketStatusEnum.of(t.getStatus()).getLabel());
        vo.setCreatorId(t.getCreatorId());
        vo.setHandlerId(t.getHandlerId());
        vo.setSkillGroup(t.getSkillGroup());
        vo.setSlaDeadline(t.getSlaDeadline());
        vo.setAcceptTime(t.getAcceptTime());
        vo.setFinishTime(t.getFinishTime());
        vo.setCreateTime(t.getCreateTime());
        boolean active = !TicketStatusEnum.of(t.getStatus()).isFinal();
        vo.setOverdue(t.getSlaDeadline() != null && active
                && t.getSlaDeadline().isBefore(LocalDateTime.now()));
    }

    /** 按某一列分组计数，返回有序 Map（多的一定排前面，便于看板展示） */
    private Map<String, Long> groupCount(String column) {
        QueryWrapper<Ticket> qw = new QueryWrapper<>();
        qw.select(column + " AS k", "COUNT(*) AS c").groupBy(column);
        List<Map<String, Object>> rows = ticketMapper.selectMaps(qw);
        Map<String, Long> result = new LinkedHashMap<>();
        rows.stream()
                .filter(r -> r.get("k") != null)
                .sorted((a, b) -> Long.compare(((Number) b.get("c")).longValue(),
                        ((Number) a.get("c")).longValue()))
                .forEach(r -> result.put(String.valueOf(r.get("k")),
                        ((Number) r.get("c")).longValue()));
        return result;
    }

    /** 近 N 天每天新增量。数据量小时用 Java 分组，Phase 6 改成 SQL 聚合 */
    private List<TicketStatsVO.DailyCount> dailyTrend(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1);
        List<Ticket> list = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .select(Ticket::getId, Ticket::getCreateTime)
                .ge(Ticket::getCreateTime, start.atStartOfDay()));

        Map<String, Long> counter = new HashMap<>();
        for (Ticket t : list) {
            if (t.getCreateTime() == null) {
                continue;
            }
            String d = t.getCreateTime().toLocalDate().toString();
            counter.merge(d, 1L, Long::sum);
        }

        List<TicketStatsVO.DailyCount> trend = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            String d = start.plusDays(i).toString();
            TicketStatsVO.DailyCount dc = new TicketStatsVO.DailyCount();
            dc.setDate(d);
            dc.setCount(counter.getOrDefault(d, 0L));
            trend.add(dc);
        }
        return trend;
    }

    /** 供 Controller 复用：把实体转成单条 VO */
    public TicketVO toVO(Ticket t) {
        return toVOList(List.of(t)).get(0);
    }
}
