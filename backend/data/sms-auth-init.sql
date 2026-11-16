-- ============================================
-- C 端手机号登录：租户内手机号唯一索引（2026-10-21）
-- 文档：docs/superpowers/specs/2026-10-20-c-end-phone-auth-design.md
-- 背景：手机号是 C 端唯一身份锚点，登录/自动建号依赖「租户内手机号唯一」。
--   原 sys_user 只有 uk_sys_user_tenant_username 与 idx_sys_user_global，手机号无唯一约束。
-- 方案：MySQL 8.0 函数唯一索引（**不新增列**），(表达式, tenant_id)。
--   ⚠️ 不能用生成列：旧版 twelvet 种子脚本的 sys_user INSERT 无列名，加列会报列数不匹配。
--   ⚠️ 不能直接对 phonenumber 建唯一索引：运营/客服账号手机号为空串，空串互撞。
--      故表达式必须带空串守卫 phonenumber <> ''。
--   ⚠️ 不能全局唯一：同一自然人可跨 N 个租户各有一行（global_user_id），故第二列必须是 tenant_id。
--   ⚠️ 表达式必须含 del_flag：deleteUserByIds 是软删（del_flag='2'），而登录查询只认 del_flag='0'。
--      若索引只按手机号去重，软删行会永久占位 —— 该手机号每次登录都「查不到 → insert 撞索引 →
--      重查仍查不到」，用户被无声锁死且无法自助恢复。把非 del_flag='0' 的行归为 NULL 后，
--      软删行不再参与唯一约束，手机号可被重新注册。
--      ⇒ 与登录查询的可见性口径一致：仅 del_flag='0' 的行占用手机号。
--   ⚠️ 必须用「正向匹配 + NULL 安全等号」del_flag <=> '0'，不能写成排除式
--      IF(phonenumber = '' OR del_flag <> '0', NULL, phonenumber)：
--      del_flag 可为 NULL（旧版 sys_user DDL 定义为 `char(1) NULL DEFAULT '0'`），
--      del_flag IS NULL 的行在排除式里是「FALSE OR NULL = NULL」，IF 当 false → 该行仍占用手机号；
--      而应用层查询是 `and su.del_flag = '0'`，对 NULL 行不可见。两边口径冲突就回到死锁：
--      查不到 → insert 撞索引 → 重查仍查不到 →「账号状态异常」。<=> 对 NULL 返回 0 而非 NULL，
--      语义与 `del_flag = '0'` 完全对齐。
--   ⚠️ 口径一致性约束：本表达式必须与 SysUserMapper.xml 的 selectUserByPhoneAndTenant
--      （`where su.phonenumber = #{phonenumber} and su.del_flag = '0'`）保持一致，
--      改一处必须同步改另一处，否则重新引入上述死锁。
-- ⚠️ 适用范围（2026-10-27 更新）：仅用于 2026-10-21 之前创建的旧库升级；
--   2026-10-21 全量基线（docs/db/twelvet-fast_2026-10-21.sql）已包含本索引，新环境无需执行。
--   旧库前提：sys_user 已有 tenant_id 列（历史 tenant migration 添加；当前基线已包含）。
--   执行：mysql -u root -p twelvet-fast < backend/data/sms-auth-init.sql
--   不要挂载到 docker-entrypoint-initdb.d；按需手工执行。
--   ⚠️ 不要给本脚本加「表/列不存在就跳过」的静默 guard：那会让索引永远建不出来且无任何告警，
--      比直接报错更糟（后台无保护地并发建号，手机号唯一性形同虚设）。
-- ⚠️ 需手工执行（应用不会自动加载本文件）
-- ⚠️ 先跑下面的前置检查：若存量数据已有同租户重复活跃手机号，ADD 会失败，
--   而此时表上**没有任何手机号唯一性**（比只有一个弱约束更糟）。先合并重复数据再执行。
-- 幂等 / 自动收敛：可重复执行，且能把任何历史旧版索引自动重建为正确版本。
--   判定「已就绪」须同时满足四条（任一不满足即视为旧版并重建）：
--     ① 索引唯一（NON_UNIQUE = 0）—— 非唯一索引零约束力，不能放过；
--     ② 组成恰为 2 部分，且第 2 部分是 tenant_id 列 —— 缺 tenant_id 的全局唯一会破坏跨租户共存；
--     ③ 第 1 部分表达式含 del_flag、含 <=>、含空串守卫 <> 三者；
--     ④ 上一轮 DROP 与 ADD 合并为**单条原子 ALTER**，不存在「删完没建上」的窗口。
--   执行逻辑：
--     索引不存在      → ADD
--     存在但非就绪    → 单条 ALTER DROP INDEX + ADD UNIQUE KEY（原子替换）
--     已就绪          → no-op
--   注意 ADD UNIQUE KEY ... IF NOT EXISTS 是 MariaDB 语法，MySQL 8.0 不支持（→ ERROR 1064），
--   故这里改用 @ddl + PREPARE/EXECUTE 的写法（与历史迁移脚本写法一致）。
-- 历史旧索引变体（脚本会自动识别并重建，无需手工 DROP，仅供人工核对）：
--   a) 不含 del_flag：((IF(phonenumber = '', NULL, phonenumber)), tenant_id)
--   b) 排除式（del_flag IS NULL 的行仍占位）：
--      ((IF(phonenumber = '' OR del_flag <> '0', NULL, phonenumber)), tenant_id)
--   c) 缺空串守卫（同租户多个空手机号会互撞）：
--      ((IF(del_flag <=> '0', phonenumber, NULL)), tenant_id)
--   d) 非唯一索引（ADD KEY 建的同名索引）/ 缺 tenant_id 的全局唯一索引
--   ⚠️ 判据不能只看 del_flag：b) c) 都含 del_flag；也不能只看 del_flag + <=>：b) 缺 <=>，c) 缺 <>。
--   ⚠️ LIKE 里的 _ 是通配符（'del-flag' LIKE '%del_flag%' 为真），故必须写成 '%del\_flag%'。
-- 人工核对当前表达式：SHOW CREATE TABLE sys_user;
-- 回滚（如需）：
-- ALTER TABLE sys_user DROP INDEX uk_sys_user_tenant_phone;
-- ============================================
SET NAMES utf8mb4;

-- 前置检查（务必先跑，有结果则先人工合并再执行下面的 DDL）：
-- SELECT tenant_id, phonenumber, COUNT(*) c FROM sys_user
--  WHERE phonenumber IS NOT NULL AND phonenumber <> '' AND del_flag = '0'
--  GROUP BY tenant_id, phonenumber HAVING c > 1;

-- 索引名下的组成部分数：正确版应为 2（表达式 + tenant_id）
SET @parts := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_tenant_phone');

-- 第 2 部分必须是 tenant_id 列（缺它则是全局唯一，会破坏同一自然人跨租户共存）
SET @tenant_part := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_tenant_phone'
      AND SEQ_IN_INDEX = 2 AND COLUMN_NAME = 'tenant_id');

-- 第 1 部分是否已就绪：唯一 + 表达式含 del_flag + NULL 安全等号 <=> + 空串守卫 <>
-- 注意：函数索引只有第 1 部分有 EXPRESSION（第 2 部分 tenant_id 是普通列，EXPRESSION 为 NULL）
-- 注意：'%del\_flag%' 里反斜杠转义下划线，否则 _ 会当通配符
SET @expr_ok := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_tenant_phone'
      AND SEQ_IN_INDEX = 1 AND NON_UNIQUE = 0
      AND EXPRESSION LIKE '%del\_flag%' AND EXPRESSION LIKE '%<=>%' AND EXPRESSION LIKE '%<>%');

-- ① 不存在 → ADD；② 已就绪 → no-op；③ 存在但非就绪 → 单条原子 DROP + ADD（无中间窗口）
SET @ddl := IF(@parts = 0,
    'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_sys_user_tenant_phone` ((IF(`del_flag` <=> ''0'' AND `phonenumber` <> '''', `phonenumber`, NULL)), `tenant_id`)',
    IF(@parts = 2 AND @tenant_part = 1 AND @expr_ok = 1,
        'SELECT 1',
        'ALTER TABLE `sys_user` DROP INDEX `uk_sys_user_tenant_phone`, ADD UNIQUE KEY `uk_sys_user_tenant_phone` ((IF(`del_flag` <=> ''0'' AND `phonenumber` <> '''', `phonenumber`, NULL)), `tenant_id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
