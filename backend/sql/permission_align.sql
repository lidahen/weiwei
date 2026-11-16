-- ============================================================================
-- 权限漂移治理：让「代码注解」与「菜单权限」重新对齐
--
-- 背景：全量比对发现 28 个 @SaCheckPermission 在 sys_menu 里没有对应行，
--       且部分模块两侧命名分叉（gen:type vs gen:FieldType 等）。
--       admin 持有 "*" 超级权限，所以问题长期隐形；一旦给非超管角色授权就 403。
--
-- 本脚本只做菜单侧调整（新增缺的按钮行、统一命名），代码侧改动见同批提交：
--   * SysUserController           system:user:edit      → system:user:update
--   * SysJobLogController         system:job:*          → monitor:job:*
--   * SysDictDataController       system:dict:list      → system:dict:query
--   * AdminRecommendController    admin:marketplace:*   → marketplace:admin:recommend
--
-- 幂等：UPDATE 按 perms 精确匹配；INSERT 用 NOT EXISTS 防重
-- ============================================================================

SET NAMES utf8mb4;
START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 1. 补「定时任务导出」按钮（代码 SysJobController 要 monitor:job:export，
--    菜单只有 query/insert/update/remove）
-- ---------------------------------------------------------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '任务导出', 110, 5, '', NULL, '1', 'F', '1', '0',
       'monitor:job:export', '#', '0', 'admin', NOW(), '补齐代码要求的导出按钮权限'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'monitor:job:export');

-- ---------------------------------------------------------------------------
-- 2. 补「推荐效果」页面（前端已有 /admin/marketplace/recommend-stats 路由，
--    但菜单表没有这一行，运营侧根本看不到入口）
-- ---------------------------------------------------------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '推荐效果', 2100, 6, '/admin/marketplace/recommend-stats', 'marketplace/admin/RecommendStats',
       '1', 'C', '0', '0', 'marketplace:admin:recommend', 'icon-monitor', '0', 'admin', NOW(),
       '推荐曝光/点击/CTR 看板'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'marketplace:admin:recommend');

-- 授权：管理员与运营可见
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m
WHERE m.perms = 'marketplace:admin:recommend'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.menu_id FROM sys_menu m
WHERE m.perms = 'marketplace:admin:recommend'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.menu_id);

-- ---------------------------------------------------------------------------
-- 3. 命名统一：以代码里的实体名为准
-- ---------------------------------------------------------------------------
-- 3.1 gen:type:* → gen:FieldType:*（字段类型，GenFieldTypeController）
UPDATE sys_menu SET perms = 'gen:FieldType:list'  WHERE perms = 'gen:type:list';
UPDATE sys_menu SET perms = 'gen:FieldType:query' WHERE perms = 'gen:type:query';
UPDATE sys_menu SET perms = 'gen:FieldType:add'   WHERE perms = 'gen:type:add';
UPDATE sys_menu SET perms = 'gen:FieldType:edit'  WHERE perms = 'gen:type:edit';
UPDATE sys_menu SET perms = 'gen:FieldType:remove' WHERE perms = 'gen:type:remove';

-- 3.2 gen:templateGroup:* → gen:template:*（模板，GenTemplateController；
--     菜单名叫「模板管理」，Group 是历史笔误；模板分组另有 gen:group:*）
UPDATE sys_menu SET perms = 'gen:template:list'   WHERE perms = 'gen:templateGroup:list';
UPDATE sys_menu SET perms = 'gen:template:query'  WHERE perms = 'gen:templateGroup:query';
UPDATE sys_menu SET perms = 'gen:template:add'    WHERE perms = 'gen:templateGroup:add';
UPDATE sys_menu SET perms = 'gen:template:edit'   WHERE perms = 'gen:templateGroup:edit';
UPDATE sys_menu SET perms = 'gen:template:remove' WHERE perms = 'gen:templateGroup:remove';

-- 3.3 ai:music:works:list → ai:music:song:list（作品列表，与 song:submit/query/remove 同族）
UPDATE sys_menu SET perms = 'ai:music:song:list' WHERE perms = 'ai:music:works:list';

-- ---------------------------------------------------------------------------
-- 4. 补齐按钮级权限：AI 文档 / 知识库 / 写词（菜单只建了页面级 list）
-- ---------------------------------------------------------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '文档新增', 2115, 1, '', NULL, '1', 'F', '1', '0', 'ai:doc:add', '#', '0', 'admin', NOW(), '补齐 ai:doc:add'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'ai:doc:add');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '文档查看', 2115, 2, '', NULL, '1', 'F', '1', '0', 'ai:doc:query', '#', '0', 'admin', NOW(), '补齐 ai:doc:query'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'ai:doc:query');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '文档修改', 2115, 3, '', NULL, '1', 'F', '1', '0', 'ai:doc:update', '#', '0', 'admin', NOW(), '补齐 ai:doc:update'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'ai:doc:update');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '文档删除', 2115, 4, '', NULL, '1', 'F', '1', '0', 'ai:doc:remove', '#', '0', 'admin', NOW(), '补齐 ai:doc:remove'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'ai:doc:remove');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '知识库新增', 2114, 1, '', NULL, '1', 'F', '1', '0', 'ai:knowledge:add', '#', '0', 'admin', NOW(), '补齐 ai:knowledge:add'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'ai:knowledge:add');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '知识库查看', 2114, 2, '', NULL, '1', 'F', '1', '0', 'ai:knowledge:query', '#', '0', 'admin', NOW(), '补齐 ai:knowledge:query'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'ai:knowledge:query');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '知识库修改', 2114, 3, '', NULL, '1', 'F', '1', '0', 'ai:knowledge:update', '#', '0', 'admin', NOW(), '补齐 ai:knowledge:update'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'ai:knowledge:update');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '知识库删除', 2114, 4, '', NULL, '1', 'F', '1', '0', 'ai:knowledge:remove', '#', '0', 'admin', NOW(), '补齐 ai:knowledge:remove'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'ai:knowledge:remove');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT 'AI 改词', 2501, 4, '', NULL, '1', 'F', '1', '0', 'ai:music:lyrics:revise', '#', '0', 'admin', NOW(), '补齐 ai:music:lyrics:revise'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'ai:music:lyrics:revise');

COMMIT;
