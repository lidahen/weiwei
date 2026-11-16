-- ============================================================
-- 举报处理闭环：report 表约束补齐 + 废弃表清理
-- 设计文档：docs/superpowers/specs/2026-10-29-report-handling-design.md
-- 前置：docs/db/twelvet-fast_2026-10-21.sql 基线已导入
-- 幂等：所有 ADD 前先查 information_schema，存在则跳过
-- ============================================================

SET NAMES utf8mb4;

-- 1. 同人同对象唯一（重复举报走 UPDATE 复活，见 ReportServiceImpl.submit）
SET @has_uk := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report'
                  AND INDEX_NAME = 'uk_reporter_target');
SET @sql := IF(@has_uk = 0,
    'ALTER TABLE `report` ADD UNIQUE KEY `uk_reporter_target` (`reporter_id`,`target_type`,`target_id`)',
    'SELECT "uk_reporter_target 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 按对象反查（运营列表按 targetType 批量取摘要）
SET @has_idx_target := (SELECT COUNT(*) FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report'
                          AND INDEX_NAME = 'idx_target');
SET @sql := IF(@has_idx_target = 0,
    'ALTER TABLE `report` ADD KEY `idx_target` (`target_type`,`target_id`)',
    'SELECT "idx_target 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 待处理/已处理 tab 按时间排序
SET @has_idx_status_time := (SELECT COUNT(*) FROM information_schema.STATISTICS
                             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report'
                               AND INDEX_NAME = 'idx_status_time');
SET @sql := IF(@has_idx_status_time = 0,
    'ALTER TABLE `report` ADD KEY `idx_status_time` (`status`,`create_time`)',
    'SELECT "idx_status_time 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3b. 「全部」tab 无 status 谓词、只按 create_time 排序，以 status 打头的复合索引服务不了它
--     （会退化成全表扫 + filesort）。单列 create_time 索引补这个缺口。
SET @has_idx_create_time := (SELECT COUNT(*) FROM information_schema.STATISTICS
                             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report'
                               AND INDEX_NAME = 'idx_create_time');
SET @sql := IF(@has_idx_create_time = 0,
    'ALTER TABLE `report` ADD KEY `idx_create_time` (`create_time`)',
    'SELECT "idx_create_time 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3c. 基线 KEY idx_status(status) 现在是 idx_status_time 的严格前缀，纯冗余，只增加写放大
SET @has_idx_status := (SELECT COUNT(*) FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report'
                          AND INDEX_NAME = 'idx_status');
SET @sql := IF(@has_idx_status > 0,
    'ALTER TABLE `report` DROP INDEX `idx_status`',
    'SELECT "idx_status 不存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 列注释对齐实际取值
ALTER TABLE `report` MODIFY COLUMN `action` varchar(20) DEFAULT NULL
    COMMENT 'dismiss/warn/remove_product/remove_feed/ban_user';
ALTER TABLE `report` MODIFY COLUMN `target_type` varchar(20) NOT NULL
    COMMENT 'product/feed/user';

-- 5. 删除重复建模的死表 feed_report（已核实 0 行、全仓 0 代码引用）
DROP TABLE IF EXISTS `feed_report`;
