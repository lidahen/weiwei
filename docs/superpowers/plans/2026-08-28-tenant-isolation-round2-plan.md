# 租户隔离第二轮 实现计划

- 日期：2026-08-28
- 设计：`docs/superpowers/specs/2026-08-28-tenant-isolation-round2-design.md`
- 分支：`feature/tenant-isolation-2`（worktree `.worktrees/tenant-isolation-2`）
- 验证策略：沿用前几轮惯例——mvn 编译 + 运行时验证矩阵（项目零单测现状）

## 任务分解

| # | 任务 | 产出 |
|---|---|---|
| 1 | DDL | `backend/data/tenant-isolation-2-init.sql`（增量）+ `neighborswap.sql` / `neighborswap-feed.sql` 末尾同步；实库执行 |
| 2 | 框架 | `framework-core/tenant/TenantSupport.java`（writeTid 守卫）；`ProductMapper.xml` INSERT tenant_id 改 `<if>` |
| 3 | trade | `TradeOrder`+tenantId；`TradeOrderMapper`/XML 全语句；`TradeLogisticsMapper`/XML；`TradeOrderServiceImpl` 透传+守卫；`PayOrderService` JdbcTemplate INSERT/SELECT |
| 4 | feed/favorite | `FeedComment`+tenantId；`FeedMapper`/`FeedCommentMapper`/`FeedLikeMapper`/`FavoriteMapper` + XML + 两个 ServiceImpl 透传；`FavoriteServiceImpl` 补跨租户商品校验；`ProductMapper` 两条收藏查询 + 调用点 |
| 5 | im | `ImMessage`+tenantId；`ImConversationMapper`/`ImMessageMapper` + XML；`ImServiceImpl` 透传+守卫 |
| 6 | UI | `setting.tsx` 导出 `TENANT_CODE`；`requestErrorConfig.ts` 请求拦截器附加 `X-Tenant-Code` |
| 7 | 验证 | 运行时矩阵（下表）+ DB tenant_id 分布实查 |
| 8 | 收尾 | 文档（`docs/tenant-isolation-2.md` + summary）、commit、merge master |

## 运行时验证矩阵

前置：`--tenant.enabled=true` 启动新 jar；thu(token=2)/pku(3)/default(1) 三租户 + 已登录用户 token。

| # | 步骤 | 期望 |
|---|---|---|
| 1 | 回归：`GET /product/home/recommend` + thu | 仅 tenant=2 商品 |
| 2 | thu 建订单（thu 商品）→ 实库查 `trade_order.tenant_id` | =2 |
| 3 | pku `GET /trade/order/page` / `GET /trade/order/detail/{thu订单}` | 空 / 404 语义 |
| 4 | thu `POST /trade/pay/create` → 实库查 `trade_pay.tenant_id` | =2 |
| 5 | thu 发动态/点赞/评论 → 实库 tenant_id | =2 |
| 6 | pku `GET /feed/stream`、对 thu 动态点赞/评论 | 看不到 / 失败 |
| 7 | thu 收藏 thu 商品 → tenant_id=2；pku 收藏 thu 商品 | 失败「商品不存在」 |
| 8 | thu 发 IM 消息 → conv/msg tenant_id=2；pku `GET /im/message/list`（thu conv） | 空 |
| 9 | 无 Header：`GET /product/home/recommend` | 三租户全量（兼容老调用） |
| 10 | 无 Header：C 端写（如发动态） | 500「缺少租户上下文」（严格守卫） |
| 11 | `X-Tenant-Code: invalid` | 404（回归） |
| 12 | 回滚验证：`--tenant.enabled=false` 重启，无 Header 发动态/商品 | 200 正常落 default（本轮修复的洞） |
| 13 | 配额回归：default(pro/quota=0) 发商品 | 429（billing 不受影响） |

## 风险与回滚

- 回滚：`tenant.enabled=false`（本轮已把 INSERT 补成真零侵入）；DDL 回滚 `ALTER TABLE ... DROP COLUMN tenant_id`。
- 风险：存量数据回填不完美（feed/im 留 default）→ 仅影响种子数据展示归属，不影响隔离正确性。
