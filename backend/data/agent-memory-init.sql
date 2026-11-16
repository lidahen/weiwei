-- ============================================
-- Agent 长期记忆（用户画像） 初始化脚本
-- 文档：docs/superpowers/specs/2026-10-22-agent-long-term-memory-design.md §4.1
-- 内容：ai_user_memory 聚合表（fact=事实层 / trait=聚合特质层）
--       唯一键去重 → 行数 = 用户数 × ≤50，与对话数无关
-- 幂等：可重复执行（CREATE TABLE IF NOT EXISTS）
-- 回滚（如需）：
-- DROP TABLE IF EXISTS `ai_user_memory`;
-- ============================================
SET NAMES utf8mb4;

-- ============================================
-- 1. Agent 长期记忆表（每用户聚合，行数有界）
--    唯一键 (tenant_id, user_id, memory_type, mem_key, scope) 是"表不爆炸"的根本：
--    用户说 10 次"要便宜"仍是同一行，hit_count 从 1 涨到 10。
--    scope 必须进唯一键：类目相关偏好写 category:<一级类目>，
--    否则「喜欢数码」和「喜欢服装」会互相覆盖。
--    evidence 内联 JSON 只留最近 5 条（每条 ≤100 字），滚动覆盖，不建证据流水表。
-- ============================================
CREATE TABLE IF NOT EXISTS `ai_user_memory` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
  `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
  `memory_type`     VARCHAR(16)  NOT NULL DEFAULT 'fact' COMMENT 'fact=事实 / trait=聚合特质',
  `mem_key`         VARCHAR(32)  NOT NULL COMMENT '稳定短标识，取值见设计文档 3.2 词表',
  `mem_value`       VARCHAR(500) NOT NULL COMMENT '偏好值（自然语言短语）',
  `confidence`      DECIMAL(3,2) NOT NULL DEFAULT 0.60 COMMENT '基础置信度 0~1',
  `hit_count`       INT          NOT NULL DEFAULT 1 COMMENT '累计命中次数（跨会话累加）',
  `session_count`   INT          NOT NULL DEFAULT 1 COMMENT '累计命中的不同会话数',
  `last_session_id` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '最近一次命中的会话，用于会话内去重',
  `evidence`        JSON         NULL COMMENT '最近 5 条原话片段 [{text,at,session}]',
  `scope`           VARCHAR(64)  NOT NULL DEFAULT 'global' COMMENT 'global / category:数码 等',
  `source`          VARCHAR(16)  NOT NULL DEFAULT 'dialog' COMMENT 'dialog=对话提取 / behavior=工具行为',
  `first_seen`      DATETIME     NOT NULL,
  `last_seen`       DATETIME     NOT NULL,
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=有效 0=已失效（覆盖/淘汰/超期）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_mem` (`tenant_id`, `user_id`, `memory_type`, `mem_key`, `scope`),
  KEY `idx_user_active` (`tenant_id`, `user_id`, `status`, `last_seen`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 长期记忆（每用户聚合，行数有界）';
