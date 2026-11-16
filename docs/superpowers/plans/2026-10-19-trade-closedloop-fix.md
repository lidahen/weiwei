# 二手交易闭环止血 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复交易主链路断裂：支付后可发货、订单可取消可超时关闭、库存/商品状态联动、Checkout/Ship 前端页真实现。

**Architecture:** 后端在 `twelvet-trade-server` 内重构 `TradeOrderServiceImpl` 状态迁移（借 `twelvet-product-server` 的 `ProductMapper`/`UserAddressMapper` 新增原子 SQL），新增 quartz 超时任务（仿 `RecommendQuartzTask` bean 反射模式）；前端真实现 `Checkout.tsx`/`Ship.tsx` 两个占位页。支付链路完全不动（用户明确：支付保持占位，仅保留 dev mock 路径）。

**Tech Stack:** Java 17 + Spring Boot 3.5.4 + MyBatis XML（无 MyBatis-Plus）+ Sa-Token + Mockito 单测；前端 Umi Max 4 + React 18 + AntD 5（npm）。

**Spec:** `docs/superpowers/specs/2026-10-19-trade-closedloop-fix-design.md`

## Global Constraints

- 状态机唯一合法词汇：`unpaid → unshipped → shipped → done`，旁路 `closed`；`paid` 仅作为存量野状态被兼容读取，不再写入。
- 租户模式：所有新 SQL 用 `<if test="tenantId != null">` 手写条件（零侵入回滚）；quartz 任务与支付回调链路**故意**无租户上下文（`TenantContext.get()` 返回 null 即自动跳过过滤，不要"修复"这一点）。
- Java 格式：提交前对改动模块执行 `mvn spring-javaformat:apply -pl <模块> -am`（或确认 IDEA 格式一致——现有代码是 tab 缩进、链式换行风格）。
- 包管理器一律 npm（`cd ui && npm run build` 验证），禁 yarn。
- 错误一律抛 `TWTException`（`com.twelvet.framework.core.exception.TWTException`），Controller 层沿用现有不捕获风格（全局异常处理器转 JsonResult）。
- JSON 序列化用 `com.twelvet.framework.utils.JacksonUtils.toJson(Object)`，禁止 `String.format` 拼 JSON。
- 每个任务独立 commit；消息格式 `fix(trade): ...` / `feat(ui): ...`，结尾加 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。

## Review Focus

1. **并发超卖**：两个买家同时拍库存 1 的商品，必须恰好一单成功。兜底是 `deductStock` 的 `WHERE stock > 0 AND status='onsale'` 原子条件——Task 1 的测试锁定 rows=0 → 抛异常且不落订单。
2. **脏数据快照**：商品 title 含 `"` 时快照必须是合法 JSON（Jackson 而非 format）——Task 1 测试用含引号 title 断言可被 `JacksonUtils.readValue` 还原。
3. **存量野状态 `paid` 订单**：必须仍可发货（ship SQL `status IN ('unshipped','paid')`）——XML 变更在 Task 2，无单测覆盖（纯 SQL），实现者自查一遍 XML 字符串。
4. **超时关单与买家主动取消并发**：两条路径都走 `WHERE status='unpaid'` 的原子 UPDATE，只有 rows>0 的一方释放库存——Task 3 测试锁定 rows=0 时不调 `restoreStock`（防止库存双倍回补）。
5. **租户开关关闭回滚**：`tenant.enabled=false` 时下单/取消/超时全链路仍可用（tenantId 传 null）——Task 1 扩展现有 `switchOff` 用例。

---

### Task 1: 下单链路重构（校验 + 扣库存 + 地址 + 过期时间 + 快照）

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-api/src/main/java/com/twelvet/trade/api/domain/TradeOrder.java`（新增 `expireAt` 字段，`@Schema` 状态注释改 `unpaid/unshipped/shipped/done/closed/refunding/refunded`）
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/resources/mapper/trade/TradeOrderMapper.xml`（resultMap 加 `expire_at`；`insertOrder` 重写：加 `delivery_type/address_snapshot/buyer_remark/expire_at` 列、**删掉 paid_at 列**（现状下单即写 sysdate() 是 bug））
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/mapper/TradeOrderMapper.java`（`insertOrder` 不变签名；无需新方法）
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/ProductMapper.java` + `resources/mapper/product/ProductMapper.xml`（新增 `deductStock`）
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/UserAddressMapper.java` + `resources/mapper/product/UserAddressMapper.xml`（新增 `selectAddressById`）
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/ITradeOrderService.java` + `impl/TradeOrderServiceImpl.java` + `controller/TradeOrderController.java`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeOrderServiceImplCreateTest.java`（新建）；Modify `TradeOrderServiceImplInternalTest.java`（构造器加参 + stub 补齐）

**Interfaces:**
- Produces: `ITradeOrderService.createOrder(Long buyerId, Long productId, Long addressId, String buyerRemark, String deliveryType)`（新五参方法；旧两参 `createOrder` 删除，`createOrderForUser(Long, Long)` 签名不变、内部委托五参方法传 `(buyerId, productId, null, null, null)`）
- Produces: `ProductMapper.deductStock(@Param("productId") Long, @Param("tenantId") Long) int`
- Produces: `UserAddressMapper.selectAddressById(@Param("addressId") Long, @Param("userId") Long) Map<String,Object>`
- Controller `POST /trade/order/create` body 扩参：`{productId, addressId?, buyerRemark?}`（`deliveryType` 也接受，可空）

- [ ] **Step 1: 写失败测试**（`TradeOrderServiceImplCreateTest`，Mockito 风格仿 `TradeOrderServiceImplInternalTest`，构造器注入 mock：`orderMapper, logisticsMapper, adminMapper, productMapper, userAddressMapper, tenantSupport`）：

  1. `create_happyPath_deductsStockAndSetsExpire`：product stub `status="onsale"`、`sellerId=99`、`shippingMethod="express"`、`shippingFee=3.00`、`price=10.00`、`title='含"引号"的商品'`、`stock=1`；`deductStock` 返回 1；`selectAddressById(5L, 7L)` 返回含 `receiver/mobile/province/city/district/detail` 的 Map。调 `createOrder(7L, 100L, 5L, "留言", null)`。断言：`deductStock(100L, tid)` 被调；captor 捕获 `insertOrder` 参数——`expireAt` 非空、`buyerRemark="留言"`、`addressSnapshot` 非空且 `JacksonUtils.readValue(addressSnapshot, Map.class)` 能还原 receiver、`productSnapshot` 同样可还原且 title 含引号、`deliveryType="express"`。
  2. `create_productNotOnsale_rejected`：product `status="sold"` → 抛 `TWTException`，`deductStock` 从未被调，`insertOrder` 从未被调。
  3. `create_selfPurchaseRejected`：product `sellerId=7`（=买家）→ 抛 `TWTException`。
  4. `create_stockConcurrentLoss_rejected`：product onsale 但 `deductStock` 返回 0 → 抛 `TWTException` 且 `insertOrder` 从未被调（Review Focus #1）。
  5. `create_expressWithoutAddress_rejected`：`addressId=null` 且生效 deliveryType=express → 抛 `TWTException`。
  6. `create_addressNotOwned_rejected`：`selectAddressById` 返回 null → 抛 `TWTException`。
  7. `create_selfPickup_noAddressNeeded`：product `shippingMethod="self_pickup"`、`addressId=null` → 成功，`addressSnapshot` 为 null，`deliveryType="self_pickup"`，运费仍取商品 shippingFee。
  8. `create_tenantOff_usesNullTid`：`tenantSupport.enabled()=false` 时 `deductStock(100L, null)` 被调（Review Focus #5）。
  9. 快照 title 引号用例已并入用例 1（Review Focus #2）。

- [ ] **Step 2: 跑测试确认 RED**
  Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=TradeOrderServiceImplCreateTest`
  Expected: 编译失败（五参 `createOrder`/`deductStock`/`selectAddressById` 不存在）。

- [ ] **Step 3: 实现**
  - `deductStock` SQL（原子兜底，一条语句同时防超卖与非在售）：
    ```sql
    UPDATE product SET stock = stock - 1,
        status = IF(stock - 1 <= 0, 'locked', status), update_time = NOW()
    WHERE product_id = #{productId} AND stock > 0 AND status = 'onsale'
    <if test="tenantId != null">AND tenant_id = #{tenantId}</if>
    ```
  - `selectAddressById`：`SELECT address_id, receiver, mobile, province, city, district, detail, is_default FROM user_address WHERE address_id=#{addressId} AND user_id=#{userId}`（resultMap 复用现有 `AddressResult`）。
  - `createOrder` 五参实现顺序：查商品（现有 `selectById(productId, currentTid())`）→ 校验 `onsale` → 自购拦截 → `deductStock`（rows=0 抛"商品已下架或已被拍下"）→ 解析 deliveryType（入参优先，空则商品 `self_pickup→self_pickup` 其余→`express`）→ express 时查地址并校验归属 → 快照用 `JacksonUtils.toJson(Map.of(...))`（title/price/cover/condition）→ 地址快照 `JacksonUtils.toJson(addressMap)` → `expireAt = LocalDateTime.now().plusMinutes(unpaidTimeoutMinutes)` → `order.setTenantId(tenantSupport.writeTid())` → `insertOrder`。`@Value("${trade.order.unpaid-timeout-minutes:30}")` 注入超时分钟数。
  - `insertOrder` XML 重写：列 `order_no, buyer_id, seller_id, product_id, product_snapshot, sku_price, freight, total_amount, delivery_type, address_snapshot, buyer_remark, expire_at, status, create_time, update_time`，VALUES 对应（status 仍 'unpaid' 硬编码），租户 `<if>` 保持。
  - Controller `create`：`body.get("addressId")`（可空，`toLong` 风格解析）、`body.get("buyerRemark")`、`body.get("deliveryType")` 透传。
  - `TradeOrder` 域对象加 `private LocalDateTime expireAt;`（带 `@Schema(description = "支付过期时间")`），resultMap 加 `<result property="expireAt" column="expire_at"/>`。
  - 更新旧测试 `TradeOrderServiceImplInternalTest`：构造器插入 `userAddressMapper` mock（放 productMapper 之后、tenantSupport 之前）；`onsaleProduct()` 补 `when(product.getStatus()).thenReturn("onsale")`；所有走通 `createOrder` 的用例补 `when(productMapper.deductStock(eq(PRODUCT_ID), any())).thenReturn(1)`。

- [ ] **Step 4: 跑测试确认 GREEN**
  Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test`
  Expected: 全部 PASS（含旧 InternalTest）。

- [ ] **Step 5: Commit**
  ```bash
  git add -A && git commit -m "fix(trade): 下单补状态/自购/库存校验，原子扣库存防超卖，地址与过期时间落库"
  ```

---

### Task 2: 状态机修正（发货/收货/取消 + 监听器 + 释放库存）

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/listener/PaySuccessEventListener.java`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/resources/mapper/trade/TradeOrderMapper.xml`（`shipOrder` 加 auto_confirm_at 写入与状态兼容；新增 `cancelOrder`）
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/mapper/TradeOrderMapper.java`（`cancelOrder`）
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/ProductMapper.java` + XML（新增 `restoreStock`、`markSold`）
- Modify: `ITradeOrderService.java` + `TradeOrderServiceImpl.java`（`cancelOrder` 方法、`shipOrder`/`confirmReceipt` 抛异常化 + markSold）+ `TradeOrderController.java`（`POST /cancel`）
- Test: `TradeOrderServiceImplLifecycleTest.java`（新建）；Modify `TradeOrderServiceImplInternalTest.java`（如受构造器影响——不受，跳过）

**Interfaces:**
- Consumes: Task 1 的 `tenantSupport.writeTid()`/`currentTid()` 模式与 `TWTException` 风格。
- Produces: `ITradeOrderService.cancelOrder(Long orderId, Long buyerId)` → `String`（"OK"）
- Produces: `ProductMapper.restoreStock(@Param("productId") Long, @Param("tenantId") Long) int`、`ProductMapper.markSold(@Param("productId") Long, @Param("tenantId") Long) int`

- [ ] **Step 1: 写失败测试**（`TradeOrderServiceImplLifecycleTest`，构造器同 Task 1）：

  1. `payListener_setsUnshipped`：直接构造 `PaySuccessEvent`（orderId=1, paidAt=now），调监听器，verify `orderMapper.updateOrderStatus(1L, "unshipped", "paid_at", null)`（注意新监听器构造器需 `TradeOrderMapper`，直接 mock 注入）。
  2. `ship_rowsZero_throws`：`orderMapper.shipOrder(...)` 返回 0 → `shipOrder` 抛 `TWTException`，且 `logisticsMapper.insertLogistics` 从未被调（顺序：先更新后插物流，或插物流前先校验——**实现必须保证 rows=0 时不写物流表**）。
  3. `ship_success_writesAutoConfirm`：`shipOrder` 返回 1 → verify 物流插入 + 商品未被改动（商品状态流转只发生在 done/closed）。
  4. `confirm_rowsZero_throws`：`confirmReceipt` 返回 0 → 抛 `TWTException`。
  5. `confirm_success_marksProductSold`：订单 productId=100 → verify `productMapper.markSold(eq(100L), any())` 被调。
  6. `cancel_rowsZero_throws`：`orderMapper.cancelOrder(...)` 返回 0 → 抛 `TWTException` 且 `restoreStock` 从未被调。
  7. `cancel_success_restoresStock`：返回 1 → verify `restoreStock(productId, tid)` 被调。

- [ ] **Step 2: RED** — Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=TradeOrderServiceImplLifecycleTest` → 编译失败。

- [ ] **Step 3: 实现**
  - 监听器：`updateOrderStatus(orderId, "unshipped", "paid_at", null)`。
  - `shipOrder` XML：`UPDATE trade_order SET status='shipped', shipped_at=sysdate(), auto_confirm_at=DATE_ADD(NOW(), INTERVAL #{autoConfirmDays} DAY), update_time=sysdate() WHERE order_id=#{orderId} AND seller_id=#{sellerId} AND status IN ('unshipped','paid')` + 租户 `<if>`。Mapper 签名加 `@Param("autoConfirmDays") int`；Service `@Value("${trade.order.auto-confirm-days:7}")` 注入后传参。**先执行 UPDATE，rows==0 直接抛异常，成功才插物流**（Review Focus #3：这条 IN 列表是存量 `paid` 单的唯一兼容口，实现后自查 XML 字符串）。
  - `cancelOrder` XML：`UPDATE trade_order SET status='closed', update_time=sysdate() WHERE order_id=#{orderId} AND buyer_id=#{buyerId} AND status='unpaid'` + 租户 `<if>`。Service 实现需先 `selectOrderByIdAndBuyerId` 取 `productId`（查不到或状态非 unpaid 直接依赖 UPDATE rows 判定，避免 TOCTOU：先 UPDATE，rows=1 再用查出的 productId 释放）。
  - `restoreStock` SQL：`UPDATE product SET stock = stock + 1, status = IF(status='locked','onsale',status), update_time=NOW() WHERE product_id=#{productId}` + 租户 `<if>`。
  - `markSold` SQL：`UPDATE product SET status='sold', update_time=NOW() WHERE product_id=#{productId} AND stock=0 AND status='locked'` + 租户 `<if>`（stock=0 守卫保证多库存商品不被误标）。
  - `confirmReceipt`：rows=0 抛异常；rows=1 时需 productId——`confirmReceipt` XML 同理先 UPDATE 后用 `selectOrderByIdAndBuyerId` 之前查出的订单（Service 先查后更：查订单 → 校验是买家 → UPDATE → rows=1 则 markSold）。
  - Controller：`POST /trade/order/cancel?orderId=`，`StpUtil.getLoginIdAsLong()` 传 service。

- [ ] **Step 4: GREEN** — Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test` → 全部 PASS。

- [ ] **Step 5: Commit**
  ```bash
  git add -A && git commit -m "fix(trade): 支付成功置 unshipped 打通发货，发货/收货失败不再静默，新增买家取消与库存释放"
  ```

---

### Task 3: 超时任务（30 分钟关单 + 7 天自动确认）+ sys_job 种子

**Files:**
- Modify: `TradeOrderMapper.java` + `TradeOrderMapper.xml`（`selectExpiredUnpaidOrders`、`selectAutoConfirmDueOrders`、`closeOrder`、`autoConfirmOrder`）
- Modify: `ITradeOrderService.java` + `TradeOrderServiceImpl.java`（`cancelExpiredUnpaid()`、`autoConfirmReceipt()`，无参返回 int）
- Create: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/task/TradeTimeoutTask.java`
- Create: `backend/data/trade-timeout-init.sql`
- Test: `TradeOrderServiceImplTimeoutTest.java`（新建）

**Interfaces:**
- Consumes: Task 2 的 `restoreStock`/`markSold`。
- Produces: bean `@Component("tradeTimeout")`，方法 `cancelExpiredUnpaid()` / `autoConfirmReceipt()`（无参，供 quartz 反射）。

- [ ] **Step 1: 写失败测试**（mock 同前）：

  1. `cancelExpired_closesEachAndRestores`：`selectExpiredUnpaidOrders(200)` 返回两条（orderId 1/productId 100、orderId 2/productId 101）；`closeOrder(1L)` 返回 1、`closeOrder(2L)` 返回 0 → verify `restoreStock(100L, null)` 被调、`restoreStock(101L, null)` **从未被调**（Review Focus #4：并发下被买家抢先取消的一方 rows=0，不释放）。
  2. `autoConfirm_marksSoldOnlyWhenClosed`：`selectAutoConfirmDueOrders(200)` 返回一条（orderId 3/productId 102）；`autoConfirmOrder(3L)` 返回 1 → verify `markSold(102L, null)` 被调。反向：返回 0 → 不 markSold。
  3. 空列表 → 无任何 close/autoConfirm 调用，返回 0。

- [ ] **Step 2: RED** — Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=TradeOrderServiceImplTimeoutTest` → 编译失败。

- [ ] **Step 3: 实现**
  - `selectExpiredUnpaidOrders`：`SELECT order_id, product_id FROM trade_order WHERE status='unpaid' AND expire_at IS NOT NULL AND expire_at < NOW() LIMIT #{limit}`（无租户参数——quartz 无上下文，属声明的设计）。
  - `closeOrder`：`UPDATE trade_order SET status='closed', update_time=NOW() WHERE order_id=#{orderId} AND status='unpaid'`。
  - `selectAutoConfirmDueOrders`：同 1，条件 `status='shipped' AND auto_confirm_at IS NOT NULL AND auto_confirm_at < NOW()`。
  - `autoConfirmOrder`：`UPDATE ... SET status='done', done_at=NOW(), update_time=NOW() WHERE order_id=#{orderId} AND status='shipped'`。
  - Service 两个方法：查列表 → 逐单原子 UPDATE → rows=1 才 `restoreStock`/`markSold(productId, null)`；返回成功数。`@Transactional` 不加（逐单独立，一单失败不影响其余；SQL 原子性已够）。
  - `TradeTimeoutTask`：仿 `RecommendQuartzTask`——`@Component("tradeTimeout")`，构造注入 `ITradeOrderService`，两个无参 public 方法各调 service 并 `log.info("[Quartz] TradeTimeout closeUnpaid={} / autoConfirm={}")`。
  - `trade-timeout-init.sql`：仿 `product-recommend-init.sql` L70-76 的幂等 INSERT 模式（`INSERT INTO sys_job (...) SELECT ... FROM DUAL WHERE NOT EXISTS (...)`），两条：job_name `交易-超时未支付关单` invoke_target `tradeTimeout.cancelExpiredUnpaid` cron `0 */5 * * * ?`；job_name `交易-发货超时自动确认收货` invoke_target `tradeTimeout.autoConfirmReceipt` cron `0 */5 * * * ?`；misfire_policy `'3'`、concurrent `'1'`、status/其余列值与模板完全一致。文件头注释写明：sys_job 种子需手工执行或随 initdb 挂载；回滚 `DELETE FROM sys_job WHERE invoke_target LIKE 'tradeTimeout.%';`。

- [ ] **Step 4: GREEN** — Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test` → 全部 PASS。

- [ ] **Step 5: Commit**
  ```bash
  git add -A && git commit -m "feat(trade): 超时未支付自动关单与发货超时自动确认收货（quartz + sys_job 种子）"
  ```

---

### Task 4: 支付单查询越权修复（一行安全补丁）

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/TradePayController.java:71-78`
- Test: 无新增（Controller 薄层，行为由 Task 2 的 service 测试间接覆盖归属语义）；验证靠编译 + 既有测试。

**Interfaces:**
- Consumes: 现有 `tradeOrderService.validateOrderOwnership(orderId, userId)`、`payOrderService.queryPayStatus(payNo)`。

- [ ] **Step 1: 实现**：先读 `PayOrderServiceImpl.queryPayStatus` 确认返回 Map 中持有 order_id 的实际键名，然后在 `payStatus` 中：取该键 → `validateOrderOwnership(orderId, StpUtil.getLoginIdAsLong())` 为 null 时返回与「支付单不存在」**完全相同**的响应（`JsonResult.error("支付单不存在")`，防探测）；非买家/卖家不可见（卖家不参与支付单，只认买家）。

- [ ] **Step 2: 验证** — Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test` → PASS。

- [ ] **Step 3: Commit**
  ```bash
  git add -A && git commit -m "fix(trade): 支付单状态查询补归属校验，防越权枚举"
  ```

---

### Task 5: 前端 Checkout 真实现 + 详情页跳转

**Files:**
- Modify: `ui/src/pages/marketplace/Checkout.tsx`（现为渲染 Empty 的占位页）
- Modify: `ui/src/pages/marketplace/ProductDetail.tsx`（L123 附近的「立即购买」从直接 `orderCreate` 改为跳转）
- Test: 无单测基建（全仓 2 例），以 `npm run build` 通过 + 交互自查为准（诚实记录）。

**Interfaces:**
- Consumes: `addressList()`（api.ts L126，已存在）、`orderCreate({productId, addressId, buyerRemark, deliveryType?})`（api.ts L143，已存在）、商品详情函数（api.ts 中已有，实现者 grep `productDetail|product/detail` 确认函数名）。
- Produces: 路由 `/trade/checkout?productId=:id`（路由已配置 `ui/config/routes.ts:511`，无需改）。

- [ ] **Step 1: 实现 Checkout.tsx**：PC 双栏（左：地址选择卡片列表 + 买家留言 Input.TextArea；右：商品摘要卡 + 费用明细 price/freight/合计）+ 移动端单列（`useIsMobile`，项目现有 hook）。逻辑：`useSearchParams` 取 productId → 并行拉商品详情与 `addressList()` → 无地址时显示引导卡「去添加地址」跳 `/user/address` → 默认选中 `isDefault` 的地址 → 提交按钮调 `orderCreate` → 成功 `navigate('/trade/order/detail?orderId=' + orderId)`。加载态用现有 `SkeletonCards` 风格，提交中按钮 loading 防重复点击。样式参照同目录 `Pay.tsx`/`OrderDetail.tsx` 现有 Card 布局，不自造设计语言。

- [ ] **Step 2: 改 ProductDetail.tsx**：立即购买按钮改为 `navigate('/trade/checkout?productId=' + id)`；删除原直接 `orderCreate` 调用与「下单失败,请确认已登录」硬编码文案（L133）。

- [ ] **Step 3: 验证** — Run: `cd ui && npm run build` → 构建成功无 TS 错误。

- [ ] **Step 4: Commit**
  ```bash
  git add -A && git commit -m "feat(ui): Checkout 结算页真实现（地址采集/费用明细/防重复提交），详情页改跳结算"
  ```

---

### Task 6: 前端 Ship 真实现 + 订单取消 + 发货入口传参

**Files:**
- Modify: `ui/src/pages/marketplace/Ship.tsx`（占位页）
- Modify: `ui/src/pages/marketplace/OrderList.tsx`（「去发货」跳转补 orderId query）
- Modify: `ui/src/pages/marketplace/OrderDetail.tsx`（unpaid 态加「取消订单」按钮）
- Modify: `ui/src/services/marketplace/api.ts`（新增 `orderCancel`）

**Interfaces:**
- Consumes: `orderShip({orderId, company, no})`（api.ts L149，已存在；后端 `POST /trade/order/ship` body 键为 `orderId/company/no`）。
- Produces: `orderCancel(orderId: number)` → `request('/trade/order/cancel?orderId=' + orderId, { method: 'POST' })`。

- [ ] **Step 1: api.ts 加 `orderCancel`**（紧邻 `orderShip`，同风格 JSDoc 注释）。

- [ ] **Step 2: 实现 Ship.tsx**：`useSearchParams` 取 orderId → 先调订单详情（现有 orderDetail 函数，api.ts 已有）回显商品快照摘要 → Form：快递公司 Select（常见公司静态选项：顺丰/中通/圆通/韵达/京东/邮政/其他）+ 单号 Input（必填校验）→ 提交 `orderShip` → 成功 message + 返回订单列表。

- [ ] **Step 3: OrderList.tsx**：找到「去发货」`navigate('/trade/ship')` 调用，补 `?orderId=${record.orderId}`。

- [ ] **Step 4: OrderDetail.tsx**：`status==='unpaid'` 时在支付按钮旁渲染「取消订单」（Popconfirm 二次确认）→ `orderCancel` → 成功后刷新详情；失败由全局错误处理提示。支付按钮与其余逻辑零改动。

- [ ] **Step 5: 验证** — Run: `cd ui && npm run build` → 构建成功。

- [ ] **Step 6: Commit**
  ```bash
  git add -A && git commit -m "feat(ui): 卖家发货页真实现，订单列表传参接通，买家可取消未支付订单"
  ```

---

## 完成定义（整体验证）

1. `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test` 全绿。
2. `cd ui && npm run build` 成功。
3. 手动冒烟（dev，mock 支付开启）：发布商品 → 另一账号下单（选地址）→ 订单详情取消 → 商品回到 onsale 且 stock 恢复；重新下单 → mock 支付 → 订单变 unshipped → 卖家发货 → 买家确认收货 → 商品变 sold。超时路径等 30 分钟或手工把 expire_at 改到过去 + 手工触发 job 验证。
4. 最终整体 code review（requesting-code-review 技能）→ finishing-a-development-branch 收尾。
