-- ============================================================================
-- 核心漏斗事件（Phase 0B，spec §6.3）
-- 10 类事件：product_exposure / product_detail_view / im_start / order_create /
--           pay_success / ship / confirm_receipt / refund_apply /
--           refund_success / review_create
-- 幂等：event_key 唯一 + 服务端 INSERT IGNORE（支付回调/超时扫描重试不重复计数）
-- 手工执行：mysql -uroot -p twelvet-fast < backend/sql/funnel_event.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS `funnel_event` (
  `event_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '租户/社区ID',
  `event_type` varchar(32) NOT NULL COMMENT '10 类漏斗事件之一',
  `user_id` bigint DEFAULT NULL COMMENT '匿名曝光为空',
  `role` varchar(16) DEFAULT NULL COMMENT 'buyer/seller/visitor',
  `product_id` bigint DEFAULT NULL,
  `category` varchar(20) DEFAULT NULL COMMENT 'product.cat_l1 快照',
  `ai_assisted` tinyint NOT NULL DEFAULT '0' COMMENT '1=AI 辅助产生',
  `scene` varchar(32) DEFAULT NULL COMMENT 'home_recommend/nearby/search/detail',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '订单/退款/评价/会话ID',
  `event_key` varchar(96) DEFAULT NULL COMMENT '幂等键，服务端事件必填',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_event_key` (`event_key`),
  KEY `idx_tenant_type_time` (`tenant_id`,`event_type`,`create_time`),
  KEY `idx_product_time` (`product_id`,`create_time`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核心漏斗事件';

-- ---------------------------------------------------------------------------
-- 菜单与权限：社区漏斗看板（运营角色 1/3 可见）
-- ---------------------------------------------------------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '社区漏斗', 2100, 7, '/admin/marketplace/funnel', 'marketplace/admin/Funnel',
       '1', 'C', '0', '0', 'marketplace:admin:funnel', 'icon-chart', '0', 'admin', NOW(),
       '核心漏斗事件与转化看板（按社区切片）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'marketplace:admin:funnel');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m
WHERE m.perms = 'marketplace:admin:funnel'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.menu_id FROM sys_menu m
WHERE m.perms = 'marketplace:admin:funnel'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.menu_id);