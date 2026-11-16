-- ============================================================
-- 交换广场（以物换物）模块 - 表结构
-- 设计文档：docs/交换广场模块设计方案.md
-- 执行方式：mysql -uroot -p twelvet-fast < exchange_schema.sql
-- ============================================================

-- 1. 换物物品表
CREATE TABLE IF NOT EXISTS `twelve_exchange_item` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '物品ID',
  `tenant_id`       BIGINT        NOT NULL COMMENT '租户ID',
  `item_no`         VARCHAR(32)   NOT NULL COMMENT '物品编号 ESC+yyyyMMddHHmmss+6位随机',
  `owner_id`        BIGINT        NOT NULL COMMENT '物品主人',
  `title`           VARCHAR(100)  NOT NULL COMMENT '物品标题',
  `cover`           VARCHAR(255)  DEFAULT NULL COMMENT '封面图',
  `images`          VARCHAR(1000) DEFAULT NULL COMMENT '图片（JSON数组）',
  `cat_l1`          VARCHAR(20)   NOT NULL COMMENT '一级类目（复用 product_category_dict）',
  `cat_l2`          VARCHAR(20)   DEFAULT NULL COMMENT '二级类目',
  `condition_level` VARCHAR(20)   NOT NULL COMMENT '成色 brand_new/99/95/90/80',
  `est_value`       DECIMAL(10,2) DEFAULT NULL COMMENT '主人自估参考价值（仅展示，不构成交易价格）',
  `want_cats`       VARCHAR(200)  DEFAULT NULL COMMENT '期望换得的类目（JSON数组，可空=不限）',
  `want_desc`       VARCHAR(500)  DEFAULT NULL COMMENT '期望换什么（自由文本）',
  `accept_any`      TINYINT       NOT NULL DEFAULT 0 COMMENT '1接受任意洽谈 0仅限期望范围',
  `delivery_mode`   VARCHAR(20)   NOT NULL DEFAULT 'face' COMMENT 'face面交/express快递/both',
  `city`            VARCHAR(50)   DEFAULT NULL COMMENT '城市',
  `location`        VARCHAR(200)  DEFAULT NULL COMMENT '面交区域描述',
  `description`     TEXT          COMMENT '物品描述',
  `status`          VARCHAR(20)   NOT NULL DEFAULT 'auditing' COMMENT 'draft/auditing/onswap/locked/swapped/offshelf/rejected',
  `reject_reason`   VARCHAR(200)  DEFAULT NULL COMMENT '审核驳回原因',
  `view_count`      INT           NOT NULL DEFAULT 0,
  `offer_count`     INT           NOT NULL DEFAULT 0 COMMENT '收到的意向数',
  `swap_count`      INT           NOT NULL DEFAULT 0 COMMENT '累计换出次数',
  `hot_score`       DECIMAL(10,4) NOT NULL DEFAULT 0,
  `publish_time`    DATETIME      DEFAULT NULL COMMENT '首次上架时间',
  `create_by`       BIGINT        DEFAULT NULL,
  `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_no` (`tenant_id`, `item_no`),
  KEY `idx_owner` (`tenant_id`, `owner_id`),
  KEY `idx_status` (`tenant_id`, `status`),
  KEY `idx_cat` (`tenant_id`, `cat_l1`, `status`),
  FULLTEXT KEY `ft_exchange_search` (`title`, `description`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换物物品表';

-- 2. 换物意向表
CREATE TABLE IF NOT EXISTS `twelve_exchange_offer` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `tenant_id`      BIGINT        NOT NULL,
  `item_id`        BIGINT        NOT NULL COMMENT '被申请的物品',
  `owner_id`       BIGINT        NOT NULL COMMENT '被申请人（物品主人，冗余）',
  `offeror_id`     BIGINT        NOT NULL COMMENT '发起人',
  `offer_item_id`  BIGINT        DEFAULT NULL COMMENT '发起人用来交换的库内物品ID',
  `offer_snapshot` VARCHAR(1000) DEFAULT NULL COMMENT '发起人物品快照或快速描述（JSON，未选库内物品时必填）',
  `message`        VARCHAR(500)  DEFAULT NULL COMMENT '交换留言',
  `diff_amount`    DECIMAL(10,2) DEFAULT NULL COMMENT '愿补差价（仅展示，MVP不走资金流）',
  `status`         VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/rejected/expired/withdrawn/closed',
  `expire_time`    DATETIME      NOT NULL COMMENT '过期时间（默认72h）',
  `handle_time`    DATETIME      DEFAULT NULL COMMENT '处理时间',
  `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_item` (`tenant_id`, `item_id`, `status`),
  KEY `idx_offeror` (`tenant_id`, `offeror_id`, `status`),
  KEY `idx_owner` (`tenant_id`, `owner_id`, `status`),
  KEY `idx_expire` (`tenant_id`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换物意向表';

-- 3. 交换订单表（双边履约：a/b 各自交付、各自确认）
CREATE TABLE IF NOT EXISTS `twelve_exchange_order` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`        BIGINT       NOT NULL,
  `order_no`         VARCHAR(32)  NOT NULL COMMENT 'EXC+yyyyMMddHHmmss+6位随机',
  `offer_id`         BIGINT       NOT NULL COMMENT '来源意向',
  `item_a_id`        BIGINT       NOT NULL COMMENT 'A 的物品',
  `item_b_id`        BIGINT       NOT NULL COMMENT 'B 的物品',
  `owner_a_id`       BIGINT       NOT NULL COMMENT 'A 用户（物品主）',
  `owner_b_id`       BIGINT       NOT NULL COMMENT 'B 用户（发起人）',
  `delivery_mode`    VARCHAR(20)  NOT NULL DEFAULT 'face' COMMENT 'face/express',
  `status`           VARCHAR(20)  NOT NULL DEFAULT 'created' COMMENT 'created/delivering/pending_confirm/completed/cancelled/disputed',
  `a_delivered_time` DATETIME     DEFAULT NULL COMMENT 'A 标记交付时间',
  `b_delivered_time` DATETIME     DEFAULT NULL COMMENT 'B 标记交付时间',
  `a_confirmed_time` DATETIME     DEFAULT NULL COMMENT 'A 确认收到时间',
  `b_confirmed_time` DATETIME     DEFAULT NULL COMMENT 'B 确认收到时间',
  `a_track_no`       VARCHAR(50)  DEFAULT NULL COMMENT 'A 快递单号',
  `b_track_no`       VARCHAR(50)  DEFAULT NULL COMMENT 'B 快递单号',
  `cancel_reason`    VARCHAR(200) DEFAULT NULL,
  `dispute_reason`   VARCHAR(200) DEFAULT NULL,
  `dispute_result`   VARCHAR(200) DEFAULT NULL COMMENT '后台裁决结果',
  `complete_time`    DATETIME     DEFAULT NULL,
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`tenant_id`, `order_no`),
  KEY `idx_owner_a` (`tenant_id`, `owner_a_id`, `status`),
  KEY `idx_owner_b` (`tenant_id`, `owner_b_id`, `status`),
  KEY `idx_item_a` (`tenant_id`, `item_a_id`),
  KEY `idx_item_b` (`tenant_id`, `item_b_id`),
  KEY `idx_status` (`tenant_id`, `status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交换订单表';

-- 4. 交换订单流转日志
CREATE TABLE IF NOT EXISTS `twelve_exchange_order_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`   BIGINT       NOT NULL,
  `order_id`    BIGINT       NOT NULL,
  `operator_id` BIGINT       NOT NULL COMMENT '操作人（用户或后台管理员）',
  `action`      VARCHAR(30)  NOT NULL COMMENT 'create/deliver/confirm/cancel/dispute/timeout/admin_force_complete/admin_force_cancel/review',
  `remark`      VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`tenant_id`, `order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交换订单流转日志';

-- 5. 交换双向评价
CREATE TABLE IF NOT EXISTS `twelve_exchange_review` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`   BIGINT       NOT NULL,
  `order_id`    BIGINT       NOT NULL,
  `reviewer_id` BIGINT       NOT NULL,
  `reviewee_id` BIGINT       NOT NULL,
  `score`       TINYINT      NOT NULL COMMENT '1-5星',
  `content`     VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_reviewer` (`tenant_id`, `order_id`, `reviewer_id`),
  KEY `idx_reviewee` (`tenant_id`, `reviewee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交换评价表';

-- 6. 换物面交码（一单两码，双方各持一个，线下互相出示核验）
CREATE TABLE IF NOT EXISTS `twelve_exchange_pickup_code` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `tenant_id`   BIGINT      NOT NULL,
  `order_id`    BIGINT      NOT NULL,
  `side`        CHAR(1)     NOT NULL COMMENT 'a/b',
  `code`        VARCHAR(6)  NOT NULL,
  `expire_at`   DATETIME    DEFAULT NULL,
  `used`        TINYINT(1)  DEFAULT '0',
  `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_side` (`order_id`, `side`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换物面交码';
