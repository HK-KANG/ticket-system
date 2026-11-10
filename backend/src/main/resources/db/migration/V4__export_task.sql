-- Phase 7 异步导出：任务表
--
-- 设计要点：
-- 1. 请求只建任务立即返回，真正的查库和写 Excel 交给 MQ 消费者，慢活不占 HTTP 线程
-- 2. params 存查询条件快照：用户点导出时的筛选条件必须冻结，
--    否则任务排队期间条件变了，导出的内容和用户预期不一致
-- 3. file_path 存服务端绝对路径，下载接口校验归属后才读文件
-- 4. status: PENDING -> RUNNING -> SUCCESS / FAILED
CREATE TABLE t_export_task (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_no     VARCHAR(32)  NOT NULL                COMMENT '任务号，给用户看',
    user_id     BIGINT       NOT NULL                COMMENT '发起人',
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    params      VARCHAR(1000) DEFAULT NULL           COMMENT '查询条件 JSON 快照',
    file_name   VARCHAR(200) DEFAULT NULL            COMMENT '下载时的文件名',
    file_path   VARCHAR(500) DEFAULT NULL            COMMENT '服务端文件路径',
    file_size   BIGINT       DEFAULT NULL            COMMENT '文件字节数',
    row_count   INT          DEFAULT NULL            COMMENT '导出行数',
    error_msg   VARCHAR(500) DEFAULT NULL            COMMENT '失败原因',
    create_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_user_time (user_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '异步导出任务';
