# SLA 超时升级：多实例实测报告

> 实测时间：2026-11-09 16:05–16:13
> 环境：Windows 11 + WSL2，MySQL 8.4 / Redis 8 / RabbitMQ 4.4 跑在 WSL 的 Docker 里
> 代码：`SlaService`、`SlaScanJob`、`SlaCheckListener`、`RabbitConfig`
> 脚本：`scripts/verify-sla.ps1`

---

## 一、结论

| 验收项 | 结果 |
|---|---|
| 单实例：到期工单在 SLA 后 30 秒内被升级 | ✅ MQ 路径 **159 毫秒**后升级 |
| 3 实例：同一条工单只产生 1 条 dispatch、1 次状态变更 | ✅ 6 张测试单，每张都是 1 条 |
| 扫描幂等（同一张单连扫两次） | ✅ 第 1 次升 1 张，第 2 次 0 张 |
| Redis 分片锁用完释放 | ✅ 扫描结束后 `job:sla-scan:*` 为空 |

**一句话**：唯一性由「条件更新」这条 SQL 保证，Redis 锁和分片只是减少重复劳动，
不是正确性的来源——这是这套设计最关键的一点。

---

## 二、这套机制怎么工作

两条路，一套逻辑：

```
创建工单
   │
   ├─▶ 投递延迟消息（TTL = 距截止毫秒数）──▶ RabbitMQ 延迟队列
   │                                          │ 到期变死信
   │                                          ▼
   │                                    ticket.sla.check.queue ──▶ 消费者
   │                                                                │
   └─▶ 定时扫描（每 30 秒，多实例分片 + Redis 锁）───────────────────┤
                                                                    ▼
                                                        escalateIfDue(ticketId)
                                                                    │
                                             ┌──────────────────────┴───────────────┐
                                             │  UPDATE t_ticket                     │
                                             │     SET status='ESCALATED',          │
                                             │         escalate_flag=1,             │
                                             │         version=version+1            │
                                             │   WHERE id=?                         │
                                             │     AND escalate_flag=0              │
                                             │     AND status IN ('PENDING','ASSIGNED') │
                                             └──────────────────────────────────────┘
                                                    影响行数 1 → 升级成功
                                                    影响行数 0 → 别人已经升过，什么都不做
```

两条路都可能同时到达同一张单，但那条 UPDATE 只可能成功一次。

---

## 三、为什么不用「延迟队列」独占，还要加扫描

RabbitMQ 没有原生的延迟投递（除非装 `rabbitmq_delayed_message_exchange` 插件，
我们镜像里没有）。标准替代是 TTL + 死信队列，但它有个硬伤：**只检查队头**。

我们项目里 SLA 有 30 / 60 / 240 / 480 分钟四档。如果队头压着一条 480 分钟后到期的消息，
后面 30 分钟到期的消息必须等它出队才能被投递——这就是**队头阻塞**。

所以最终设计是：
- **延迟队列**：负责"尽量准时"，代价是可能被队头阻塞推迟
- **定时扫描**：负责"保证不漏"，最多晚 30 秒（可配）
- **条件更新**：负责"不重复"，跟上面两者都无关

要彻底解决队头阻塞，只有三条路：装延迟插件、按延迟时长分多个队列（档位多就爆炸）、
或者上时间轮（内存态，不跨实例）。我们这个场景 30 秒的误差可以接受，选了扫描兜底。

---

## 四、实测数据

### 4.1 单实例：MQ 延迟消息

工单 19，分类「测试MQ」+ P0（临时插了一条 1 分钟规则，测完已删）。

| 事件 | 时间 |
|---|---|
| 创建工单，写 `sla_deadline` | 16:05:43.741 |
| 截止时间（+1 分钟） | 16:06:43.555 |
| 消费者收到消息并完成升级 | **16:06:43.714** |

**延迟 159 毫秒**。日志证据：

```
16:05:43.741  TicketService - 已投递 SLA 延迟消息 ticketId=19 ttl=60000ms
16:06:43.714  SlaService    - 工单已升级 ticketId=19 from=PENDING source=MQ instance=8080
16:06:43.722  SlaCheckListener - SLA 到期消息触发升级 ticketId=19
```

投递后延迟队列里的消息数：2 条（另一条是 B 单的 30 分钟消息）。A 到期后队列剩 1 条。

### 4.2 单实例：扫描 + 幂等

工单 20（退款 P0，规则 30 分钟），手工把 `sla_deadline` 回填到 5 分钟前。

| 操作 | 返回 |
|---|---|
| `POST /api/job/sla-scan` 第 1 次 | `escalated=1` |
| `POST /api/job/sla-scan` 第 2 次 | `escalated=0` |

最终状态：`status=ESCALATED`、`escalate_flag=1`、`version=1`、
dispatch 记录 1 条、ESCALATE 日志 1 条。

### 4.3 三实例：只升级一次

三个 JVM 分别监听 8081 / 8082 / 8083，都配 `--ticket.sla.shard-total=3`、
`--ticket.sla.scan-interval-ms=10000`，各自用 `-DLOG_PATH` 写独立日志。

第一轮（21/22/23）与第二轮（24/25/26）各造 3 张超时单：

| 工单 | 状态 | dispatch | ESCALATE 日志 |
|---|---|---|---|
| 21 | ESCALATED | 1 | 1 |
| 22 | ESCALATED | 1 | 1 |
| 23 | ESCALATED | 1 | 1 |
| 24 | ESCALATED | 1 | 1 |
| 25 | ESCALATED | 1 | 1 |
| 26 | ESCALATED | 1 | 1 |

第一轮的升级时间：16:08:51.171 / .176 / .223（相差 52 毫秒，三个实例并行扫描）。

---

## 五、一个必须说清楚的现象：工作没均摊

第二轮的证据显示，**24/25/26 三张单全部由 8083 这一个实例升级**：

```
实例 8081: （无）
实例 8082: （无）
实例 8083: 24 25 26
```

这不是 bug，是我选的设计带来的结果：

| 设计 | 行为 | 优点 | 缺点 |
|---|---|---|---|
| **抢锁式分片**（本项目） | 每个实例每轮尝试抢所有分片的锁，抢到哪片扫哪片 | 天然故障转移：实例挂了，它的分片下一轮被别人接手 | 不保证均衡，快的那台可能独占 |
| **固定分片**（shard-index） | 实例 i 只扫 `MOD(id,N)=i` | 负载天然均摊 | 实例挂了，它的分片就没人扫，直到人工调整 N |

选抢锁式是因为**可用性优先**：少扫一轮只是晚 30 秒升级，而分片没人扫会一直漏。
另外三个分片的锁在同一毫秒被同一个实例抢到，说明另外两台当时正在启动或上一轮还没结束——
20 台机器、每秒几十个分片的真实场景里，这种"独占"不会持续。

**要均衡怎么办**：给锁加实例权重、或者用固定分片 + 注册中心感知实例数。
这两种都比现在复杂，而这个场景不需要。

---

## 六、已知不足（被问到就直说）

1. **没有实例标识的 traceId**：日志里只能靠 `instance=8083` 区分（这个字段是这次实测时才加的）。
   生产更规范的做法是把 instance-id 写进 MDC，跟 traceId 一起输出。

2. **锁被跳过时没有日志**：抢不到锁的实例静默跳过，排查时看不出"为什么这台什么都没干"。
   加一行 debug 日志就好，但会刷屏，暂时没加。

3. **多实例共写一个日志文件**：如果不加 `-DLOG_PATH`，三个实例都往 `logs/app.log` 写，
   行会交错。生产环境每台机器各自收集日志，不存在这个问题。

4. **扫描间隔 30 秒是拍的**：真实场景应该按"工单量 × 每张处理耗时"算，
   或者改成扫描到空就退避。

---

## 七、复现步骤

```bash
# 1. 起中间件
cd docker && docker compose up -d

# 2. 起三个实例（各自独立日志）
for p in 8081 8082 8083; do
  java -Xmx256m -DLOG_PATH=D:/_setup/i$p \
    -jar backend/target/ticket-system.jar \
    --spring.profiles.active=local \
    --server.port=$p --ticket.sla.shard-total=3 --ticket.sla.scan-interval-ms=10000 &
done

# 3. 造工单并回填截止时间到过去
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-sla.ps1 -Action create
#    然后 SQL: UPDATE t_ticket SET sla_deadline=DATE_SUB(NOW(3),INTERVAL 3 MINUTE) WHERE id IN (...);

# 4. 等 30 秒，查结果
SELECT t.id, t.status, t.escalate_flag,
       (SELECT COUNT(*) FROM t_ticket_dispatch d WHERE d.ticket_id=t.id AND d.reason='ESCALATE') disp,
       (SELECT COUNT(*) FROM t_ticket_log l WHERE l.ticket_id=t.id AND l.action='ESCALATE') lg
FROM t_ticket t WHERE t.id IN (...);

# 5. 手动触发（不用等定时任务）
curl -X POST http://127.0.0.1:8080/api/job/sla-scan -H "Authorization: Bearer <admin token>"
```

---

## 八、面试怎么讲这一段（45 秒）

> SLA 超时升级我做了两条路：MQ 延迟消息负责尽量准时，定时扫描负责保证不漏。
>
> 延迟消息用的是 TTL + 死信队列，因为 RabbitMQ 没装延迟插件。但这套有队头阻塞——
> 它只检查队头是否过期，前面压着一条 8 小时的消息，后面的就得等着。
> 所以我又加了每 30 秒的扫描兜底。
>
> 两条路都会调同一个升级方法，里面是一条带条件的 UPDATE：
> 只有 `escalate_flag=0` 且状态还是待派单/已派单时才改，影响行数为 0 就什么都不做。
> **所以唯一性靠的是这条 SQL，不是锁。**
>
> 多实例我实测了三个进程：造 6 张超时单，每张都只有 1 条派单记录、1 条升级日志。
> 分片用 Redis 锁，每个实例抢到哪片扫哪片，这样一台挂了别人能接手。
