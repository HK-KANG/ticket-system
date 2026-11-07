# 售后工单系统

客户提交问题 → 系统按 SLA 规则计算时限 → 坐席抢单处理 → 管理员看板与导出。

一个**单体 Spring Boot 3 + Vue 3** 的售后工单系统，用于练习并展示 Java 后端常见工程能力：
JWT + Redis 可撤销登录态、幂等接口、状态机、乐观锁并发抢单、索引设计与 SQL 优化。

> 当前状态：**MVP 完成**（Phase 0–4）。核心流程可跑通、接口有自动化验收脚本、并发方案有实测对比数据。
> 剩余 Phase 5–9（SLA 超时扫描、异步导出、AI 分类、容器化部署）按需要有面试再补。

---

## 核心流程

```
客户               系统                     坐席                 管理员
 │                  │                        │                    │
 │ 提交工单 ────────▶│                        │                    │
 │                  │ 按(分类+优先级)查 SLA  │                    │
 │                  │ 生成单号 TK+日期+序号   │                    │
 │                  │ 状态: 待派单            │                    │
 │                  │◀────── 抢单 ───────────│                    │
 │                  │  乐观锁：只有 1 人成功   │                    │
 │                  │ 状态: 处理中 ──────────▶│                    │
 │                  │◀────── 标记解决 ────────│                    │
 │                  │ 状态: 已解决            │                    │
 │                  │◀──────── 关闭 ──────────│                    │
 │                  │ 状态: 已关闭 ───────────────────────────────▶│
 │                  │                        │                    │ 看板/导出
```

状态机（`common/enums/TicketStatusEnum.java` 集中定义，非法跳转直接拒绝）：

```
待派单 ──派单/抢单──▶ 已派单 ──接单──▶ 处理中 ──解决──▶ 已解决 ──关闭──▶ 已关闭
   │                    │                 │                                ▲
   │                    └─────────────────┴────── 直接关闭 ────────────────┘
   └──(不允许直接关闭)                                       系统超时 ──▶ 已升级
```

---

## 技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 语言/框架 | Java + Spring Boot | JDK 21 / Spring Boot 3.5.16 |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | MySQL | 8.4.13 |
| 缓存 | Redis | 8（alpine） |
| 消息队列 | RabbitMQ | 4.4.0（已就绪，Phase 5 起使用） |
| 认证 | JWT (jjwt) + Redis | 0.12.6 |
| 迁移 | Flyway | 随 Boot 版本 |
| 文档 | springdoc + Swagger UI | 2.8.13 |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + Axios | Vite 6.4.5 |
| 中间件运行 | Docker Compose（跑在 WSL2 内） | Engine 29.1 |

**刻意不引入的东西**：微服务、注册中心、TypeScript、Webpack、Redis 缓存业务数据。
理由写在 `docs/项目说明.md` 的"技术亮点"一节——每个技术选择都要能讲清为什么。

---

## 快速开始

前置：JDK 21、Maven、Node 18+、Docker（Windows 上装 Docker 或 WSL2 内原生引擎均可）。

```bash
# 1. 起中间件（MySQL / Redis / RabbitMQ）
cd docker && docker compose up -d
#   首次需按 .env.example 创建 .env，填 MYSQL_ROOT_PASSWORD 等

# 2. 起后端（表结构与演示数据由 Flyway 自动建）
./run-local.cmd                  # Windows 双击即可；或 IDEA 里 Shift+F9（.run/ 已带配置）

# 3. 起前端
cd frontend && npm install && npm run dev
```

打开 http://localhost:5173 ，用下面的账号登录。
后端 API 文档：http://localhost:8080/swagger-ui.html ，健康检查：http://localhost:8080/api/health

### 演示账号（密码统一 `123456`）

| 账号 | 角色 | 技能组 | 能做什么 |
|---|---|---|---|
| `zhangsan` / `limei` | 客户 | — | 提交工单、看自己的工单、回复、关闭 |
| `lisi` | 坐席 | 售后一组 | 抢本组待派单、处理、标记解决、转派 |
| `zhaoliu` | 坐席 | 售后二组 | 同上（看不到一组的单，可用来验证隔离） |
| `admin` / `admin2` | 管理员 | — | 全量工单、六条件查询、自动派单、看板统计 |

数据隔离规则：客户只能看到自己创建的；坐席只能看到**本技能组**的待派单 + 自己正在处理的；
管理员看全部。越权访问统一返回 404（不泄露"存在但无权看"）。

---

## 接口一览

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 公开 | 登录，返回 JWT；写入 Redis 使其可撤销 |
| POST | `/api/auth/logout` | 登录 | 删除 Redis 令牌，旧 token 立即失效 |
| GET | `/api/auth/me` | 登录 | 当前用户信息（不含密码） |
| GET | `/api/user/list` | 管理员 | 全部用户 |
| GET | `/api/user/agents` | 坐席/管理员 | 坐席列表（转派选人用） |
| POST | `/api/ticket/create` | 客户 | 创建工单，`requestId` 幂等 |
| GET | `/api/ticket/page` | 登录 | 六条件分页，按角色收窄可见范围 |
| GET | `/api/ticket/{id}` | 登录 | 详情含流转日志与回复 |
| POST | `/api/ticket/{id}/comment` | 登录 | 回复；客户传 `internal=true` 会被服务端强制降级 |
| POST | `/api/ticket/{id}/transfer` | 处理人/管理员 | 转派，写派单记录 |
| POST | `/api/ticket/{id}/close` | 客户/处理人/管理员 | 关闭工单 |
| POST | `/api/ticket/{id}/accept` | 坐席 | **抢单（乐观锁）**，并发下只有 1 人成功 |
| POST | `/api/ticket/{id}/accept-lock` | 坐席 | 抢单（Redis 分布式锁版，仅对比实验） |
| POST | `/api/ticket/{id}/auto-dispatch` | 管理员 | 自动派单给本组最闲的在线坐席 |
| POST | `/api/ticket/{id}/resolve` | 处理人/管理员 | 标记已解决 |
| GET | `/api/ticket/stats` | 管理员 | 状态/分类分布、SLA 达成率、近 7 天趋势 |

统一响应格式：`{ "code": 0, "message": "成功", "data": ..., "traceId": "..." }`
HTTP 状态码与业务语义一致：401 未登录、403 无权限、404 不存在、400 参数错误。

---

## 几个值得看的设计

| 设计 | 位置 | 为什么这么做 |
|---|---|---|
| 流水表只留 `create_time` | `V1__init_schema.sql` | 只增不改的事实记录，加 `update_time` 永远等于 `create_time` |
| 不建物理外键 | `V1__init_schema.sql` | 分库分表、批量删数据时外键是枷锁；关联由应用层保证 |
| 幂等靠唯一索引兜底 | `TicketService.create()` | Redis 的 `setIfAbsent` 也是原子的，但事务回滚后 key 还在，会误判重复 |
| 状态机集中定义 | `TicketStatusEnum.java` | 避免业务代码散落 `if (status == ...)`，新增状态只改一处 |
| 抢单用乐观锁不用悲观锁 | `TicketService.acceptOptimistic()` | 高频短事务，`FOR UPDATE` 让 20 个请求全排队持锁，吞吐更低 |
| 抢单用乐观锁不用 Redis 锁 | 同上 | 竞争只在一行上，数据库行锁足够，还少一个必须存活的依赖 |
| 客户传 `internal=true` 强制降级 | `TicketService.addComment()` | 前端可以作弊，权限判断必须在服务端 |
| 越权返回 404 而非 403 | `TicketService.canView()` | 403 会泄露"这个工单存在"，用 404 让攻击者连存在性都探测不到 |

并发方案的实测数据与取舍分析：**`docs/concurrency-compare.md`**（20 并发 × 2 轮，两版本均只有 1 人成功）。

---

## 项目结构

```
ticket-system/
├── backend/                        Spring Boot 单体
│   └── src/main/java/com/example/ticket/
│       ├── common/                 统一 Result、异常、分页、状态枚举
│       ├── config/                 MyBatis-Plus、密码编码器、拦截器注册
│       ├── security/               JWT 工具、登录拦截器、ThreadLocal 上下文
│       ├── controller/             Auth / User / Ticket
│       ├── service/                业务逻辑（TicketService 是核心）
│       ├── mapper/                 MyBatis-Plus Mapper
│       ├── entity/ dto/ vo/        实体、入参、出参
│       └── resources/db/migration/ Flyway 脚本（V1 建表 / V2 演示数据）
├── frontend/                       Vue 3 + Vite
│   └── src/
│       ├── api/                    接口封装（axios）
│       ├── views/                  客户 / 坐席 / 管理员三组页面
│       ├── layouts/ router/ stores/ utils/
├── docker/                         docker-compose + 各中间件配置
├── scripts/                        验收脚本（环境/表结构/认证/工单/并发）
└── docs/                           任务书、操作日志、设计文档
```

---

## 验收脚本

不依赖测试框架，直接打真实接口断言：

| 脚本 | 覆盖 |
|---|---|
| `scripts/verify-env.sh` | MySQL / Redis / RabbitMQ 连通性 |
| `scripts/verify-schema.sh` | 7 表 64 列 13 索引与设计一致 |
| `scripts/verify-auth.ps1` | 9 项认证断言（登录/登出/401/403/密码错/令牌撤销） |
| `scripts/verify-ticket.ps1` | **29 项**工单断言（幂等 5 连发、跨客户隔离、内部备注过滤、非法流转、日志链、六条件、统计权限） |
| `scripts/verify-concurrency.ps1` | **20 并发抢同一单**，两版本各跑一轮，校验只有 1 人成功 |

运行（PowerShell）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-ticket.ps1
```

---

## 文档

| 文档 | 内容 |
|---|---|
| `docs/面试题库.md` | **50+ 道针对本项目设计的面试题**，含口语版答案、追问链、薄弱处标注 |
| `docs/项目说明.md` | 项目是干什么的、有哪些模块、每个设计为什么这么做 |
| `docs/redis-usage.md` | **Redis 用在哪**：三个 key、代码位置、为什么不用数据库 |
| `docs/concurrency-compare.md` | 乐观锁 vs Redis 锁：两轮实测数据、选型边界、面试口径 |
| `docs/index-design.md` | 13 个索引逐条对应真实查询 |
| `docs/任务书.md`、`docs/tasks.md` | 需求与阶段清单 |
| `docs/操作日志.md` | 每一步操作、遇到的问题与修复（含 9 起事故复盘） |

---

## 未完成（有意为之）

Phase 5–9 没有做，因为 MVP 已经能讲清核心能力，继续加功能的边际收益低于去面试：

- SLA 超时扫描（RabbitMQ 延迟队列 + 定时兜底）
- 异步导出（EasyExcel + MQ + 轮询进度）
- 性能优化专项（深分页游标、慢查询对比、压测报告）
- AI 辅助分类（工单自动打标签）
- Docker 化部署 + CI

每个的取舍与实现思路在 `docs/tasks.md` 里有记录，被问到可以讲。
