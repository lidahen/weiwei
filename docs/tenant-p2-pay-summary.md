# 商业化 P2 — 上下文摘要（2026-08-29 节点）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> ## 当前快照（新会话先读这 8 行）
> - **master = 商业化 P0-P2 完成 + 第四轮已完成 + C 端 AI 导购已上线**（注意：本文件早先的「第四轮未做」记录已过时——`3fd9ca7` 已实现 1 用户跨 N 租户/租户级登录/token 域绑定/TenantBindInterceptor；后续还有管理台视觉修复、AI 服务优化等。以 `git log` 为准）。
> - **运行态**：后端 master jar 跑 8080（tenant.enabled=true, base-domain=localhost, billing.pay.mock=true）；UI dev server 8000（用 `thu.localhost:8000` 看租户店铺）；Redis 6379；MySQL root/123456 库 twelvet-fast。
> - **租户种子**：default=pro(不限)/thu=pku=free(100商品/50AI)，到期 2026-09-27（thu/pku）/2028（default）。
> - **本会话验证基线**：curl Host 矩阵 8 步 + 浏览器子域名店铺，全过；测试数据已清理还原。
> - **文档地图**：实现细节 `docs/tenant-{isolation-2,round3,p2-pay}.md`；各 `*-summary.md` 是续做入口；spec/plan 在 `docs/superpowers/`。
> - **环境坑**：见下方「环境坑」节（Bigfish dev 弹窗状态 / dev chunk 陈旧查大写转义 / git add -A 卫生）。
> - **登录账号矩阵**（登录按租户域隔离，`LoginServiceImpl` 有租户上下文时只查该租户用户——预期行为）：
>   thu.localhost → `thuadmin`/`alice`（密码均 123456，bob 密码不同）；localhost:8000 → `admin`/123456；`user_demo`/123456 密码不对（demo 按钮走 quickLogin 自带逻辑）。
>   租户域里 demo 快捷按钮（admin 等 default 账号）登录失败是**正常现象**（第四轮租户级登录的设计行为）。
> - **下一步**：P2 已清零。剩余见文末「下次续做的优先级」（第四轮用户绑定 / 支付记录页 / 租户自助付费页）。

> 下次继续这个工作，先读这个文件。前置：round3 已合 master。

## 当前位置

- **Tag**: `tenant-p2-pay-2026-08-29`（merge `f58d340`，feat `8f39eab`）
- **内容**：租户管理页 AI 配额/用量列 + 扫码续费支付闭环（含回调首次接线）

## 已完成 ✅（7 步验证全过，详见 `docs/tenant-p2-pay.md`）

| 目标 | 状态 |
|---|---|
| `Plan`/`PlanCatalog` 移 system-api + `monthlyPrice` 定价（basic ¥99/pro ¥299） | ✅ |
| `sys_tenant_renew_order` 履约凭据表 + `trade_pay.order_id` 可空 | ✅ 实库已执行 |
| 下单/状态轮询（网关自愈）/mock-pay 演示 三个端点 | ✅ |
| **支付回调 `/payment/callback/{channel}` 首次接线**（续费履约 + 商品订单 handlePaySuccess 分发） | ✅ |
| 履约幂等（markPaid 抢占）+ 续费语义与直接续费一致 | ✅ 实测 expire 不重复累加 |
| `billing.pay.mock` 开关（dev true / base false） | ✅ |
| Tenant 页 AI 列 + 扫码续费两段式弹窗（QRCode + 2s 轮询 + mock 按钮） | ✅ 浏览器实测 |

## 关键决策（不要重新讨论）

| 决策 | 选择 | 理由 |
|---|---|---|
| 端点位置 | trade-server（`/payment/tenant-renew/*` + `/payment/callback/*`） | 网关在 trade；system 不反向依赖 trade |
| PlanCatalog 位置 | system-api `com.twelvet.system.api.plan` | trade 履约要用；避免 trade→system-server |
| 履约凭据 | 独立表（不是塞 trade_pay） | 计划+月数必须落库，按 out_trade_no 反查 |
| mock 支付 | `billing.pay.mock`（dev true/base false）+ `/mock-pay` 端点 | 沙箱无密钥也要能端到端演示；生产关掉走真网关 |
| 幂等 | `UPDATE WHERE status='unpaid'` 抢占 | 回调/轮询/模拟并发安全 |

## 已知边界

- 真网关链路需生产密钥+公网 notify-url；本环境验证到「验签拒绝」一级。
- wechat v3 回调 JSON+证书验签未适配（`verifyNotify` 留给各网关实现）。
- 支付流水无管理页展示（trade_pay 有数据，需要时加「支付记录」Tab）。

## 环境坑（本会话实测，务必避开）

- **Bigfish dev（regenerator 转译）下，antd Modal 的 open 状态偶发不随 setState 关闭**：setState 确实执行、无 JS 错误、toast/表格刷新正常，唯弹窗不重渲染。已在 Tenant 页加 `forceModalDom` DOM 兜底（开/关两端）。其他页面若遇同类现象，复用该模式。
- **max dev 的 chunk 会陈旧/混杂**：git merge 换代码后 dev server 可能持续输出旧 bundle。且中文编译为**大写**十六进制 uXXXX 转义（如 `\u5957\u9910`），grep 中文查不到 ≠ 不存在，先查转义再下结论。彻底解法：杀 8000 进程 → `rm -rf ui/src/.umi ui/node_modules/.cache ui/.mfsu` → 重启 max dev。
- **git add -A 会把用户未跟踪文件扫进提交**（.claude/、docs/design-compare/、weiwei(1).sql、网站灵感.txt）——提交前检查 status，已加 .gitignore 忽略 .worktrees/。

## 下次续做的优先级

### P2 剩余
- [x] **子域名支持**：已完成（2026-08-29，`tenant.base-domain` 配置 + TenantFilter Host 解析 + UI hostname 注入，验证 8 步全过，见 `docs/superpowers/specs/2026-08-29-tenant-subdomain-design.md`）
- [ ] 支付记录管理页（trade_pay 列表 + 对账）

### P3（第四轮）
- [x] 1 用户跨 N 租户（`3fd9ca7` 已完成：租户级登录+token 域绑定+global_user_id+用户读隔离，DDL tenant-round4-init.sql）
- [ ] AI 用量月账单、抽佣闭环、审计日志
- [ ] 租户前台 `/product/plan` 套餐展示 + 升级引导（租户自助付费，非超管代付）

## 启动方式

```bash
/e/Redis-8.8.0-Windows-x64-cygwin-with-Service/redis-server.exe --port 6379 --daemonize yes
cd E:/code/weiwei/backend && java -jar twelvet-admin/target/twelvet-admin.jar
cd E:/code/weiwei/ui && node_modules/.bin/max dev
# http://localhost:8000/login → admin/123456 → 系统管理 → Tenant Mgmt → 续费/升级 → 扫码支付 → 模拟支付
```

## 回滚

`billing.pay.mock=false`；`DROP TABLE sys_tenant_renew_order`；`git revert f58d340`。

---

**商业化主线（租户→计费→隔离→支付）已闭环。下一步建议：子域名支持或租户自助付费页。**
