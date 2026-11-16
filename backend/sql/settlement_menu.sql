-- ============================================================================
-- 结算（Phase 1）菜单 / 角色授权 / 定时任务
-- 前置：settlement_schema.sql 已执行
-- 幂等：菜单按 perms 判重、授权按 (role_id, menu_id) 判重、任务按 invoke_target 判重，
--       可重复执行；sys_job status='1'（暂停）为本地开发惯例，联调时在后台启用。
-- 手工执行：mysql -uroot -p twelvet-fast < backend/sql/settlement_menu.sql
-- ============================================================================

-- ---------- 运营端菜单（parent 2100 运营管理，平台专属 scope=1） ----------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '实名审核', 2100, 9, '/admin/marketplace/kyc', 'marketplace/admin/Kyc',
       '1', 'C', '0', '0', 'marketplace:admin:kyc', 'icon-user', '1', 'admin', NOW(),
       '卖家实名资料审核（证件/卡号脱敏展示）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'marketplace:admin:kyc');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '结算单', 2100, 10, '/admin/marketplace/settlement', 'marketplace/admin/Settlement',
       '1', 'C', '0', '0', 'marketplace:admin:settlement', 'icon-order', '1', 'admin', NOW(),
       '结算单查询（holding/pending_settle/settled/closed/clawback）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'marketplace:admin:settlement');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '提现审核', 2100, 11, '/admin/marketplace/withdraw', 'marketplace/admin/Withdraw',
       '1', 'C', '0', '0', 'marketplace:admin:withdraw', 'icon-money', '1', 'admin', NOW(),
       '卖家提现审核与线下打款凭证回填'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'marketplace:admin:withdraw');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '结算对账', 2100, 12, '/admin/marketplace/recon', 'marketplace/admin/Recon',
       '1', 'C', '0', '0', 'marketplace:admin:recon', 'icon-chart', '1', 'admin', NOW(),
       '每日对账差异入队、处理与解冻卖家'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'marketplace:admin:recon');

-- ---------- C 端菜单（parent 2007 我的，scope=0 通用） ----------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '卖家中心', 2007, 8, '/user/settlement', 'marketplace/MySettlement',
       '1', 'C', '0', '0', 'marketplace:seller:settlement', 'icon-money', '0', 'admin', NOW(),
       '卖家实名、余额、提现记录'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'marketplace:seller:settlement');

-- ---------- 角色授权：运营菜单 → admin(1) / ops(3) ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_menu m
JOIN sys_role r ON r.role_id IN (1, 3)
WHERE m.perms IN ('marketplace:admin:kyc', 'marketplace:admin:settlement',
                  'marketplace:admin:withdraw', 'marketplace:admin:recon')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- ---------- 角色授权：卖家中心 → common(2) ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.menu_id
FROM sys_menu m
WHERE m.perms = 'marketplace:seller:settlement'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.menu_id);

-- ---------- 定时任务（默认暂停） ----------
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent,
     status, create_by, create_time, remark)
SELECT '结算-售后期扫描', 'DEFAULT', 'settlementScan.scan', '0 50 1 * * ?', 3, 1,
       '1', 'admin', NOW(),
       'holding→pending_settle：hold_until 到期且订单 done、无进行中退款、卖家已实名'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_job j WHERE j.invoke_target = 'settlementScan.scan');

INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent,
     status, create_by, create_time, remark)
SELECT '结算-每日打款批次', 'DEFAULT', 'settlementPayout.payout', '0 0 2 * * ?', 3, 1,
       '1', 'admin', NOW(),
       'pending_settle→settled：MockPayoutGateway，冻结/未实名/失败超限跳过'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_job j WHERE j.invoke_target = 'settlementPayout.payout');

INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent,
     status, create_by, create_time, remark)
SELECT '结算-每日对账', 'DEFAULT', 'settlementRecon.reconcile', '0 30 2 * * ?', 3, 1,
       '1', 'admin', NOW(),
       '六类差异入队并冻结相关卖家'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_job j WHERE j.invoke_target = 'settlementRecon.reconcile');