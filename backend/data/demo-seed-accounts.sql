-- ============================================================================
-- 演示数据规范化 ①：角色 / 部门 / 租户 / 账号
--
-- 适用环境：twelvet-fast（基线 docs/db/twelvet-fast_2026-10-21.sql 导入后）
-- 执行顺序：本脚本 → demo-seed-data.sql
-- 幂等性：以 UPDATE / DELETE ... WHERE 为主，可重复执行
--
-- 账号矩阵（default 租户id=1）：
--   admin        平台管理员   role=admin   后台全功能
--   ops_demo     运营小张     role=ops     后台：审核/看板
--   cs_demo      客服小李     role=cs      后台：客服/纠纷
--   user_demo    买家小美     role=common  C 端：下单/收藏/动态
--   seller_demo  卖家老张     role=common  C 端：发布商品/接单
--   demo         演示账号     role=common  兜底
-- thu 租户（id=2）：alice（common）、thuadmin（admin），演示租户隔离
-- 删除：重复账号 alice(122)、bob(124)；未使用的 test 角色
-- 统一口令：123456（与 admin 当前口令一致）
-- ============================================================================

SET NAMES utf8mb4;
START TRANSACTION;

-- ---------------------------------------------------------------- 1. 角色
-- 删除未使用的 test 角色及其菜单授权
DELETE FROM sys_role_menu
WHERE role_id IN (SELECT role_id FROM (SELECT role_id FROM sys_role WHERE role_key = 'test') t);
DELETE FROM sys_role WHERE role_key = 'test';

-- ---------------------------------------------------------------- 2. 部门
-- 新增「客服中心」，作为 cs_demo 的归属部门（原部门表没有客服相关节点）
INSERT INTO sys_dept
    (dept_id, parent_id, ancestors, dept_name, order_num, leader, phone, email, status, del_flag, create_by, create_time)
SELECT 110, 101, '0,100,101', '客服中心', 6, '小李', '13800001004', 'cs@weiwei.com', '0', '0', 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dept d WHERE d.dept_id = 110);

-- ---------------------------------------------------------------- 3. 租户
-- thu：演示用租户，原到期日 2026-09-27 已过期（发布商品会报「套餐已过期」），续期一年
UPDATE sys_tenant
SET expire_at = '2027-10-27 23:59:59'
WHERE tenant_code = 'thu';

-- pku：既无账号也无业务数据，保留只会让租户列表出现空壳；其数据在 ② 中先归位再删除
UPDATE sys_tenant
SET expire_at = '2027-10-27 23:59:59'
WHERE tenant_code = 'pku';

-- ---------------------------------------------------------------- 4. 账号资料
-- 4.1 admin：后台管理员，归属总部
UPDATE sys_user
SET nick_name   = '平台管理员',
    phonenumber = '13800000000',
    email       = 'admin@weiwei.com',
    user_type   = '00',
    dept_id     = 101,
    status      = 0,
    del_flag    = '0'
WHERE user_id = 1;

-- 4.2 买家（C 端普通用户，无部门）
UPDATE sys_user
SET nick_name   = '买家小美',
    phonenumber = '13800001001',
    email       = 'buyer@weiwei.com',
    user_type   = '02',
    dept_id     = NULL
WHERE user_id = 101;

-- 4.3 卖家（C 端普通用户，无部门）
UPDATE sys_user
SET nick_name   = '卖家老张',
    phonenumber = '13800001002',
    email       = 'seller@weiwei.com',
    user_type   = '02',
    dept_id     = NULL
WHERE user_id = 102;

-- 4.4 运营（平台运营 user_type=00，归市场部）
UPDATE sys_user
SET nick_name   = '运营小张',
    phonenumber = '13800001003',
    email       = 'ops@weiwei.com',
    user_type   = '00',
    dept_id     = 104
WHERE user_id = 103;

-- 4.5 客服（平台客服 user_type=01，归客服中心）
UPDATE sys_user
SET nick_name   = '客服小李',
    phonenumber = '13800001004',
    email       = 'cs@weiwei.com',
    user_type   = '01',
    dept_id     = 110
WHERE user_id = 104;

-- 4.6 兜底演示账号
UPDATE sys_user
SET nick_name   = '演示账号',
    phonenumber = '13800001005',
    email       = 'demo@weiwei.com',
    user_type   = '02',
    dept_id     = NULL
WHERE user_id = 108;

-- 4.7 thu 租户账号
UPDATE sys_user
SET nick_name   = 'Alice',
    phonenumber = '13800002001',
    email       = 'alice@thu.edu',
    user_type   = '02',
    dept_id     = NULL,
    global_user_id = 123
WHERE user_id = 123;

UPDATE sys_user
SET nick_name   = '清华管理员',
    phonenumber = '13800002002',
    email       = 'thuadmin@thu.edu',
    user_type   = '00',
    dept_id     = NULL
WHERE user_id = 125;

-- 4.8 统一口令 123456（与 admin 现有 BCrypt 一致）
UPDATE sys_user
SET password = '$2a$10$MBq3PvaIX9Yghhu.mm45wO8IC4WKXAUST9LvneCsQ71k/mrOdN6SO'
WHERE user_id IN (1, 101, 102, 103, 104, 108, 123, 125);

-- ---------------------------------------------------------------- 5. 角色映射
-- 一人一角色：admin→admin，ops→ops，cs→cs，其余→common
DELETE FROM sys_user_role WHERE user_id IN (1, 101, 102, 103, 104, 108, 123, 125);
INSERT INTO sys_user_role (user_id, role_id) VALUES
    (1,   1),   -- admin       → admin
    (103, 3),   -- ops_demo    → ops
    (104, 4),   -- cs_demo     → cs
    (101, 2),   -- user_demo   → common
    (102, 2),   -- seller_demo → common
    (108, 2),   -- demo        → common
    (123, 2),   -- alice       → common
    (125, 1);   -- thuadmin    → admin

-- ---------------------------------------------------------------- 6. 删除重复/空壳账号
-- 122 alice（与 123 同名，登录不带租户头会歧义）、124 bob（无角色无数据）
-- 6.1 会话与消息（含 122 参与的会话 15、123↔124 的会话 17）
DELETE FROM im_unread
WHERE user_id IN (122, 124)
   OR conv_id IN (SELECT conv_id FROM im_conversation WHERE user_a_id IN (122, 124) OR user_b_id IN (122, 124));
DELETE FROM im_message
WHERE sender_id IN (122, 124) OR receiver_id IN (122, 124)
   OR conv_id IN (SELECT conv_id FROM im_conversation WHERE user_a_id IN (122, 124) OR user_b_id IN (122, 124));
DELETE FROM im_conversation WHERE user_a_id IN (122, 124) OR user_b_id IN (122, 124);

-- 6.2 alice 的 Agent 记忆迁移到保留的 123（thu 租户）
UPDATE ai_user_memory SET user_id = 123, tenant_id = 2 WHERE user_id = 122;
DELETE FROM ai_user_memory WHERE user_id = 124;

-- 6.3 其余引用
DELETE FROM user_address      WHERE user_id IN (122, 124);
DELETE FROM product_favorite  WHERE user_id IN (122, 124);
DELETE FROM sys_user_role     WHERE user_id IN (122, 124);
DELETE FROM sys_user_post     WHERE user_id IN (122, 124);
DELETE FROM sys_user          WHERE user_id IN (122, 124);

-- ---------------------------------------------------------------- 7. 孤儿数据
-- sys_user_post 里有 user_id=2,3,4,5 等已不存在的账号；sys_user_role 里也有 user_id=4 的残留
DELETE FROM sys_user_post WHERE user_id NOT IN (SELECT user_id FROM sys_user);
DELETE FROM sys_user_role WHERE user_id NOT IN (SELECT user_id FROM sys_user);

COMMIT;
