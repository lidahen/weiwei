-- ============================================================
-- 退款售后闭环：trade_refund 结构补齐 + 流水注释修正 + 菜单/授权/定时任务注册
-- 设计文档：docs/superpowers/specs/2026-10-30-refund-aftermarket-and-fill-gaps-design.md
-- 前置：docs/db/twelvet-fast_2026-10-21.sql 基线已导入
-- 幂等：ADD/INSERT 前先查 information_schema 或按业务键查重，存在则跳过
-- 说明：sys_job status='1'（暂停）为本地开发惯例，与库内既有 21 条 Job 一致，
--       联调需要时在后台「定时任务」中启用。
-- ============================================================

SET NAMES utf8mb4;

-- 1. 申请前订单状态（退款关闭时用于恢复）
SET @has_prev := (SELECT COUNT(*) FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_refund'
                    AND COLUMN_NAME = 'prev_order_status');
SET @sql := IF(@has_prev = 0,
    'ALTER TABLE `trade_refund` ADD COLUMN `prev_order_status` varchar(20) DEFAULT NULL COMMENT ''申请退款前的订单状态(unshipped/shipped/done/paid)，退款关闭时用于恢复''',
    'SELECT "prev_order_status 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 进行中唯一：MySQL 不支持部分索引，用生成列（终态为 NULL 时唯一索引允许多条）
SET @has_active := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_refund'
                      AND COLUMN_NAME = 'active_flag');
SET @sql := IF(@has_active = 0,
    'ALTER TABLE `trade_refund` ADD COLUMN `active_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `status` IN (''pending_seller'',''seller_agreed'',''buyer_returned'',''refunding'',''mediation'') THEN 1 ELSE NULL END) STORED COMMENT ''进行中标记(终态为NULL)''',
    'SELECT "active_flag 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_uk := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_refund'
                  AND INDEX_NAME = 'uk_order_active');
SET @sql := IF(@has_uk = 0,
    'ALTER TABLE `trade_refund` ADD UNIQUE KEY `uk_order_active` (`order_id`,`active_flag`)',
    'SELECT "uk_order_active 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3b. 基线 KEY idx_order_id(order_id) 现在是 uk_order_active 的严格前缀，纯冗余，只增加写放大
SET @has_idx_order_id := (SELECT COUNT(*) FROM information_schema.STATISTICS
                          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_refund'
                            AND INDEX_NAME = 'idx_order_id');
SET @sql := IF(@has_idx_order_id > 0,
    'ALTER TABLE `trade_refund` DROP INDEX `idx_order_id`',
    'SELECT "idx_order_id 不存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3c. 买家 / 卖家列表索引：trade_refund 无 tenant_id，列表查询 JOIN trade_order 过滤租户；
--     没有 buyer_id / seller_id 索引时 C 端「我的退款」会全表扫。
SET @has_idx_buyer := (SELECT COUNT(*) FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_refund'
                         AND INDEX_NAME = 'idx_buyer_id');
SET @sql := IF(@has_idx_buyer = 0,
    'ALTER TABLE `trade_refund` ADD INDEX `idx_buyer_id` (`buyer_id`)',
    'SELECT "idx_buyer_id 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx_seller := (SELECT COUNT(*) FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_refund'
                          AND INDEX_NAME = 'idx_seller_id');
SET @sql := IF(@has_idx_seller = 0,
    'ALTER TABLE `trade_refund` ADD INDEX `idx_seller_id` (`seller_id`)',
    'SELECT "idx_seller_id 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 列注释对齐实际写入值（DDL 里的 in/out 是历史误写）
ALTER TABLE `trade_pay_flow` MODIFY COLUMN `type` varchar(20) NOT NULL COMMENT 'pay/refund';

-- 4. 运营退款页菜单（parent 2100 =「运营管理」目录，与 2101-2107 同级）
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '退款管理', 2100, 8, '/admin/marketplace/refund', 'marketplace/admin/Refund',
       '1', 'C', '0', '0', 'marketplace:admin:refund', 'icon-money', '1', 'admin', NOW(),
       '退款审核 / 客服裁决 / 手工收口'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'marketplace:admin:refund');

-- 5. C 端「我的退款」菜单（parent 2007 =「我的」目录，与 2008-2013 同级）
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '我的退款', 2007, 7, '/user/refunds', 'marketplace/RefundList',
       '1', 'C', '0', '0', 'marketplace:refund:my', 'icon-money', '0', 'admin', NOW(),
       '买家退款申请列表'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'marketplace:refund:my');

-- 6. 授权：管理员(1)/运营(3) 看运营退款页；管理员(1)/普通角色(2) 看我的退款
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m
WHERE m.perms IN ('marketplace:admin:refund','marketplace:refund:my')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.menu_id FROM sys_menu m
WHERE m.perms = 'marketplace:admin:refund'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.menu_id FROM sys_menu m
WHERE m.perms = 'marketplace:refund:my'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.menu_id);

-- 7. 退款超时扫描任务（status='1' 暂停，与库内既有 Job 一致，需要时后台启用）
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent,
     status, create_by, create_time, remark)
SELECT '退款-超时扫描', 'DEFAULT', 'tradeRefundScan.scan', '0 0/30 * * * ?', 3, 1,
       '1', 'admin', NOW(),
       'pending_seller 48h→mediation；seller_agreed 7d→closed；buyer_returned 7d→mediation'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_job j WHERE j.invoke_target = 'tradeRefundScan.scan');
