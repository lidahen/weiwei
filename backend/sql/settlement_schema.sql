-- ============================================================
-- Phase 1 模拟结算：5 张表结构
-- 设计文档：docs/superpowers/specs/2026-11-02-settlement-compliance-design.md
-- 前置：docs/db/twelvet-fast_2026-10-21.sql 基线已导入
-- 幂等：CREATE TABLE IF NOT EXISTS；trade_withdraw 旧表先按 information_schema 判断后改名存档，
--       二次执行直接跳过（改名后新表已含 account_id，不再命中改名分支）
-- 说明：基线里的 trade_withdraw 是空表、旧结构（user_id/bank_card_no 明文）；
--       AdminMarketplaceMapper.selectFinanceStats 的 pendingWithdraws 仍引用它（已随本脚本改为新结构口径），
--       改名后旧表以 trade_withdraw_legacy_baseline 存档（可手工删除），再建新结构。
-- ============================================================

SET NAMES utf8mb4;

-- 1. 卖家收款账户（余额是账本结果，不是资金池）
CREATE TABLE IF NOT EXISTS `seller_account` (
  `account_id` bigint NOT NULL AUTO_INCREMENT,
  `seller_id` bigint NOT NULL COMMENT '卖家 sys_user.user_id',
  `tenant_id` bigint NOT NULL COMMENT '租户ID(sys_tenant.id)，与订单对齐',
  `account_name` varchar(64) DEFAULT NULL COMMENT '实名姓名',
  `id_card_no` varchar(255) DEFAULT NULL COMMENT '证件号密文(AES/GCM)',
  `bank_card_no` varchar(255) DEFAULT NULL COMMENT '银行卡号密文(AES/GCM)',
  `bank_name` varchar(64) DEFAULT NULL COMMENT '开户行',
  `kyc_status` varchar(16) NOT NULL DEFAULT 'unverified' COMMENT 'unverified/pending/verified/rejected',
  `balance` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '可用余额（账本结果，非资金池）',
  `frozen` tinyint NOT NULL DEFAULT '0' COMMENT '1=冻结（负余额/对账差异），禁止提现与新打款',
  `unfreeze_reason` varchar(255) DEFAULT NULL COMMENT '人工解冻原因',
  `unfreeze_by` bigint DEFAULT NULL COMMENT '解冻操作人',
  `unfreeze_time` datetime DEFAULT NULL COMMENT '解冻时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_seller_tenant` (`seller_id`,`tenant_id`),
  KEY `idx_kyc_status` (`kyc_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卖家收款账户';

-- 1.1 已建表的环境：补齐人工解冻审计列（幂等，按列存在性判断）
SET @has_unfreeze_reason := (SELECT COUNT(*) FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_account'
                               AND COLUMN_NAME = 'unfreeze_reason');
SET @sql := IF(@has_unfreeze_reason = 0,
    'ALTER TABLE `seller_account` ADD COLUMN `unfreeze_reason` varchar(255) DEFAULT NULL COMMENT ''人工解冻原因'', ADD COLUMN `unfreeze_by` bigint DEFAULT NULL COMMENT ''解冻操作人'', ADD COLUMN `unfreeze_time` datetime DEFAULT NULL COMMENT ''解冻时间''',
    'SELECT "seller_account 已有解冻审计列，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 结算单（一单一结算，uk_order_id 承担重复回调幂等）
CREATE TABLE IF NOT EXISTS `settlement_order` (
  `settlement_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '商品订单ID',
  `refund_id` bigint DEFAULT NULL COMMENT '关联退款单（closed/clawback 时写）',
  `seller_id` bigint NOT NULL,
  `buyer_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `gross_amount` decimal(12,2) NOT NULL COMMENT '订单实付金额',
  `commission_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '平台佣金',
  `net_amount` decimal(12,2) NOT NULL COMMENT '应结给卖家=gross-commission',
  `status` varchar(20) NOT NULL DEFAULT 'holding' COMMENT 'holding/pending_settle/settled/withdrawn/closed/clawback',
  `done_at` datetime DEFAULT NULL COMMENT '订单完成时间（扫描时从 trade_order 派生）',
  `hold_until` datetime DEFAULT NULL COMMENT 'done_at + hold-days',
  `settled_at` datetime DEFAULT NULL,
  `channel` varchar(16) DEFAULT NULL COMMENT 'mock/alipay/wechat',
  `channel_trans_no` varchar(64) DEFAULT NULL COMMENT '渠道结算流水号',
  `fail_count` int NOT NULL DEFAULT '0' COMMENT '打款失败次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`settlement_id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  UNIQUE KEY `uk_channel_trans` (`channel`,`channel_trans_no`),
  KEY `idx_status_hold` (`status`,`hold_until`),
  KEY `idx_seller_tenant` (`seller_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算单（一单一结算）';

-- 3. 卖家余额账本（只追加，uk_biz_key 承担重复消费幂等）
CREATE TABLE IF NOT EXISTS `seller_balance_flow` (
  `flow_id` bigint NOT NULL AUTO_INCREMENT,
  `account_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `settlement_id` bigint DEFAULT NULL,
  `refund_id` bigint DEFAULT NULL,
  `withdraw_id` bigint DEFAULT NULL,
  `type` varchar(16) NOT NULL COMMENT 'settle_in/clawback/withdraw_out/adjust',
  `amount` decimal(12,2) NOT NULL COMMENT '正数入账，负数扣减',
  `balance_after` decimal(12,2) NOT NULL COMMENT '变动后余额（对账用）',
  `biz_key` varchar(64) NOT NULL COMMENT '幂等键 settle:{id}/clawback:{refundId}/withdraw:{id}/withdraw_reject:{id}',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`flow_id`),
  UNIQUE KEY `uk_biz_key` (`biz_key`),
  KEY `idx_account_time` (`account_id`,`create_time`),
  KEY `idx_settlement` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卖家余额账本（只追加）';

-- 4. 提现申请：基线同名空表先改名存档，再建新结构
SET @has_account := (SELECT COUNT(*) FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_withdraw'
                       AND COLUMN_NAME = 'account_id');
SET @sql := IF(@has_account = 0,
    'RENAME TABLE `trade_withdraw` TO `trade_withdraw_legacy_baseline`',
    'SELECT "trade_withdraw 已是新结构，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `trade_withdraw` (
  `withdraw_id` bigint NOT NULL AUTO_INCREMENT,
  `withdraw_no` varchar(64) NOT NULL COMMENT '对外提现单号 W+uuid（W+32 位 = 33 字符）',
  `account_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/paid/rejected',
  `operator_id` bigint DEFAULT NULL COMMENT '审核/打款人',
  `voucher_url` varchar(255) DEFAULT NULL COMMENT '线下打款凭证',
  `fail_reason` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`withdraw_id`),
  UNIQUE KEY `uk_withdraw_no` (`withdraw_no`),
  KEY `idx_account_status` (`account_id`,`status`),
  KEY `idx_seller_tenant` (`seller_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现申请';

-- 4.1 已建表的环境：withdraw_no 曾为 varchar(32)，W+uuid 是 33 字符，插入会报 1406；这里幂等加宽
SET @withdraw_no_len := (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
                         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_withdraw'
                           AND COLUMN_NAME = 'withdraw_no');
SET @sql := IF(@withdraw_no_len < 64,
    'ALTER TABLE `trade_withdraw` MODIFY COLUMN `withdraw_no` varchar(64) NOT NULL COMMENT ''对外提现单号 W+uuid（W+32 位 = 33 字符）''',
    'SELECT "trade_withdraw.withdraw_no 已是 varchar(64)，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. 对账差异队列（uk_diff 保证同一业务日同一类型同一业务键只入队一次）
CREATE TABLE IF NOT EXISTS `settlement_recon_diff` (
  `diff_id` bigint NOT NULL AUTO_INCREMENT,
  `biz_date` date NOT NULL COMMENT '对账业务日',
  `type` varchar(24) NOT NULL COMMENT 'missing_settlement/phantom_settlement/amount_mismatch/status_mismatch/duplicate_settlement/ledger_mismatch',
  `biz_key` varchar(96) NOT NULL COMMENT '本类型内幂等键',
  `order_id` bigint DEFAULT NULL,
  `settlement_id` bigint DEFAULT NULL,
  `seller_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `channel_amount` decimal(12,2) DEFAULT NULL,
  `platform_amount` decimal(12,2) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'open' COMMENT 'open/resolved',
  `resolved_by` bigint DEFAULT NULL,
  `resolved_note` varchar(255) DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`diff_id`),
  UNIQUE KEY `uk_diff` (`biz_date`,`type`,`biz_key`),
  KEY `idx_status_date` (`status`,`biz_date`),
  KEY `idx_seller_tenant` (`seller_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算对账差异队列';