# 商业化第四轮（1 用户跨 N 租户：租户级登录 + token 绑定 + 用户读隔离）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 设计：`docs/superpowers/specs/2026-08-29-tenant-round4-design.md`
> 摘要：`docs/tenant-round4-summary.md`

## 启用步骤（已有库执行一次）

```bash
mysql -uroot -p twelvet-fast < backend/data/tenant-round4-init.sql
```

内容：`sys_user.global_user_id`（回填=自身 user_id）+ `idx_sys_user_global` + **租户内用户名唯一 uk**（`uk_sys_user_tenant_username(tenant_id, username)`，存量已验证无重复）+ **role 1 补全菜单映射**（本轮起「租户管理员 = 本租户内拥有 admin 角色的用户」，非超管走角色权限链路，role 1 存量仅映射了 AI/租户菜单）。

## 四件事

### ① 租户级登录
`LoginServiceImpl.loadUserByUsername`：`TenantContext` 非空 → `selectUserByUserNameAndTenant(username, tenantId)`；为空 → 全局查找（兼容 curl 无 header / 回滚）。上下文来自 TenantFilter（子域名 → X-Tenant-Code），UI 登录请求经 requestErrorConfig 已带 header。同名账号各租户独立存在、各自密码。

### ② token 绑定租户（本轮核心增量）
- `LoginUser` + `tenantId`（登录时从 sys_user 行写入；refresh 重建时延续）。
- 新 `TenantBindInterceptor`（framework-security，注册在 SaInterceptor 之后）：上下文非空 && 登录态 && loginUser.tenantId 非空 && 不一致 → 400「当前登录租户与访问租户不一致」。关闭「default token + X-Tenant-Code: thu 换头读数据」穿越。
- 跳过条件：匿名请求（C 端浏览/登录本身）、无上下文（平台视角）、旧 token（升级前签发）、**超管 userId=1 豁免**（否则超管无法为其他租户建管理员账号，死锁；与数据范围/平台接口豁免同语义）。

### ③ global_user_id 串联（1 人跨 N 租户）
- 一行 sys_user = 一租户成员关系；跨租户多行靠 `global_user_id` 串联。
- 首个身份：插入后回填 `global_user_id = user_id`（`backfillGlobalUserId`，带 `IS NULL` 防重入）。
- 跨租户串联：建号时手机号在任意租户命中既有身份（`selectGlobalIdByPhone`，非空才查）→ 复用其 global_user_id。本轮仅锚点列、无运行时行为；假手机号会误串联（可接受）。
- username/phone/email 唯一性全部按租户判定（上下文为空 = 全局，兼容）；跨租户同手机号是特性不是冲突。

### ④ 用户表读隔离 + 跨租户写防护
- `selectUserList` XML：`<if tenantId>` 租户过滤 + **补 `userId` 条件**（原先没有，checkUserDataScope 实际从没按目标用户过滤过，只靠部门 dataScope——本轮补上后租户+部门联合生效）。
- `SysUserController.pageQuery/export`：上下文非空强制 `setTenantId(context)`（覆盖请求参数，防 `?tenantId=2` 越权枚举）；无上下文 = 平台全局视角（与商品列表兼容模式对称）。
- `checkUserDataScope` 注入上下文租户 → 非超管的 edit/remove/resetPwd/changeStatus 跨租户全被「没有权限访问用户数据！」拦截。
- 按 username 更新的 `updateUserAvatar`/`resetUserPwd`（个人中心自改路径）加租户条件——同名账号跨租户不再误伤另一行。

## 验证记录（2026-08-29 实测）

**矩阵 17/17 全过**（node 脚本，alice=default+thu 同手机号、bob 仅 thu、thuadmin=thu 管理员）：

| # | 用例 | 结果 |
|---|---|---|
| 1 | admin 登录（header default）→ getInfo | tenantId=1, globalUserId=1 ✅ |
| 2 | default 建 alice（13800001101） | tenant_id=1, gid=自身 ✅ |
| 3 | thu 建 alice（同手机号） | 200（跨租户同名允许），gid 串联 default alice ✅ |
| 4 | thu 再建 alice（新手机号） | 400「登录账号已存在」（租户内唯一）✅ |
| 5a | alice header default 登录 | 命中 default 行（userId=118）✅ |
| 5b | alice Host: thu.localhost 登录 | 命中 thu 行（userId=123）✅ |
| 5c | 两行 global_user_id | 相同（gid=122，同人串联）✅ |
| 6 | bob（仅 thu）header default 登录 | 400「登录用户不存在」✅ |
| 7 | default token + thu header getInfo | 400「当前登录租户与访问租户不一致」✅ |
| 8 | 用户列表 thu / default / 无 header | 3 条全 tenant=2 / 7 条全 tenant=1 / 10 条全量 ✅ |
| 9 | thuadmin resetPwd default 用户 | 400「没有权限访问用户数据！」✅ |
| 9b | thuadmin resetPwd 本租户 bob | 200 ✅ |
| 10 | 匿名 Host: thu.localhost 商品列表 | 全 tenant=2 ✅ |

**回滚开关**（`--tenant.enabled=false` 重启实测）：无 header 登录/用户列表/建用户全 200（建用户 tenant_id 落 DB 默认 1）；Host: thu.localhost 不解析 → 商品 21 条全量（兼容模式）。恢复开关后正常。

**浏览器双租户实测**：`thu.localhost:8000` 登录 alice → 右上角「Alice清华租户」、/home 仅 thu 8 件商品（截图 `docs/tenant-round4-thu-alice-home.png`）；`localhost:8000` 登录 admin → 默认租户工作台不变。

**验证中发现并修复**：① `checkUserDataScope` 复用的 selectUserList 缺 userId 条件（跨租户 resetPwd 首测 200）→ 补 `<if userId>`；② 超管无绑定豁免会死锁（无法给其他租户建管理员）→ userId=1 豁免；③ role 1 无 system 菜单映射 → thuadmin「无此权限」→ init.sql 补全映射。

### 环境坑（本会话新增）

- undici（node fetch）会把 `Host` 当 forbidden header 剥掉——带 Host 的子域名用例必须走 `node:http`，否则静默变兼容模式（表现为「全租户混出」）。
- Windows 下重启后端前先 `taskkill`：jar 被运行中进程锁定，`spring-boot:repackage` rename 直接失败。
- `-pl twelvet-admin -am` 单模块打包会跳过 spring-boot repackage（「没有主清单属性」），要用全量 `mvn package`。

## 已知边界

- ~~第三方 GitHub OAuth 登录未按租户圈定~~ → 已整移除（2026-08-29，国内用户不友好且后端端点本就缺失）。
- 无 C 端注册；跨租户同人靠手机号串联（假手机号会误串联，仅锚点列无运行时行为）。
- 角色/菜单/部门仍全局共享：租户管理员权限全同平台（菜单裁剪是第五轮候选）。
- 超管（userId=1）token 不绑租户，可任意换头（平台运营语义，与既有豁免一致）。
- 存量已登录 token 无 tenantId → 绑定校验跳过，重新登录后生效。
- 单元测试 0（沿用运行时矩阵惯例）。

## 回滚

`tenant.enabled=false`（Filter/拦截器/绑定全关，global_user_id 列无害）；DDL 见 init 脚本尾注释（DROP uk/idx/COLUMN）；`git revert`。
