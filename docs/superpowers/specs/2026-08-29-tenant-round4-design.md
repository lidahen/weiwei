# 商业化第四轮（1 用户跨 N 租户）设计文档

- 日期：2026-08-29
- 主题：sys_user.global_user_id + 租户级登录 + token 绑定租户 + 用户表读隔离
- 状态：已批准（用户预授权「继续完成租户隔离开发」，按前几轮模式执行）
- 前置：tenant-isolation-2、tenant-round3、tenant-p2-pay、tenant-subdomain 均已合 master

## 0. 背景与目标

round3 只做了 sys_user 写归属（tenant_id 拦截器注入），明确的已知边界：
「用户可从任意租户前端登录（绑定≠登录隔离）」。本轮闭合它：

1. **租户级登录**：登录按当前租户解析用户（同名账号各租户独立存在，各自密码）。
2. **token 绑定租户**：登录后 token 记住租户，请求上下文与登录租户不一致 → 拒绝
   （关闭「default token + X-Tenant-Code: thu 可读 thu 数据」的换头洞）。
3. **1 人跨 N 租户**：`global_user_id` 锚点列，同一手机号在不同租户建号自动串联为同一人。
4. **用户表读隔离**：用户列表按租户过滤（平台全局视角 = 无租户上下文请求，与商品列表兼容模式对称）。

## 1. 关键决策（不要重新讨论）

| 决策 | 选择 | 理由 |
|---|---|---|
| 用户模型 | sys_user 一行 = 一租户成员关系；跨租户多行靠 global_user_id 串联 | 不引入独立 membership 表，最小改动；查询/隔离与既有业务表同构 |
| global_user_id 取值 | 首个身份的 user_id（存量回填=自身；新用户插入后回填；跨租户串联=复用既有行） | 与存量对齐、可读可调试；无需新 ID 发生器 |
| 跨租户串联键 | 手机号（非空才串联）；email 不串联 | 手机号是本系统最可靠的身份数据；global_user_id 本轮仅锚点列、无运行时行为，假手机号误串联可接受（文档注明） |
| 登录解析 | TenantContext 非空 → 按 (username, tenant_id) 查；为空 → 全局查（兼容） | 上下文来自 TenantFilter（子域名/header），登录请求经 UI requestErrorConfig 已带 header；无上下文兼容 curl/回滚，上下文非空即开关开启，无需注入 TenantSupport |
| username 唯一性 | 应用层 checkUserNameUnique 按租户 + DB 层 `uk_sys_user_tenant_username(tenant_id, username)` | DB 当前无 username 唯一索引，加 uk 硬化；软删行同现状（计入重复） |
| phone/email 唯一性 | 应用层按租户（DB 不加 uk，手机号允许跨租户重复是特性） | 同租户唯一防误绑，跨租户重复支撑 1 人 N 租户 |
| token 绑定 | LoginUser + tenantId（登录时从 sys_user 行写入）；新拦截器 TenantBindInterceptor 校验上下文一致 | 不一致=换头穿越 → 400；匿名/旧 token（无 tenantId）/无上下文 → 跳过（兼容）；**超管 userId=1 豁免**——否则超管无法为其他租户建管理员账号（死锁），与数据范围/平台接口豁免同语义 |
| 平台视角 | 无租户上下文请求 = 平台全局（用户列表不过滤）；上下文存在则强制覆盖请求参数 tenantId | 与第二轮「商品列表兼容模式」「平台运营不过滤」对称；防止 ?tenantId=2 越权枚举 |
| 跨租户写防护 | 沿用 checkUserDataScope（非超管经 selectUserList 校验，自动被租户过滤圈定） | edit/remove/resetPwd/changeStatus 已全部先走该检查，零新增代码 |
| UI | 零改动 | 登录请求已带 header；Staff 列表自动按上下文过滤，租户列冗余 |
| refresh | 按原逻辑 PK 查用户重建 LoginUser（tenantId 从行带入，绑定延续） | token 本身即凭证，不重复校验租户 |

## 2. 改动清单

| 文件 | 改动 |
|---|---|
| `backend/data/tenant-round4-init.sql` | 新增：global_user_id 列 + 回填 + `idx_sys_user_global` + `uk_sys_user_tenant_username` |
| `SysUser.java`（system-api） | +`globalUserId` 字段（手写 getter/setter，样式一致） |
| `LoginUser.java`（framework-security） | +`tenantId` 字段 |
| `TenantBindInterceptor.java`（framework-security，新） | 上下文非空 && 登录态 && loginUser.tenantId 非空 && 不一致 → TWTException（400） |
| `SaTokenConfigure.java` | 注册 TenantBindInterceptor（在 SaInterceptor 之后） |
| `LoginServiceImpl.java` | 登录解析按租户；buildLoginUser 填 tenantId |
| `SysUserMapper.java/.xml` | +`selectUserByUserNameAndTenant`；selectUserVo/resultMap/selectUserList 加 tenant_id、global_user_id；selectUserList 加租户 `<if>`；checkUserNameUnique/Phone/Email 加租户 `<if>`；updateUserAvatar/resetUserPwd（按 username 更新）加租户 `<if>`；insertUser 加 global_user_id `<if>`；+`backfillGlobalUserId`、+`selectGlobalIdByPhone` |
| `SysUserServiceImpl.java` | checkUserNameUnique/checkPhoneUnique/checkEmailUnique/updateUserAvatar/resetUserPwd 内部传租户（服务签名不变）；insertUser 串联+回填；selectUserByUserName(service) 与 importUser 查重按租户（`selectUserByUserNameScoped`） |
| `SysUserController.java` | pageQuery/export 强制 `setTenantId(TenantContext.get())`（覆盖请求参数） |

## 3. 验证矩阵

| # | 用例 | 期望 |
|---|---|---|
| 1 | admin 登录（X-Tenant-Code: default）→ getInfo | 200；tenantId=1、globalUserId=1 |
| 2 | 建 alice（header default，手机 138xxx）→ 查库 | tenant_id=1、global_user_id=自身 user_id |
| 3 | 建 alice（header thu，同手机号） | 成功（跨租户同名允许）；tenant_id=2、global_user_id=串联 default alice |
| 4 | header thu 再建 alice（新手机号） | 400「登录账号已存在」（租户内唯一生效） |
| 5 | alice header default 登录 / Host: thu.localhost 登录 | 分别命中 default 行 / thu 行（getInfo user_id 不同） |
| 6 | bob 只建在 thu：header default 登录 bob | 400「登录用户不存在」 |
| 7 | default token + X-Tenant-Code: thu 访问 /system/user/getInfo | 400「当前登录租户与访问租户不一致」（token 绑定生效） |
| 8 | 用户列表：header thu / header default / 无 header | 各自租户用户 / 全量（平台视角） |
| 9 | thu 管理员（非 userId=1）resetPwd default 普通用户 | 400「没有权限访问用户数据」（checkUserDataScope 被租户过滤圈定） |
| 10 | 回归：C 端匿名 Host: thu.localhost 商品列表；同租户 IM 互发 | 不变 |

## 4. 已知边界

- 第三方 GitHub OAuth 登录未按租户圈定（本轮未接线改动，沿用原路径）。
- 无 C 端注册；跨租户同人靠手机号串联（假手机号会误串联，仅锚点列无运行时行为）。
- 角色/菜单/部门仍全局共享：租户管理员 = 该租户内拥有 admin 角色的用户，权限全同平台（第五轮候选）。
- 存量已登录 token 无 tenantId → 绑定校验跳过，重新登录后生效。

## 5. 回滚

`tenant.enabled=false`（Filter/拦截器/绑定全关，global_user_id 列无害）；`ALTER TABLE sys_user DROP INDEX uk_sys_user_tenant_username, DROP INDEX idx_sys_user_global, DROP COLUMN global_user_id`；`git revert <merge>`。

## 6. 验证记录（2026-08-29 实测）

矩阵 17/17 全过（详见 `docs/tenant-round4.md`）：建号串联（alice default+thu 同手机号同 gid）、租户内唯一 400、双租户登录命中各自行、仅 thu 用户在 default 登录 400、换头 400、列表三态（thu/default/无 header 全量）、thuadmin 跨租户 resetPwd 400 + 本租户 200、匿名子域名商品全 tenant=2。回滚开关 `--tenant.enabled=false`：无 header 登录/列表/建用户全 200（tenant_id 落 DB 默认 1）、子域名不解析 21 条全量。浏览器：`thu.localhost:8000` alice 登录 → 「Alice清华租户」+ 仅 thu 商品（截图 `docs/tenant-round4-thu-alice-home.png`）；`localhost:8000` admin 登录 → 默认租户不变。

实现中三处修正（相对本 spec 初稿）：
1. TenantBindInterceptor 增加超管（userId=1）豁免——否则超管无法为其他租户创建管理员账号（引导死锁），与数据范围/平台接口豁免同语义。
2. checkUserDataScope 复用的 selectUserList 原本没有 userId 条件（只靠部门 dataScope），补 `<if userId>` 后租户+部门联合生效，跨租户 resetPwd 才真正被拦。
3. role 1 补全 sys_role_menu 全量映射（存量仅 AI/租户菜单）——「租户管理员 = admin 角色用户」必须走角色权限链路。

