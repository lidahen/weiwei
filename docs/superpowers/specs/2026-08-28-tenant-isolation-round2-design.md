# 租户隔离第二轮（全业务表）设计文档

- 日期：2026-08-28
- 主题：把租户隔离从「仅 product 表」推广到全部 C 端业务表（trade / feed / favorite / im）
- 状态：已批准（用户预授权：不需确认，按推荐方案执行）
- 前置：tenant-mvp（`81f72da`+`a1c9eb5`）、billing-mvp（`46eca18`）已合 master

## 1. 概述

第一轮 MVP 只隔离了 `product` 表，订单/动态/IM/收藏在租户之间互相可见（B 社区能看 A 社区的订单、动态、消息、收藏）。本轮把隔离扩展到全部 C 端业务表，沿用第一轮已验证的模式（`X-Tenant-Code` Header → `TenantContext` → Mapper XML `<if>` 过滤 + INSERT 自动/显式注入），并补上两块短板：

1. **前端接线**：UI 目前不发 `X-Tenant-Code`，tenant 开启时所有 C 端写操作会因「缺少租户上下文」失败。本轮让 UI 请求统一携带租户 Header（可配置）。
2. **回滚洞修复**：`ProductMapper.xml` 的 INSERT 无条件写 `tenant_id` 列，`tenant.enabled=false` 时插入 NULL 报错，违背「零侵入回滚」承诺。改为 `<if>` 包裹。

## 2. 范围

| 表 | 域 | 现状 |
|---|---|---|
| `trade_order` | trade | 无 tenant_id；实体是纯 POJO（不继承 BaseEntity） |
| `trade_pay` | trade | 无 tenant_id；不走 MyBatis（`PayOrderService` 用 JdbcTemplate 手写 SQL） |
| `trade_logistics` | trade | 无 tenant_id；无实体类，全 @Param Map |
| `feed` | product | 无 tenant_id；实体继承 BaseEntity（自动注入可用） |
| `feed_comment` | product | 无 tenant_id；纯 POJO |
| `feed_like` | product | 无 tenant_id；无实体（@Param） |
| `product_favorite` | product | 无 tenant_id；无实体（@Param） |
| `im_conversation` | im | 无 tenant_id；实体继承 BaseEntity |
| `im_message` | im | 无 tenant_id；纯 POJO |

不在范围（明确排除）：

| 项 | 理由 |
|---|---|
| `sys_user` / 用户-租户绑定 | 第三轮（global_user_id），本轮用户仍全局共享 |
| `AdminMarketplaceMapper` / `CsDisputeMapper` / feed 审核队列 | 平台运营视角，天然跨租户，不过滤 |
| `trade_dispute` / `trade_pay_flow` / `trade_review` 等 B 端/分析表 | 平台运营数据，同上 |
| `TenantRunnable`（@Async/@Scheduled/WebSocket 上下文传递） | 实测全仓库 0 处 @Scheduled、0 处 WebSocket，@Async 仅日志/文档解析且不触及业务表——没有需要传递的场景，不做无用封装 |
| 单元测试 | 项目现状零测试，沿用前几轮的「运行时验证矩阵」惯例 |

## 3. 沿用的已定决策（tenant-mvp，不重新讨论）

- 识别方式：HTTP Header `X-Tenant-Code` → `TenantFilter` → `TenantContext`（ThreadLocal）
- 过滤方式：Mapper XML `<if test="tenantId != null">AND tenant_id = #{tenantId}</if>`（MyBatis 非 Plus，无法用拦截器改 SELECT）
- 写入方式：BaseEntity 子类由 `TenantFieldInterceptor` 自动注入；非 BaseEntity / @Param Map 由 Service 显式传
- 跨租户读：空结果或 404（不泄露存在性）；非法租户 code：404
- 开关：`tenant.enabled=false` 零侵入回滚

## 4. 本轮新决策

| # | 决策点 | 结论 | 理由 |
|---|---|---|---|
| 1 | 非 BaseEntity 写入缺租户上下文时的行为 | 抛 `TWTException("缺少租户上下文，无法写入")`（与 `TenantFieldInterceptor` 行为一致），统一封装为 `TenantSupport.writeTid()` | 严禁静默把数据落到 default 租户（数据污染难清理）；与第一轮拦截器语义对齐 |
| 2 | `TenantSupport` 位置 | framework-core `tenant` 包，@Component + @Value 读开关 | product/trade/im 都已依赖 framework-core（TenantContext 就在那里），不引入 system-server 循环依赖；framework-core 已有被扫描的 Spring 组件（TWTExceptionHandler/TWProperties） |
| 3 | INSERT 的 tenant_id 列 | XML 一律 `<if test="tenantId != null">` 包裹（含修复 product 既有写法） | 开关关闭时列缺省 → DB `NOT NULL DEFAULT 1` 兜底，回滚真正零侵入 |
| 4 | B 端运营接口（marketplace 看板/纠纷/审核队列） | 不过滤 | 平台超管要跨租户看全局 |
| 5 | 跨租户收藏 | `addFavorite` 先按当前租户查商品，查不到即「商品不存在」 | 否则可用 ID 枚举给外租户商品刷 want_count |
| 6 | 支付回调路径（`handlePaySuccess`，无 Controller 入口/未接线） | UPDATE 按 `out_trade_no`/`order_id` 唯一键不过滤租户；`queryPayStatus` 的 SELECT 在有上下文时过滤 | 回调请求来自支付网关，永远不会有 `X-Tenant-Code`；当前回调未接线，先留活口 |
| 7 | UI 租户 Header | `setting.tsx` 导出 `TENANT_CODE`（默认 `'default'`），`requestErrorConfig.ts` 拦截器统一附加；配置为空串则不发 | 单前端单租户（未来子域名模式只改 Filter，UI 可置空）；不改它则 tenant 开启时 UI 全部写操作 500 |
| 8 | 存量数据回填 | `trade_order`/`trade_pay`/`trade_logistics`/`product_favorite` 按 `product_id` 关联回填商品所属租户；`feed`/`im_conversation` 有 `related_product_id` 的按商品回填、其余留 default(1)，`feed_comment`/`feed_like`/`im_message` 跟随父行 | 存量订单/收藏/带商品的动态挂在真实商品上，无商品归属的种子数据留 default 无碍验证 |
| 9 | IM 跨租户会话边界 | 会话按发起方租户归属并过滤；用户仍全局共享（第三轮解决） | 过渡期已知边界：A(thu) 与 B(pku) 会话后 B 侧看不到。第三轮用户绑定租户后自然消除 |
| 10 | im_conversation 子查询 | 不加过滤（外层会话已按租户过滤，conv_id 已收敛） | 避免重复过滤；`im_message.selectMessageListByConvId`（直接吃用户输入 conv_id）必须过滤 |

## 5. 数据流（新增部分加粗）

```
HTTP 请求
  → TenantFilter（X-Tenant-Code → TenantContext）
  → Controller
  → Service：查询用 TenantContext.get()（null = 不过滤）
             **写入用 TenantSupport.writeTid()（开关开且无上下文 → TWTException）**
  → Mapper XML：SELECT/UPDATE/DELETE <if> 过滤；INSERT **<if>** 写 tenant_id 列
  → TenantFieldInterceptor（BaseEntity INSERT 自动注入，非 BaseEntity 由 Service 显式 set）
UI（**requestErrorConfig.ts 统一附加 X-Tenant-Code**）
```

## 6. 索引规划（对齐 product 的 `(tenant_id, 常用排序列)` 惯例）

| 表 | 索引 |
|---|---|
| trade_order | `(tenant_id, create_time)` |
| trade_pay | `(tenant_id, create_time)` |
| trade_logistics | `(tenant_id, order_id)` |
| product_favorite | `(tenant_id, user_id)` |
| feed | `(tenant_id, publish_time)` |
| feed_comment | `(tenant_id, feed_id)` |
| feed_like | `(tenant_id, feed_id)` |
| im_conversation | `(tenant_id, update_time)` |
| im_message | `(tenant_id, conv_id)` |
