# 租户管理 UI（前端） — 上下文摘要（2026-08-28 节点）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 续做入口。billing MVP 后端已合并（`tag: billing-mvp-2026-08-28`），本节为前端"超管 web 页面"。
>
> **Git tag: `tenant-mgmt-ui-2026-08-28`**（含详细 tag message：commits、API 端点、数据库、已知洞、下次续做）

## 当前位置

- **Branch**: `feature/tenant-mgmt-ui`（worktree `E:\code\weiwei\.worktrees\tenant-mgmt-ui` 已合并 master）
- **Master commits**:
  - `9ea4bda` merge: 租户管理 UI
  - `c402455` feat(ui): 租户管理 web 页面
  - `4006902` feat(ui): UI 大改（Login/marketplace 改版）
  - `3352abd` docs(tenant-mgmt-ui): 补新 UI 兼容性 + 浏览器截图
- **依赖**: 后端 `feature/billing-mvp` 已合 master（commit `06bb175` + `46eca18`，tag `billing-mvp-2026-08-28`）
- **新增路由**: `/system/tenant`（"系统管理 → Tenant Mgmt"）

## 已完成 ✅

| 目标 | 状态 | 验证 |
|---|---|---|
| `pages/System/Tenant/{index.tsx, service.ts, typings.d.ts}`（ProTable + Modal 模板） | ✅ | 浏览器实测 |
| 列表展示 3 个租户 + 套餐标签 + 配额颜色 + 续费操作 | ✅ | 截图 `docs/tenant-page-1.png` |
| 续费弹窗：套餐下拉 + 月数输入 + 当前套餐提示 | ✅ | 截图 `docs/tenant-renew-modal.png` |
| 续费提交 → 后端 `/system/tenant/{code}/renew` → 自动 reload | ✅ | thu: FREE/2026-09-27/8-100 → BASIC/2027-09-27/8-1000 |
| 路由注册 `config/routes.ts` | ✅ | 浏览器侧边菜单显示 |
| `sys_menu` 2200/2201/2202 菜单 + F 按钮 + admin role binding | ✅ | MySQL 实查 + 菜单可见 |
| `sys_tenant` 临时改动恢复（thu 改回 free/30天/100） | ✅ | MySQL 实查 |

## 浏览器实测结果（2026-08-28）

**老 UI 截图（worktree 早期）**：
| # | 操作 | 截图 | 结果 |
|---|---|---|---|
| 1 | 登录 admin/123456 → 进 `/system/tenant` | `tenant-page-1.png` | 列表显示 default(PRO)/thu(FREE)/pku(FREE) ✅ |
| 2 | 点 thu "续费/升级" | `tenant-renew-modal.png` | 弹窗显示当前套餐 FREE + 到期 2026-09-27 ✅ |
| 3 | 选"基础版（1000 商品）" + 12 月 → 确认续费 | `tenant-page-after-renew.png` | thu 升级到 BASIC/2027-09-27/8-1000 ✅ |

**新 UI 截图（master stash pop 后，2026-08-28）**：
| # | 操作 | 截图 | 结果 |
|---|---|---|---|
| 4 | pop stash 拿到新 UI（Login 改版 + marketplace 改版 + footer + global.less + app.tsx）后访问 `/system/tenant` | `tenant-page-new-ui.png` | 新 UI 风格（暖色调 + weiwei footer + 面包屑），Tenant 页 ProTable 兼容良好，套餐标签/配额颜色正常 ✅ |
| 5 | 新 UI 下点 thu 续费 | `tenant-renew-modal-new-ui.png` | 弹窗"确认续费"按钮变墨绿（与新 UI 主色一致），整体协调 ✅ |

**结论**：Tenant 页**无需样式调整**即可融入新 UI（只用了 ProTable + Tag + Modal 等基础 Ant Design 组件，主题色自动跟随 `ConfigProvider`）。

## 关键决策（不要重新讨论）

| 决策 | 选择 | 理由 |
|---|---|---|
| 列表数据来源 | 前端硬编码 3 个 seed (`['default','thu','pku']`) + Promise.all `getPlanInfo` | 后端无 list 端点，MVP 阶段 3 个租户直接拉；未来要做分页/搜索再补 list 端点 |
| 权限粒度 | `@SaCheckPermission("system:tenant:list"/query/edit)` 3 个 F 按钮 | 与 billing-mvp 控制器对齐；admin role wildcard 自动覆盖 |
| 续费语义 | 后端已实现 `base = max(expireAt, now) + N 月`（提前续费不浪费） | 前端在弹窗"说明"里复制贴出，避免重复解释 |
| 配色 | PRO 金、Basic 蓝、Free 灰 + 配额比例颜色（绿/黄/红） | Ant Design Tag 默认色 + 自定义 ratio |
| i18n 菜单名 | 用英文 `Tenant Mgmt` 避免 utf8 字符集问题 | sys_menu 的 remark 列是 utf8 装不下中文，i18n 表结构未知；后续要本地化改用前端 i18n 资源 |
| 路由位置 | `ui/config/routes.ts` 加在 `/system/I18n` 后 | 系统管理下，与 Client/Token 风格一致 |

## 验证过程中踩过的坑（已修复，避免重蹈）

| # | 坑 | 修复 |
|---|---|---|
| 1 | Umi `request` 拦截器从 `localStorage.TWT_access_token` 取的是 `JSON.parse()`（**不是裸字符串**），否则 `Authorization` header 永远是 `Bearer undefined` | 用 `localStorage.setItem('TWT_access_token', JSON.stringify({access_token, refresh_token, expires_in}))` |
| 2 | `yarn` / `cross-env` 命令不在 PATH，直接 `node_modules/.bin/umi` 报 `Invalid config keys` | 用 `node_modules/.bin/max dev`（Umi Max CLI 认 Bigfish 扩展配置） |
| 3 | master 上有未提交 ui/ 改动阻塞 worktree | `git stash push -m "..." -- ui/` 暂存 |
| 4 | `node_modules` 装在 master，重新 yarn install 工作树浪费 5 分钟 | `ln -s ../../../ui/node_modules node_modules` 软链 |
| 5 | `sys_menu` 的 remark 列 utf8 装不下中文，INSERT 报 1366 | 改用英文 remark（"tenant management menu"），菜单名也用英文 |
| 6 | `sys_i18n` 表不存在（实际表名不同），INSERT 报 1146 | 删掉 i18n INSERT 块；菜单英文显示即可 |
| 7 | 前端 `fill_form` 工具会**追加**而非替换原值（React 受控组件） | 改用 `evaluate_script` + `nativeInputValueSetter` 直接派发 input/change 事件 |
| 8 | master 上有用户未提交的 UI 大改（Login 改版 + marketplace + footer + app.tsx + global.less + package.json 等），worktree 跑的是老 UI 截图 | merge 完成后 `git stash pop` 把新 UI 合到 master（无冲突），重启 max dev 重新截图 → Tenant 页 ProTable 风格天然兼容新 UI（用 ConfigProvider 主题色），无需改样式 |

## 下次续做的优先级

按 ROI 排序：

### P1（1 周内）
- [ ] **订单/支付/物流加 `tenant_id`**（订单租户隔离 + 计费触发源）
- [ ] **菜单中文 i18n**（找对 sys_i18n 表名 + 补 `Tenant Mgmt`/`Tenant Query`/`Tenant Renew`）
- [ ] **过期租户自动降级**：在 `enforceProductQuota` 加 `if (expireAt < now) throw QuotaExceeded`（已知未修的洞）

### P2
- [ ] 后端补 `GET /system/tenant/page` 端点，前端切换到分页 + 搜索
- [ ] 套餐升降级 UI：租户前台 `/product/plan` 展示当前套餐 + 升级引导
- [ ] **支付集成**：支付宝/微信扫码续费

### P3
- [ ] 审计日志：超管每次续费写操作日志（已有 `@Log` 注解可激活）
- [ ] AI 计量（Redis）：AiChatHistory 调用次数检查 → 超限 429
- [ ] 抽佣闭环（订单完成按比例扣平台费）

## 启动方式（下次回来直接跑）

```bash
# 1. 后端 + Redis 起来
java -jar twelvet-admin/target/twelvet-admin.jar
/e/Redis-8.8.0-Windows-x64-cygwin-with-Service/redis-server.exe --port 6379 --daemonize yes

# 2. 前端（worktree 已有 symlinked node_modules）
cd E:/code/weiwei/.worktrees/tenant-mgmt-ui/ui
node_modules/.bin/max dev
# 监听 8000，代理 /api -> 8080

# 3. 浏览器打开
# http://localhost:8000/login → admin/123456 → 系统管理 → Tenant Mgmt
```

## 回滚方案

```bash
# 1. 删菜单
mysql -e "DELETE FROM sys_menu WHERE menu_id IN (2200, 2201, 2202);
         DELETE FROM sys_role_menu WHERE menu_id IN (2200, 2201, 2202);"

# 2. 还原代码
git revert <merge-commit>
```

## 关键文件索引

| 文件 | 作用 |
|---|---|
| `ui/src/pages/System/Tenant/index.tsx` | 主页面（ProTable + Modal） |
| `ui/src/pages/System/Tenant/service.ts` | `getPlanInfo` / `renew` / `listAll` |
| `ui/src/pages/System/Tenant/typings.d.ts` | TypeScript 类型 |
| `ui/config/routes.ts` | 路由注册（+1 路由） |
| `backend/data/tenant-mgmt-ui-init.sql` | sys_menu 3 条 + sys_role_menu 3 条 |
| `docs/tenant-page-1.png` / `tenant-renew-modal.png` / `tenant-page-after-renew.png` | 浏览器实测截图 |

---

**下次先读 worktree 的 `docs/tenant-mgmt-ui-summary.md`（如有）+ 本文件；订单加 `tenant_id` 是最高 ROI。**
