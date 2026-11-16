# 商业化第三轮 实现计划

- 设计：`docs/superpowers/specs/2026-08-28-tenant-round3-design.md`
- 分支：`feature/tenant-round3`（worktree `.worktrees/tenant-round3`）

## 任务

| # | 任务 | 文件 |
|---|---|---|
| 1 | DDL：sys_tenant.quota_ai_call_day + sys_user.tenant_id（增量 + neighborswap.sql 第 10 段 + 实库） | `backend/data/tenant-round3-init.sql` |
| 2 | RedisUtils.incrementAndGet | `twelvet-framework-redis/…/RedisUtils.java` |
| 3 | 计量 key 模板 | `twelvet-framework-core/…/constants/BillingKeys.java`（新增） |
| 4 | SysTenant +quotaAiCallDay；Mapper XML 列 + updatePlan；renew 写入；VO +getPlanInfo | system-api / system-server |
| 5 | AiQuotaChecker（过期→429、配额→429、计数）；AiChatController 挂钩；pom +system-api | twelvet-ai-server |
| 6 | enforceProductQuota 过期检查 | ProductServiceImpl |
| 7 | insertUser 加 tenant_id 列（拦截器自动注入） | SysUserMapper.xml |
| 8 | ImUserMapper.selectTenantIdByUserId + sendMessage 同租户校验 | twelvet-im |
| 9 | 验证矩阵 8 步（见设计文档 §4） | — |
| 10 | 文档 + merge master + tag | — |

验证策略：沿用运行时矩阵惯例（项目零单测现状）。
