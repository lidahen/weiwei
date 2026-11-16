# 商业化第三轮 P1 — 上下文摘要（2026-08-28 节点）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 下次继续这个工作，先读这个文件。前置：tenant-isolation-2（同日完成，见 `tenant-isolation-2-summary.md`）。

## 当前位置

- **Branch**: `feature/tenant-round3`（worktree `.worktrees/tenant-round3`，合并后可删）
- **Tag**: `tenant-round3-2026-08-28`

## 已完成 ✅（8 步运行时验证全过，详见 `docs/tenant-round3.md`）

| 目标 | 状态 |
|---|---|
| ① 过期租户自动降级：商品发布 + AI 调用两处 `expireAt < now → 429` | ✅（billing 摘要里的已知洞已修） |
| ② AI 计量：`sys_tenant.quota_ai_call_day` 落库 + Redis 日计数 + `AiChatController` 入口 429 | ✅（billing 轮预留的 `PlanCatalog.quotaAiCallDay` 本轮生效） |
| ③ sys_user 租户绑定 MVP：`tenant_id` 列 + 拦截器自动归属 + IM 同租户校验 | ✅ |
| 验证中发现并修复：`im_conversation` 全局唯一键 `uk_ab` 与租户过滤冲突（同对用户跨租户建会话撞键 500）→ 改 `uk_tenant_ab(tenant_id,user_a_id,user_b_id)` | ✅ |
| `getPlanInfo` 增加 `quotaAiCallDay` / `aiCallToday`（供租户 UI 用量展示） | ✅ |

## 关键决策（不要重新讨论）

| 决策 | 选择 | 理由 |
|---|---|---|
| AI 配额存储 | `sys_tenant.quota_ai_call_day`（0=不限），renew 按 PlanCatalog 写入 | 与 quota_product_max 同模式 |
| 计数维度 | 租户 × 自然日，`billing:ai:day:{tid}:{yyyyMMdd}`，key 模板在 framework-core `BillingKeys` | ai-server 与 system-server 共用一条链路 |
| AI 检查位置 | `AiChatController.chat` 入口、代理上游**之前**；失败调用不回滚 | 防刷优先，MVP 从简 |
| AI 依赖 | twelvet-ai-server pom + system-api | 同 product-server 先例 |
| 用户绑定 | 仅归属属性（拦截器自动注入 + insertUser XML `<if>`），登录不改 | 完整 1 用户跨 N 租户是第四轮 |
| IM 跨租户 | `sendMessage` 校验对方 tenant_id → 400 | 关闭 isolation-2 的已知边界 |
| im_conversation 唯一键 | `(tenant_id, user_a_id, user_b_id)` | 全局 uk_ab 与租户过滤冲突 |

## 已知边界

- AI 计数无历史/无按用户维度；aiCallToday 只读当天。
- 用户可从任意租户前端登录（绑定≠登录隔离）。
- 租户管理 UI 未展示 AI 配额/用量字段（后端 VO 已就绪，前端加两列即可）。

## 下次续做的优先级（按 ROI）

### P2
- [ ] **租户管理 UI 补 AI 配额/用量列**（VO 已返回 quotaAiCallDay/aiCallToday，前端 ProTable 加列即可）
- [ ] **支付集成**：支付宝/微信扫码续费（回调按 out_trade_no 反查租户落账）
- [ ] **子域名支持**：`thu.ershou.com` → tenant_code（只改 TenantFilter，UI 置空 tenantCode）

### P3（第四轮）
- [ ] **1 用户跨 N 租户**：sys_user.global_user_id + 租户级登录 + 用户表读隔离
- [ ] AI 计量历史表（按月账单）
- [ ] 抽佣闭环、审计日志

## 启动方式

```bash
/e/Redis-8.8.0-Windows-x64-cygwin-with-Service/redis-server.exe --port 6379 --daemonize yes
cd E:/code/weiwei/.worktrees/tenant-round3/backend   # 或 master
java -jar twelvet-admin/target/twelvet-admin.jar
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/auth/oauth2/token -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456","grant_type":"password"}' | jq -r .data.access_token)
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/system/tenant/thu   # 看 quotaAiCallDay/aiCallToday
```

## 回滚

`billing.enabled=false`（配额+计量全关）；DDL 见 `tenant-round3-init.sql` 尾注释；`git revert`。
