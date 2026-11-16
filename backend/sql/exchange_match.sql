-- ============================================================
-- 交换广场二期（向量智能匹配）- 推送去重表 + 定时任务
-- 设计文档：docs/交换广场二期-智能匹配与信用设计.md
-- 前置：exchange_schema.sql / exchange_menu.sql 已执行（一期）
-- Job ID：124/125（线上最大 job_id=123，2026-10-25 校准）
-- 说明：status='1'（暂停）为本地开发惯例，联调时经 /job/cron/run 手动触发
-- ============================================================

-- 1. 匹配推送去重快照（同一人对同一物品只推一次）
CREATE TABLE IF NOT EXISTS `twelve_exchange_match_push` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `tenant_id`       BIGINT        NOT NULL,
  `item_id`         BIGINT        NOT NULL COMMENT '物品ID',
  `pushed_user_ids` VARCHAR(1000) NOT NULL DEFAULT '[]' COMMENT '已推送过的需求方 userId（JSON 数组，diff 去重）',
  `last_push_time`  DATETIME      DEFAULT NULL,
  `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item` (`tenant_id`, `item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换物匹配推送去重快照';

-- 2. 定时任务（默认暂停，与库内全部 Job 一致）
INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark) VALUES
(124, '换物向量索引增量同步', 'DEFAULT', 'exchangeMatchTask.syncVectors', '0 */30 * * * ?', 3, 1, '1', 'admin', NOW(), '每30分钟调 ai-service /exchange-match/reindex 增量同步双索引（镜像 productRecommend.syncVectors）'),
(125, '换物反向匹配每日推送', 'DEFAULT', 'exchangeTask.scanMatchPush', '0 0 10 * * ?', 3, 1, '1', 'admin', NOW(), '每日10点反向匹配扫描，快照diff去重后 IM 推送"有人想换你的物品"（镜像 productRecommend.scanPriceDrop）');
