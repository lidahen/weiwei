# 商业化第五轮 — 上下文摘要（2026-08-29 节点）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> ## 当前快照（新会话先读这 8 行）
> - **master = 商业化 P0-P2 + 用户体系四/五轮完成**：…→ 子域名支持 → 1 用户跨 N 租户（租户级登录+token 绑定）→ **角色/菜单租户化（平台菜单对租户不可见不可用）**；GitHub 登录已移除，C 端注册缓办（等阿里云短信对接）。
> - **运行态**：后端 master jar 跑 8080（tenant.enabled=true, base-domain=localhost, billing.pay.mock=true）；UI dev server 8000（`thu.localhost:8000` 看租户店铺）；Redis 6379；MySQL root/123456 库 twelvet-fast。
> - **租户种子**：default=pro(不限)/thu=pku=free(100商品/50AI)；用户种子（密码全 123456）：alice=default+thu 串联、bob/thuadmin=仅 thu（thuadmin=admin 角色=租户管理员，侧边栏已裁剪）。
> - **菜单租户化**：`sys_menu.menu_scope`（0=通用 101→打标后 1=平台专属 101 项）；租户管理员可见 二手交易市场/AI(除模型)/人事(用户部门岗位)；平台判定=tenantId null 或 1；双滤点=菜单树+权限串。
> - **文档地图**：本轮细节 `docs/tenant-round5.md`；前轮 `tenant-round4/isolation-2/round3/p2-pay`；`*-summary.md` 是续做入口；spec 在 `docs/superpowers/specs/2026-08-29-tenant-round5-design.md`。
> - **本会话验证基线**：round5 矩阵 11/11 + 浏览器 thuadmin 侧边栏截图；round4 矩阵 17/17（回归抽查过）。
> - **环境坑**：见下方「环境坑」节（undici 剥 Host / Windows jar 锁 / 登录期无 token 会话）。
> - **下一步**：见文末「下次续做的优先级」（支付记录页 / 审计日志 / 抽佣 / AI 月账单；部门租户化、C 端注册缓办）。

> 下次继续这个工作，先读这个文件。前置：round4 已合 master。

## 当前位置

- **Branch**: `feature/tenant-round5`（worktree `.worktrees/tenant-round5`，已合并 master 则删）
- **内容**：角色/菜单租户化 MVP（`sys_menu.menu_scope`）+ GitHub 登录移除 + C 端注册缓办决策

## 已完成 ✅（矩阵 11/11 + 浏览器实测，详见 `docs/tenant-round5.md`）

| 目标 | 状态 |
|---|---|
| `sys_menu.menu_scope` 列 + 递归 CTE 打标平台子树（101 项，幂等） | ✅ 实库已执行 |
| 侧边栏裁剪：`selectMenuTreeByUserId` 按租户过滤平台菜单 | ✅ |
| 权限裁剪：`selectMenuPermsByUserId` 同步过滤 → 平台端点服务端 403/无此权限 | ✅ |
| thuadmin（租户管理员）可见：二手交易市场/AI(除模型管理)/人事(用户部门岗位)；角色管理收平台 | ✅ 浏览器实测截图 |
| admin（default）全量菜单与平台端点不受影响 | ✅ |
| GitHub 登录整移除（登录页入口/组件/回调页/路由/service；后端端点本就缺失） | ✅（master `751e4f4`） |
| 脚手架欢迎页移除：删 Index 页 + 路由 `/` 重定向 `/home` + 登录统一落 `/home` + 删侧边栏 TwelveT 外链菜单（`backend/data/remove-scaffold-welcome.sql`） | ✅（浏览器实测 admin/thuadmin 均落 `/home`，截图 `docs/admin-home-after-welcome-removal.png`） |
| round4 回归抽查：换头 400、用户列表租户过滤 | ✅ |

## 关键决策（不要重新讨论）

| 决策 | 选择 | 理由 |
|---|---|---|
| 方案 | menu_scope 标志位读时过滤，不复制角色/菜单数据 | role 1 全量映射不动；两处 `<if>` 最小改动 |
| 平台判定 | tenantId null 或 1（default）不过滤 | null=兼容/平台视角，与既有语义一致 |
| 租户来源 | `TenantContext`（非 LoginUser） | **登录构建权限时 token 会话尚未建立**，getLoginUser 会抛；登录请求已被 TenantFilter 赋上下文，语义也更对 |
| 平台子树 | 系统管理/系统工具/日志/监控/运营/客服/角色管理/AI模型管理 | 租户管理员=业务运营者；角色是全局资源 |
| 菜单管理 UI | 不加 menuScope 字段（SQL 维护） | 菜单管理本身是平台页，MVP 最小化 |
| GitHub 登录 | 整移除（用户决策：国内不友好） | 后端端点本就不存在，纯前端清理 |
| C 端注册 | 缓办（用户决策：需对接阿里云等短信服务） | 后置，做其他功能改造 |

## 已知边界（不掩盖）

- 直接输 URL 可加载平台页面壳（前端路由静态），接口全 403——MVP 接受。
- 菜单管理 UI 无 menu_scope 编辑字段；旧 token 权限串未重算（重登生效）。
- 角色/部门仍全局资源：给租户员工配角色需平台操作；部门数据范围未租户化。
- 单元测试 0。

## 下次续做的优先级（按 ROI）

- [ ] **支付记录管理页**（trade_pay 列表+对账，p2-pay 遗留）
- [ ] 审计日志、抽佣闭环、AI 用量月账单
- [ ] 角色租户化第二步（sys_role.tenant_id，租户管理员可自配角色）——本轮只做了菜单/权限读时裁剪
- [ ] 部门数据范围租户化
- [ ] ~~C 端注册~~ 缓办：等对接阿里云短信；届时注册即串联 global_user_id

## 租户隔离模块完成度盘点（2026-08-29，用户确认记录）

**核心已闭环**（全部运行时实测）：数据（9 张业务表 tenant_id+索引+回填+全链路过滤）→ 写防护（跨租户写/IM 校验/防 ID 枚举）→ 身份（租户级登录+token 绑定+global_user_id 串联+用户表读写隔离）→ 入口（子域名+Header+保留字）→ 权限（平台菜单/权限双裁）→ 商业化联动（配额/过期降级/续费）→ 回滚开关（每轮实测）。

**剩余边界（按需补，非漏洞）**：
1. 角色/部门仍全局资源（sys_role/sys_dept 无 tenant_id）——租户自运营时启动「角色租户化第二步」；当前配角色需平台操作。
2. 次要表按需补：feed_report（举报）等，无租户管理入口，风险低。
3. C 端注册缓办（等阿里云短信）；当前管理端开户即按租户归属，无缺口。
4. 设计内豁免：超管 userId=1 不绑租户、平台运营接口不过滤、支付回调按唯一键定位。
5. 小项：旧 token 需重登刷新权限串；单元测试 0（运行时矩阵惯例）。

**一句话**：单平台多租户 SaaS + 管理端开户 + 子域名入口前提下的隔离已完整；开放租户自助运营时再启动角色租户化与 C 端注册。

## 环境坑（本会话新增）

- **登录期不能 `SecurityUtils.getLoginUser()`**：buildLoginUser 构建权限时 token 会话未建立 → NotLoginException；取租户用 `TenantContext`。
- undici（node fetch）剥 Host 头（子域名用例走 node:http）、Windows 重启后端先 taskkill（jar 锁）、`-pl` 打包无主清单：详见 `tenant-round4-summary.md` 环境坑节。
- Bigfish dev 弹窗状态/chunk 陈旧（大写转义 grep）：见 `tenant-p2-pay-summary.md`。

## 启动方式

```bash
/e/Redis-8.8.0-Windows-x64-cygwin-with-Service/redis-server.exe --port 6379 --daemonize yes
cd E:/code/weiwei/backend && java -jar twelvet-admin/target/twelvet-admin.jar
cd E:/code/weiwei/ui && node_modules/.bin/max dev
# 租户管理员：http://thu.localhost:8000/login → thuadmin/123456（侧边栏已裁剪）
# 平台超管：http://localhost:8000/login → admin/123456（全量菜单）
```

## 回滚

menu_scope 过滤随租户上下文消失而失效（`tenant.enabled=false` 天然回滚）；`ALTER TABLE sys_menu DROP COLUMN menu_scope`；`git revert <merge>`。

---

**用户体系五轮闭环（租户→计费→隔离→支付→子域名→用户跨租户→菜单租户化）。下一步建议：支付记录页或审计日志。**
