-- ============================================================
-- 交换广场（以物换物）模块 - 菜单 / 角色绑定 / 定时任务
-- 设计文档：docs/交换广场模块设计方案.md
-- 前置：exchange_schema.sql 已执行
-- 菜单 ID 段：2700-2730（校准于 2026-10-25，线上最大 menu_id=2613，
--             2400/2500/2600 段已被 AI 日报/音乐/创作台占用）
-- Job ID：121-123（线上最大 job_id=120）
-- 说明：sys_job status='1'（暂停）为本地开发惯例，与现有全部 Job 一致，
--       联调需要时在后台"定时任务"中启用。
-- ============================================================

-- ---------- C 端菜单（绑定角色 1 admin / 2 common） ----------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, menu_scope, create_by, create_time, remark) VALUES
(2700, '交换广场', 0, 6, '/exchange', NULL, '1', 'M', '0', '0', '', 'icon-order', '0', 'admin', NOW(), '以物换物广场目录'),
(2701, '换物广场', 2700, 1, '/exchange/square', 'Exchange/Square', '1', 'C', '0', '0', 'exchange:square:list', 'icon-takeaway', '0', 'admin', NOW(), '换物广场列表页'),
(2702, '我的物品', 2700, 2, '/exchange/my-items', 'Exchange/MyItems', '1', 'C', '0', '0', 'exchange:item:my-list', 'icon-task', '0', 'admin', NOW(), '我的换物物品'),
(2703, '我的意向', 2700, 3, '/exchange/offers', 'Exchange/Offers', '1', 'C', '0', '0', 'exchange:offer:list', 'icon-list', '0', 'admin', NOW(), '收到/发出的换物意向'),
(2704, '我的交换', 2700, 4, '/exchange/my-orders', 'Exchange/MyOrders', '1', 'C', '0', '0', 'exchange:order:my-list', 'icon-order', '0', 'admin', NOW(), '我的交换订单'),
(2705, '发布物品', 2700, 5, '/exchange/publish', 'Exchange/Publish', '1', 'C', '1', '0', 'exchange:item:publish', 'icon-plus', '0', 'admin', NOW(), '发布换物物品（隐藏菜单）'),
(2706, '物品详情', 2700, 6, '/exchange/detail', 'Exchange/Detail', '1', 'C', '1', '0', 'exchange:item:detail', '#', '0', 'admin', NOW(), '换物物品详情（隐藏菜单）'),
(2707, '订单详情', 2700, 7, '/exchange/order-detail', 'Exchange/OrderDetail', '1', 'C', '1', '0', 'exchange:order:detail', '#', '0', 'admin', NOW(), '交换订单详情（隐藏菜单）');

-- ---------- 管理端菜单（绑定角色 1 admin / 3 ops） ----------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, menu_scope, create_by, create_time, remark) VALUES
(2710, '交换管理', 0, 7, '/admin/exchange', NULL, '1', 'M', '0', '0', '', 'icon-monitor', '0', 'admin', NOW(), '交换广场管理目录'),
(2711, '物品审核', 2710, 1, '/admin/exchange/item-audit', 'Exchange/admin/ItemAudit', '1', 'C', '0', '0', 'exchange:admin:item:list', 'icon-list', '0', 'admin', NOW(), '换物物品审核（AI初审medium兜底）'),
(2712, '订单管理', 2710, 2, '/admin/exchange/order', 'Exchange/admin/OrderPage', '1', 'C', '0', '0', 'exchange:admin:order:list', 'icon-order', '0', 'admin', NOW(), '交换订单管理/纠纷裁决'),
(2713, '数据看板', 2710, 3, '/admin/exchange/stats', 'Exchange/admin/Stats', '1', 'C', '0', '0', 'exchange:admin:stat:view', 'icon-data-analysis', '0', 'admin', NOW(), '交换广场数据看板');

-- ---------- 按钮级权限（F） ----------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, menu_scope, create_by, create_time, remark) VALUES
(2720, '发起意向', 2701, 1, '', NULL, '1', 'F', '1', '0', 'exchange:offer:send', '#', '0', 'admin', NOW(), ''),
(2721, '接受意向', 2703, 1, '', NULL, '1', 'F', '1', '0', 'exchange:offer:accept', '#', '0', 'admin', NOW(), ''),
(2722, '婉拒意向', 2703, 2, '', NULL, '1', 'F', '1', '0', 'exchange:offer:reject', '#', '0', 'admin', NOW(), ''),
(2723, '下架物品', 2702, 1, '', NULL, '1', 'F', '1', '0', 'exchange:item:offshelf', '#', '0', 'admin', NOW(), ''),
(2724, '标记交付', 2704, 1, '', NULL, '1', 'F', '1', '0', 'exchange:order:deliver', '#', '0', 'admin', NOW(), ''),
(2725, '确认收货', 2704, 2, '', NULL, '1', 'F', '1', '0', 'exchange:order:confirm', '#', '0', 'admin', NOW(), ''),
(2726, '取消订单', 2704, 3, '', NULL, '1', 'F', '1', '0', 'exchange:order:cancel', '#', '0', 'admin', NOW(), ''),
(2727, '发起纠纷', 2704, 4, '', NULL, '1', 'F', '1', '0', 'exchange:order:dispute', '#', '0', 'admin', NOW(), ''),
(2728, '提交评价', 2704, 5, '', NULL, '1', 'F', '1', '0', 'exchange:order:review', '#', '0', 'admin', NOW(), ''),
(2729, '审核通过/驳回', 2711, 1, '', NULL, '1', 'F', '1', '0', 'exchange:admin:item:audit', '#', '0', 'admin', NOW(), ''),
(2730, '订单裁决', 2712, 1, '', NULL, '1', 'F', '1', '0', 'exchange:admin:order:force', '#', '0', 'admin', NOW(), '');

-- ---------- 角色-菜单绑定 ----------
-- C 端（2700-2707、2720-2728）→ admin + common
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM sys_menu m
JOIN sys_role r ON r.role_id IN (1, 2)
WHERE m.menu_id BETWEEN 2700 AND 2707 OR m.menu_id BETWEEN 2720 AND 2728;

-- 管理端（2710-2713、2729-2730）→ admin + ops
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM sys_menu m
JOIN sys_role r ON r.role_id IN (1, 3)
WHERE m.menu_id BETWEEN 2710 AND 2713 OR m.menu_id IN (2729, 2730);

-- ---------- 定时任务（默认暂停，与库内现有 Job 一致，需要时后台启用） ----------
INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark) VALUES
(121, '换物物品AI初审扫描', 'DEFAULT', 'exchangeAiModeration.auditItems', '0 */10 * * * ?', 3, 1, '1', 'admin', NOW(), '每10分钟扫一次auditing换物物品走AI初审三分流（low过/high驳/medium留人工）'),
(122, '换物意向超时扫描', 'DEFAULT', 'exchangeTask.scanTimeoutOffer', '0 */5 * * * ?', 3, 1, '1', 'admin', NOW(), '每5分钟扫一次pending意向，72h未处理自动过期'),
(123, '换物订单超时扫描', 'DEFAULT', 'exchangeTask.scanTimeoutOrder', '0 0 * * * ?', 3, 1, '1', 'admin', NOW(), '每小时扫一次订单：建单7天未双交付取消；双交付后3天未双确认自动完成');
