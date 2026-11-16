# 商业化第五轮（角色/菜单租户化 MVP + GitHub 登录移除）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 设计：`docs/superpowers/specs/2026-08-29-tenant-round5-design.md`
> 摘要：`docs/tenant-round5-summary.md`

## 启用步骤（已有库执行一次）

```bash
mysql -uroot -p twelvet-fast < backend/data/tenant-round5-init.sql
```

内容：`sys_menu.menu_scope char(1) DEFAULT '0'`（0=通用 1=平台专属）+ 递归 CTE 打标平台子树（101 项）。幂等可重跑。

## 两件事

### ① 角色/菜单租户化（平台菜单对非 default 租户不可见+不可用）
- DDL：`sys_menu.menu_scope`；平台子树 = 系统管理(1，含租户管理/OAuth2终端/字典/DFS/令牌/i18n/菜单管理)、系统工具(3)、日志管理(4)、监控管理(1083)、运营管理(2100)、客服工作台(2110)、角色管理(101 子树)、AI模型管理(2113 子树)。
- 双滤点（`SysMenuServiceImpl`，租户取自 `TenantContext`——登录构建权限时 token 会话未建立，而登录请求已被 TenantFilter 赋上下文）：
  - `selectMenuTreeByUserId`（getRouters 侧边栏树）；
  - `selectMenuPermsByUserId`（登录/getInfo 权限串）→ 平台端点 `@SaCheckPermission` 服务端拦截同步生效。
- 判定：tenantId null（兼容/平台视角）或 =1（default）→ 不过滤；admin(userId=1) 走 `selectMenuTreeAll`/"*" 旁路天然不受影响。
- 租户管理员保留：二手交易市场全家桶、AI（导购/知识库/文档/聊天/画图/视频）、人事管理（用户/部门/岗位）。

### ② GitHub 登录移除（用户决策：国内用户不友好）
后端 `/auth/login/oauth2/{code}` 端点本就不存在（按钮点击即报错），纯前端清理：登录页「其他登录方式」入口、`Login/componets/oauth2/`（github 组件+service）、回调页 `Login/OAuth2/`、路由 `/login/oauth2/:oauth2`、`Login/service.ts#oauth2Login`。
C 端手机验证码注册**缓办**（依赖阿里云等短信服务，用户决策后置）。

## 验证记录（2026-08-29 实测 11/11 + 浏览器）

| # | 用例 | 结果 |
|---|---|---|
| 1 | thuadmin 重登 getRouters | 仅 /ai、/marketplace、/human、TwelveT；平台目录全无 ✅ |
| 1b | 人事管理/AI知识库子级 | 角色管理、AI模型管理已裁 ✅ |
| 2 | thuadmin getInfo permissions | 无 system:tenant:*/system:role:*/marketplace:admin:*；有 system:user:list/marketplace:im:list ✅ |
| 3 | thuadmin curl /system/tenant/thu | 500「无此权限：system:tenant:query」（权限串已裁，服务端拦截生效）✅ |
| 4a | thuadmin 用户列表 | 200 且全 tenant=2（业务回归）✅ |
| 5 | admin（default）getRouters | 全量 11 个顶层含 /system /monitor ✅ |
| 5b | admin curl /system/tenant/thu | 200（平台视角不受影响）✅ |
| 6 | alice（C 端无角色）登录 | 行为不变（routers=0）✅ |
| 7a/7b | round4 抽查：换头 400、用户列表租户过滤 | 不变 ✅ |
| 8 | 浏览器 thuadmin 登录 thu.localhost:8000 | 侧边栏 AI/二手交易市场/人事管理(用户/部门/岗位)/TwelveT/AI知识库，右上角「清华管理员」，截图 `docs/tenant-round5-thuadmin-menu.png` ✅ |

## 已知边界

- 直接输 URL 访问平台页面：页面壳可加载（前端路由静态），接口全 403——MVP 接受。
- 菜单管理 UI 无 menu_scope 编辑字段（SQL 维护；菜单管理本身是平台页）。
- 旧 token 权限串未重算，重登生效。
- 部门数据范围仍全局；角色仍是全局资源（租户管理员被收走角色管理权限，配角色需平台操作）。

## 回滚

menu_scope 读取不依赖 `tenant.enabled`，但租户上下文来自 Filter——开关关闭即上下文 null=平台视角=不过滤（天然回滚）；`ALTER TABLE sys_menu DROP COLUMN menu_scope`；`git revert`。
