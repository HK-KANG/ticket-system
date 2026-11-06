# 售后工单系统 · 开发任务书（给 Hermes 用）

> **用法**：Hermes 直接读盘，不需要粘贴。**一次只做一个 Phase**：先让它输出执行计划，你确认后再写代码。每个 Phase 结束按「验收标准」逐条验证，不通过不进下一阶段。
>
> **本文件只包含开发任务。面试话术、选型答辩、简历写法在另一份《工单系统-面试笔记.md》，不要混进开发上下文，否则会污染它的判断。**

---

## 0. 先读这一节：两档交付目标

> ⚠ 这一节是本次修订新增的。原任务书只有"14 天出精品"一档，对第一次上手 Spring Boot + Redis + MQ + Docker + Vue3 的人不现实。**先跑通 MVP，再决定要不要做精品版。**

### 档位 A：MVP（7 个工作日）—— 必须先完成这个

```
范围：Phase 0 → 1 → 2 → 3 → 4
页面：只做 B1 抢单大厅、B3 工单处理页（2 页）
砍掉：SLA 升级、性能优化、异步导出、AI 分类、管理后台、3 实例部署、Docker 之外的一切
产出：公网可访问，能走通「建单 → 抢单 → 处理 → 关闭」
      + 一份 20 并发抢单的实测数据（成功数严格 = 1）
```

**这一版就够写进简历了。** 做完立刻投简历、约面试，不要等精品版。

### 档位 B：精品版（再 +17 个工作日，累计 24 天）

```
范围：补齐 Phase 5 → 9
页面：补齐到 8 页
产出：性能优化报告（6 项前后对比）+ 20 万数据压测 + 3 实例部署 + 3 篇技术文章
```

### 铁律

**Phase 6 是唯一不能砍的阶段**（它产出的那张对比表是面试的全部价值）。如果时间不够，先砍 Phase 8、再砍 Phase 9、再砍页面数量，**不砍 Phase 6 和 Phase 4**。

---

## 1. 全局约束（每个 Phase 开头都带上这段）

```
【项目】售后工单处理系统（单体 Spring Boot 应用，个人作品集项目）
【硬约束】
1. 单体应用，不拆微服务，不引入 Spring Cloud、Nacos、Sentinel
2. JDK 21 + Spring Boot 3.5.16 + MyBatis-Plus 3.5.18 + MySQL 8.4 + Redis 8.x + RabbitMQ 4.x
3. 代码版本管理用 Git（GitHub 仓库），禁止使用 SVN
4. 所有 SQL 走 MyBatis-Plus 或 XML，禁止在 Service 层拼接 SQL 字符串
5. 所有接口返回统一 Result<T> 结构：code / message / data / traceId
6. 所有表必须有 create_time、update_time、deleted（逻辑删除）
7. 每个接口必须有参数校验（Jakarta Validation）
8. 不允许引入任何需要联网注册的第三方服务（AI 能力用本地规则实现，见 Phase 8）
9. 不引入 Elasticsearch、Milvus、ShardingSphere 等重型组件
10. 每个类、每个公开方法必须有中文注释，说明职责
11. 禁止生成"示例/演示用的假数据初始化代码"，数据由独立脚本生成
12. 敏感配置（数据库密码、密钥）全部走环境变量，提交 .env.example，禁止提交 .env

【依赖 artifactId（必须完全一致，写错会启动失败）】
- org.springframework.boot:spring-boot-starter-web
- org.springframework.boot:spring-boot-starter-validation
- org.springframework.boot:spring-boot-starter-data-redis
- org.springframework.boot:spring-boot-starter-amqp
- org.springframework.boot:spring-boot-starter-actuator
- com.baomidou:mybatis-plus-spring-boot3-starter      ← Boot 3 专用，不是 mybatis-plus-boot-starter
- com.mysql:mysql-connector-j
- com.alibaba:easyexcel:4.0.3
- com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter   ← 必须带 jakarta
- io.micrometer:micrometer-registry-prometheus
- org.springframework.security:spring-security-crypto   ← 只为 BCrypt，不要引 spring-security 全家桶
- org.projectlombok:lombok

【代码风格】
- 分层：controller / service / mapper / entity / dto / vo / config / common / job
- Controller 只做参数校验和调用，不写业务逻辑
- Service 方法命名体现业务动作：createTicket / acceptTicket / transferTicket
```

---

## 2. 前端约束（涉及前端的 Phase 都带上）

```
【前端约束】
1. Vue3 + Vite + Element Plus + Pinia + axios 封装，单页应用（SPA）
2. 只做 PC 端，1280px 宽度可用即可，不做移动端适配、不做复杂动效
3. 只实现清单里的页面，一个都不许多加，也不许少做
4. 每个页面必须正确处理三种状态：空数据、接口报错、长列表分页
5. 列表页统一用 Element Plus 的 Table + Pagination，查询条件放 SearchForm
6. 请求统一走封装好的 request.js（带 token、带 traceId、统一错误提示）
7. 权限按角色渲染：没权限的菜单不显示，直接访问路由要拦到 403 页
```

---

## 3. 页面清单：8 个页面 + 4 个弹窗

> ⚠ 本次修订从原 16 页裁到 8 页。**裁掉的页面不是"不做"，是"精品版再补"，避免一开始就铺开 16 个页面导致工期崩掉。**
> 用户/坐席账号不用做管理页，直接用初始化 SQL 脚本插数据。

### 页面

| # | 页面 | 路由 | 核心元素 | 优先级 | 档位 |
|---|---|---|---|---|---|
| 1 | 登录 | `/login` | 用户名、密码、（演示用）角色切换 | P0 | A |
| 2 | 提交工单 | `/customer/submit` | 标题、分类下拉、描述、优先级、附件上传 | P0 | A |
| 3 | 我的工单列表 | `/customer/tickets` | 状态 Tab、关键词搜索、分页、点开行看详情抽屉 | P0 | A |
| 4 | **抢单大厅** | `/agent/pool` | 工单卡片（分类/优先级/SLA 剩余倒计时）、**抢单按钮**、30 秒自动刷新 | **P0** | A |
| 5 | **工单处理页** | `/agent/ticket/:id` | 详情区、状态流转按钮组、回复区、内部备注 | **P0** | A |
| 6 | 工单总览 | `/admin/tickets` | 全量多条件查询（分类/状态/优先级/坐席/时间）、深度分页 | **P0** | B |
| 7 | 数据看板 | `/admin/dashboard` | 工单量趋势折线、分类分布饼图、SLA 达成率 | **P0** | B |
| 8 | 导出中心 | `/admin/export` | 导出任务列表、进度条、下载 | **P0** | B |

### 弹窗

| # | 弹窗 | 触发位置 | 依赖接口 |
|---|---|---|---|
| D1 | 转派 | 处理页 | `/api/ticket/{id}/transfer` |
| D2 | 关闭工单 | 处理页 | `/api/ticket/{id}/close` |
| D3 | 抢单失败提示 | 抢单大厅 | 前端本地逻辑（"已被他人抢走"，刷新列表） |
| D4 | 发起导出 | 导出中心 | `/api/ticket/export` |

### 精品版可选扩展（做不完不影响交付）

客户侧详情页、消息通知、坐席看板、坐席个人设置、用户与坐席管理、SLA 规则配置页、分类字典管理。

> SLA 规则配置页可以不做页面，直接在 `t_sla_rule` 表里插数据，用 Knife4j 演示接口即可。

### 权限矩阵

| 页面 | CUSTOMER | AGENT | ADMIN |
|---|---|---|---|
| 登录、提交工单、我的工单 | ✅ | ❌ | ❌ |
| 抢单大厅、工单处理页 | ❌ | ✅ | 只读 |
| 工单总览、数据看板、导出中心 | ❌ | ❌ | ✅ |

**两个必须写清的口径**（原文档在这里有矛盾）：
- `/api/ticket/page` 按角色自动收窄：CUSTOMER 只看 creator_id = 自己；AGENT 只看 skill_group 匹配且已分派给自己或池中；ADMIN 全量
- `/api/category/list` 对 CUSTOMER **只读开放**（提交工单要下拉）；`/api/admin/category/save` 仅 ADMIN
- 坐席个人设置如果做，接口是 `/api/agent/setting`，**不要复用** `/api/admin/user/save`

---

## 4. 技术栈（版本已核对，artifact 见第 1 节）

| 分层 | 组件 | 版本 | 用途 |
|---|---|---|---|
| 运行时 | JDK | 21 (LTS, Temurin) | 支持虚拟线程 |
| 框架 | Spring Boot | 3.5.16 | 应用基座，3.x 最后一版，生态最稳 |
| Web | Spring MVC | 内嵌 | REST 接口 |
| 安全 | 自研 JWT + 拦截器 + BCrypt | — | 登录态、角色校验 |
| 持久层 | MyBatis-Plus | 3.5.18 | ORM + 条件构造 + 分页 + 乐观锁 |
| 连接池 | HikariCP | 内置 | 不换 Druid |
| 业务库 | MySQL | 8.4 (LTS) | 7 张业务表 |
| 缓存 | Redis | 8.x | token、缓存、分布式锁、令牌桶 |
| 消息 | RabbitMQ | 4.x | SLA 延迟队列、异步任务 |
| 接口文档 | Knife4j (jakarta) | 4.x | `/doc.html` |
| 参数校验 | Jakarta Validation | 内置 | DTO 校验 |
| Excel | EasyExcel | 4.0.3 | 分批导出 |
| 日志 | Logback + MDC | 内置 | traceId 串联 |
| 监控 | Micrometer + Actuator | 内置 | `/actuator/prometheus` |
| 可视化 | Prometheus + Grafana | 最新 | Docker 起，导入现成面板 ID 4701 |
| 前端 | Vue3 + Vite + Element Plus + Pinia | 最新 | 8 个页面 |
| 容器 | Docker + Docker Compose | 最新 | 一键起服务 |
| 压测 | JMeter 5.6 或 wrk | — | 并发、分页压测 |
| 版本控制 | Git + GitHub | — | 见第 7 节 |

### 数据库只用两个

| 库 | 存什么 |
|---|---|
| MySQL 8.4 | 7 张业务表；InnoDB、utf8mb4、RR 隔离、开启慢查询日志（long_query_time=1） |
| Redis 8.x | token、工单详情缓存、分布式锁、令牌桶、导出进度、SLA 扫描分片标记 |

**明确不用**：Elasticsearch、MongoDB、ShardingSphere、Seata、Spring Cloud。

（面试能说的一句话见《面试笔记》第 4 节。）

---

## 5. 数据模型：7 张表

> ⚠ 本次修订**删掉了 t_outbox**。原文档建了这张表但全篇没有任何 Phase 用到它——面试官翻表结构会问"这个是干什么的"，你答不上来反而扣分。

**t_user**（用户/坐席）
`id, username, password(BCrypt), real_name, role(CUSTOMER/AGENT/ADMIN), skill_group, status(ONLINE/OFFLINE), max_load, create_time, update_time, deleted`

**t_ticket**（工单主表，核心）
`id, ticket_no(唯一业务号), title, content, category, priority(P0-P3), status(PENDING/ASSIGNED/PROCESSING/RESOLVED/CLOSED/ESCALATED), creator_id, handler_id, skill_group, version(乐观锁), sla_deadline, accept_time, finish_time, escalate_flag, create_time, update_time, deleted`

**t_ticket_log**（流转日志）
`id, ticket_id, from_status, to_status, operator_id, action, remark, create_time`

**t_ticket_dispatch**（派单记录）
`id, ticket_id, from_handler_id, to_handler_id, reason(AUTO/MANUAL/TRANSFER/ESCALATE), create_time`

**t_ticket_comment**（回复记录）
`id, ticket_id, user_id, content, is_internal, create_time`

**t_sla_rule**（超时规则）
`id, category, priority, sla_minutes, escalate_to_group, enabled`

**t_idempotent**（幂等表）
`id, biz_key(唯一索引), biz_type, result_snapshot, expire_time, create_time`

### 索引（要让 Hermes 解释理由）

- `t_ticket`：`(skill_group, status, create_time)`、`(handler_id, status)`、`(sla_deadline)`、`ticket_no` 唯一
- `t_idempotent.biz_key` 唯一
- 每个索引必须能说清"为哪个查询服务"

### 验收

- [ ] `schema.sql` 在空库执行无报错
- [ ] Hermes 输出《索引设计说明》，每个索引对应具体查询场景
- [ ] 表字段与上面完全一致

---

## 6. Phase 拆解

### Phase 0 · 工程初始化（0.5 天）

1. 建工程 `ticket-system`，单模块，包名 `com.example.ticket`
2. **第一步 `git init` + 建 GitHub 仓库**（建议直接 public，你的目的是给面试官看），写好 `.gitignore`（target、.idea、日志、`.env`、本地配置），创建 `.env.example`
3. 引入依赖（artifactId 严格按第 1 节）
4. `docker-compose.yml`：mysql:8.4、redis:8.x、rabbitmq:4-management、应用 Dockerfile
5. 三套配置：`application-local.yml` / `application-docker.yml`，敏感值走环境变量
6. Logback + MDC traceId
7. 全局异常处理器 + 统一 Result + `BizException` + 错误码枚举
8. 当天完成第一次 commit

**验收**
- [ ] `git log` 有提交，仓库已推 GitHub，`target/` 和 `.env` 未被跟踪
- [ ] `docker compose up -d` 后三个容器健康
- [ ] 应用启动成功，`/doc.html` 能打开
- [ ] 访问不存在接口返回统一 Result，不是 Spring 默认错误页
- [ ] 同一请求内所有日志 traceId 一致

---

### Phase 1 · 数据模型（0.5 天）

按第 5 节建 7 张表，输出 `schema.sql` + `docs/index-design.md`。

**验收**
- [ ] `schema.sql` 空库执行无报错
- [ ] 索引说明覆盖全部索引
- [ ] 字段无缺失

---

### Phase 2 · 基础能力（1 天）

1. JWT 登录/登出，token 存 Redis（**JWT 本身无状态，存 Redis 只为支持踢下线，这点要在注释里写清**）
2. `@RequireRole` 注解式权限
3. MyBatis-Plus 配置：自动填充、逻辑删除、分页插件、乐观锁插件
4. Redis 工具类：分布式锁（SETNX + Lua 释放）、令牌桶限流（Lua）
5. RabbitMQ：工单通知队列、超时延迟队列（TTL + 死信）、导出队列，手动 ack
6. traceId 过滤器
7. **初始化 SQL 脚本**：3 个角色各 2 个账号 + 8 条 SLA 规则

**验收**
- [ ] 未登录访问业务接口返回 401（统一结构）
- [ ] 两个用户互相查不到对方工单
- [ ] 分布式锁测试：20 线程抢同一 key，只有 1 个成功
- [ ] 限流测试：同一用户 10 秒内第 11 次返回 429
- [ ] 初始化脚本跑完能直接用 demo 账号登录

---

### Phase 3 · 工单主流程（1.5 天）

**接口**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/ticket/create` | 创建（幂等） |
| GET | `/api/ticket/page` | 多条件分页 |
| GET | `/api/ticket/{id}` | 详情 |
| POST | `/api/ticket/{id}/transfer` | 转派 |
| POST | `/api/ticket/{id}/comment` | 回复 |
| POST | `/api/ticket/{id}/close` | 关闭 |
| GET | `/api/ticket/stats` | 统计 |

**关键实现**
1. **创建幂等**：客户端传 `requestId`，`t_idempotent.biz_key` 唯一索引拦截。并发场景必须靠数据库唯一索引兜底，不能只靠 Redis 判断。
2. **状态机**：`TicketStatusEnum` + 合法流转表，非法流转抛 `BizException`。流转必须写 `t_ticket_log`，与主表更新同一事务。
3. **分页**：支持 category / status / priority / handlerId / 时间区间 / 关键词六条件。**先按普通 offset 分页实现**，这是 Phase 6 的基线，不要提前优化。

**验收**
- [ ] 同一 requestId 连发 5 次，只生成 1 条工单
- [ ] 非法流转（PENDING → CLOSED）返回明确错误码
- [ ] 状态变更后 `t_ticket_log` 有记录，事务回滚时日志不落库
- [ ] 六条件任意组合都能正确返回

---

### Phase 4 · 派单与抢单（并发核心，1.5 天）

**两个版本都要保留，这是面试的核心材料。**

1. **自动派单** `/api/ticket/{id}/auto-dispatch`
   按 skill_group 找在线坐席，过滤 max_load 已满的，按当前并行数升序选人，写 `t_ticket_dispatch`，发 MQ

2. **抢单版本 A：乐观锁**
   ```sql
   UPDATE t_ticket SET handler_id=?, status='PROCESSING', version=version+1
   WHERE id=? AND status='PENDING' AND handler_id IS NULL AND version=?
   ```
   影响行数 0 → 返回"已被他人抢走"

3. **抢单版本 B：Redis 分布式锁**
   锁 key `lock:ticket:accept:{ticketId}`，SETNX + 30 秒过期 + Lua 释放；拿到锁后二次校验状态再更新

4. **压测对比**：同一 ticketId 起 20 并发，两版本各跑一次，记录成功次数、失败响应耗时分布、DB UPDATE 实际执行次数

5. **写 `docs/concurrency-compare.md`**：什么并发量下乐观锁可接受、超过多少该换锁、Redis 锁本身的开销

**验收**
- [ ] 20 并发抢同一单，两版本成功次数都严格 = 1
- [ ] `docs/concurrency-compare.md` 含两组实测数据表 + 结论
- [ ] 日志验证不存在"两人同时处理一张单"

> ✅ **到这里 MVP 结束。停下去投简历，剩下的等有面试了再补。**

---

### Phase 5 · SLA 超时升级（1 天，档位 B）

1. 创建工单时按 `t_sla_rule` 算 `sla_deadline`，投延迟消息
2. 消息到期时**条件更新兜底幂等**：
   ```sql
   UPDATE t_ticket SET status='ESCALATED', escalate_flag=1
   WHERE id=? AND escalate_flag=0 AND status IN ('PENDING','ASSIGNED')
   ```
3. 兜底扫描 `/job/sla-scan`：每 30 秒一次，多实例防重用 Redis 锁 + 按 id 取模分片
4. `docker compose up --scale app=3` 验证只升级一次

**验收**
- [ ] 单实例：到期工单在 SLA 后 30 秒内被升级
- [ ] 3 实例：同一条工单只产生 1 条 dispatch、1 次状态变更
- [ ] 输出 `docs/sla-multi-instance.md`

---

### Phase 6 · 性能优化（2-3 天，**唯一不能砍的阶段**）

> 前置：造 30 万条工单数据（日志 90 万、回复 40 万），随机分布，时间跨度 18 个月，分批插入（每批 5000）。
> **机器扛不住就降到 10 万，记录实际数据量即可，不要为了凑数字卡住。**

**流程：先建基线，再逐项优化，每项都记录前后数据。**

| # | 优化项 | 手段 |
|---|---|---|
| ① | 索引 | 按 6 个查询条件组合建联合索引，输出 EXPLAIN 前后对比 |
| ② | 深度分页 | offset → 延迟关联（先查主键再回表）→ 游标分页 |
| ③ | N+1 | 详情接口循环查日志/回复 → 批量 IN，记录 SQL 次数从 N 降到 3 |
| ④ | 缓存 | 详情 Cache Aside，处理击穿（互斥重建 + 逻辑过期），压击穿场景 |
| ⑤ | 统计 | `/stats` 实时聚合 → 定时预聚合 + 缓存 |
| ⑥ | 虚拟线程 | `spring.threads.virtual.enabled=true`，对比平台线程/虚拟线程的吞吐与 P99 |

**⚠ 虚拟线程的坑（原文档漏了，必须写进任务）**：
JDK 21 下 `synchronized` 块会 pin 住载体线程（JDK 24 才由 JEP 491 修复）。
压测前必须确认热点路径上没有 `synchronized`，统一换 `ReentrantLock`，**否则会得出"虚拟线程没有提升"的错误结论**。
另外：虚拟线程只对 IO 等待型场景有效，CPU 密集型不会有提升——这点要在报告里写明。

**验收（已从绝对指标改为对比指标，避免达不到卡死）**
- [ ] 深翻页：记录优化前后 P99，**优化后比基线下降 ≥80%**，绝对值写入报告（不设硬门槛）
- [ ] 详情接口 MySQL 查询次数 ≤ 4 次
- [ ] 缓存击穿：记录前后 MySQL 峰值 QPS，**下降 ≥10 倍**
- [ ] 虚拟线程：完整两组数据 + 写清"什么场景有效、什么场景无效、pinning 怎么排查"
- [ ] `docs/perf-report.md` 一张总表：优化项 / 优化前 / 优化后 / 手段 / 代价
- [ ] 所有数字可复现（脚本和压测命令写进文档）

---

### Phase 7 · 异步导出（0.5-1 天，档位 B）

1. `/api/ticket/export` 提交任务 → 队列 → 返回 taskId
2. 消费者 EasyExcel 分批查 + 分批写（每批 1 万行），进度写 Redis
3. `/progress` 查进度，`/download` 下载
4. **验证内存**：导出 20 万行时堆内存曲线平稳，GC 日志无频繁 Full GC

**验收**
- [ ] 导出 20 万行不 OOM
- [ ] 导出中修改数据库，结果不受影响（分批读一致性）
- [ ] 失败重试 3 次后标记 FAILED 并记录原因

---

### Phase 8 · 智能分类（0.5 天，**已降级为本地规则**）

> ⚠ 本次修订把 AI 分类改为本地关键词规则实现。原因：不花钱、不联网、不会被 API key 卡住、面试也不会被追问"准确率怎么保证"。
> 想加 AI 的话，做成配置开关（`ticket.ai.enabled=true` 时走外部 API），默认关闭——**有开关比有功能更能体现工程能力**。

1. `POST /api/ticket/ai/classify`：标题 + 描述 → 8 类分类 + 优先级建议
2. 实现：关键词词典 + 规则打分（每类 10-20 个关键词，命中最多的胜出，0 命中给默认类）
3. **工程化必须做**：结果缓存 1 小时、单用户限流、超时降级、失败返回默认分类（接口不 500）
4. 自建 50 条测试样本，输出 `docs/classify-accuracy.md`

**验收**
- [ ] 规则异常时 100% 降级成功，接口不 500
- [ ] 50 条样本准确率有统计表（不追求高，70% 以上即可，能说清"为什么是这个水平"更重要）
- [ ] 单次调用耗时 < 10ms

---

### Phase 9 · 上线与交付（1-1.5 天，档位 B）

1. Dockerfile 多阶段构建 + compose 一键起（app × 3 + mysql + redis + rabbitmq + nginx）
2. Actuator 暴露 `/actuator/prometheus`，Grafana 导入面板 ID 4701（**不要自己画**）
3. 部署云服务器，Nginx 反代，公网可访问
4. README：Mermaid 架构图、技术栈表、压测结果表、启动步骤
5. 三篇技术文章选题：
   - 《一次工单抢单的三种实现：从乐观锁到分布式锁，我的实测数据》
   - 《30 万工单下的深度分页优化：从 X ms 到 Y ms》
   - 《多实例部署下，定时任务怎么保证只执行一次》

**验收**
- [ ] 公网可访问，能走通完整流程
- [ ] Grafana 有真实曲线
- [ ] README 数据与 `docs/` 一致
- [ ] 换台干净机器按 README 能一键起

---

## 7. Git 提交纪律（本次修订重点）

> 原文档写"每天至少 1 次 commit"，但 14 天只有 14 个提交，看起来仍像集中作业。
> **目标是 40-60 个提交，每天 2-4 个。** 这是"你真的做了"最硬的证据。

- 分支：`main`（随时可运行）+ `feat/phase-0X-xxx`
- 中文 commit message，写清做了什么：
  ```
  feat: 工单表结构 + 索引设计说明
  feat: 创建工单接口（含幂等表拦截）
  fix: 修复状态机非法流转未抛 BizException
  test: 补充抢单乐观锁并发用例
  perf: 工单列表加联合索引，深翻页 P99 下降 82%
  docs: 补充 Phase 4 并发对比数据
  ```
- 仓库结构：`backend/` + `frontend/` + `docs/` + `docker/`，根目录 README 是门面
- **绝对禁止最后一次性导入所有代码**

---

## 8. 时间盘（修订）

### 档位 A：MVP —— 7 个工作日

| 阶段 | 时间 |
|---|---|
| Phase 0 基建 | 0.5 |
| Phase 1 数据模型 | 0.5 |
| Phase 2 基础能力 | 1.5 |
| Phase 3 主流程 | 1.5 |
| Phase 4 抢单并发 | 1.5 |
| 前端 2 页（抢单大厅 + 处理页） | 1.5 |
| **合计** | **7** |

### 档位 B：精品版 —— 再 +17 天

| 阶段 | 时间 |
|---|---|
| Phase 5 SLA | 1 |
| Phase 6 性能优化 | 3 |
| Phase 7 导出 | 1 |
| Phase 8 分类 | 0.5 |
| Phase 9 上线 | 1.5 |
| 造数 + 压测 + 文档返工 | 4 |
| 前端补 6 页 | 4 |
| 技术文章 3 篇 | 2 |
| **合计** | **17** |

> 这是按"会用这些技术的人"估的。第一次上手再乘 1.3。**如果实际进度落后，砍功能不砍验收，更不要砍 Phase 6。**

---

## 9. 卡住了怎么办（原文档没有，很重要）

1. **任何 Phase 超过预估 1.5 倍仍未通过验收** → 立刻停，砍该 Phase 的非核心功能，先过验收再往下走
2. **环境问题（Docker、MQ、Grafana）超过 1 小时配不通** → 直接跳过，用本地安装或纯本地模式，环境配置不是交付物
3. **生成的代码看不懂** → 不要硬改。让它"逐行解释这段代码"，理解了再改；理解不了就重生成
4. **某个技术点学不动（比如虚拟线程）** → 先把 Phase 6 的其他 5 项做完，第 6 项留到最后，做不了就在报告里写明"未完成"
5. **整体拖延超过 2 周还没跑通 MVP** → 停下来，把范围砍到"Phase 0-3 + 1 个页面"，先有一个能演示的东西

> **最后一条也是最重要的：进度落后不代表失败，范围缩小才代表成功。**
> 你现在需要的是"一个能演示的东西"，不是"一个完美的项目"。
