# 租户隔离第二轮（trade / feed / favorite / im 全业务表）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 设计：`docs/superpowers/specs/2026-08-28-tenant-isolation-round2-design.md`
> 计划：`docs/superpowers/plans/2026-08-28-tenant-isolation-round2-plan.md`
> 上下文摘要：`docs/tenant-isolation-2-summary.md`

## 范围

`product`（第一轮）之外新增 9 张表：`trade_order` / `trade_pay` / `trade_logistics` / `product_favorite` / `feed` / `feed_comment` / `feed_like` / `im_conversation` / `im_message`。

## 启用步骤（已有库执行一次）

```bash
mysql -uroot -p twelvet-fast < backend/data/tenant-isolation-2-init.sql
```

脚本内容：9 张表 `ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1` → 存量回填（按 `product_id` 归属）→ 9 个 `(tenant_id, …)` 组合索引 → 顺带修复存量 bug `trade_order.order_no varchar(32)→64`（代码生成 `NO+32位UUID`=34 字符，C 端建单一直报 Data too long，本轮验证时发现并修复）。

新库直接跑 `neighborswap.sql`（末尾第 9 段已同步；`neighborswap-feed.sql` 的 CREATE TABLE 已内联 tenant_id 列）。

## 与第一轮（仅 product）的差异点

| 机制 | 第一轮 | 本轮 |
|---|---|---|
| BaseEntity 实体 INSERT（feed/im_conversation） | TenantFieldInterceptor 自动注入 | 同左；XML 加 `<if>` 包裹的 tenant_id 列 |
| 非 BaseEntity 实体（TradeOrder/FeedComment/ImMessage） | — | Service 显式 `entity.setTenantId(tenantSupport.writeTid())` |
| @Param Map 形态 INSERT（logistics/favorite/feed_like） | — | Service 传 `tenantId` 参数，XML `<if>` 包裹 |
| 写守卫 | 仅拦截器（BaseEntity） | 新增 `TenantSupport.writeTid()`（framework-core）：开关开且无上下文 → TWTException，与拦截器语义一致 |
| 跨租户写防护 | 商品 detail 天然 404 | 新增：收藏先查商品、点赞/评论先查动态（防 ID 枚举刷 want_count/like_count） |
| JdbcTemplate（PayOrderService） | — | INSERT 条件拼 tenant_id 列（开关关闭时不带该列）；SELECT 有上下文时过滤；回调路径不过滤（网关无 Header） |
| UI | 不发 Header | `requestErrorConfig.ts` 统一附加 `X-Tenant-Code`（`setting.tsx` 的 `TWT.tenantCode`，默认 `'default'`，置空不发） |

## 数据流（完整）

```
UI（X-Tenant-Code: default）
  → TenantFilter（查 sys_tenant → TenantContext.set / 非法 404）
  → Service：
      查询  → TenantContext.get()（null = 不过滤，零侵入回滚）
      写入  → TenantSupport.writeTid()（开关开且 null → TWTException「缺少租户上下文，无法写入」）
  → Mapper XML：SELECT/UPDATE/DELETE <if test="tenantId != null">AND tenant_id=#{tenantId}</if>
                INSERT <if test="tenantId != null">, tenant_id … , #{tenantId}</if>
  → TenantFieldInterceptor（仅 BaseEntity INSERT 自动注入）
```

## 明确不过滤的路径（平台运营视角）

- `AdminMarketplaceMapper`（运营看板/财务/商品审核）、`CsDisputeMapper`（纠纷）
- `FeedMapper.selectPendingFeeds` + `FeedServiceImpl.audit`（动态审核队列，tid 显式传 null）
- `PayOrderService.handlePaySuccess`/`updatePayStatus`（支付回调，无租户上下文，按唯一键定位）
- `TradeOrderMapper.selectOrderStatistics`（GMV 统计）

## 已知边界

| 边界 | 说明 | 何时补 |
|---|---|---|
| 用户仍全局共享 | 跨租户 IM 会话按发起方租户归属，对端（另一租户用户）看不到该会话 | 第三轮 sys_user + global_user_id |
| 无 Header 写入被拒 | `tenant.enabled=true` 且不带 X-Tenant-Code 的 C 端写操作一律失败（动态/商品走拦截器 500，订单/IM/收藏/评论走 TenantSupport 400） | 设计如此：严禁静默落 default |
| 回调路径无隔离 | 支付回调未接线；接线时按 out_trade_no 反查订单租户即可 | 支付集成时 |
| 单元测试 | 0，沿用运行时验证矩阵 | 后续补 |

## 运行时验证记录（2026-08-28 实测，13 步全过）

| # | 用例 | 结果 |
|---|---|---|
| 1 | thu/pku/无Header 商品列表 | 8(t2) / 7(t3) / 21(t1,2,3) ✅ |
| 2 | thu 建订单 → `trade_order.tenant_id=2` | ✅（先修复 order_no 列） |
| 3 | pku 看 thu 订单 detail | 空 data ✅ |
| 4 | thu 建支付单 → `trade_pay.tenant_id=2` | ✅（网关 RSA 报错为沙箱密钥缺失，与隔离无关） |
| 5 | thu 发动态/点赞/评论 → 三表 tenant_id=2 | ✅ |
| 6 | pku stream 无 thu 动态；pku 点赞/评论 thu 动态 | 400「动态不存在」✅ |
| 7 | thu 收藏 thu 商品 → tenant_id=2；pku 收藏 thu 商品 | 400「商品不存在」✅ |
| 8 | thu 发 IM → conv/msg tenant_id=2；pku 拉消息 0 条、thu 1 条 | ✅ |
| 9 | 无Header 列商品（兼容老调用） | 21 条三租户 ✅ |
| 10 | 无Header 写（动态 500 / IM 400「缺少租户上下文，无法写入」） | ✅ |
| 11 | `X-Tenant-Code: invalid` | 404 ✅ |
| 12 | `--tenant.enabled=false` 重启后无Header 写动态/商品 | 200（INSERT `<if>` 修复生效）✅ |
| 13 | default 临时 quota=8 → 发商品 | 429「商品配额已用完（8/8）」✅（billing 无回归） |
| 14 | UI 浏览器实测：admin 登录 → `/home` | 仅 default 租户 6 个在售商品，thu/pku 不可见（`docs/tenant-home-default-tenant.png`）✅ |

## 回滚

```yaml
# application.yml
tenant:
  enabled: false   # 写入缺省列 → DB default 1，行为等同改造前（本轮已验证）
```

```sql
-- 彻底回滚（可选）
ALTER TABLE trade_order DROP COLUMN tenant_id, DROP INDEX idx_order_tenant;
-- …其余 8 张表同理，见 tenant-isolation-2-init.sql 尾部注释
```

## 关键文件索引

| 文件 | 作用 |
|---|---|
| `backend/data/tenant-isolation-2-init.sql` | 增量 DDL + 回填 + order_no 修复 |
| `backend/twelvet-framework/…/core/tenant/TenantSupport.java` | 写路径租户守卫 |
| `backend/twelvet-trade/…/domain/TradeOrder.java` | +tenantId |
| `backend/twelvet-trade/…/mapper/TradeOrderMapper.java/.xml` | 全语句租户过滤 |
| `backend/twelvet-trade/…/mapper/TradeLogisticsMapper.java/.xml` | 同上（含子查询外层） |
| `backend/twelvet-trade/…/service/impl/TradeOrderServiceImpl.java` | 透传 + writeTid |
| `backend/twelvet-trade/…/service/PayOrderService.java` | JdbcTemplate 条件拼列 |
| `backend/twelvet-product/…/domain/FeedComment.java` | +tenantId |
| `backend/twelvet-product/…/mapper/FeedMapper.java/.xml` | 17 语句过滤（审核队列豁免） |
| `backend/twelvet-product/…/mapper/FeedCommentMapper.java/.xml` | 过滤 + INSERT |
| `backend/twelvet-product/…/mapper/FeedLikeMapper.java/.xml` | 过滤 + INSERT |
| `backend/twelvet-product/…/mapper/FavoriteMapper.java/.xml` | 过滤 + INSERT |
| `backend/twelvet-product/…/service/impl/FeedServiceImpl.java` | 透传 + 点赞/评论前置校验 |
| `backend/twelvet-product/…/service/impl/FavoriteServiceImpl.java` | 透传 + 收藏前置校验 |
| `backend/twelvet-product/…/mapper/ProductMapper.java/.xml` | 收藏查询过滤 + INSERT `<if>` 修复 |
| `backend/twelvet-im/…/domain/ImMessage.java` | +tenantId |
| `backend/twelvet-im/…/mapper/ImConversationMapper.java/.xml` | 过滤 + INSERT |
| `backend/twelvet-im/…/mapper/ImMessageMapper.java/.xml` | 过滤 + INSERT |
| `backend/twelvet-im/…/service/impl/ImServiceImpl.java` | 透传 + writeTid |
| `ui/src/setting.tsx` | `TWT.tenantCode` 配置 |
| `ui/src/requestErrorConfig.ts` | 请求拦截器附加 X-Tenant-Code |
