# 索引设计说明

> 对应建表脚本：`backend/src/main/resources/db/migration/V1__init_schema.sql`
> 速查表：`D:\新建文件夹\工单系统-表结构.xlsx`
> 原则：**每一条索引都必须对应一个真实查询**。没有查询支撑的索引只是拖慢写入。

---

## 一、t_ticket（核心表，5 个索引）

### 1. uk_ticket_no (ticket_no) 唯一

- 服务的查询：工单详情页 `WHERE ticket_no = ?`（对外只暴露业务单号，不暴露自增 id）
- 为什么唯一：单号由"TK + 日期 + 序列"生成，唯一索引是对生成逻辑的最后一道保险
- 代价：一次额外的唯一性检查，可接受

### 2. idx_skill_status_time (skill_group, status, create_time)

- 服务的查询：**抢单大厅**
  ```sql
  SELECT ... FROM t_ticket
  WHERE skill_group = ? AND status = 'PENDING'
  ORDER BY create_time DESC LIMIT 20;
  ```
- 字段顺序的理由：
  - `skill_group` 放第一位——查询必带它，且区分度适中（技能组通常 5-20 个）
  - `status` 放第二位——带上后可直接定位到 PENDING 这一段
  - `create_time` 放最后——用于排序，索引本身有序，**避免 filesort**
- 为什么不是 (status, create_time)：只按状态查会把所有技能组的待抢单都扫一遍，
  坐席只能抢自己组内的单，status 单列区分度太低

### 3. idx_handler_status (handler_id, status)

- 服务的查询：**坐席工作台"我的工单"**
  ```sql
  SELECT ... FROM t_ticket WHERE handler_id = ? AND status IN ('ASSIGNED','PROCESSING');
  ```
- handler_id 放第一位：坐席 id 区分度最高（上千坐席时接近唯一）
- status 放第二位：进一步缩小到"未完成"那几个状态

### 4. idx_sla_deadline (sla_deadline)

- 服务的查询：**超时扫描定时任务**（每 30 秒一次）
  ```sql
  SELECT id FROM t_ticket
  WHERE sla_deadline < NOW() AND status IN ('PENDING','ASSIGNED','PROCESSING') AND escalate_flag = 0
  LIMIT 500;
  ```
- 这是一个**范围查询**（`< NOW()`）。范围条件在联合索引里只能用到自己那一位，
  后面的字段用不上，所以这里只建单列索引，把 status / escalate_flag 作为回表后的过滤条件
- 为什么必须建：不建这个索引就是全表扫，30 万数据下每 30 秒扫一次会把 MySQL 压出问题

### 5. idx_creator_time (creator_id, create_time) —— 任务书漏掉，本项目补上

- 服务的查询：**客户端"我的工单"列表**
  ```sql
  SELECT ... FROM t_ticket WHERE creator_id = ? ORDER BY create_time DESC LIMIT 20;
  ```
- 任务书第 5 节没有这条索引，但页面清单里有"我的工单"，每天都会被点。
  只建 creator_id 而不带 create_time，排序仍要 filesort；
  两个字段一起建，既是过滤又是有序，可以直接 LIMIT 取前 20 条就停

---

## 二、流水表（3 张，各 1 个索引）

三张流水表用的都是同一个模式：`idx_ticket_time (ticket_id, create_time)`

| 表 | 服务的查询 | 说明 |
|---|---|---|
| t_ticket_log | 工单详情页「流转记录」 | `WHERE ticket_id = ? ORDER BY create_time` |
| t_ticket_dispatch | 工单详情页「派单轨迹」 | 同上 |
| t_ticket_comment | 工单详情页「对话列表」 | 同上，客户侧还要加 `is_internal = 0` 过滤 |

- ticket_id 在前：一张工单的流水通常几条到几十条，定位后数据量很小
- create_time 在后：保证按时间正序直接读，不用排序
- 为什么不用 (ticket_id) 单列：单列能过滤但不能保证顺序，MySQL 还要额外排序

---

## 三、t_idempotent（2 个索引）

### 1. uk_biz_key (biz_key) 唯一

- 服务的查询：**所有写接口的幂等拦截**
  ```sql
  INSERT INTO t_idempotent (biz_key, ...) VALUES (?, ...);
  -- 捕获 DuplicateKeyException 即说明是重复请求
  ```
- 关键点：**并发下"先查后插"有竞态**，两个线程可能同时查到"不存在"。
  只有数据库唯一索引能真正保证唯一，这是幂等方案的底座，不能换成 Redis 判断

### 2. idx_expire_time (expire_time)

- 服务的查询：**清理任务**
  ```sql
  DELETE FROM t_idempotent WHERE expire_time < NOW() LIMIT 1000;
  ```
- 没有它，清理任务每轮都是全表扫。幂等表增长很快（每次写请求一行）

---

## 四、t_sla_rule（1 个索引）

### uk_category_priority (category, priority) 唯一

- 服务的查询：创建工单时算截止时间
  ```sql
  SELECT sla_minutes FROM t_sla_rule
  WHERE category = ? AND priority = ? AND enabled = 1 AND deleted = 0;
  ```
- 为什么唯一：一个「分类+优先级」组合只应该有一条规则，多条会造成结果不确定
- 为什么两个字段都放进去：业务上就是这两个字段共同决定规则，单独查 category 时
  也能用上最左前缀

---

## 五、t_user（2 个索引）

### 1. uk_username (username) 唯一

- 服务的查询：登录 `WHERE username = ?`
- 注意一个副作用：**逻辑删除与唯一索引会打架**。
  用户被逻辑删除后 username 仍占着唯一索引，无法用同名重新注册。
  解决方式二选一：① 删除时把 username 改写为 `原值_deleted_时间戳`；② 唯一索引改为 (username, deleted)。
  本项目选 ①，在删除逻辑里处理，保持索引简单

### 2. idx_role_status (role, status)

- 服务的查询：自动派单时筛在线坐席
  ```sql
  SELECT id FROM t_user WHERE role = 'AGENT' AND status = 'ONLINE' AND skill_group = ?;
  ```
- 为什么 (role, status) 而不是 (skill_group, status)：坐席总量小（几十到几百），
  先按角色把客户和管理员过滤掉，剩下的量已经很小，skill_group 回表过滤即可。
  如果哪天坐席过千，应改为 (role, skill_group, status)

---

## 六、验证方式（面试可复述）

每条关键查询都用 `EXPLAIN` 验证，判断标准：

| 指标 | 期望值 | 含义 |
|---|---|---|
| type | `ref` 或 `range` | 走了索引；出现 `ALL` 就是全表扫，不合格 |
| key | 命中了预期的索引名 | 没命中或命中了别的索引都要查原因 |
| rows | 远小于表总行数 | 估算扫描行数 |
| Extra | 没有 `Using filesort` / `Using temporary` | 排序和分组都靠索引完成 |

示例（抢单大厅）：
```sql
EXPLAIN SELECT id, ticket_no, title FROM t_ticket
WHERE skill_group = 'REFUND' AND status = 'PENDING'
ORDER BY create_time DESC LIMIT 20;
```
期望：`type=ref`、`key=idx_skill_status_time`、`Extra` 只有 `Using index condition`（或 Backward index scan），
**不应出现 Using filesort**。

---

## 七、明确不加索引的地方

| 字段 | 为什么不加 |
|---|---|
| t_ticket.priority | 优先级几乎不做单独过滤条件，总是和 skill_group/status 一起出现 |
| t_ticket.deleted | 区分度极低（99% 都是 0），单独建索引无意义；它由 MyBatis-Plus 自动拼在 WHERE 里 |
| t_ticket.title / content | 不做模糊搜索（任务书没有搜索功能），若要全文检索用 Redis 8 内置搜索而不是 LIKE '%x%' |
| t_user.skill_group | 坐席量小，回表过滤成本可接受 |
