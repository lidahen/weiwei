# 交易支付多态收口（biz_type）— 设计文档

> 日期：2026-11-03
> 来源：`docs/superpowers/specs/2026-10-19-trade-closedloop-fix-design.md` §8.1（资金窗口）、§8.4（biz_type 硬前置 + NOT EXISTS 守卫驳回记录）
> 前置：Phase 1A/1B 已合并；`trade_pay` 现状已实地核实（2026-11-03）

## 1. 背景与问题（Why）

`trade_pay.order_id` 是**多态外键**：商品订单（trade）、互助任务（runner）、租户续费（renew，`order_id=NULL`）三类业务共用一列，无业务类型列。后果：

1. **撞号事实**：本机实测 trade_pay 17–21 号是跑腿已付单，trade_order 17–21 号是商品单，同 ID 不同物。任何「按 order_id 反查支付状态」的 SQL 都会误伤（`TradePayLookupMapper.xml` 顶部注释保留了实测记录）。
2. **closeOrder 无支付守卫**（`TradeOrderMapper.xml:171`）：§8.4 评审曾建议加 `NOT EXISTS (SELECT 1 FROM trade_pay ... status='paid')` 守卫，因多态撞号驳回——守卫会把同 ID 跑腿已付单误判为商品单已付，商品订单永远无法关单、库存永久锁定。
3. **资金窗口（§8.1）**：买家在 `expire_at` 边界支付、回调迟到，quartz 先关单（closed + 库存回补），支付成功监听器 rows=0 仅记日志——扣了钱、单已关、库存已回补转售。
4. **退款反查启发式**：`TradeRefundServiceImpl.pickPayOrder` 只能靠 `pay_user_id + 金额` 双重校验，候选不唯一转人工——因为按 order_id 查会查到跑腿单。

## 2. 目标 / 非目标

**目标：**
1. `trade_pay` 增加 `biz_type` 列，消除多态；存量数据回填。
2. `closeOrder` 落地 SQL 级支付守卫（§8.4 驳回的方案在多态消除后成立）。
3. 迟到支付收口：支付成功但订单已非 `unpaid` → 不迁移状态、自动原路退款（复用 `IPayOrderService.refund`，mock 模式已有闭环实现）、记日志。
4. 退款反查按 `biz_type='trade'` 收窄，删除撞号启发式注释里的被迫妥协。
5. 监听器判别按 `biz_type` 断言。

**非目标：**
- 真实支付渠道接入（无资质，mock 支付保持现状）。
- mock 支付流程本身不动。

**反目标（做错即失败）：**
- 不得破坏 runner/续费现有支付链路（回归测试必须覆盖）。
- 不得让守卫误伤：`biz_type` 判断必须精确，回填错误的存量数据比没有守卫更危险。

## 3. 现状核实（2026-11-03 实地）

| 调用方 | 写入路径 | order_id 语义 |
|---|---|---|
| 商品订单 | `TradePayController.createPayOrder` → `PayRequestDTO.orderId` → `PayOrderServiceImpl.createPayOrder` → `insertPayOrder` | trade_order.order_id |
| 互助任务 | `RunnerTaskController.createPayOrderForTask` → 同上 | runner_task.id（撞号源） |
| 租户续费 | `TenantRenewPayServiceImpl.createOrder` → `insertRenewPayOrder`（独立 SQL） | NULL |

消费方：
- `closeOrder`（TradeOrderMapper.xml:171）：`UPDATE trade_order SET status='closed' WHERE order_id=? AND status='unpaid'`，无支付守卫。
- `TradePayLookupMapper.selectPaidByOrderId`：退款反查，无 biz_type 过滤。
- `PaySuccessEventListener`（trade）：`orderId==null` 跳过（续费）；runner 监听器同样按 null + out_trade_no 反查。
- 存量数据：本机 trade_pay 共 23 行（含撞号区 17–21），回填可逐行核对。

## 4. 设计

### 4.1 DDL 与回填（`backend/sql/pay_biz_type.sql`，幂等，手工执行）

```sql
-- biz_type: trade(商品订单) / runner(互助任务) / renew(租户续费)
ALTER TABLE trade_pay ADD COLUMN biz_type VARCHAR(16) NOT NULL DEFAULT 'trade' COMMENT '业务类型:trade/runner/renew' — guarded（information_schema 判断后 ADD）
-- 回填（顺序敏感）：
UPDATE trade_pay SET biz_type='renew'  WHERE order_id IS NULL AND biz_type='trade';
UPDATE trade_pay p JOIN runner_task t ON t.pay_out_trade_no = p.out_trade_no SET p.biz_type='runner' WHERE p.biz_type='trade';
-- 其余保持 'trade'（DEFAULT 已覆盖）
-- 索引：idx_biz_order (biz_type, order_id)
```

回填判据（builder check 后修正）：
1. `order_id IS NULL` → `renew`（写入路径唯一）。
2. **只用** `runner_task.pay_out_trade_no = trade_pay.out_trade_no` join → `runner`。注意 `runner_task.pay_order_id` 存的是业务单号（`bindPayInfoQuietly` 写入 `payResp.get("orderId")` = task id），**不是** `trade_pay.pay_id`，不能用做 join——原稿此判据是恒假 join，已废除。
3. 其余 → `trade`。
回填后**逐行人工核对**（本机仅 23 行），核对结果记入实施记录。

### 4.2 写入路径收窄

- `PayRequestDTO` 加 `bizType` 字段（String，"trade"/"runner"）。
- `PayOrderServiceImpl.createPayOrder`：`insertPayOrder` SQL 加 `biz_type` 列；`bizType` 为空时默认 `'trade'`（防御性，调用方都应显式传）。
- `TradePayController.createPayOrder`：`request.setBizType("trade")`。
- `RunnerTaskController.createPayOrderForTask`：`request.setBizType("runner")`。
- `insertRenewPayOrder` SQL 写死 `biz_type='renew'`。

### 4.3 closeOrder 守卫（§8.4 驳回方案的落地）

```sql
UPDATE trade_order o SET o.status='closed', o.update_time=NOW()
WHERE o.order_id=#{orderId} AND o.status='unpaid'
  AND NOT EXISTS (
    SELECT 1 FROM trade_pay p
    WHERE p.order_id=o.order_id AND p.biz_type='trade' AND p.status='paid'
  )
```

守卫语义：已关单的自然满足（closeOrder 前置已保证）；有已付 trade 支付单的订单不可关单。runner/renew 的 paid 单不再影响商品订单关单。

### 4.4 迟到支付收口（§8.1 终修）

**触发条件（builder check 修正，原稿有退掉正常订单的缺陷）**：`updateOrderStatus` rows=0 有两种语义——订单已关/已取消（该退）与**事件重复投递、订单已 unshipped**（绝不能退，`handlePaySuccess` rows=0 会主动重发事件自愈，重发路径必然二次进入本监听器）。因此**不能用 rows=0 触发退款**，必须显式查状态：

`PaySuccessEventListener.onPaySuccess`（trade 监听器）rows=0 时：
1. `selectOrderDetail(orderId, null)` 查订单状态，**仅 status ∈ {closed, cancelled} 才退款**；unshipped/shipped/done 等状态说明是事件重发，只记日志返回。
2. 退款：按 `event.getOutTradeNo()` 调 `IPayOrderService.refund(outTradeNo, event 金额, "订单已超时关闭，自动退款")`。mock 模式 `markRefunded` 已有；真实渠道走 `gateway.refund`。
3. 记 WARN 日志 + 通过 IM 通知买家「支付时订单已关闭，款项将原路退回」。
4. 幂等：`markRefunded` 现无状态守卫（builder 核实 `WHERE out_trade_no=?` 裸翻），补为 `WHERE out_trade_no=? AND status='paid'`；refund 入口先查 `selectOrderInfoByOutTradeNo`，非 paid 直接返回 false——「网关退成功但本地已 refunded」的补偿场景由该入口检查拦截，不用 `status != 'refunded'` 这种宽守卫。

**runner 侧（builder 核实存在同窗口）**：`RunnerQuartzTask` 每 5 分钟扫超时未接单取消任务，`markPayStatusByTaskId` 无状态前置——迟到支付会把已取消任务标回 paid。本次 runner 监听器**加状态守卫**（`markPayStatusByTaskId` SQL 加 `AND pay_status != 'paid'` 且任务未取消前置，实施时按 runner 状态机定精确条件）；**退款不加**——runner 取消退款已有独立链路（Controller 取消时调 refund），迟到支付的 runner 单由该链路在取消时退，监听器再退会双退。

### 4.5 消费路径收窄

- `selectPaidByOrderId` 加 `AND biz_type='trade'`；`TradePayLookupMapper.xml` 顶部撞号注释更新为「已由 biz_type 消歧，保留历史记录」。
- `TradeRefundServiceImpl.pickPayOrder`：biz_type 收窄后 `pay_user_id+金额` 启发式保留为防御（不改），注释更新。
- 两个监听器的 `orderId==null` 判别：trade 监听器改为 `biz_type != 'trade'` 跳过；runner 同理。需要 `PaySuccessEvent` 携带 `bizType`（`publishPaySuccess` 时从 trade_pay 读出）。
- **Phase 1A 对账 join 收窄（builder check 裁决进范围，原稿 §2 表述自相矛盾）**：`SettlementOrderMapper.xml:141/152` 两个 `JOIN trade_pay p ON p.order_id = ... AND p.pay_user_id = ... AND p.tenant_id = ...` 各加 `AND p.biz_type='trade'`；对账相关测试回归。

### 5.1 DDL 脚本登记（builder check 裁决）

`backend/sql/pay_biz_type.sql` 放目录、文档注明手工执行，**不进 CLAUDE.md 初始化序列**——此前 `report_feature.sql` 同类决策保持一致，序列整理留待统一处理。

## 5. 测试策略（TDD）

1. DDL 契约测试：`biz_type` 列存在、默认值、索引。
2. `createPayOrder` 写入 biz_type（trade/runner 两个路径）。
3. `closeOrder` 守卫：有 paid trade 单不可关；有 paid runner 单**可**关（关键反例）；无支付单可关。
4. 迟到支付收口：订单 closed/cancelled → 调 refund、通知买家退款；**订单 unshipped（事件重发）→ 绝不退款**（0a 缺陷回归用例）；runner 已取消任务 → 不标记 paid。
5. `markRefunded` 幂等：仅 paid 可翻 refunded，重复调用无害。
6. `selectPaidByOrderId` 只返回 trade 单；对账 join 收窄后 recon 测试回归。
7. 回归：runner 支付链路、续费链路现有测试全绿。

## 6. 风险与权衡

- **回填错误风险**：判别依据按可靠性排序，23 行逐行核对兜底；新环境（空表）无此风险。
- **refund 依赖 markRefunded 幂等**：核实其 WHERE 条件；不幂等则补状态守卫。
- **监听器事件字段**：`PaySuccessEvent` 加 `bizType` 是 API 变更，两个监听器 + payment 模块三处联动，改动面小但必须一起改。
- **守卫与入口守卫（validateOrderPayable）的关系**：入口守卫防新增流量，SQL 守卫防存量窗口，两者互补，都保留。

## 7. 实施后补充（2026-11-03 实施完成记录）

**提交序列**（master 直做，e251a29 → 41e1f12 → docs）：
1. `e251a29` DDL 脚本 `backend/sql/pay_biz_type.sql`（幂等，已在本机 twelvet-fast 执行）
2. `89182c9` 写入路径携带 biz_type（PayRequestDTO.bizType + insertPayOrder 7→8 参 + 两 Controller 显式传值 + insertRenewPayOrder 写死 'renew'）
3. `97d1c1b` PaySuccessEvent 携带 bizType（8 参构造器，旧 7 参删除）；markRefunded 补 `AND status='paid'`；refund 入口非 paid 直接拒绝；selectOrderInfoByOutTradeNo 增加 status/biz_type 列
4. `5357034` trade 监听器 biz_type 门卫 + 迟到支付收口（rows=0 时显式 selectOrderDetail，仅 closed/cancelled 退款）
5. `74543dc` closeOrder NOT EXISTS 守卫 + selectPaidByOrderId/对账 join 收窄
6. `41e1f12` runner 监听器门卫 + markPayStatusByTaskId 加 `pay_status != ? AND status != 'cancelled'` 守卫

**实施偏差（均为 builder check 后的正常落地差异）**：
- `SettlementOrderMapper.xml` 的对账 join 实为 **3 处**（:141/:152/:162，设计文档只列 2 处），已一并加 `AND p.biz_type='trade'`。
- 迟到支付通知断言落点：标题=`支付时订单已关闭`、内容含「退回」（brief 写的 contains("退款") 不精确，按实现修正测试）。
- `TradeOrderServiceImplLifecycleTest.payListener_lateCallbackAfterCancel_ignored` 断言旧契约（迟到回调零副作用），与 §4.4 新契约冲突 → 改写为 `..._refunds`（closed → refund + 通知），spec 为权威。
- trade 监听器构造器插入 `IPayOrderService`（funnelEventService 之后、settlementService 之前）；两个直接实例化监听器的测试文件联动更新。

**DDL 落库与回填核对（2026-11-03 本机 twelvet-fast）**：
- 执行前 trade_pay 共 23 行（pay_id 1–6, 15–21, 23–32，非连续）。执行后逐行核对：**全部 23 行 biz_type='trade'**。
- 偏差说明：设计文档 §1 记录的「17–21 号是跑腿已付单」撞号区，当前库中 pay_id 17–21 均为 **unpaid 的商品订单支付单**（order_id 17/18/19 对应 trade_order），且全表无 `order_id IS NULL`（renew）行、无 `pay_out_trade_no` 能 join 上 `twelve_runner_task` 的行——即本机当前**不存在 runner/renew 存量支付单**，回填 UPDATE 各命中 0 行。撞号记录应为更早时点（10-19 设计期）的实测，期间数据已被清理/重置。判据本身（NULL→renew、pay_out_trade_no join→runner）保持不变，新环境与真实数据下依然成立。
- `idx_biz_order (biz_type, order_id)` 已建。

**验证**：全量 `mvn test` BUILD SUCCESS，746 用例 0 失败（trade 228 / payment 13 / runner 及其余模块全绿）。人工冒烟（mock 支付全链路）未做，与 Phase 1B 同状态遗留。

