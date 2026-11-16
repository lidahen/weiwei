# 商业化第五轮（角色/菜单租户化 MVP）设计文档

- 日期：2026-08-29
- 主题：`sys_menu.menu_scope` 平台专属标识——租户管理员的侧边栏菜单与后端权限同步裁剪
- 状态：已批准（用户预授权「做其他功能改造」；GitHub 登录移除与 C 端注册缓办为同批用户决策）
- 前置：round4（租户级登录 + token 绑定）已合 master

## 0. 背景与目标

round4 后租户管理员（如 thuadmin，拥有 admin 角色 + 全量菜单映射）登录仍能看到**全部平台菜单**：
租户管理、监控管理、日志管理、系统工具、运营管理、客服工作台等——这是用户体系隔离的最后一块大边界。
本轮闭合它：**平台专属菜单对非 default 租户不可见（侧边栏）且不可用（@SaCheckPermission 权限串同步裁剪）**。

同批决策（用户指示，不再讨论）：
- GitHub 第三方登录**整移除**（国内用户不友好；后端 `/auth/login/oauth2/{code}` 端点本就不存在，纯前端清理）。
- C 端手机验证码注册**缓办**（依赖阿里云等短信服务对接，后置）。

## 1. 关键决策（不要重新讨论）

| 决策 | 选择 | 理由 |
|---|---|---|
| 方案 | `sys_menu.menu_scope char(1)`（'0'=通用 '1'=平台专属），读时过滤 | 不复制角色/菜单数据，role 1 全量映射保持不变；一个标志位 + 两处 `<if>`，最小改动 |
| 平台判定 | `LoginUser.tenantId == null \|\| == 1`（default 租户）→ 不过滤 | null=兼容模式/平台视角（与既有语义一致）；default 是平台自营租户 |
| 过滤点 | ① `selectMenuTreeByUserId`（getRouters 侧边栏树）② `selectMenuPermsByUserId`（登录/getInfo 权限串） | 菜单不可见 + 权限不可用双保险：thuadmin 即使 curl 平台端点也 403「无此权限」 |
| 过滤语义 | `menu_scope = '0'`（含 NULL 兼容）；admin(userId=1) 走 `selectMenuTreeAll`/"*" 旁路天然不受影响 | 与 round4 超管豁免同构 |
| 平台子树 | 系统管理(1，含租户管理/OAuth2终端/字典/DFS/令牌/i18n/菜单管理)、系统工具(3)、日志管理(4)、监控管理(1083)、运营管理(2100)、客服工作台(2110)、角色管理(101 子树)、AI模型管理(2113 子树) | 租户管理员保留：二手交易市场全家桶、AI（知识库/文档/聊天/画图/视频/导购）、人事管理（用户/部门/岗位）；角色创建是全局操作收平台 |
| 打标方式 | 递归 CTE 物化到临时表再 JOIN UPDATE（MySQL 8.4；规避 ERROR 1093 同表更新） | 一次性种子操作，可重复执行（幂等） |
| 菜单管理 UI | 不加 menuScope 表单字段（菜单管理本身是平台页，SQL 管理） | MVP 最小化；后续需要再加 |
| 租户信息表 / getPlanInfo 等 | 不动 | 租户自助信息接口本就非平台专属 |

## 2. 改动清单

| 文件 | 改动 |
|---|---|
| `backend/data/tenant-round5-init.sql` | 新增：menu_scope 列 + 递归打标平台子树 |
| `SysMenu.java`（system-api） | +`menuScope` 字段（手写 getter/setter） |
| `SysMenuMapper.java` | `selectMenuTreeByUserId`/`selectMenuPermsByUserId` 加 `@Param("tenantId")` |
| `SysMenuMapper.xml` | resultMap/selectMenuVo/selectMenuTreeByUserId 显式列加 menu_scope；两个 ByUserId 查询加 `<if test="tenantId != null and tenantId != 1"> AND m.menu_scope = '0'</if>` |
| `SysMenuServiceImpl.java` | 两个查询调用处从 `SecurityUtils.getLoginUser().getTenantId()` 取租户，平台（null/1）传 null |

## 3. 验证矩阵

| # | 用例 | 期望 |
|---|---|---|
| 1 | thuadmin 重登 → getRouters | 无 系统管理/监控管理/日志管理/系统工具/运营管理/客服工作台/角色管理/AI模型管理；有 二手交易市场/AI/人事管理(用户部门岗位) |
| 2 | thuadmin getInfo permissions | 无 system:tenant:*/system:monitor:*/marketplace:admin:* 等 |
| 3 | thuadmin token curl 平台端点（如 /system/tenant/list） | 403「无此权限」 |
| 4 | thuadmin 业务端点回归 | 用户列表（本租户）、AI 聊天、marketplace 列表正常 |
| 5 | admin（default）登录 → getRouters | 全量菜单不变（平台视角） |
| 6 | alice（C 端，无角色）登录 thu | 行为不变（本来无菜单） |
| 7 | 回归：round4 矩阵抽查（租户级登录/换头 400/用户读隔离） | 不变 |
| 8 | 浏览器：thuadmin 登录 thu.localhost:8000 | 侧边栏裁剪后菜单；admin 登录 localhost 菜单不变 |

## 4. 已知边界

- 直接输 URL 访问平台页面：页面壳可加载（前端路由静态），接口全 403（权限已裁）——MVP 接受。
- 菜单管理 UI 无 menu_scope 编辑字段（SQL 维护）。
- 旧 token（升级前签发）权限串未重算，重登后生效。
- 部门数据范围（dept dataScope）仍全局共享——租户内多部门细分是后续轮次。

## 5. 回滚

菜单过滤随 `tenant.enabled=false` 不联动（menu_scope 读取不查该开关，但租户上下文来自 Filter，开关关=上下文 null=平台视角=不过滤，天然回滚）；`ALTER TABLE sys_menu DROP COLUMN menu_scope`；`git revert`。
