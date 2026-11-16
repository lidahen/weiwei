# C 端手机号自助注册与登录（短信 mock）设计

- 日期：2026-10-20
- 状态：待批准
- 关联：`docs/superpowers/specs/2026-10-19-trade-closedloop-fix-design.md`（交易闭环）、`backend/data/neighborswap-menus.sql`（C 端角色菜单）

---

## 1. 背景与问题

### 1.1 现状（实测）

| 事实 | 证据 |
|---|---|
| 后端只有账号密码登录 | `LoginServiceImpl.login()` 仅 `selectUserByUserName` + `BCrypt.checkpw`；`LoginDTO` 的 `username`/`password` 均 `@NotBlank` |
| 前端的「手机号登录」是假的 | `ui/src/pages/Login/index.tsx` 的 `onGetCaptcha` 只 `console.log` + 弹「验证码为：1234」，从未调用后端；提交时把 `mobile`/`code` 塞进 payload，后端 `LoginDTO` 无此字段 |
| 没有任何短信基础设施 | 全仓无 `dysmsapi` 依赖、无 sms 配置、无短信 Service |
| 没有自助注册入口 | 全仓无 `/register` 路由、无注册页；用户只能由后台「用户管理」创建 |
| 没有手机号唯一约束 | `sys_user` 只有 `uk_sys_user_tenant_username(tenant_id, username)` 与 `idx_sys_user_global(global_user_id)` |
| 手机号已经是「人」的锚点 | `SysUserServiceImpl.insertUser()` 用 `selectGlobalIdByPhone` 做跨租户同人串联；`global_user_id` 机制依赖手机号 |
| **前端全站强制登录** | `ui/src/app.tsx` 的 `getInitialState` 与 `onPageChange`：除 `/login` 和 `TWT.authIgnore`（只有 `/login/oauth2/github`、`/ai/flow`）外，未登录一律 `history.push('/login')` |

### 1.2 目标

1. C 端用户可用「手机号 + 验证码」自助注册并登录，无需人工建号。
2. **维持全站登录门禁**：未登录只能看到登录页，登录后才能进入任何业务页面（含首页、商品详情）。本期不开放匿名浏览。
3. 短信先走 mock（固定验证码 `123456`），保留切换到真实短信服务的接口。

### 1.3 非目标（本期不做）

- **匿名浏览**（未登录看首页/商品详情）：已由产品决策否决，维持现有 `app.tsx` 全站门禁
- 微信/QQ 第三方登录
- 真实短信服务商对接（阿里云/腾讯云）——仅留接口
- 用户协议/隐私政策勾选（项目尚无协议页面）
- 注册后的资料完善引导（昵称/头像在「个人设置」里随时可改）
- B 端（运营/客服/管理员）改用手机号登录——保持账号密码
- 登录页写死的 demo 账号口令（属「阶段 D」范围，本期不动）

---

## 2. 产品设计

### 2.1 核心决策：登录即注册

**不做独立的注册页，也不加第三个 Tab。** 手机号 + 验证码只有一个动作：

```
用户输入手机号 → 点「获取验证码」→ 输入验证码 → 提交
    ├─ 该手机号在当前租户已存在 → 直接登录
    └─ 不存在 → 自动建号 → 直接登录（返回 token，无二次确认）
```

理由：

- C 端身份唯一维度就是手机号，让用户在「登录/注册」间做选择是多余认知负担。
- 减少一整套重复表单与页面。
- 与主流 C 端产品（闲鱼、转转、B 站）一致。

`账号密码` Tab 保留，供运营/客服/管理员使用，行为不变。

### 2.2 自动建号的字段取值

| 字段 | 取值 | 理由 |
|---|---|---|
| `username` | `u` + 手机号 + `_` + 6 位随机十六进制（如 `u13800001005_9f3a1c`） | NOT NULL 且 `(tenant_id, username)` 唯一（`uk_sys_user_tenant_username` **不含 del_flag**）。若用确定性的 `u`+手机号，用户被软删后（查询只认 `del_flag='0'` → 查不到）再次登录建号会撞同名唯一键，该手机号被永久锁死。用户永远不会输入该字段（登录走手机号），可预测性无业务价值。长度 19 ≤ varchar(30) |
| `nick_name` | `邻里用户` + 手机号后 4 位 | 避免满屏「用户1234」；可随时改 |
| `phonenumber` | 用户输入 | 身份锚点 |
| `user_type` | `02` | 与现有 C 端 demo 用户一致 |
| `avatar` | 空 | 前端已有默认头像兜底；不引入外部图床依赖 |
| `password` | 随机 UUID 的 BCrypt 哈希 | NOT NULL 约束；随机值确保无法用账号密码方式登录该账户 |
| `status` | `0`（正常） | — |
| `del_flag` | `0` | — |
| `tenant_id` | `TenantContext.get()`，无上下文时 `1` | 必须显式赋值，否则 `TenantFieldInterceptor` 在 `tenant.enabled=true` 且无上下文时抛异常 |
| `global_user_id` | 复用 `ISysUserService.insertUser()` 的既有逻辑 | 该逻辑会按手机号跨租户复用或回填自身 |
| 角色 | `role_id=2`（普通角色） | 该角色已由 `neighborswap-menus.sql` 授予 C 端全部菜单；不建角色会导致新用户侧边栏为空 |

### 2.3 前端交互

**登录页**

- `账号` Tab：账号 + 密码（行为不变，B 端用）
- `手机号` Tab：手机号 + 验证码；「获取验证码」真正调后端；mock 模式下把返回值自动填进验证码框并提示「当前为调试模式，验证码 123456」
- 登录页写死的 demo 账号口令本期不动（见 §1.3）

**准入策略：维持全站登录门禁**

`ui/src/app.tsx` 现有逻辑（未登录 → 一律 `history.push('/login')`，除 `/login` 与 `TWT.authIgnore`）**保持不变**。本期不新增公开路由。因此：

- 未登录用户只能看到登录页；登录成功后才进入 `/home`
- 后端已有的 `@SaIgnore` 公开接口（商品列表/详情、动态流等）继续保留不动 —— 它们服务于未来可能的匿名浏览、以及 AI 服务内部调用，不属于本期范围
- 登录页已有的 `redirect` 参数逻辑保持不变

**注册后的首次进入**

新用户登录成功 → `fetchUserInfo()` → 侧边栏渲染 C 端菜单（role 2 已授权） → 落在 `/home`。无需任何额外的引导页。

### 2.4 验证码规则

| 规则 | 值 | 理由 |
|---|---|---|
| 有效期 | 300 秒 | 常规 |
| 发送间隔 | 同一手机号 60 秒 | 防刷 |
| 同手机号每日上限 | 10 条 | 防短信费被刷（接真实服务后是真金白银） |
| 验证码错误上限 | 5 次，超过则作废需重新获取 | 防爆破 |
| 单次有效 | 校验成功即删除 | 防重放 |

---

## 3. 技术设计

### 3.1 数据模型

**新增唯一约束**（`backend/data/sms-auth-init.sql`）：

```sql
-- 前置检查：同租户内是否存在重复非空手机号（有则先人工合并，否则 ALTER 会失败）
-- SELECT tenant_id, phonenumber, COUNT(*) c FROM sys_user
--  WHERE phonenumber IS NOT NULL AND phonenumber <> '' AND del_flag = '0'
--  GROUP BY tenant_id, phonenumber HAVING c > 1;

-- 方案：MySQL 8.0 函数索引（不新增列）；正向匹配 del_flag <=> '0'，仅活跃行占用手机号
-- 索引名下的组成部分数：正确版应为 2（表达式 + tenant_id）
SET @parts := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_tenant_phone');

-- 第 2 部分必须是 tenant_id 列（缺它则是全局唯一，会破坏同一自然人跨租户共存）
SET @tenant_part := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_tenant_phone'
      AND SEQ_IN_INDEX = 2 AND COLUMN_NAME = 'tenant_id');

-- 第 1 部分是否已就绪：唯一 + 表达式含 del_flag + NULL 安全等号 <=> + 空串守卫 <>
-- 注意：函数索引只有第 1 部分有 EXPRESSION（第 2 部分 tenant_id 是普通列，EXPRESSION 为 NULL）
SET @expr_ok := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_tenant_phone'
      AND SEQ_IN_INDEX = 1 AND NON_UNIQUE = 0
      AND EXPRESSION LIKE '%del\_flag%' AND EXPRESSION LIKE '%<=>%' AND EXPRESSION LIKE '%<>%');

-- 不存在 → ADD；已就绪 → no-op；存在但非就绪 → 单条原子 DROP + ADD（无「删完没建上」的窗口）
SET @ddl := IF(@parts = 0,
    'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_sys_user_tenant_phone` ((IF(`del_flag` <=> ''0'' AND `phonenumber` <> '''', `phonenumber`, NULL)), `tenant_id`)',
    IF(@parts = 2 AND @tenant_part = 1 AND @expr_ok = 1,
        'SELECT 1',
        'ALTER TABLE `sys_user` DROP INDEX `uk_sys_user_tenant_phone`, ADD UNIQUE KEY `uk_sys_user_tenant_phone` ((IF(`del_flag` <=> ''0'' AND `phonenumber` <> '''', `phonenumber`, NULL)), `tenant_id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

> 幂等靠 `information_schema.STATISTICS` 判存 + 条件 DDL，**不要**用
> `ADD UNIQUE KEY IF NOT EXISTS` —— 那是 MariaDB 语法，MySQL 8.0 直接 `ERROR 1064`，脚本会整体退出、索引建不出来。
> 同一写法见 `backend/data/product-recommend-init.sql:51-56`。
>
> **自动收敛**：脚本不仅判「索引是否存在」，还判四条就绪条件，任一不满足即单条原子
> `ALTER ... DROP INDEX ..., ADD UNIQUE KEY ...` 重建（索引不是数据，重建安全；合并为单条是为了消除
> 「删完没建上」的并发窗口——实测两步写法下并发插入可在窗口内写入重复活跃手机号，随后 ADD 报 1062）。
> 四条就绪条件：① `NON_UNIQUE = 0`（非唯一索引零约束力，不能放过）；
> ② 组成恰为 2 部分且第 2 部分是 `tenant_id` 列（缺它则退化为全局唯一，破坏 alice 跨租户共存）；
> ③ 第 1 部分表达式同时含 `del_flag`、`<=>`、空串守卫 `<>`。
> 判据必须**三者俱全**，任何单一 token 都不够：`bc04bbe` 的排除式旧版含 `del_flag` 但缺 `<=>`；
> 缺空串守卫的变体 `IF(del_flag <=> '0', phonenumber, NULL)` 含 `del_flag` 和 `<=>` 但缺 `<>`
> （实测：同租户两个空手机号的运营/客服账号会互撞 `ERROR 1062`）。
> 只查 `del_flag` 会把前者误判为「已是新版」而静默跳过，那个环境就仍然留着「软删锁死手机号」的 bug 且零告警
> （重审实测复现：exit 0、索引一字节未变）。
> 历史旧索引形态（脚本自动识别，无需人工 DROP）：
> a) 不含 del_flag：`((IF(phonenumber = '', NULL, phonenumber)), tenant_id)`；
> b) 排除式：`((IF(phonenumber = '' OR del_flag <> '0', NULL, phonenumber)), tenant_id)`；
> c) 缺空串守卫：`((IF(del_flag <=> '0', phonenumber, NULL)), tenant_id)`；
> d) 非唯一索引 / 缺 `tenant_id` 的全局唯一索引。
> ⚠️ `LIKE` 里的 `_` 是通配符（`'del-flag' LIKE '%del_flag%'` 为真），故必须写成 `'%del\_flag%'`。
> 人工核对：`SHOW CREATE TABLE sys_user;`
>
> **前置检查必跑**：若存量数据已有同租户重复活跃手机号，ADD/重建会失败，
> 而此时表上**没有任何手机号唯一性**（比只有一个弱约束更糟）。
>
> **执行顺序**：本脚本依赖 `sys_user` 表**已有 `tenant_id` 列** —— 它不在 `backend/data/twelvet.sql` 里，
> 由 `backend/data/tenant-round3-init.sql:16` 新增。必须按 `twelvet.sql` → `tenant-round3-init.sql` → 本脚本
> 的顺序分别执行（`mysql -u root -p twelvet-fast < backend/data/sms-auth-init.sql`）。不要靠
> `docker-entrypoint-initdb.d` 整目录挂载 —— 字母序下有两个独立失败原因：
> ① 本文件（第 13 位）先于 `twelvet.sql`（第 30 位），此时 `sys_user` 还不存在；
> ② 即便 `sys_user` 已存在，`tenant_id` 由 `tenant-round3-init.sql`（第 17 位）新增，同样排在本文件之后。
> 见 CLAUDE.md 的数据库初始化顺序说明。

选择理由：

- **不能全表唯一**：同一自然人在 N 个租户各有一行（`global_user_id` 机制），全表唯一会直接破坏多租户模型。
- **不能直接对 `phonenumber` 建唯一索引**：运营/客服账号的手机号是空字符串，MySQL 唯一索引把 `''` 当有效值，两个空手机号账号会互相冲突。表达式把空值转 `NULL`，`NULL` 不参与唯一约束。
- **表达式必须带 `del_flag` 口径**：软删（`del_flag='2'`）的行不能继续占用手机号，否则该手机号「查不到 → 建号撞索引 → 重查仍查不到」，用户被无声锁死。带上后索引可见性与登录查询严格一致。
- **必须是「正向匹配 + NULL 安全等号」`del_flag <=> '0'`，不能写排除式 `del_flag <> '0'`**：`del_flag` 可为 `NULL`（`twelvet.sql:1005` 为 `char(1) NULL DEFAULT '0'`）。排除式下 `del_flag IS NULL` 的行算出 `FALSE OR NULL = NULL`，`IF` 当 false → **该行仍占用手机号**；而应用层查询是 `del_flag = '0'`，对 NULL 行不可见 —— 口径冲突直接复现同一个死锁。`<=>` 对 NULL 返回 0 而非 NULL，与 `del_flag = '0'` 语义完全对齐。已在 MySQL 8.0.37 实测：排除式对两行 `del_flag IS NULL` 的同号记录报 `ERROR 1062`，正向式插入成功。
- **口径一致性约束**：本表达式必须与 `SysUserMapper.xml` 的 `selectUserByPhoneAndTenant`（`su.del_flag = '0'`）保持一致，改一处必须同步改另一处。
- **不用「生成列 + 唯一索引」（初版方案，已否决）**：`backend/data/twelvet.sql:1023,1027` 的 `sys_user` 种子语句是**不带列名**的 `INSERT INTO sys_user VALUES (...)`，新增任何列（含生成列）都会让这两条语句报 `Column count doesn't match value count`。函数索引不改变列数，天然兼容位置参数 INSERT。
- 应用层写入不受影响：`SysUserMapper.xml:142` 的 `insertUser` 带显式列名。
- MySQL 8.0.32（`docker-compose.yml`）支持函数索引（8.0.13+）。

**存量数据验证**（`docs/db/twelvet-fast_2026-10-21.sql:2621` 实测；原引用为已删除的 09-22 导出，数据一致）：

- `alice` 有 2 行、手机号均为 `13800001101`，但 `tenant_id` 分别为 `1` 和 `2` → **租户内唯一可通过**；若选全局唯一则直接失败。这条数据本身就是「必须租户内唯一」的实证。
- 其余 demo 用户手机号各不相同。
- 空手机号用户（`ops_demo` 等）→ 表达式转 `NULL`，不参与约束。

**副作用：后台改手机号会抛重复错误**。`SysUserMapper.xml:186` 的 `updateUser` 会更新 `phonenumber`。当前无唯一约束，后台把两个用户改成同一手机号是**静默允许**的；加索引后会抛 `DuplicateKeyException`。

- 这是**期望的收紧**，但必须配套：`SysUserServiceImpl.updateUser` 前置调用 `checkPhoneUnique`（该方法已存在，`SysUserMapper.xml:128`）并抛出 `TWTException("手机号已被占用")`，把数据库异常翻译成用户可读提示，避免 500。

**新增 Mapper 查询**：`selectUserByPhoneAndTenant(phone, tenantId)`。

> ⚠️ 实施注意：函数索引表达式必须与查询条件字面量完全一致才能命中；`selectUserByPhoneAndTenant` 的 `WHERE` 需写成 `phonenumber = #{phone}`，且该查询本身不依赖索引命中（唯一性由索引保证，查询走 `idx_sys_user_tenant` 即可）。

### 3.2 后端结构

```
twelvet-system-server/
  service/
    ISmsCodeService.java          (新) 发送/校验验证码
    ILoginService.java            (+ smsLogin)
    impl/
      LoginServiceImpl.java       (+ smsLogin：查用户→无则建号→签发 token)
      SmsCodeServiceImpl.java     (新)
  sms/
    SmsSender.java                (新) 接口：send(mobile, code)
    MockSmsSender.java            (新) 仅打日志，不真发
twelvet-admin/
  controller/LoginController.java (+ /auth/sms/send, /auth/sms/login)
```

**接口设计**

```http
POST /auth/sms/send
{ "mobile": "13800001005" }
→ 200 { "code":200, "data": { "expireIn": 300, "mockCode": "123456" } }
   # mockCode 仅在 sms.provider=mock 时存在；真实短信不返回

POST /auth/sms/login
{ "mobile": "13800001005", "code": "123456" }
→ 200 { "code":200, "data": { "accessToken":"...", "refreshToken":"...", "expiresIn":86400 } }
   # 与 /auth/oauth2/token 返回同构的 LoginVO，前端复用 setAuthority
```

两者均 `@SaIgnore`。

**配置**

```yaml
sms:
  provider: mock        # mock | aliyun（aliyun 暂未实现）
  code-expire-seconds: 300
  send-interval-seconds: 60
  daily-limit: 10
  max-verify-attempts: 5
```

`provider=mock` 且 `spring.profiles.active=pro` 时，启动阶段打 `ERROR` 日志（不阻断启动，便于演示环境）。

**Redis 键**

| 键 | 值 | TTL |
|---|---|---|
| `sms:code:{mobile}` | 验证码 | 300s |
| `sms:send:limit:{mobile}` | `1` | 60s |
| `sms:daily:{mobile}:{yyyyMMdd}` | 计数 | 至次日 |
| `sms:fail:{mobile}` | 错误次数 | 300s |

### 3.3 前端结构

```
ui/src/pages/Login/index.tsx        (改) 手机号 Tab 接真接口，删除假验证码逻辑
ui/src/pages/Login/service.ts       (改) + sendSmsCode / smsLogin
```

`app.tsx` 与路由配置**本期不改**（全站门禁维持现状）。

### 3.4 错误处理

| 场景 | 后端 | 前端表现 |
|---|---|---|
| 手机号格式错误 | 400 `手机号格式不正确` | 表单校验直接拦，不发请求 |
| 60 秒内重复发送 | 400 `请求过于频繁，请稍后再试` | toast |
| 当日超限 | 400 `该手机号今日发送次数已达上限` | toast |
| 验证码错误 | 400 `验证码不正确`（含剩余次数） | toast |
| 验证码过期/不存在 | 400 `验证码已失效，请重新获取` | toast + 清空验证码框 |
| 账号被冻结（status=1） | 400 `账号已被冻结` | toast（复用现有文案） |
| 建号并发冲突（唯一索引） | 捕获 `DuplicateKeyException` → 重查并登录 | 用户无感 |

---

## 4. 测试策略

**后端（JUnit + Mockito，TDD）**

- `SmsCodeServiceImplTest`：发送写入 Redis / 60 秒内拒绝 / 日限拒绝 / 校验成功删除 / 校验失败计数 / 超次数作废
- `LoginServiceImplSmsTest`：新手机号自动建号（字段断言）/ 已有手机号直接登录 / 验证码错误抛异常 / 冻结账号拒绝 / 并发建号冲突降级为登录
- `LoginController` 契约：两个新端点 `@SaIgnore` 生效

**前端**

- 无新增单测（`app.tsx` 不改，登录页逻辑靠手工验证）

**手工验证**

- 未登录访问任意业务页面 → 被挡在登录页（门禁未变）
- 新手机号登录 → 建号成功 → 侧边栏有 C 端菜单 → 落在 `/home`
- 同一手机号再次登录 → 直接进，不重复建号
- 验证码连错 5 次 → 需重新获取
- 60 秒内重复点「获取验证码」→ 后端拒绝
- `admin` 账号密码登录 → 行为不变
- `ops_demo` / `cs_demo` 登录 → 行为不变

---

## 5. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 手机号唯一索引在存量数据上创建失败 | SQL 文件内置前置检查查询；失败时报错信息明确指向该查询。已实测存量数据无同租户重复（`alice` 两行跨租户，不冲突） |
| 后台改手机号为已存在值时抛 500 | `updateUser` 前置 `checkPhoneUnique`，翻译成 `TWTException("手机号已被占用")`（见 §3.1 副作用） |
| 加了唯一索引后，历史遗留的「同租户重复手机号」无法再写入 | 属期望的收紧；若线上已有重复，迁移前必须先人工合并 |
| mock 短信被误开在生产 | pro profile + mock provider 时启动打 ERROR；配置项写注释 |
| 自动建号被恶意刷（脚本批量造号） | 60s + 日限 + 验证码错误上限；真实短信接入后天然受短信成本约束 |
| 用户手机号输错 → 建了废号 | 无解，与所有同类产品一致；后续可加「手机号修改」功能（本期不做） |
| 全站门禁导致新用户看不到商品就流失 | 已知产品取舍，本期接受；未来若要放开匿名浏览，后端 `@SaIgnore` 已就绪，只需改前端路由守卫 |

---

## 6. 实施顺序

1. `sms-auth-init.sql`（函数唯一索引）+ `selectUserByPhoneAndTenant`（TDD：Mapper 层）
2. `SmsSender` / `MockSmsSender` / `SmsCodeServiceImpl`（TDD）
3. `LoginServiceImpl.smsLogin` + `LoginController` 两端点（TDD）
4. `SysUserServiceImpl.updateUser` 补 `checkPhoneUnique` 前置校验（TDD）
5. 前端 `Login/service.ts` + 登录页手机号 Tab 接真接口
6. 全量回归 + 手工验证

---

## 7. 已确认的决策（产品经理判断 + 用户拍板）

| # | 决策 | 结论 | 来源 |
|---|---|---|---|
| 1 | 注册与登录是否合并 | **合并**（首次登录即注册） | 产品经理判断 |
| 2 | 注册时是否要求填昵称 | **不要求**，自动生成「邻里用户+尾4位」 | 产品经理判断 |
| 3 | 手机号唯一性范围 | **租户内唯一**（生成列 + NULL 规避） | 产品经理判断 |
| 4 | mock 验证码如何让用户知道 | **响应体返回 + 前端自动填入** | 产品经理判断 |
| 5 | 是否本期移除登录页写死的 demo 口令 | **否**，阶段 D 单独处理 | 产品经理判断 |
| 6 | 未登录能否浏览商品 | **否**，维持全站登录门禁 | **用户明确拍板** |
