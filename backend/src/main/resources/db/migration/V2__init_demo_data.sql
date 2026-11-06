-- ============================================================================
--  V2 初始化数据：6 个演示账号 + 8 条 SLA 规则
--
--  说明：
--    1. 密码统一 123456，存的是 BCrypt 密文（自带随机盐，每个账号密文都不同）。
--       想验证：用 BCryptPasswordEncoder.matches("123456", 密文) 返回 true。
--    2. 数据与结构分开放：V1 只管表结构，V2 只管初始数据，回滚和排查都清楚。
--    3. 这是初始化数据，不是「演示假数据」——账号是系统能跑起来的最小前提，
--       任务书第 11 条约束针对的是往业务表塞假工单。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 账号：3 个角色各 2 个
-- ---------------------------------------------------------------------------
INSERT INTO t_user (username, password, real_name, role, skill_group, status, max_load) VALUES
-- 客户
('zhangsan', '$2a$10$tgrRDJW/2AKquD5XnTo0WOMG9NnYZjBYX1orQAmehvVO3sxJxyCdC', '张三', 'CUSTOMER', NULL, 'ONLINE', 5),
('limei',    '$2a$10$xeCChw8BCr5xu0Y1JOUD4uZrulL2y0QMRPFWqrb4PtYfkUzKSjYiW', '李梅', 'CUSTOMER', NULL, 'ONLINE', 5),
-- 坐席：分属两个技能组，抢单时各看各的池子
('lisi',     '$2a$10$4avs3cUYZWZd..Knkk4M1u7mu0WSZ2XmmbcgXO7SoqVk2591P8bcy', '李四', 'AGENT', '售后一组', 'ONLINE', 5),
('zhaoliu',  '$2a$10$bFutiw72UZBN4lGPpQBgTeCFj6ZiSRjqHWDct1ZYWsxjlUtf6TtDm', '赵六', 'AGENT', '售后二组', 'ONLINE', 5),
-- 管理员
('admin',    '$2a$10$4ekwM2uARmIdYMhk2NOkqesnMwrUTPJSlWFrVEe5Z7Y1Jn/WukF1O', '王五', 'ADMIN', NULL, 'ONLINE', 20),
('admin2',   '$2a$10$v/XLhaj97YidVWsIKvao6e/58SJABhMaQMfW6h.KQ1T9g0vJAvD5m', '孙七', 'ADMIN', NULL, 'ONLINE', 20);

-- ---------------------------------------------------------------------------
-- SLA 规则：分类 + 优先级 唯一命中一条，决定工单的 sla_deadline
--   规律：P0 一律 30 分钟，P1 一小时量级，P2 半天，发票类可放宽
-- ---------------------------------------------------------------------------
INSERT INTO t_sla_rule (category, priority, sla_minutes, escalate_to_group, enabled) VALUES
('退款',    'P0', 30,  '售后一组', 1),
('退款',    'P2', 240, '售后一组', 1),
('物流',    'P1', 60,  '售后二组', 1),
('物流',    'P2', 480, '售后二组', 1),
('质量问题', 'P0', 30,  '售后一组', 1),
('质量问题', 'P2', 240, '售后一组', 1),
('发票',    'P2', 480, '售后二组', 1),
('其他',    'P2', 480, '售后二组', 1);
