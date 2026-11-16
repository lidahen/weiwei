# 交易支付多态收口（biz_type）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `trade_pay` 增加 `biz_type` 列消除多态外键，落地 closeOrder 支付守卫与迟到支付自动退款。

**Architecture:** 写入路径显式传 bizType（trade/runner/renew），消费路径（closeOrder 守卫 / 退款反查 / 对账 join / 事件监听器）全部按 biz_type 收窄；迟到支付在 trade 监听器显式查单状态后退款（不用 rows=0 触发）。

**Tech Stack:** Java 17 + Spring Boot 3.5.4 + MyBatis XML + JUnit5/Mockito。

**Spec:** `docs/superpowers/specs/2026-11-03-pay-biz-type-design.md`

## Global Constraints

- biz_type 取值仅 `trade` / `runner` / `renew`，VARCHAR(16) NOT NULL DEFAULT 'trade'。
- DDL 脚本 `backend/sql/pay_biz_type.sql` 幂等、手工执行，**不进 CLAUDE.md 初始化序列**。
- 回填判据（顺序敏感）：order_id IS NULL → renew；`runner_task.pay_out_trade_no = trade_pay.out_trade_no` join → runner（**禁止用 runner_task.pay_order_id join**，那是业务单号不是 pay_id）；其余保持 trade。
- 迟到支付退款**必须显式查订单状态**（仅 closed/cancelled 退款），不得用 updateOrderStatus rows=0 触发（事件重发会退掉正常订单）。
- 反目标：不得破坏 runner / 租户续费现有支付链路。
- 代码格式 Spring Java Format；提交信息 `feat:`/`fix:`/`test:` 前缀。

## Review Focus

- 事件重发（订单已 unshipped）进入迟到支付分支 → 必须零退款（Task 4 有回归测试钉住）。
- markRefunded 幂等：已 refunded 的单再次 refund 不得重复翻状态（Task 3 守卫 + 测试）。
- 回填后 runner paid 单存在时商品订单必须**可**关单（守卫反例，Task 5 测试钉住）。
- 续费链路（order_id=NULL）监听器跳过逻辑不得因 bizType=null 的旧构造事件回归（Task 3 统一构造器，全测试同步更新）。
- runner 取消退款链路（RunnerTaskServiceImpl:261 附近）与新守卫共存，不双退。

---

### Task 1: DDL 与回填脚本

**Files:**
- Create: `backend/sql/pay_biz_type.sql`

**Interfaces:**
- Produces: `trade_pay.biz_type` 列 + `idx_biz_order(biz_type, order_id)` 索引，供后续任务 SQL 引用。

- [ ] **Step 1: 编写脚本**

参照 `backend/sql/refund_feature.sql` 的幂等风格（information_schema 判断列/索引是否存在再执行）。内容：

```sql
-- biz_type: trade(商品订单) / runner(互助任务) / renew(租户续费)
-- 幂等：列存在则跳过；手工执行（不进初始化序列）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_pay' AND COLUMN_NAME = 'biz_type');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE trade_pay ADD COLUMN biz_type VARCHAR(16) NOT NULL DEFAULT ''trade'' COMMENT ''业务类型:trade/runner/renew'' AFTER order_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填（顺序敏感，仅对仍为默认 trade 的行）：
UPDATE trade_pay SET biz_type = 'renew' WHERE order_id IS NULL AND biz_type = 'trade';
UPDATE trade_pay p JOIN twelve_runner_task t ON t.pay_out_trade_no = p.out_trade_no
  SET p.biz_type = 'runner' WHERE p.biz_type = 'trade';

-- 索引（幂等同列）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_pay' AND INDEX_NAME = 'idx_biz_order');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE trade_pay ADD INDEX idx_biz_order (biz_type, order_id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

- [ ] **Step 2: 提交**

```bash
git add backend/sql/pay_biz_type.sql
git commit -m "feat(payment): trade_pay 增加 biz_type 列的幂等 DDL 与回填脚本"
```

（本机执行 DDL、23 行回填逐行核对：在 Task 6 完成后统一执行并记录到设计文档 §7。）

---

### Task 2: 写入路径收窄（PayRequestDTO + insertPayOrder + 两个 Controller）

**Files:**
- Modify: `backend/twelvet-payment/twelvet-payment-api/src/main/java/com/twelvet/payment/api/dto/PayRequestDTO.java`
- Modify: `backend/twelvet-payment/twelvet-payment-server/src/main/java/com/twelvet/payment/server/mapper/PayOrderMapper.java:36-38`
- Modify: `backend/twelvet-payment/twelvet-payment-server/src/main/resources/mapper/payment/PayOrderMapper.xml:20-29,80-86`
- Modify: `backend/twelvet-payment/twelvet-payment-server/src/main/java/com/twelvet/payment/server/service/impl/PayOrderServiceImpl.java:77`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/TradePayController.java:60-67`
- Modify: `backend/twelvet-runner/twelvet-runner-server/src/main/java/com/twelvet/runner/server/controller/RunnerTaskController.java:177-186`
- Test: `backend/twelvet-payment/twelvet-payment-server/src/test/java/com/twelvet/payment/server/service/impl/PayOrderServiceImplTest.java`

**Interfaces:**
- Produces: `PayRequestDTO.getBizType()/setBizType(String)`；`insertPayOrder(orderId, payUserId, outTradeNo, channel, amount, bizType, tid)`（bizType 插在 tid 之前）。
- Consumes: 现有 createPayOrder 流程。

- [ ] **Step 1: 更新/新增失败测试（PayOrderServiceImplTest）**

现有 `verify(payOrderMapper).insertPayOrder(eq(ORDER_ID), payUserCaptor.capture(), anyString(), eq("alipay"), ...)`（:180 附近）加 bizType 参数断言：

- trade 收银台路径：captor 值 = `"trade"`（createPayOrder 对 null bizType 默认 'trade'）。
- 显式 runner：`request.setBizType("runner")` 后 captor 值 = `"runner"`。

- [ ] **Step 2: 运行确认 RED**

Run: `cd backend && mvn -pl twelvet-payment/twelvet-payment-server -am test -q`
Expected: 编译失败（insertPayOrder 签名未变则断言失败）。

- [ ] **Step 3: 实现**

- `PayRequestDTO` 加 `private String bizType;` + getter/setter（注释：业务类型 trade/runner；renew 走独立 SQL）。
- `PayOrderMapper.insertPayOrder` 加 `@Param("bizType") String bizType`（tid 前）；XML insert 列表加 `biz_type`，VALUES 加 `#{bizType}`。
- `insertRenewPayOrder` XML 的列与 VALUES 各加 `biz_type` / `'renew'`。
- `PayOrderServiceImpl.createPayOrder`：`String bizType = (request.getBizType() == null || request.getBizType().isBlank()) ? "trade" : request.getBizType();` 传入 insertPayOrder；同时更新 :76 处「trade_pay 无业务类型列」的过时注释。
- `TradePayController.createPayOrder`：`request.setBizType("trade");`
- `RunnerTaskController.createPayOrderForTask`：`payReq.setBizType("runner");`

- [ ] **Step 4: 运行确认 GREEN（含 runner/renew 既有测试回归）**

Run: `cd backend && mvn -pl twelvet-payment/twelvet-payment-server -am test -q`
Expected: 全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add -A backend/twelvet-payment backend/twelvet-trade backend/twelvet-runner
git commit -m "feat(payment): trade_pay 写入路径携带 biz_type（trade/runner/renew）"
```

---

### Task 3: PaySuccessEvent 携带 bizType + markRefunded 幂等 + refund 前置检查

**Files:**
- Modify: `backend/twelvet-payment/twelvet-payment-api/src/main/java/com/twelvet/payment/api/event/PaySuccessEvent.java`
- Modify: `backend/twelvet-payment/twelvet-payment-server/src/main/java/com/twelvet/payment/server/service/impl/PayOrderServiceImpl.java:174-233`
- Modify: `backend/twelvet-payment/twelvet-payment-server/src/main/resources/mapper/payment/PayOrderMapper.xml:46-50,66-70`
- Test: `backend/twelvet-payment/twelvet-payment-server/src/test/java/com/twelvet/payment/server/service/impl/PayOrderServiceImplTest.java`
- Test（构造器联动更新）: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/listener/PaySuccessEventListenerFlowTest.java`、`.../listener/SettlementCreateOnPayTest.java`、`.../service/impl/TradeOrderServiceImplLifecycleTest.java`

**Interfaces:**
- Produces: `new PaySuccessEvent(outTradeNo, orderId, bizType, channel, amountCents, paidAt, tradeNo, notifyData)`（bizType 插在 orderId 后，**旧 7 参构造器删除**，所有调用点同步）；`event.getBizType()`。
- Produces: `selectOrderInfoByOutTradeNo` 返回 map 额外含 `status`、`biz_type`。

- [ ] **Step 1: 失败测试（PayOrderServiceImplTest）**

- `refund_notPaid_returnsFalse`：mock selectOrderInfoByOutTradeNo 返回 `status=unpaid` 的 map → `refund(...)` 返回 false，且 markRefunded 从未被调用。
- `refund_paid_markRefundedOnce`：status=paid、mockEnabled → markRefunded 调用一次。
- `refund_alreadyRefunded_noReflip`：status=refunded → 返回 false，markRefunded 零调用。
- 既有 publishPaySuccess 相关测试：断言事件 `getBizType()` 为 selectOrderInfoByOutTradeNo 返回的 biz_type。

- [ ] **Step 2: 运行确认 RED**

Run: `cd backend && mvn -pl twelvet-payment/twelvet-payment-server -am test -q`
Expected: FAIL。

- [ ] **Step 3: 实现**

- `PaySuccessEvent` 加 `private final String bizType;`，构造器改 8 参（bizType 在 orderId 后），加 getter，删旧构造器。
- `publishPaySuccess`：从 row 读 `biz_type`（null 兜底：order_id==null → "renew"，否则 "trade"——防御 DDL 未执行环境）；构造 8 参事件。
- `markRefunded` XML：`WHERE out_trade_no = #{outTradeNo} AND status = 'paid'`。
- `selectOrderInfoByOutTradeNo` XML：SELECT 加 `status, biz_type`。
- `refund`：row 非空后先判 `row.get("status")`，非 `"paid"` 直接 `return false`（记 debug 日志）；mock 分支与网关成功分支照旧 markRefunded。
- 全局替换 4 个测试文件的 `new PaySuccessEvent(...)` 为 8 参（trade 类测试 bizType 传 `"trade"`，其余按原 orderId 是否 null 定）。

- [ ] **Step 4: 运行确认 GREEN**

Run: `cd backend && mvn -pl twelvet-payment/twelvet-payment-server,twelvet-trade/twelvet-trade-server -am test -q`
Expected: 全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-payment backend/twelvet-trade
git commit -m "feat(payment): PaySuccessEvent 携带 bizType；markRefunded 仅 paid 可翻、refund 增加状态前置"
```

---

### Task 4: trade 监听器 — bizType 门卫 + 迟到支付收口

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/listener/PaySuccessEventListener.java`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/listener/PaySuccessEventListenerFlowTest.java`

**Interfaces:**
- Consumes: Task 3 的 `event.getBizType()`；`IPayOrderService.refund(String outTradeNo, BigDecimal amount, String reason)`（payment-api 已有）；`orderMapper.selectOrderDetail(orderId, null)`（已有，返回 TradeOrder 含 status）。
- Produces: 监听器构造器新增 `IPayOrderService payOrderService` 参数（位置：funnelEventService 之后、settlementService 之前）。

- [ ] **Step 1: 失败测试（PaySuccessEventListenerFlowTest 补用例）**

helper `event(...)` 已用 Task 3 的 8 参构造器，bizType 默认 "trade"。

1. `runnerEvent_skipped`：bizType="runner" 的事件 → updateOrderStatus 零调用、refund 零调用。
2. `renewEvent_skipped`：bizType="renew"、orderId=null → 同上。
3. `latePay_orderClosed_refunds`：updateOrderStatus 返回 0；selectOrderDetail 返回 status="closed" 的订单（buyerId=1L，amountCents=10000）→ `verify(payOrderService).refund(eq("OUT123"), eq(new BigDecimal("100.00")), contains("超时关闭"))`；且 `imService.sendNotice(eq(1L), eq("order"), contains("退款"), anyString())` 被调用（经现有 notify helper）。
4. `latePay_orderCancelled_refunds`：同上但 status="cancelled"。
5. `latePay_orderUnshipped_neverRefunds`（0a 缺陷回归）：updateOrderStatus 返回 0、selectOrderDetail status="unshipped" → refund 零调用。
6. `latePay_orderNotFound_neverRefunds`：selectOrderDetail 返回 null → refund 零调用。

- [ ] **Step 2: 运行确认 RED**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -q -Dtest=PaySuccessEventListenerFlowTest`
Expected: 编译失败（构造器缺 IPayOrderService）或断言 FAIL。

- [ ] **Step 3: 实现（PaySuccessEventListener）**

- 注入 `IPayOrderService payOrderService`，构造器加参。
- `onPaySuccess` 开头：`if (orderId == null || !"trade".equals(event.getBizType())) { return; }`（注释：bizType 由 publishPaySuccess 从 trade_pay 读出；旧 order_id==null 即续费的判别被此取代）。
- else（rows==0）分支改为：
  ```java
  else {
      TradeOrder order = orderMapper.selectOrderDetail(orderId, null);
      String status = order != null ? order.getStatus() : null;
      if ("closed".equals(status) || "cancelled".equals(status)) {
          log.warn("[支付] 迟到支付：订单已 {}，自动原路退款 orderId={}, outTradeNo={}", status, orderId, event.getOutTradeNo());
          BigDecimal amount = event.getAmountCents() == null ? null
                  : new BigDecimal(event.getAmountCents()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
          boolean ok = payOrderService.refund(event.getOutTradeNo(), amount, "订单已超时关闭，自动退款");
          if (order.getBuyerId() != null) {
              notify(order.getBuyerId(), "支付时订单已关闭",
                      ok ? "您支付的款项将原路退回" : "退款处理失败，请联系客服");
          }
      }
      else {
          log.info("订单已处于已支付状态或不存在: orderId={}, outTradeNo={}", orderId, event.getOutTradeNo());
      }
  }
  ```
- 既有测试 mock 无需显式 stub selectOrderDetail（默认 null → 走 else 的 not-found 路径），断言行为不变。

- [ ] **Step 4: 运行确认 GREEN**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -q`
Expected: 全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade
git commit -m "fix(trade): 监听器按 biz_type 门卫；迟到支付显式查单后自动原路退款"
```

---

### Task 5: closeOrder 守卫 + 消费路径收窄

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/resources/mapper/trade/TradeOrderMapper.xml:171-174`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/resources/mapper/trade/TradePayLookupMapper.xml:7-21`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/resources/mapper/trade/SettlementOrderMapper.xml:141,152,162`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImplJudgeTest.java`（如该测试 mock selectPaidByOrderId，确认无需改动；XML 变更不改签名）

**Interfaces:**
- Consumes: Task 1 的 `trade_pay.biz_type` 列。
- Produces: 无新签名；XML 行为变更。

- [ ] **Step 1: 修改三处 XML**

1. `closeOrder`（§4.3 原文照抄）：
   ```xml
   <update id="closeOrder">
       UPDATE trade_order o SET o.status = 'closed', o.update_time = NOW()
       WHERE o.order_id = #{orderId} AND o.status = 'unpaid'
         AND NOT EXISTS (
           SELECT 1 FROM trade_pay p
           WHERE p.order_id = o.order_id AND p.biz_type = 'trade' AND p.status = 'paid'
         )
   </update>
   ```
   并更新 :165-170 注释，补一句守卫语义（有 paid trade 支付单不可关单；runner/renew 的 paid 单不影响）。
2. `selectPaidByOrderId` 加 `AND biz_type = 'trade'`；顶部撞号注释末尾追加一行「2026-11-03 已由 biz_type 消歧，本注释保留为历史记录」。
3. `SettlementOrderMapper.xml` 三个 join 各加 `AND p.biz_type = 'trade'`；:132 处多态列注释同步更新。

- [ ] **Step 2: 编译 + 全量回归**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -q`
Expected: 全部 PASS（对账/recon 测试回归）。

- [ ] **Step 3: Commit**

```bash
git add backend/twelvet-trade
git commit -m "fix(trade): closeOrder 支付守卫与退款反查/对账 join 按 biz_type 收窄"
```

---

### Task 6: runner 守卫与门卫

**Files:**
- Modify: `backend/twelvet-runner/twelvet-runner-server/src/main/resources/mapper/runner/RunnerTaskMapper.xml:230-234`
- Modify: `backend/twelvet-runner/twelvet-runner-server/src/main/java/com/twelvet/runner/server/listener/RunnerPaySuccessEventListener.java:35-51`

**Interfaces:**
- Consumes: Task 3 的 `event.getBizType()`。
- Produces: `markPayStatusByTaskId` SQL 行为变更（不加参数）。

- [ ] **Step 1: 修改**

1. `markPayStatusByTaskId` XML：
   ```xml
   UPDATE twelve_runner_task
   SET pay_status = #{payStatus}, update_time = NOW()
   WHERE id = #{taskId} AND pay_status != #{payStatus} AND status != 'cancelled'
   ```
   （守卫：已 cancelled 任务不得被迟到支付标回 paid；同状态重复写幂等。Controller:131 的 mock-pay 路径与 ServiceImpl:261 的退款置 refunded 不受影响。）
2. `RunnerPaySuccessEventListener.onPaySuccess`：跳过条件改为 `if (orderId == null || !"runner".equals(event.getBizType())) { return; }`；bizType 为 runner 时 `taskId` 直接用 `orderId`，保留 `selectTaskIdByOutTradeNo` 反查作防御（taskId 与反查结果不一致时以反查为准并 WARN）——实现为：
   ```java
   Long taskId = taskMapper.selectTaskIdByOutTradeNo(event.getOutTradeNo());
   if (taskId == null) {
       taskId = orderId; // biz_type=runner 时 orderId 即任务 ID
   }
   ```

- [ ] **Step 2: 回归**

Run: `cd backend && mvn -pl twelvet-runner/twelvet-runner-server -am test -q`
Expected: 全部 PASS（runner 既有测试回归）。

- [ ] **Step 3: Commit**

```bash
git add backend/twelvet-runner
git commit -m "fix(runner): 支付监听器按 biz_type 门卫；已取消任务不被迟到支付标记为已付"
```

---

### Task 7: 全量回归 + DDL 落库核对 + 设计文档 §7 留痕

- [ ] **Step 1: 全量测试**

Run: `cd backend && mvn test -q`
Expected: BUILD SUCCESS，零失败。

- [ ] **Step 2: 本机执行 DDL 并核对回填**

对本地 MySQL 执行 `backend/sql/pay_biz_type.sql`，然后：

```sql
SELECT p.pay_id, p.order_id, p.out_trade_no, p.status, p.biz_type
FROM trade_pay p ORDER BY p.pay_id;
```

逐行核对：order_id IS NULL → renew；能 join 到 runner_task（pay_out_trade_no）→ runner；其余 trade。核对结果记入设计文档 §7。

- [ ] **Step 3: 设计文档 §7 记录实施偏差**（已知候选：SettlementOrderMapper 第三处 join :162 一并收窄；其余如实记录）

- [ ] **Step 4: Commit + 合并收尾**

```bash
git add docs/superpowers/specs/2026-11-03-pay-biz-type-design.md
git commit -m "docs: biz_type 实施记录与偏差"
```

按 superpowers:finishing-a-development-branch 收尾（用户选择合并方式）。
