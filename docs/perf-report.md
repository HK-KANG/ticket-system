# 性能优化报告

> 实测时间：2026-11-10 22:00–22:20
> 环境：Windows 11 + WSL2，MySQL 8.4（buffer pool 128MB，容器限 600MiB）、Redis 8、JDK 21、Spring Boot 3.5 单实例
> 数据量：**100,000 工单 / 258,976 流转日志 / 130,001 回复**，库大小 110.5 MB，时间跨度 18 个月
> 造数脚本：`scripts/seed-perf.py`（固定随机种子，可复现）
> 压测脚本：`scripts/bench-api.ps1`（接口耗时）、`scripts/bench-concurrency.ps1`（并发吞吐）

---

## 一、先给结论

| # | 优化项 | 结果 |
|---|---|---|
| ① | 索引 | 列表查询本来就命中索引（0.5ms / 扫 20 行）；**但深分页加索引无效**，见下 |
| ② | 深分页 | 游标分页 **34.2ms → 4.31ms（降 87%）**；纯 offset 写法怎么优化都没用 |
| ③ | N+1 | **基线即达标**：详情接口固定 4 条查询，合计 0.5ms |
| ④ | 缓存 | 统计接口 **507ms → 33ms**；击穿防护修了一个真 bug（30 并发重建从 24 次降到 1 次）|
| ⑤ | 统计聚合 | 靠 ④ 的缓存解决，没做预聚合表（理由见末节）|
| ⑥ | 虚拟线程 | **无提升**（112 → 95 req/s），原因说清楚 |

**最重要的一条**：深分页的瓶颈不是"有没有索引"，而是"要先跳过 9 万行再回表"。
在这个查询上强制走索引反而慢 4 倍（342ms）。唯一解法是改查询写法。

---

## 二、测量方法（先说明可信度）

两种测量，**不能混用**：

| 层面 | 方法 | 说明 |
|---|---|---|
| SQL 执行时间 | MySQL `slow_log`（`long_query_time=0`，`log_output=TABLE`）| 服务端真实耗时，不含连接和网络 |
| 接口耗时 | PowerShell `Invoke-WebRequest` + Stopwatch | HTTP 往返，用户实际感受，含 ~40ms 固定开销 |

⚠️ **踩过的坑**：一开始用 `docker exec mysql -e "..."` 外面套 `date` 计时，
测出来每个查询都 110–190ms——那 118ms 是 `docker exec` 启动进程的开销，把真实耗时全盖住了。
换成 slow_log 才看到真相（列表查询其实只要 0.5ms）。

---

## 三、优化前后的 SQL 数据

### 3.1 列表类查询（索引本来就有）

| 查询 | 优化前 | 优化后 |
|---|---|---|
| 本组待派单 + 时间倒序（走 `idx_skill_status_time`）| 0.5ms / 扫 20 行 | 无变化（已最优）|
| 客户视角 + 时间倒序（走 `idx_creator_time`）| 0.5ms / 扫 20 行 | 无变化 |
| `title LIKE '%退款%' LIMIT 20` | 0.2ms / 扫 67 行 | 无变化（前缀模糊用不上索引，但命中即停）|
| `COUNT(*) + LIKE '%退款%'` | 31.9ms / 扫 100,004 行 | 未优化（要精确计数只能全扫）|

**结论**：带过滤条件的列表查询，索引设计是有效的——只扫 20 行就返回。

### 3.2 深分页（核心发现）

| 写法 | 优化前 | 优化后 | 扫描行数 |
|---|---|---|---|
| `LIMIT 90000, 20`（offset）| 79.3ms | **92.3ms（更慢）** | 190,024 |
| `FORCE INDEX(idx_create_time)` 的 offset | — | **342.9ms** | 90,020 |
| 延迟关联（先查主键再回表）| 33.0ms | 24.8ms（+26.3ms 子查询）| 60 |
| **游标分页**（`create_time < ? OR (create_time = ? AND id < ?)`）| 34.2ms | **4.31ms** | **20** |

三个必须讲清的现象：

1. **加了 `idx_create_time` 之后，纯 offset 反而变慢了**（79 → 92ms）。
   `EXPLAIN` 显示 `key=NULL, rows=97935, Using filesort`——优化器宁愿全表扫 + 内存排序，
   也不用索引。因为用索引就得先扫 90,020 个索引项，再回表 20 次，它算下来更贵。

2. **强制走索引更糟**（342.9ms）。因为 9 万次回表变成随机 I/O，比顺序全扫慢得多。

3. **只有改写法才有效**：游标分页只扫 20 行，`EXPLAIN` 显示
   `key=idx_create_time, Backward index scan`，耗时从 34.2ms 降到 4.31ms。

> 面试可以直接说：**"offset 深分页是算法问题，不是索引问题。加索引解决不了'跳过 N 行'的成本。"**

### 3.3 统计聚合（5 个查询，每个都全表扫）

| 查询 | 耗时 | 扫描行数 |
|---|---|---|
| 按状态分组 | 46.9ms | 100,004 |
| 按分类分组 | 60.7ms | 100,004 |
| SLA 达成率 | 33.1ms | 100,004 |
| 近 7 天趋势 | 18.1ms | 100,004 |
| 超时未处理数 | 39.4ms | 100,004 |
| **合计** | **约 200ms** | — |

### 3.4 详情页（N+1 检查）

| 查询 | 耗时 | 行数 |
|---|---|---|
| 工单主表 | 0.1ms | 1 |
| 流转日志 | 0.2ms | 3 |
| 回复 | 0.2ms | 1 |
| 用户（`IN (1,3)` 批量）| 0.1ms | 2 |
| **合计 4 条** | **0.5ms** | — |

**基线即达标**：从第一版就是用 `selectBatchIds` 批量取用户、再在内存里拼名字，
没有"循环里查用户"的 N+1。所以这一项只验证、不优化。

---

## 四、接口层数据（用户实际感受）

| 接口 | 优化前 | 优化后 |
|---|---|---|
| `GET /api/ticket/stats` 首次（缓存未命中）| 507ms | 507ms（首次仍要实时算）|
| `GET /api/ticket/stats` 命中缓存 | — | **33ms**（min 27）|
| `GET /api/ticket/cursor` 第 1 页 | — | 45ms |
| `GET /api/ticket/cursor` 第 90,000 行处 | — | **47ms（不退化）** |
| `GET /api/ticket/page` 第 1 页 | 74ms | 74ms |
| `GET /api/ticket/page` 第 4500 页 | 346ms | 346ms（未改善，见 3.2）|
| `GET /api/ticket/{id}` 详情 | 45ms | 45ms |

> 注：HTTP 有约 40ms 固定开销（连接建立 + JSON），所以详情接口服务端 0.5ms 却测出 45ms。
> 对比要看**相对变化**，不要拿 HTTP 耗时和 slow_log 耗时直接相减。

---

## 五、缓存击穿：验证时发现并修掉的真 bug

设计：统计缓存 TTL 60 秒，缓存失效时用 Redis 锁保证只有一个人重建，其他人等。

### 第一版（等待窗口 500ms）—— 失败

清空缓存，30 个并发同时请求 `/api/ticket/stats`：

| 指标 | 结果 |
|---|---|
| 总耗时 | **11,841ms** |
| 单请求中位数 | 8,360ms |
| 数据库聚合查询次数 | **120 次 = 重建了 24 次** |

**根因**：一次重建要 ~500ms，而等待线程只等 500ms 就超时，超时后各自去查库——
反而制造了它本该防止的雪崩。30 个请求里 24 个都自己算了一遍。

### 第二版（等待窗口 5 秒，与锁 TTL 一致）—— 通过

| 指标 | 修复前 | 修复后 |
|---|---|---|
| 总耗时 | 11,841ms | **1,058ms** |
| 单请求中位数 | 8,360ms | **754ms** |
| 聚合查询次数 | 120（24 次重建）| **4（1 次重建）** |

**教训**：防击穿的等待时间必须大于"一次重建的耗时"，否则等待者变成新的击穿源。
这个值不能拍脑袋，要根据实测的重建耗时定——而且它和锁 TTL 应该对齐。

---

## 六、虚拟线程对比

压测：50 并发，目标接口 `/api/ticket/cursor?size=20`，跑 3 轮取第 3 轮（前两轮含 JIT 预热）。

| 指标 | 平台线程（默认）| 虚拟线程（`spring.threads.virtual.enabled=true`）|
|---|---|---|
| 总耗时 | **446ms** | 526ms |
| 吞吐 | **112.1 req/s** | 95.1 req/s |
| 中位数 | 119ms | 116ms |
| P95 | 202ms | 208ms |

**结论：没有提升，甚至低 15%（在噪声范围内）。**

原因：
1. 瓶颈不在线程数。这个接口的开销是 MySQL 查询 + Redis + JSON 序列化，
   而 Hikari 连接池只有 10 个连接——**连接池就是并发上限**，加多少线程都得排队。
2. 50 并发根本没打满 Tomcat 的线程池（默认 200），平台线程绰绰有余。

**虚拟线程什么时候有用**：大量请求同时阻塞在**没有池限制的外部 IO** 上，
比如一次请求要串行调 5 个第三方 HTTP 接口、每个 300ms。那时平台线程会被打满，
虚拟线程能撑住几万个并发。

**一个必须知道的坑**（JDK 21）：`synchronized` 块会 pin 住载体线程，
表现为"虚拟线程开了没效果"。JDK 24 的 JEP 491 才修复。
排查方法：`-Djdk.tracePinnedThreads=full` 看有没有 pinning。
本项目热点路径没有 `synchronized`（用的 `ReentrantLock` 和 Redis 锁），所以不受影响。

---

## 七、没做的事，以及为什么

| 任务书要求 | 我的决定 | 理由 |
|---|---|---|
| ④ 详情接口加缓存 | **不做** | 实测 4 条查询共 0.5ms。加缓存要处理 6 个写入口的失效（抢单/转派/回复/解决/关闭/升级），失效逻辑出错的风险远大于 0.5ms 的收益 |
| ⑤ 统计预聚合表 | **只做缓存** | 缓存已经把 507ms 降到 33ms，命中率高。预聚合表要加表 + 定时任务 + 历史回填，等数据量再大 10 倍（百万级）才值得 |
| 造 30 万数据 | **降到 10 万** | 任务书自己允许降级。10 万行时深分页的差距已经是数量级的（4.31 vs 92ms），再多不影响结论 |
| 压测工具 | **PowerShell runspace** | 机器上没有 wrk/JMeter。runspace 能测并发和吞吐，但精度不如专业工具，这点在报告里说明了 |

**共用的选择标准**：优化前先量，量完再决定改不改。不加"看起来高级但没收益"的东西。

---

## 八、复现步骤

```bash
# 1. 造数据（10 万工单 / 25.9 万日志 / 13 万回复，约 70MB SQL）
python3 scripts/seed-perf.py
docker exec -i ticket-mysql sh -c "MYSQL_PWD=xxx mysql -uroot ticket" < /tmp/seed_perf.sql

# 2. 打开慢日志（记录所有语句的服务端耗时）
docker exec ticket-mysql sh -c "MYSQL_PWD=xxx mysql -uroot -e \"
  SET GLOBAL log_output='TABLE'; SET GLOBAL long_query_time=0; SET GLOBAL slow_query_log=ON;\""

# 3. 跑要测的 SQL，然后读结果
docker exec ticket-mysql sh -c "MYSQL_PWD=xxx mysql -uroot -e \"
  SELECT ROUND(query_time*1000,2) ms, rows_examined, sql_text FROM mysql.slow_log ORDER BY start_time\""

# 4. 接口压测
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bench-concurrency.ps1 -Label off -Parallel 50 -Rounds 3

# 5. 清空压测数据（省磁盘）
docker exec ticket-mysql sh -c "MYSQL_PWD=xxx mysql -uroot ticket -e \"
  TRUNCATE t_ticket_log; TRUNCATE t_ticket_comment; TRUNCATE t_ticket_dispatch;
  DELETE FROM t_ticket WHERE id > 10000;\""
docker exec ticket-mysql sh -c "MYSQL_PWD=xxx mysql -uroot -e 'PURGE BINARY LOGS BEFORE NOW()'"
```

---

## 九、面试口径（一分钟）

> 我用 10 万工单做了六项优化，每项都有优化前后的实测数据。
>
> 最有意思的是深分页：我一开始以为加个 create_time 索引就好了，结果加了之后反而慢
> （79 → 92ms），因为 MySQL 宁愿全表扫加内存排序，也不想扫 9 万个索引项再回表。
> 我试了强制走索引，342ms，更糟。最后明白这是个算法问题——offset 要求跳过 N 行，
> 换写法才是解：改成游标分页，只扫 20 行，34ms 降到 4.3ms。
>
> 另一个是我自己写的缓存击穿防护有 bug：等待窗口设成 500ms，但重建要 500ms，
> 结果 30 个并发里有 24 个超时后各自去查库，打出了 120 条聚合查询。
> 把等待窗口调到 5 秒后，只重建 1 次，总耗时从 11.8 秒降到 1 秒。
>
> 虚拟线程我也测了，50 并发下没有提升——因为瓶颈是数据库连接池只有 10 个连接，
> 不是线程数。虚拟线程解决的是"大量线程阻塞在无池限制的外部 IO"，不是这个场景。
