-- ============================================================
-- 社区跑腿 → 社区互助：展示文案迁移（2026-10-27）
-- 说明：只改菜单 / 定时任务 / 表注释里的展示文字，
--       不改模块名、类名、URL、权限标识、表名、字段名。
-- 执行：mysql -u root -p123456 twelvet-fast < sql/runner_rename_mutual_aid.sql
-- 幂等：重复执行无副作用。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 1. C 端菜单（2300-2309） ----------
UPDATE `sys_menu` SET `menu_name` = '社区互助', `remark` = '互助服务C端目录' WHERE `menu_id` = 2300;
UPDATE `sys_menu` SET `menu_name` = '互助大厅', `remark` = '互助大厅' WHERE `menu_id` = 2301;

-- ---------- 2. B 端菜单（2310-2319） ----------
UPDATE `sys_menu` SET `menu_name` = '互助管理', `remark` = '互助管理后台' WHERE `menu_id` = 2310;
UPDATE `sys_menu` SET `remark` = '互助任务管理' WHERE `menu_id` = 2311;
UPDATE `sys_menu` SET `remark` = '互助数据看板' WHERE `menu_id` = 2312;

-- ---------- 3. 按钮级权限（2320-2329），只改展示名 ----------
UPDATE `sys_menu` SET `menu_name` = '接单', `remark` = '接单按钮' WHERE `menu_id` = 2320;
UPDATE `sys_menu` SET `menu_name` = '开始服务', `remark` = '开始服务按钮' WHERE `menu_id` = 2321;

-- ---------- 4. AI 社区管家菜单备注 ----------
UPDATE `sys_menu` SET `remark` = 'AI 社区管家（互助悬赏/社区近况/好物问答）' WHERE `menu_id` = 2403;

-- ---------- 5. 定时任务名称（invoke_target 不变，仅改展示名） ----------
UPDATE `sys_job` SET `job_name` = '互助任务超时未接单扫描' WHERE `job_name` = '跑腿任务超时未接单扫描';
UPDATE `sys_job` SET `job_name` = '互助任务超时未确认自动确认' WHERE `job_name` = '跑腿任务超时未确认自动确认';

-- ---------- 6. 表注释（可选，仅影响数据库工具展示） ----------
ALTER TABLE `twelve_runner_task` COMMENT = '互助任务表';
ALTER TABLE `twelve_runner_notify_log` COMMENT = '互助状态流转通知日志';
ALTER TABLE `twelve_runner_review` COMMENT = '互助任务评价';

-- ---------- 7. 如启用菜单 i18n（i18n_dict 有数据时按需执行） ----------
-- UPDATE `i18n_dict` SET `value` = '社区互助' WHERE `locale` = 'zh-CN' AND `value` = '社区跑腿';
-- UPDATE `i18n_dict` SET `value` = '互助大厅' WHERE `locale` = 'zh-CN' AND `value` = '接单大厅';

-- 回滚：把上面每条 UPDATE / ALTER 的新旧值对调即可。