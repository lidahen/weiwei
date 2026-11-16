# 商业化第三轮（P1 三件套）设计文档

- 日期：2026-08-28
- 主题：过期租户自动降级 + AI 计量 + sys_user 租户绑定 MVP
- 状态：已批准（用户预授权，按推荐顺序执行）
- 前置：tenant-mvp、billing-mvp、tenant-isolation-2 均已合 master

## 1. 过期租户自动降级（billing 已知洞）

`enforceProductQuota` 在读取租户后增加过期检查：`expireAt != null && expireAt < now` → 抛 `QuotaExceededException`（429）「套餐已过期，请续费」。位置在配额检查之前（过期的 Pro 也拦截）。AI 计量检查器内做同样检查。

## 2. AI 计量（Redis 计数 → 429）

billing 轮已在 `PlanCatalog` 预留 `quotaAiCallDay`（free=50 / basic=500 / pro=0 不限），但未落库、未实现。本轮补齐：

| 决策 | 选择 | 理由 |
|---|---|---|
| 配额存储 | `sys_tenant.quota_ai_call_day INT NOT NULL DEFAULT 0`（0=不限），按 plan_code 回填（free→50/basic→500） | 与 `quota_product_max` 同模式；renew 时由 PlanCatalog 写入 |
| 计数维度 | 租户 × 自然日，key `billing:ai:day:{tenantId}:{yyyyMMdd}` | 商业化按租户收费；key 模板放 framework-core `BillingKeys`（ai-server 与 system-server 共用） |
| 原子自增 | `RedisUtils.incrementAndGet(key)`（新增，RedisTemplate opsForValue().increment）；首次自增后 expire 26h | RedisUtils 现无自增方法 |
| 检查位置 | `AiChatController.chat`（统一聊天入口，SSE 与导购两种模式都在代理前检查） | LLM 调用的唯一入口 |
| 计数语义 | 代理前自增（含失败调用），不回滚 | MVP 从简，防刷优先 |
| 未识别租户 / billing.enabled=false | 跳过检查 | 与既有 billing 模式一致 |
| 超限响应 | `QuotaExceededException` → 429「今日 AI 调用配额已用完（x/y），请升级套餐」 | 与商品配额同语义 |
| 依赖 | `twelvet-ai-server` pom 增加 `twelvet-system-api`（读 SysTenantMapper） | ai-server 原来不依赖 system-api；单向无循环（同 product-server 先例） |
| 套餐查询 | `getPlanInfo` VO 增加 `quotaAiCallDay` + `aiCallToday` | 租户 UI 后续展示用量 |

## 3. sys_user 租户绑定 MVP

| 决策 | 选择 | 理由 |
|---|---|---|
| 范围 | `sys_user.tenant_id BIGINT NOT NULL DEFAULT 1` + 索引；存量全部 default(1) | 完整「1 用户跨 N 租户（global_user_id）」是第四轮，本轮只做归属 |
| 写入归属 | 无需新代码：`SysUser extends BaseEntity` → `TenantFieldInterceptor` 自动注入；`SysUserMapper.insertUser` XML 加 `<if>` 列 | 管理端建用户经 UI 带 `X-Tenant-Code`，自动归属 |
| 登录 | 不改（username 全局唯一，登录与租户解耦） | 完整租户级登录隔离是第四轮 |
| IM 跨租户 | `sendMessage` 前校验对方 `tenant_id`：非空且 ≠ 当前租户 → 400「对方不在当前租户，无法发送」 | 关闭第二轮文档记录的「跨租户会话对端不可见」边界 |
| 对方租户查询 | `ImUserMapper.selectTenantIdByUserId`（sys_user 单列查询，im 模块本就查 sys_user） | 不引 system-server 依赖 |
| 读过滤 | sys_user 查询不加租户过滤 | 平台运营视角 + 用户表本轮只做写归属 |

## 4. 验证矩阵

| # | 用例 | 期望 |
|---|---|---|
| 1 | default 租户 expire_at 临时改昨天 → 发商品 | 429「套餐已过期」；恢复后正常 |
| 2 | thu 临时 quota_ai_call_day=2 → 第 3 次 POST /ai/chat | 429 配额；Redis key 计数正确；恢复 50 |
| 3 | 管理端建用户（带 default Header） | sys_user.tenant_id=1（拦截器自动注入） |
| 4 | thu 新用户发 IM 给 default 用户 | 400「对方不在当前租户」 |
| 5 | default 用户互发 IM | 正常（回归） |
| 6 | getPlanInfo | 含 quotaAiCallDay / aiCallToday |
| 7 | renew thu → basic | quota_ai_call_day=500 落库 |
| 8 | 回归：商品配额 429、租户隔离读（thu 列表） | 不变 |

## 5. 回滚

`billing.enabled=false` 关闭配额/计量；DDL 可 DROP COLUMN；`git revert`。
