# 商业化第三轮 P1（过期降级 + AI 计量 + 用户租户绑定）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 设计：`docs/superpowers/specs/2026-08-28-tenant-round3-design.md`
> 计划：`docs/superpowers/plans/2026-08-28-tenant-round3-plan.md`
> 摘要：`docs/tenant-round3-summary.md`

## 启用步骤（已有库执行一次）

```bash
mysql -uroot -p twelvet-fast < backend/data/tenant-round3-init.sql
```

内容：`sys_tenant.quota_ai_call_day`（按 plan_code 回填 free=50/basic=500/pro=0）+ `sys_user.tenant_id` + 索引 + **im_conversation 唯一键改租户内唯一**（`uk_ab` → `uk_tenant_ab(tenant_id,user_a_id,user_b_id)`，验证中发现全局唯一键会在「同对用户跨租户各建会话」时撞键 500）。

## 三件事

### ① 过期租户自动降级
`ProductServiceImpl.enforceProductQuota` 与 AI 检查器都在读出租户后检查 `expireAt < now` → 429「套餐已于 X 过期，请续费」。过期的 Pro 也拦截。

### ② AI 计量
- `PlanCatalog.quotaAiCallDay`（billing 轮预留）本轮落库生效：`sys_tenant.quota_ai_call_day`，renew 时按套餐写入。
- 检查点：`AiChatController.chat`（SSE 与导购两种模式统一入口），代理上游**之前**计数。
- key：`billing:ai:day:{tenantId}:{yyyyMMdd}`（模板收口在 framework-core `BillingKeys`），`RedisUtils.incrementAndGet`（新增），首次写入 26h 过期。
- 超限 → 429「今日 AI 调用配额已用完（x/y），请升级套餐」；`billing.enabled=false` 或未识别租户跳过。
- `getPlanInfo` 返回 `quotaAiCallDay` + `aiCallToday`（供租户 UI 展示用量）。
- 依赖变化：`twelvet-ai-server` pom + `twelvet-system-api`（同 product-server 先例，单向无循环）。

### ③ sys_user 租户绑定 MVP
- `sys_user.tenant_id NOT NULL DEFAULT 1`；存量全 default。
- 归属写入零新代码：`SysUser extends BaseEntity` → `TenantFieldInterceptor` 自动注入（管理端建用户经 UI 带租户 Header），`insertUser` XML 加 `<if>` 列。
- 登录不改（username 全局唯一）；完整「1 用户跨 N 租户」是第四轮。
- IM `sendMessage` 校验对方租户：非空且 ≠ 当前 → 400「对方不在当前租户，无法发送」（关闭第二轮记录的「跨租户会话对端不可见」边界）。

## 验证记录（2026-08-28 实测 8 步全过）

| # | 用例 | 结果 |
|---|---|---|
| 1 | default 临时过期 → 发商品 | 429「套餐已于 2026-08-27 过期」；恢复后 200 ✅ |
| 2 | thu 临时 quota_ai=2 → 第 3 次 /ai/chat | 429「3/2」（前两次上游拒连仍计数，符合"代理前计数"设计）✅ |
| 3 | 管理端建用户（default Header） | sys_user.tenant_id=1 ✅ |
| 4 | thu 用户发 IM 给 default 用户 | 400「对方不在当前租户」✅ |
| 5 | 同租户互发 | 200（唯一键修复后）✅ |
| 6 | getPlanInfo | quotaAiCallDay=50 / aiCallToday=3 ✅ |
| 7 | renew thu→basic | quota_ai_call_day=500 + quota_product_max=1000 落库 ✅ |
| 8 | 回归：thu 商品 8 条、IM 消息列表 | 不变 ✅ |

测试数据已清理（临时改的配额/有效期/测试用户/测试消息）。

## 已知边界

- AI 计数含失败调用（上游不可用也计数）——防刷优先，不回滚。
- 用户绑定是"属性"不是"登录隔离"：用户可从任意租户前端登录，写操作归属按请求 Header。完整方案第四轮。
- `getPlanInfo.aiCallToday` 只反映当天 Redis 计数，无历史。
- 单元测试 0（沿用运行时矩阵惯例）。

## 回滚

`billing.enabled=false`（关配额+计量）；DDL 见 init 脚本尾注释；`git revert`。
