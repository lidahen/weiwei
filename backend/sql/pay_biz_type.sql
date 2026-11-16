-- biz_type: trade(商品订单) / runner(互助任务) / renew(租户续费)
-- 幂等：列/索引存在则跳过；手工执行（不进 CLAUDE.md 初始化序列，与 report_feature.sql 同类决策）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_pay' AND COLUMN_NAME = 'biz_type');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE trade_pay ADD COLUMN biz_type VARCHAR(16) NOT NULL DEFAULT ''trade'' COMMENT ''业务类型:trade/runner/renew'' AFTER order_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填（顺序敏感，仅对仍为默认 trade 的行，可重复执行）：
-- 1) order_id IS NULL → 租户续费（写入路径唯一：insertRenewPayOrder）
UPDATE trade_pay SET biz_type = 'renew' WHERE order_id IS NULL AND biz_type = 'trade';
-- 2) 互助任务：只能用 pay_out_trade_no join（pay_order_id 存的是业务单号，不是 pay_id，禁止用其 join）
UPDATE trade_pay p JOIN twelve_runner_task t ON t.pay_out_trade_no = p.out_trade_no
  SET p.biz_type = 'runner' WHERE p.biz_type = 'trade';
-- 3) 其余保持 'trade'（DEFAULT 已覆盖）

-- 索引（幂等同列）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_pay' AND INDEX_NAME = 'idx_biz_order');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE trade_pay ADD INDEX idx_biz_order (biz_type, order_id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
