-- ============================================================================
--  售后工单系统 · 初始化建表脚本
--  Flyway 版本化迁移：V1__init_schema.sql
--  对应设计文档：docs/index-design.md、D:\新建文件夹\工单系统-表结构.xlsx
--
--  约定：
--    1. 引擎 InnoDB，字符集 utf8mb4（能存 emoji），排序规则 utf8mb4_0900_ai_ci
--    2. 时间统一 DATETIME(3)（毫秒精度），避免并发下同秒排序错乱
--    3. 业务表（t_user / t_ticket / t_sla_rule）带 create_time、update_time、deleted
--    4. 流水表（log / dispatch / comment）只带 create_time：只增不改，不留删除语义
--    5. 不建物理外键：关联由应用层保证，避免分库分表和批量归档时被枷锁卡住
--    6. 索引顺序 = 查询条件顺序，详见 docs/index-design.md
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 用户 / 坐席
-- ---------------------------------------------------------------------------
CREATE TABLE t_user (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username     VARCHAR(50)  NOT NULL                COMMENT '登录名，唯一',
    password     VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密文，不存明文',
    real_name    VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
    role         VARCHAR(20)  NOT NULL                COMMENT '角色：CUSTOMER 客户 / AGENT 坐席 / ADMIN 管理员',
    skill_group  VARCHAR(50)  DEFAULT NULL            COMMENT '技能组，抢单与派单的匹配依据',
    status       VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE' COMMENT '状态：ONLINE 在线 / OFFLINE 离线 / BUSY 忙碌',
    max_load     INT          NOT NULL DEFAULT 5      COMMENT '同时处理工单上限，派单时判满',
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_role_status (role, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户/坐席';

-- ---------------------------------------------------------------------------
-- 2. 工单主表（核心）
-- ---------------------------------------------------------------------------
CREATE TABLE t_ticket (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ticket_no     VARCHAR(32)  NOT NULL                COMMENT '业务单号，如 TK202611060001',
    title         VARCHAR(200) NOT NULL                COMMENT '标题',
    content       TEXT         NOT NULL                COMMENT '问题描述',
    category      VARCHAR(50)  NOT NULL                COMMENT '分类，与 t_sla_rule.category 对应',
    priority      VARCHAR(10)  NOT NULL DEFAULT 'P2'   COMMENT '优先级：P0 紧急 / P1 高 / P2 中 / P3 低',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/ASSIGNED/PROCESSING/RESOLVED/CLOSED/ESCALATED',
    creator_id    BIGINT       NOT NULL                COMMENT '创建人（客户），关联 t_user.id',
    handler_id    BIGINT       DEFAULT NULL            COMMENT '当前处理人（坐席），未派单为 NULL',
    skill_group   VARCHAR(50)  NOT NULL                COMMENT '所需技能组，抢单大厅按此过滤',
    version       INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号，抢单靠它防超卖',
    sla_deadline  DATETIME(3)  DEFAULT NULL            COMMENT 'SLA 截止时间，超时任务扫这个字段',
    accept_time   DATETIME(3)  DEFAULT NULL            COMMENT '接单时间',
    finish_time   DATETIME(3)  DEFAULT NULL            COMMENT '完成时间',
    escalate_flag TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已升级：0 否 / 1 是，防重复升级',
    create_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_no (ticket_no),
    KEY idx_skill_status_time (skill_group, status, create_time),
    KEY idx_handler_status (handler_id, status),
    KEY idx_sla_deadline (sla_deadline),
    KEY idx_creator_time (creator_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单主表';

-- ---------------------------------------------------------------------------
-- 3. 流转日志（流水表，只增不改）
-- ---------------------------------------------------------------------------
CREATE TABLE t_ticket_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ticket_id   BIGINT       NOT NULL                COMMENT '工单 ID',
    from_status VARCHAR(20)  DEFAULT NULL            COMMENT '变更前状态，创建时为空',
    to_status   VARCHAR(20)  NOT NULL                COMMENT '变更后状态',
    operator_id BIGINT       NOT NULL                COMMENT '操作人，系统操作为 0',
    action      VARCHAR(50)  NOT NULL                COMMENT '动作：CREATE/ACCEPT/TRANSFER/RESOLVE/CLOSE/ESCALATE',
    remark      VARCHAR(500) DEFAULT NULL            COMMENT '备注，如转派原因',
    create_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_ticket_time (ticket_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单流转日志';

-- ---------------------------------------------------------------------------
-- 4. 派单记录（流水表）
-- ---------------------------------------------------------------------------
CREATE TABLE t_ticket_dispatch (
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    ticket_id       BIGINT      NOT NULL                COMMENT '工单 ID',
    from_handler_id BIGINT      DEFAULT NULL            COMMENT '原处理人，首次派单为空',
    to_handler_id   BIGINT      NOT NULL                COMMENT '新处理人',
    reason          VARCHAR(20) NOT NULL                COMMENT 'AUTO 自动 / MANUAL 手动 / TRANSFER 转派 / ESCALATE 升级',
    create_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '派单时间',
    PRIMARY KEY (id),
    KEY idx_ticket_time (ticket_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '派单记录';

-- ---------------------------------------------------------------------------
-- 5. 回复记录（流水表）
-- ---------------------------------------------------------------------------
CREATE TABLE t_ticket_comment (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    ticket_id   BIGINT        NOT NULL                COMMENT '工单 ID',
    user_id     BIGINT        NOT NULL                COMMENT '发言人，关联 t_user.id',
    content     VARCHAR(1000) NOT NULL                COMMENT '回复内容',
    is_internal TINYINT       NOT NULL DEFAULT 0      COMMENT '是否内部备注：1 仅坐席可见',
    create_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发言时间',
    PRIMARY KEY (id),
    KEY idx_ticket_time (ticket_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单回复记录';

-- ---------------------------------------------------------------------------
-- 6. SLA 超时规则
-- ---------------------------------------------------------------------------
CREATE TABLE t_sla_rule (
    id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    category           VARCHAR(50) NOT NULL                COMMENT '分类，与 t_ticket.category 对应',
    priority           VARCHAR(10) NOT NULL                COMMENT '优先级，与 t_ticket.priority 对应',
    sla_minutes        INT         NOT NULL                COMMENT '响应时限（分钟），决定 sla_deadline',
    escalate_to_group  VARCHAR(50) DEFAULT NULL            COMMENT '超时后升级到哪个技能组',
    enabled            TINYINT     NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 / 0 停用',
    create_time        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted            TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_priority (category, priority)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SLA 超时规则';

-- ---------------------------------------------------------------------------
-- 7. 幂等表
-- ---------------------------------------------------------------------------
CREATE TABLE t_idempotent (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_key         VARCHAR(128) NOT NULL                COMMENT '幂等键：业务类型 + requestId',
    biz_type        VARCHAR(50)  NOT NULL                COMMENT '业务类型，如 CREATE_TICKET',
    result_snapshot TEXT         DEFAULT NULL            COMMENT '首次执行结果快照（JSON），重复请求直接返回它',
    expire_time     DATETIME(3)  NOT NULL                COMMENT '过期时间，定时任务清理',
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_key (biz_key),
    KEY idx_expire_time (expire_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '接口幂等表';
