# 二手交易闭环止血 — 设计文档（第 1 期）

> 日期：2026-10-19
> 类型：工作流 A（feature/修复混合，复杂度 L）
> 前置调研：2026-10-19 三路调研（后端模块 / 前端页面 / db-export 表结构），结论已与用户对齐
> 用户约束：**支付功能保持占位跳过**（微信/支付宝对接好之前不做支付侧改造，mock 支付链路保留不动）

## 1. 背景与问题（Why）

2026-10-19 全面体检发现交易主链路**断裂**，当前系统无法完成一笔真实交易：

1. **支付后订单卡死**：`PaySuccessEventListener.java:47` 把订单置为 `paid`，但发货 SQL
   只认 `unshipped`（`TradeOrderMapper.xml:113`），全仓不存在 `paid → unshipped` 流转。
2. **无取消、无超时**：后端零 cancel 代码；`expire_at`/`auto_confirm_at`/`closed` 全是死字段；
   quartz 无交易任务。
3. **库存/商品状态全程不联动**：下单不校验商品状态、不扣库存、无自购拦截；同一商品可无限重复下单；
   卖掉的商品永远在售。
4. **前端 Checkout（确认订单/地址采集）、Ship（发货）是占位页**，收货地址从未进入交易
   （下单只传 productId）。
5. **失败静默成功**：`confirmReceipt`/`shipOrder` 在 `rows == 0` 时只 log.warn 仍返回 "OK"。

## 2. 目标 / 非目标

**目标**：dev 环境用 mock 支付跑通「发布 → 浏览 → 下单(锁库存) → 支付 → 发货 → 收货/超时自动确认 → 评价」全链路；未支付订单 30 分钟自动关闭并释放商品；买家可主动取消未支付订单。

**非目标（本期不做）**：
- 支付渠道选择 UI、PayResult 轮询、支付对账（用户明确：支付跳过，仅保留现有 mock 路径）
- 退款/售后 C 端（第 2 期）、议价（第 3 期）
- 物流轨迹 API 对接（发货仍手填单号）
- 多库存营销玩法（stock>1 仅保证扣减/回补正确，不做限购）

**反目标**：不破坏零侵入租户回滚（`<if test="tenantId != null">` 模式）；不改动 InternalOrderController 内部下单契约（AI Agent 自动获得新校验与库存语义）。

## 3. 状态机设计（核心决策）

DDL 注释与运营统计（`AdminMarketplaceServiceImpl.PAID_STATUSES`）的真实意图是
`unpaid → unshipped(已付款待发货) → shipped → done`，`paid` 是监听器引入的野状态。本期统一为：

```
unpaid ──支付成功──> unshipped ──卖家发货──> shipped ──买家确认/7天自动──> done
   │                                                        │
   ├─买家主动取消                                            └─（done 时商品 → sold）
   └─30分钟超时（quartz）
   ↓
 closed（释放库存：stock+1，商品 locked → onsale）
```

- **商品状态联动**（利用 DDL 已有 `locked` 态）：
  - 下单成功扣库存后 `stock == 0` → 商品 `onsale → locked`（拍下即从列表消失，闲鱼模式）
  - 支付/发货期间商品保持 `locked`；`done` 时 → `sold`
  - 取消/超时释放：`stock+1`，且 `locked → onsale`（回补后库存 > 0 时）
- **兼容存量**：`shipOrder` 的 WHERE 改为 `status IN ('unshipped','paid')`（兼容已落库的野状态 `paid` 行）；`selectOrderDetail` 的 paid 判断已兼容，不动。
- `TradeOrder.java` 的 `@Schema` 状态注释补全为 `unpaid/unshipped/shipped/done/closed/refunding/refunded`。

## 4. 后端改动清单

### 4.1 createOrder 重构（TradeOrderServiceImpl）
- 入参从 `productId` 扩展为 `{productId, addressId?, buyerRemark?, deliveryType?}`（Map body，保持现有风格）
- 校验链（新增，任一失败抛 `TWTException`，不再静默）：
  1. 商品存在（现有）且 `status = 'onsale'`（否则「商品已下架或已售出」）
  2. `buyerId != product.getSellerId()`（自购拦截）
  3. 扣库存：`UPDATE product SET stock = stock - 1 WHERE product_id=? AND stock > 0`，影响行数 0 → 「商品已被拍下」（防超卖 + 防重复下单，单 SQL 原子）
  4. 扣减后 `stock == 0` → 商品置 `locked`
- 快照：`String.format` 拼 JSON 改为 Jackson（framework `JacksonUtils`）序列化 `{title, price, cover, condition, images}`；含引号的 title 不再产出坏 JSON
- `expire_at = now + trade.order.unpaid-timeout-minutes`（默认 30，`@Value` 注入）
- `addressId` 有值时：查 `user_address` 并校验 `user_id` 归属，Jackson 序列化整行入 `address_snapshot`；无值（同城面交）存 null
- `delivery_type`：入参优先，缺省按商品 `shipping_method` 映射（`self_pickup → self_pickup`，其余 → `express`）
- 失败路径补偿：扣库存成功但后续插入订单失败时，事务回滚覆盖库存回补（同一 `@Transactional`，无需额外处理）

### 4.2 状态迁移修正
- `PaySuccessEventListener`：`updateOrderStatus(orderId, "unshipped", "paid_at", null)`（原 `"paid"`）
- `shipOrder` SQL：WHERE `status IN ('unshipped','paid')`；发货时写 `auto_confirm_at = now + trade.order.auto-confirm-days`（默认 7 天）；`rows == 0` 抛异常「订单状态不允许发货」
- `confirmReceipt`：`rows == 0` 抛异常「订单状态不允许确认收货」；成功后将商品置 `sold`
- 新增 `cancelOrder(orderId, buyerId)`：仅 `unpaid` 可取消（SQL 带 status 条件），置 `closed` + 释放库存（同超时逻辑）；`rows == 0` 抛异常
- 新增 `TradeOrderController POST /trade/order/cancel`

### 4.3 超时任务（TradeTimeoutTask）
- `@Component("tradeTimeout")`，仿 `RecommendQuartzTask` 模式，两个无参方法：
  - `cancelExpiredUnpaid()`：`UPDATE trade_order SET status='closed' WHERE status='unpaid' AND expire_at < now()`，逐单释放库存
  - `autoConfirmReceipt()`：`UPDATE ... SET status='done', done_at=now() WHERE status='shipped' AND auto_confirm_at < now()`，逐单置商品 `sold`
- 注册种子：新增 `backend/data/trade-timeout-init.sql`（`sys_job` INSERT，invokeTarget `tradeTimeout.cancelExpiredUnpaid` / `tradeTimeout.autoConfirmReceipt`，cron 每 5 分钟，仿 `product-recommend-init.sql` 格式）
- 释放库存 SQL：`UPDATE product SET stock = stock + 1, status = IF(status = 'locked', 'onsale', status) WHERE product_id = ?`

### 4.4 夹带修复（一行级，同 PR）
- `TradePayController.payStatus`：查回支付单后校验其 `order_id` 归属当前用户（复用 `validateOrderOwnership`；无归属 → 与「不存在」同响应，防探测）

## 5. 前端改动清单（ui/）

| 页面 | 改动 |
|---|---|
| `Checkout.tsx`（现为占位 Empty） | 真实现：地址选择（复用 `user_address` 现有 service；无地址引导去 `/user/address`）+ 买家留言 + 费用明细（商品价 + 运费）+ 提交 → `orderCreate({productId, addressId, buyerRemark})` → 跳订单详情 |
| `Ship.tsx`（现为占位 Empty） | 真实现：读 query 的 orderId → 表单（快递公司 + 单号）→ `orderShip`（api.ts 已有，0 引用）→ 成功回订单列表 |
| `ProductDetail.tsx` | 「立即购买」从直接 `orderCreate` 改为跳 `/trade/checkout?productId=:id` |
| `OrderDetail.tsx` | `unpaid` 态显示「取消订单」按钮（调新 cancel 接口）；支付按钮保持现状（mock 链路不动） |
| `OrderList.tsx` | 「去发货」跳转携带 orderId（现为空跳占位页，改后自然生效） |

不做：支付渠道选择、PayResult 轮询（非目标）。

## 6. 测试策略（TDD）

- **后端单测**（Mockito，仿 `TradeOrderServiceImplInternalTest` 风格）：
  - `createOrder`：onsale 校验 / 自购拦截 / 扣库存 0 行拒绝 / stock=0 置 locked / expire_at 写入 / addressId 归属校验 / 快照 JSON 安全（title 含引号）
  - `cancelOrder`：unpaid 才可取消、释放库存调用
  - `PaySuccessEventListener`：置 `unshipped` 而非 `paid`
  - `TradeTimeoutTask`：到期关单 + 释放、发货超时自动确认
  - `shipOrder`/`confirmReceipt`：rows==0 抛异常
- **前端**：单测基建薄弱（全仓 2 例），Checkout/Ship 以实现 + `npm run build` 通过为准，不强制新增用例（诚实记录在完成报告中）
- **验证命令**：`mvn -pl twelvet-trade/twelvet-trade-server -am test`；前端 `npm run build`

## 7. 风险与权衡

- **`paid` 野状态的存量数据**：ship SQL 双状态兼容，不做数据迁移脚本（测试库可手改）
- **扣库存与商品状态双写**：同事务内完成；quartz 释放逐单处理，量小可接受
- **`product.status='locked'` 从 onsale 列表消失**：符合「拍下即不可见」预期；详情页直链仍可访问（订单跳转需要）
- **AI Agent 下单走同一 createOrder**：自动获得校验/库存语义；Agent 的 estimate/confirm 流程无需改动（其风险检查已在读 trade_bargain/商品状态）
- **sys_job 种子依赖人工执行**（或 initdb 挂载）：在 seed 文件头注释说明执行方式

## 8. 实施后补充（2026-10-19 实现完成时记录）

### 8.1 第 2 期支付接入的**显式前置条件**（最终审查 I-1，必须先于真实支付上线）

存在资金窗口：买家在 `expire_at` 边界支付、回调迟到，quartz 先超时关单（closed + 库存回补），
随后支付成功监听器 rows=0 仅记日志——结果：扣了钱、订单已关、库存已回补、商品仍在售（可被二次拍下）。
当前 mock 支付无真实资损，但**真实支付上线前必须实现**：支付成功处理校验订单仍为 `unpaid`，
否则不落地订单状态、走退款流程（依赖第 2 期退款能力）。

### 8.2 第 2 期顺手项清单（最终审查 deferred triage 产出）

- 时钟源统一：`expire_at` 用 JVM 时钟写入 vs 关单扫描用 DB `NOW()`，生产前统一 NTP 或改 DB 时钟
- `trade.order.auto-confirm-days` 加下界守卫（`Math.max(1, …)`），防配置 0 导致即刻自动确认
- 前端 HTTP 400 双 toast 收敛（requestErrorConfig 非 500 分支追加「无法链接API」）
- OrderDetail「取消订单」按钮按角色隐藏（卖家不应见，后端守卫已兜底）

### 8.3 实施中的设计偏差（已裁决，均为改进）

1. `deductStock` SQL 与本设计 4.1 原文不同：`status = IF(stock <= 1, 'locked', status)` 必须排在
   `stock = stock - 1` 之前（MySQL UPDATE 赋值左→右求值，排在后面会读到扣减后库存导致 off-by-one）。
2. `createOrderForUser` 补 `@Transactional`（自调用绕过代理，双写需外层事务保证原子性）。
3. `updateOrderStatus` 加 `AND status='unpaid'` 前置（防迟到支付回调复活已取消订单），该 SQL 现为监听器专用。
4. 前端 Checkout 对 `self_pickup` 商品跳过地址必选（对齐本设计 4.1「面交无地址存 null」）。

### 8.4 第 1 期代码评审后的 P0 修复（2026-10-20，commit 1bc18fd）

全量代码评审（15 项发现）后当日修复 7 项（F1–F7，详见 commit message）：
收银台入口守卫（`validateOrderPayable`：归属+仅 unpaid+未过期+存量单排除）、支付事件可靠发布
（回调 rows=0 重发自愈 + 轮询路径补发，`publishPaySuccess` 三路共用）、mock-pay 归属校验
（`trade_pay.pay_user_id` 新列，增量 SQL `backend/data/trade-pay-security-init.sql`）、
存量单取消不回补库存（`expire_at IS NULL` 判别）、deliveryType 不采信客户端、
自提/包邮运费后端归零、Agent 内部下单透传 addressId。顺带：`updateQrCode` 改按
`out_trade_no` 限定 + 唯一索引（修复重复流水号根因）、Pay.tsx 补 dev 模拟支付按钮。

**对评审建议的驳回记录（防止后续照抄重蹈）**：评审建议 `closeOrder` 加
`NOT EXISTS (SELECT 1 FROM trade_pay ... status='paid')` 守卫——机制核验后不采纳：
`trade_pay.order_id` 是多态外键（商品订单/跑腿任务/租户续费 NULL 三类共用，无业务类型列），
按 order_id 反查支付状态会误伤同 ID 的跑腿支付单，令商品订单永远无法关单、库存永久锁定。
替代组合：F1 入口守卫（不再产生"已关单仍可支付"的新增流量）+ F2 事件自愈（把
"已付未迁移"窗口从永久收敛到秒级），残余风险（秒级窗口内支付回调与关单竞态）
并入下方第 2 期退款能力一并解决。

**第 2 期硬前置（在 §8.1 基础上追加）**：`trade_pay` 增加 `biz_type` 列（trade/runner/renew），
解多态反查问题后方可实现"关单前校验支付状态"的 SQL 级守卫。`pay_user_id` 列已随本次修复落地。
