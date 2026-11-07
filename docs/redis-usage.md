# Redis 在本项目里的三个用途

> 这份文档回答一个问题：**Redis 到底用在哪了？**
> 每一处都给出真实文件与行号（2026-11-06 核对）。行号会随代码变动，看方法名比看行号可靠。

一句话定位：**Redis 在这里不是"缓存业务数据"，而是 MySQL 干不了的两件事——可主动失效的登录态、并发安全的序号；外加一个刻意保留、生产未使用的分布式锁。**

业务数据（工单、日志、回复、用户）**全部在 MySQL**，Redis 里一个业务字段都没有。

---

## 一、三种 key，一目了然

| key 格式 | 存什么 | 有效期 | 谁在用 | 生产是否使用 |
|---|---|---|---|---|
| `auth:token:{userId}` | JWT 令牌字符串 | 120 分钟 | 登录 / 登出 / 每个请求 | ✅ 是 |
| `ticket:no:{yyyyMMdd}` | 当天工单序号（整数） | 48 小时 | 创建工单 | ✅ 是 |
| `lock:ticket:accept:{ticketId}` | 一个随机 UUID（锁标记） | 30 秒 | `/accept-lock` 接口 | ❌ 仅对比实验 |

---

## 二、用途 1：登录态（最核心的那个）

### 代码位置

| 动作 | 文件 | 方法/位置 |
|---|---|---|
| 登录时写入 | `service/AuthService.java` | `login()`，`redis.opsForValue().set(...)` |
| 登出时删除 | `service/AuthService.java` | `logout()`，`redis.delete(...)` |
| 每个请求校验 | `security/AuthInterceptor.java` | `preHandle()`，`redis.opsForValue().get(...)` |
| key 前缀常量 | `security/AuthInterceptor.java` | `TOKEN_KEY_PREFIX = "auth:token:"` |

### 核心代码（AuthInterceptor.preHandle）

```java
String token = header.substring(BEARER.length()).trim();
LoginUser user = jwtUtil.parse(token);                    // 先验签+解析

String cached = redis.opsForValue().get(TOKEN_KEY_PREFIX + user.userId());
if (cached == null || !cached.equals(token)) {            // 再比对服务端登记的那份
    throw new AuthException("登录已失效，请重新登录");
}
```

### 为什么必须有这一层

JWT 是自包含的：签名一签，服务端就再也收不回来。想实现「点退出就真的失效」，
必须由服务端记住"当前有效的是哪个 token"，Redis 就是这份登记簿。

没有 Redis，只能等 JWT 自然过期（本项目 120 分钟），期间旧 token 一直有效——
用户改了密码、被辞退、手机丢了，都无法立即断开。

### 一个容易忽略的行为

同一账号在第二台设备登录 → Redis 里这个 key 被新 token **覆盖** → 第一台设备的 token 立刻 401。
这是"单端登录"的实现方式，是取舍不是 bug。

要允许多端同时在线，key 就得改成 `auth:token:{userId}:{deviceId}`，或用 Set 存多个 token。
本项目选单端登录，因为售后坐席共用工作机的场景下，防止两人串号更重要。

---

## 三、用途 2：工单单号

### 代码位置

`service/TicketService.java` 的 `nextTicketNo()`：

```java
String key = NO_KEY_PREFIX + date;                    // ticket:no:20261106
Long seq = redis.opsForValue().increment(key);        // 原子自增
if (seq != null && seq == 1L) {
    redis.expire(key, Duration.ofHours(48));          // 只在第一次创建时设过期
}
return "TK" + date + String.format("%06d", seq);      // TK20261106 + 000001
```

### 为什么不用数据库

```sql
-- 这种写法在并发下必重号：两个请求可能读到同一个 MAX
SELECT MAX(ticket_no) + 1 FROM t_ticket WHERE ...
```

Redis 的 `INCR` 是单线程原子操作，不可能两个请求拿到同一个值。
数据库层面还有 `ticket_no` 唯一索引兜底。

### 为什么 48 小时后过期

key 按天分开（`ticket:no:20261106`），过期时间 48 小时——
历史 key 由 Redis 自己清理，**不需要写定时清理任务**。这是 Redis TTL 相比数据库表的天然优势。

### 降级设计

```java
} catch (Exception e) {
    log.warn("Redis 生成单号失败，降级为时间戳：{}", e.getMessage());
    return "TK" + date + String.format("%06d", System.nanoTime() % 1000000);
}
```

Redis 挂了不能导致整个系统不能提单。降级后可能重号，但唯一索引会拒绝，用户重试即可。
**取舍原则：宁可偶发失败，不可全站不可用。**

---

## 四、用途 3：抢单分布式锁（生产未使用）

### 代码位置

`service/TicketService.java` 的 `acceptWithLock()`：

```java
String key = ACCEPT_LOCK_PREFIX + id;                  // lock:ticket:accept:9
String token = UUID.randomUUID().toString();

Boolean got = redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(30));
if (!Boolean.TRUE.equals(got)) {
    throw new BizException(..., "手慢了，这单正在被其他坐席抢");
}
try {
    doAccept(id, user.userId(), "REDIS_LOCK");         // 锁内还要二次校验状态
} finally {
    redis.execute(UNLOCK_SCRIPT, List.of(key), token); // Lua 比对后删除
}
```

释放锁的 Lua 脚本（`UNLOCK_SCRIPT` 常量）：

```lua
if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end
```

用 Lua 的原因：如果先 get 再 del，两步之间锁可能刚好过期并被别人拿走，你的 del 就删了别人的锁。

### 为什么生产不用它

页面上的「抢单」按钮调的是 `/api/ticket/{id}/accept`（**乐观锁，完全不碰 Redis**）。

原因：竞争只发生在同一行数据上，数据库行锁就够；少一次网络往返，也少一个必须存活的依赖。
20 并发实测两个版本都只有 1 个人抢到——**唯一性由数据库保证，不是由锁保证**。

完整对比与实测数据见 `docs/concurrency-compare.md`。

---

## 五、现在 Redis 里到底有什么（实测）

```bash
docker exec -it ticket-redis redis-cli --scan
```

某个时间点的真实输出：

```
auth:token:2          # 李梅（客户）登录中
auth:token:3          # 李四（坐席）登录中
auth:token:4          # 赵六（坐席）登录中
auth:token:5          # 王五（管理员）登录中
ticket:no:20261106    # 值 = 16，TTL 还剩 170014 秒
```

## 六、三个可以自己做的验证

```bash
docker exec -it ticket-redis redis-cli
```

| 命令 | 看到什么 | 说明 |
|---|---|---|
| `keys *` | 上面的几个 key | 只有 auth 和 ticket:no，没有任何业务数据 |
| `get ticket:no:20261106` | `16` | 今天已生成 16 个单号 |
| `ttl ticket:no:20261106` | 秒数 | 48 小时倒计时 |
| `get auth:token:1` | `eyJhbGciOi...` 长串 | 就是张三当前登录的 JWT |

**最直观的实验**：浏览器登录张三 → `keys *` 多出 `auth:token:1` → 点退出登录 → 再 `keys *`，它消失了。

---

## 七、配置位置

`backend/src/main/resources/application-local.yml`：

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
      timeout: 5000ms          # 曾设 2000ms，WSL 端口转发首握手超时导致登录 500
      connect-timeout: 5000ms
      lettuce:
        shutdown-timeout: 200ms
```

容器侧（`docker/docker-compose.yml`）：

```yaml
command: >
  redis-server
  --maxmemory 200mb
  --maxmemory-policy allkeys-lru
  --appendonly no            # 开发环境不做持久化，重启即清空
```

**`--appendonly no` 的后果**：容器重启后令牌全部失效（需重新登录）、单号从 1 重新开始。
开发环境这是对的；生产必须开 AOF 或 RDB。

---

## 八、面试怎么讲（30 秒版）

> 我这个项目里 Redis 只干两件事。
>
> 一是存 JWT 令牌。JWT 本身无法撤销，要支持退出登录和踢下线，就必须在服务端记一份有效令牌，
> 我用 `auth:token:{userId}` 存，每次请求比对一次。
>
> 二是生成工单单号，用 `INCR` 做原子自增，按天分 key 加 48 小时过期——
> 比 `SELECT MAX(no)+1` 安全，也不需要写清理任务。
>
> 抢单我另外实现了一版 Redis 分布式锁，但生产用的是数据库乐观锁。因为竞争只在同一行上，
> 数据库行锁足够，还能少一个必须存活的依赖。我做了 20 并发对比，两版都只有一个成功。

追问时可能被问到：

| 问题 | 答题要点 |
|---|---|
| 为什么单号用 Redis 不用数据库自增 | 用 Redis：可读单号（TK+日期+序号）、按天重新计数、TTL 自动清理；数据库自增是全局单调，无法按天归零 |
| Redis 挂了会怎样 | 登录态全失效（用户重新登录即可）；单号降级为时间戳尾数，可能重号但唯一索引兜底 |
| token 存 Redis 会不会内存爆 | 每个用户一个 key，值约 200 字节；1 万在线用户约 2MB。配了 maxmemory 200mb + LRU |
| 锁过期时间怎么定 | 业务耗时上界 × 2~3。抢单是几条 SQL，毫秒级，30 秒很宽松 |
| 为什么不把工单列表缓存到 Redis | 数据量小、查询都走索引、且要求实时（状态一变就要看到）。缓存反而引入一致性成本 |
