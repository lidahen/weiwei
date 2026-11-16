# 退款售后闭环 + 三处占位补齐 — 设计文档

- 日期：2026-10-30
- 状态：待批准
- 前置：`docs/superpowers/specs/2026-10-19-trade-closedloop-fix-design.md`（交易闭环第 1 期）
- 约束：**支付保持 mock**，不做真实资金渠道接入

---

## 1. 意图与为什么

交易闭环第 1 期跑通了「发布 → 下单 → 支付 → 发货 → 收货 → 评价」，但**售后是断的**：

- 买家收到货发现与描述不符，前端只有两个 `Empty` 占位页（`RefundApply.tsx` / `RefundDetail.tsx`），后端**一个退款 Controller/Service 都没有**
- `trade_refund` / `trade_refund_log` 两张表结构完整（含退货物流字段），0 行数据，0 处代码引用
- 客服纠纷（`trade_dispute`）已有接单/裁决能力，但 `judgeDispute` **只改状态、不真的退钱**——裁决完买家拿不到退款
- 运营「资金对账」页 `Finance.tsx` 是占位，但后端 `/finance/overview` 早已实现
- 个人设置里 3 个隐私开关全是「开发中」提示，而 `user_privacy` 表三个字段都在，**没有任何写入点**

本期把这些补齐，让售后真正能走完，并且让纠纷裁决第一次真正落地。

---

## 2. 目标

1. **买家能申请退款**：从订单详情发起，选类型/原因/金额/证据，进入真实的状态流转
2. **卖家能审**：同意 / 拒绝，超时自动升级平台介入
3. **退货有物流**：退货退款的买家填单号，卖家确认收货后退款
4. **平台能介入**：客服裁决结果**真正触发退款**（补上现有 `judgeDispute` 的空洞）
5. **资金对账页可用**：展示统计 + 最近流水
6. **隐私设置可保存**：三个开关真实读写 `user_privacy`
7. **绑定微信/支付宝**：改为「联系客服绑定」的过渡文案（无开放平台资质）

## 3. 非目标

- **真实支付渠道退款**：`PayOrderService.refund()` 在 `mock=true` 下直接置 `refunded`，本期只走这条路径。微信网关的 `not implemented` 保持现状
- **`trade_pay.biz_type` 列**：设计文档 §8.4 的硬前置（解多态外键反查），本期**不做**——因为退款定位支付单用的是 `order_id + status='paid'`，且必须显式校验 `out_trade_no` 非空（见 §6.2 风险）。该前置留给真实支付上线
- 支付成功回调的「订单已关闭则转退款」守卫（§8.1）：依赖真实支付，本期不做
- 退款金额的部分退款按商品拆分（只支持整单部分退，不支持按行退）
- 售后时间窗口的配置化（先硬编码 7 天，留配置项）

## 4. 反目标（做到这些算失败）

- 让退款把 `unpaid` / `closed` 订单也退掉（资损）
- 卖家或买家能重复申请、重复退款（同一订单并发多单）
- 裁决后状态改了但钱没退（就是现在 `judgeDispute` 的毛病）
- 退款成功后订单还能继续流转（发货/确认收货）
- 破坏零侵入租户回滚（所有 SQL 保持 `<if test="tenantId != null">`）

---

## 5. 状态机设计（核心）

`trade_refund.status` 的 DDL 注释已经写好了完整枚举，本期**照它实现**，不新增状态：

```
                    ┌─────────────── 卖家拒绝 ───────────────┐
                    ▼                                       │
pending_seller ──卖家同意──> seller_agreed ──┬─(仅退款)──────┤
     │                                      │               │
     │                                      └─(退货退款)─> buyer_returned
     │                                                      │
     │                                             买家填退货单号
     │                                                      ▼
     │                                          卖家确认收货 → refunding
     │                                                      │
     │                                                      ▼
     └──48h 超时──> mediation ──客服裁决──> refunding ──> refund_success
                        │
                        └──裁决驳回──> closed
                                                              
seller_rejected ──买家申请介入──> mediation
```

**状态语义**

| 状态 | 含义 | 谁能推进 | 超时出路 |
|---|---|---|---|
| `pending_seller` | 待卖家处理 | 卖家（同意/拒绝）、定时任务（48h） | 48h → `mediation` |
| `seller_agreed` | 卖家同意，待买家寄回（仅退货退款） | 买家（填单号 / 撤销） | **7 天 → `closed`**（视为买家放弃，订单恢复原状态） |
| `buyer_returned` | 买家已寄回，待卖家确认收货 | 卖家（确认收货） | **7 天 → `mediation`**（卖家不确认，平台介入） |
| `refunding` | 退款执行中（人工处理中） | 运营（`resolve` 接口） | 无自动超时，运营手工收口 |
| `refund_success` | 退款成功（终态） | — | — |
| `seller_rejected` | 卖家拒绝 | 买家（申请介入 / 撤销） | 买家不操作则停留 |
| `mediation` | 平台介入中 | 客服（裁决） | — |
| `closed` | 已关闭（终态，裁决驳回 / 买家撤销 / 退货超时） | — | — |

**仅退款类型跳过 `buyer_returned`**：`seller_agreed` 直接 → `refunding`。

**每个非终态都有出路**（审阅发现 §5 原先有两个状态无出口，订单会被永久卡死）：
`pending_seller`→超时升级、`seller_agreed`→超时关闭、`buyer_returned`→超时升级、
`refunding`→运营手工收口、`seller_rejected`→买家可介入或撤销。**不存在「只能等」的状态。**

### 5.1 订单状态冻结（关键：堵住「钱货两失」）

申请退款成功的那一刻，**订单置 `refunding`**：

```
trade_order.status:  unshipped/shipped/done ──申请退款──> refunding
                     refunding ──退款成功──> refunded
                     refunding ──退款关闭/卖家拒绝──> 恢复申请前的原状态
```

- `trade_order` 的 DDL 注释与 `TradeOrder.java` 已声明 `refunding`/`refunded`，
  前端 `OrderList.tsx` / `OrderDetail.tsx` **早已渲染这两个状态**，只是后端从未写入
- `shipOrder` 只允许 `status IN ('unshipped','paid')`、`confirmReceipt` 只允许 `shipped`，
  订单置 `refunding` 后这两个动作自然失效，**不需要额外加守卫**
- 恢复原状态需要记住申请前的值 → 新增 `trade_refund.prev_order_status varchar(20)`
  （这是本期**第二个**结构变更，见 §6.1）

> 若不动订单状态，卖家可以在退款审核期间照常发货、买家照常确认收货，
> 结果就是「货拿走 + 钱退回」——这是反目标里明确要避免的资损。

### 5.2 退款成功后的订单与商品

| 原订单状态 | 订单去向 | 商品去向 | 库存 |
|---|---|---|---|
| `unshipped`（未发货退款） | `refunded` | **恢复 `onsale`** | `restoreStock` |
| `shipped` / `done`（已发货退款） | `refunded` | **置 `offshelf`**（卖家可自行重新上架） | 不回补 |

- 未发货退款：货还在卖家手里，回补库存并恢复售卖是正确的
- 已发货退款：货在买家手里，回补会造虚假库存；但商品此时停在 `locked`
  （`deductStock` 置的，`markSold` 从未执行），`locked` 状态不可见、不可编辑、不可下架，
  只能删除——所以**必须置为 `offshelf`**，让卖家能看见并自行决定是否重新发布

---

## 6. 数据模型

### 6.1 复用现有表，仅两处结构变更

`trade_refund` 现有字段：

| 字段 | 用途 |
|---|---|
| `type` | `refund_only` / `return_refund` |
| `reason_code` | 原因编码（见 §6.3） |
| `amount` | 申请退款金额（≤ 订单实付） |
| `description` / `evidence_images` | 买家说明与证据（≤3 张，复用举报的证据图约束） |
| `status` | §5 状态机 |
| `return_company` / `return_no` | 退货物流 |
| `return_address_snapshot` | 卖家同意时快照的退货地址 |

**⚠️ 该表没有 `handle_note` / `remark` / 拒绝理由 / 裁决理由列**（审阅发现原稿此处引用了一个不存在的列）。
所有备注、拒绝理由、裁决理由、人工处理说明**一律写 `trade_refund_log.content`**，不在 `trade_refund` 上加备注列。

`trade_refund_log` 记录每次状态流转（`action` + `content` + `operator_id`），用于详情页时间线。

**`action` 词表**（前端时间线与测试都依赖，必须固定）：

| action | 触发 | 写入者 |
|---|---|---|
| `apply` | 买家提交申请 | 买家 |
| `seller_agree` | 卖家同意 | 卖家 |
| `seller_reject` | 卖家拒绝（`content` = 理由） | 卖家 |
| `buyer_return` | 买家填退货物流 | 买家 |
| `seller_confirm` | 卖家确认收货 | 卖家 |
| `buyer_cancel` | 买家撤销 | 买家 |
| `mediate` | 买家申请平台介入 / 系统超时升级 | 买家 / 系统 |
| `judge` | 客服裁决（`content` = 裁决理由） | 客服 |
| `refund_ok` | 退款执行成功 | 系统 |
| `manual_required` | 支付单缺失/网关失败，转人工 | 系统 |
| `manual_resolved` | 运营手工收口 | 运营 |
| `timeout_close` | 退货超时自动关闭 | 系统 |

**两处结构变更**：

**① 进行中唯一（生成列 + 唯一索引）**：`uk_order_active (order_id, status)` 不够（status 会变），
改为**业务层保证 + 唯一索引兜底**：

```sql
-- 同一订单同时只能有一条「进行中」的退款单
-- MySQL 不支持部分索引，用生成列实现
ALTER TABLE trade_refund
  ADD COLUMN active_flag tinyint GENERATED ALWAYS AS (
    CASE WHEN status IN ('pending_seller','seller_agreed','buyer_returned','refunding','mediation')
         THEN 1 ELSE NULL END
  ) STORED,
  ADD UNIQUE KEY uk_order_active (order_id, active_flag);
```

用生成列让「进行中的退款单」唯一，防止并发重复申请。终态（`refund_success`/`seller_rejected`/`closed`）`active_flag` 为 NULL，MySQL 唯一索引允许多个 NULL，所以同一订单可以有多条历史退款单。表当前 0 行，ALTER 的拷贝开销可忽略。

> **实现注意（审阅发现的坑）**：MySQL **不允许**生成列出现在 `INSERT` 的列清单里。
> 新增的 `TradeRefund` 实体与 Mapper 的 `insert` 必须**不包含** `active_flag`；
> `SELECT *` 自动映射回 POJO 是安全的。另外该唯一索引的保护会被
> `INSERT ... ON DUPLICATE KEY UPDATE` 绕过——本功能**禁止使用该写法**。

**② 记住申请前的订单状态**：

```sql
ALTER TABLE trade_refund
  ADD COLUMN prev_order_status varchar(20) DEFAULT NULL
    COMMENT '申请退款前的订单状态(unshipped/shipped/done)，退款关闭时用于恢复';
```

退款单关闭（卖家拒绝 / 买家撤销 / 退货超时）时，用它把订单状态恢复回去。

### 6.2 退款执行与支付单定位（**本设计最高风险点**）

`trade_pay.order_id` 是**多态外键**：商品订单、跑腿任务、租户续费共用这一列，续费时 `order_id=NULL`。

**撞号已实测证实，不是理论风险**：

```
trade_pay.order_id 17–21  →  跑腿任务支付（pay_amount 1.50 / 2.00 / 4.00，pay_status=paid）
trade_order.order_id 17–21 →  商品订单（total_amount 2499 / 507 / 507 / 499 / 1511）
```

`twelve_runner_task` 的 17–21 同样存在且 `pay_status='paid'`。**只按 `order_id` 反查支付单，
会把跑腿任务的支付单退掉。**（原设计稿的「再校验买家归属」是无效防线——那校验的是
退款单↔订单的归属，不是支付单↔订单的归属。）

**正确做法：双重定位 + 金额校验**

```sql
SELECT pay_id, out_trade_no, pay_amount, pay_user_id
FROM trade_pay
WHERE order_id = #{orderId}
  AND status = 'paid'
  AND out_trade_no IS NOT NULL
  -- 关键：用支付单上的付款人区分业务类型。
  -- 商品订单的支付单 pay_user_id = 买家本人；跑腿任务的 pay_user_id 是任务发布人，
  -- 与商品订单买家是不同的人（即使同为 101，也靠金额校验兜底）。
  AND pay_user_id = #{buyerId}
ORDER BY pay_id DESC
LIMIT 1
```

执行前**必须再校验金额**：

```java
if (payAmount.compareTo(refundAmount) < 0) {
    // 支付单金额小于退款金额 → 拒绝执行，转人工
    // mock 模式下 PayOrderService.refund 只改状态、不做任何金额检查，
    // 少了这道校验，超额退款会静默「成功」
}
```

**支付单缺失（含 3 笔历史 `out_trade_no` 为 NULL 的已付订单）的处理**：

- 不报 500，退款单置 `refunding`，**在 `trade_refund_log` 写一条 `action='manual_required'`**
  的记录说明原因（`trade_refund` 表**没有** `handle_note` / `remark` 列，见 §6.1 的字段清单——
  所有备注一律写 `trade_refund_log.content`）
- 给买家与运营发站内通知
- 运营通过 `POST /admin/marketplace/refund/resolve` 手工收口

> **为什么不加 `trade_pay.biz_type`**：本期支付仍是 mock，真正需要 `biz_type` 的是
> 「关单前校验支付状态」那个 SQL 级守卫（§8.4 硬前置），留给真实支付上线。
> 本设计用 `pay_user_id` + 金额校验双重防线，**并在代码注释里写明撞号事实**，
> 防止后人照抄 `order_id` 单条件反查。

> **`pay_user_id` 的局限**：本机库 seed 数据里 `pay_user_id` 大量为 NULL（列是后加的），
> 因此实际匹配规则为：`pay_user_id = buyerId` 优先；若全部候选的 `pay_user_id` 均为 NULL，
> 则**要求候选唯一**（只有一条 `paid` 且 `out_trade_no` 非空的记录）才执行，多条候选一律转人工。
> 宁可不退，不可错退。

### 6.3 退款原因编码

与举报原因同一套做法（编码 + 中文标签），但退款原因是独立白名单：

| 编码 | 标签 | 适用 |
|---|---|---|
| `not_as_described` | 与描述不符 | 全部 |
| `damaged` | 商品损坏 | 全部 |
| `wrong_item` | 发错货 | 全部 |
| `not_received` | 未收到货 | 仅退款 |
| `no_longer_needed` | 不想要了 | 未发货时 |
| `other` | 其他 | 全部 |

---

## 7. API 设计

### 7.1 C 端（买家）

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/trade/refund/apply` | 申请退款 `{orderId, type, reasonCode, amount, description, evidenceImages}` |
| `GET` | `/trade/refund/detail/{refundId}` | 退款详情（含时间线） |
| `GET` | `/trade/refund/page` | 我的退款列表 |
| `POST` | `/trade/refund/cancel` | 撤销（`pending_seller` / `seller_agreed` / `seller_rejected`） |
| `POST` | `/trade/refund/return` | 填退货物流 `{refundId, company, returnNo}` |
| `POST` | `/trade/refund/mediate` | 申请平台介入（`seller_rejected` 时） |
| `GET` | `/trade/refund/by-order/{orderId}` | **按订单查**：返回该订单的退款单摘要 + 是否可申请（`canApply`/`reason`/`refundId`） |
| `GET` | `/trade/refund/reasons` | **原因白名单**（`{code,label,types[]}`），前端不再硬编码 |

### 7.2 卖家端

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/trade/refund/seller/page` | 待我处理的退款 |
| `POST` | `/trade/refund/seller/agree` | 同意（退货退款时快照退货地址） |
| `POST` | `/trade/refund/seller/reject` | 拒绝（需理由） |
| `POST` | `/trade/refund/seller/confirm-receipt` | 确认收到退货 → 触发退款 |

### 7.3 运营/客服端

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/admin/marketplace/refund/page` | 退款列表（按状态筛选） |
| `GET` | `/admin/marketplace/refund/detail/{refundId}` | **详情 + 时间线**（C 端 detail 校验买卖家归属，运营用不了，必须单独开） |
| `POST` | `/admin/marketplace/refund/judge` | 裁决 `{refundId, decision(buyer/seller/partial), amount, reason}` |
| `POST` | `/admin/marketplace/refund/resolve` | **手工收口 `refunding`**：`{refundId, result(success/fail), note}` |

### 7.4 隐私设置

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/user/privacy` | 读三个开关（无行时返回默认全开） |
| `POST` | `/user/privacy` | 保存 `{allowMobileSearch, allowFeedByStranger, enableNearbyRecommend}` |

### 7.5 资金对账

`GET /admin/marketplace/finance/overview` **已存在**，无需新增，只做前端。

**但「最近流水」表结构性地永远为空**（审阅发现）：`trade_pay_flow` 表**全仓零写入点**，
只有 `selectRecentPayFlows` 在读它。前端接上去只会得到一个永久空表。

**补两个写入点**（表结构 `flow_id/order_id/type/amount/channel/third_trans_no/create_time` 完全够用）：

| 时机 | 写入 |
|---|---|
| 支付成功（`PaySuccessEventListener` 更新订单为 `unshipped` 之后） | `type='pay'`, `amount=pay_amount`, `channel=pay_channel`, `third_trans_no=out_trade_no` |
| 退款成功（`executeRefund` 成功分支） | `type='refund'`, `amount=退款金额`, `third_trans_no=out_trade_no` |

写入失败**不得回滚主流程**：与举报模块的 `afterCommit` 通知同一模式，包 try/catch + `log.warn`。

### 7.6 权限

- C 端：`StpUtil` 仅校验登录 + 服务内校验归属（买家/卖家身份）
- 运营端：`@SaCheckPermission("marketplace:admin:refund")`（新增菜单权限）
- 隐私：`StpUtil` 登录即可，只能读写自己

---

## 8. 关键业务规则

### 8.1 可申请闸门

| 订单状态 | 允许类型 | 条件 |
|---|---|---|
| `unshipped` | 仅退款 | 已付款 |
| `shipped` | 仅退款 / 退货退款 | 已付款 |
| `done` | 仅退款 / 退货退款 | **完成时间在 7 天内** |
| `paid`（存量野状态） | 仅退款 | 已付款（`shipOrder` 仍接受它，闸门需兼容） |
| 其它 | ❌ | — |

**`done` 的 7 天窗口用哪个时间戳**（审阅发现 `done_at` 可为 NULL）：

```
基准 = done_at，为 NULL 时回退 update_time（存量数据实际有值），仍为 NULL 则拒绝
```

本机库实测 `trade_order` 两条 `done` 记录 `done_at` 都有值，但历史数据不保证。
**实现时不得直接 `order.getDoneAt().plusDays(7)`**，否则 NPE。

额外校验：订单归属当前买家、无进行中的退款单（唯一索引 + 友好提示）。

### 8.2 金额

默认 `order.total_amount`（商品 + 运费）。允许买家改小（部分退款），**上限校验在 SQL 与服务两层**：

```sql
-- 申请时
amount > 0 AND amount <= order.total_amount
```

### 8.3 超时任务（三种超时，一个 Quartz 任务）

`trade.refund.scan` 定时任务（建议每 30 分钟）扫描三类超时：

| 场景 | 条件 | 动作 |
|---|---|---|
| 卖家未处理 | `status='pending_seller'` 且 `create_time < NOW() - 48h` | → `mediation` |
| 买家未寄回 | `status='seller_agreed'` 且 `create_time < NOW() - 7d` | → `closed`，订单恢复 `prev_order_status` |
| 卖家未确认收货 | `status='buyer_returned'` 且 `update_time < NOW() - 7d` | → `mediation` |

> **时间戳选择（审阅发现的坑）**：`seller_agreed` 的 7 天**必须用 `create_time`**，
> 不能用 `update_time`——`update_time` 每次流转都会变，用它会重置计时，
> 导致「拒绝→介入」的退款单永远不被扫描。`buyer_returned` 用 `update_time` 是正确的
> （填单号那一刻就是它变的）。

**`sys_job` 注册**（必须随迁移 SQL 一起交付，与既有 `tradeTimeout.*` 同一模式，`status='1'` 默认暂停）：

```sql
INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression, status, ...)
VALUES ('退款超时扫描', 'system', 'tradeRefundScan.scan', '0 0/30 * * * ?', '1', ...);
```

**租户上下文**：Quartz 线程无 `TenantContext`，任务内逐租户 `TenantContext.set/clear`（与 `ProductAiModerationTask` 同一惯例，见该类的注释）。

### 8.4 退款执行（唯一入口）

所有路径（卖家确认收货、客服裁决、仅退款同意）最终都调同一个内部方法：

```java
private void executeRefund(TradeRefund refund) {
    // 1. 状态守卫：只有 seller_agreed(仅退款) / buyer_returned(已确认收货) / mediation(裁决通过)
    //    且当前状态可推进时才执行，SQL 带 WHERE status = ?
    // 2. 定位支付单（§6.2）
    // 3. 无支付单 → 置 refunding + 标注人工处理 + 通知，不抛异常
    // 4. 有支付单 → payOrderService.refund(...)
    //    成功 → refund_success + 订单状态联动
    //    失败 → refunding + 标注 + 通知
}
```

**订单状态联动**（退款成功后）：
- 订单置 `closed`（真实电商：退款成功订单关闭）
- 商品回补库存？**不回补**——退款时商品已发货，回补会造成「已卖出又上架」的虚假库存。仅当 `unshipped`（未发货退款）才回补 + 商品恢复 `onsale`

### 8.5 纠纷裁决联动（补现有空洞）

**现状核实（审阅发现，原稿基于错误前提）**：

- `trade_dispute.refund_id` 列存在，但**全仓零写入点**，seed 数据该列为 NULL
- **`trade_dispute` 表全仓没有任何 INSERT**——没有创建纠纷的 API，`CsDisputeMapper` 只有 select / claim / judge
- 所以「关联 refund_id 存在则调 executeRefund」对着现有数据是死代码

**改造方案**：**不依赖 `refund_id`，按 `order_id` 反查进行中的退款单**

```java
// judgeDispute 内，裁决通过时
// 1. 用 order_id 找进行中的退款单（status='mediation'），而不是 refund_id
TradeRefund refund = refundMapper.selectActiveByOrderId(dispute.getOrderId());
// 2. 找到 → 调 executeRefund(refund, judgeAmount)
//    找不到 → 记录日志，不阻断裁决（可能是不走退款流程的纠纷）
```

`judgeDispute` 同时**补状态守卫**（现有 SQL 只有 `WHERE dispute_id = ?`，已关闭的纠纷可被重复裁决）：

```sql
UPDATE trade_dispute SET ... WHERE dispute_id = #{disputeId} AND status <> 'closed'
```

**两个裁决入口的职责划分**（审阅发现的歧义，必须明确）：

| 入口 | 权限 | 用途 |
|---|---|---|
| `POST /admin/marketplace/refund/judge` | `marketplace:admin:refund` | 运营在**退款管理页**直接裁决 `mediation` 状态的退款单 |
| `POST /admin/marketplace/dispute/{id}/judge` | `marketplace:cs:disputes` | 客服在**纠纷工单页**裁决，裁决通过时联动退款 |

两者最终都调同一个 `executeRefund`。`decision` 取值统一为 **`buyer` / `seller` / `partial`**（沿用 `trade_dispute` 的枚举）：
- `buyer` → 全额退
- `partial` → 按 `judgeAmount` 退（注意：**退款单关闭但订单也置 `refunded`**，部分退款不拆订单）
- `seller` → 退款单 `closed`，订单恢复 `prev_order_status`

> 客服角色若没有 `marketplace:admin:*` 权限，走纠纷页入口即可，不需要新开权限。

---

## 9. 前端设计

### 9.1 新增/改写页面

| 页面 | 动作 |
|---|---|
| `marketplace/RefundApply.tsx` | 从 `Empty` 改为申请表单（订单摘要 + 类型 + 原因 + 金额 + 说明 + 证据图） |
| `marketplace/RefundDetail.tsx` | 从 `Empty` 改为详情 + 状态时间线 + 操作按钮（按状态/角色显示） |
| `marketplace/OrderDetail.tsx` | 增加「申请退款」入口（**读 `/trade/refund/by-order/{orderId}` 的 `canApply`，不在前端重复实现闸门逻辑**） |
| `marketplace/RefundList.tsx` | **新增**：我的退款列表（买家） |
| `marketplace/admin/Finance.tsx` | 从 `Empty` 改为统计卡 + 最近流水表 |
| `marketplace/admin/Refund.tsx` | **新增**：运营退款处理页（含 `refunding` 手工收口） |
| `marketplace/UserSettings.tsx` | 3 个开关接真实接口；绑定微信/支付宝改「联系客服绑定」 |
| `services/marketplace/api.ts` | 补齐退款全部接口函数 |

**闸门逻辑一律由后端返回**：`/trade/refund/by-order/{orderId}` 返回 `{canApply, denyReason, activeRefundId}`，
前端只按 `canApply` 显示按钮。避免前端硬编码 7 天窗口，与后端漂移。

### 9.2 状态时间线组件

退款详情的核心是时间线（`trade_refund_log`）。抽成 `RefundTimeline` 组件，按 `action` 渲染节点。

---

## 10. 错误处理

| 场景 | 行为 |
|---|---|
| 订单不满足闸门 | 400 + 明确文案（如「已超过 7 天售后期限」） |
| 已有进行中退款单 | 400 + 「该订单已有处理中的退款申请」 |
| 金额超限 | 400 + 「退款金额不能超过实付 ¥X」 |
| 非归属人操作 | 400 + 「无权操作该退款」 |
| 状态已变（并发） | 400 + 「退款状态已变更，请刷新」（所有 UPDATE 带 `WHERE status=?`） |
| 支付单缺失 | 200 + 退款单置 `refunding` + 通知，**不 500** |
| 退款网关失败 | 200 + 置 `refunding` + 通知运营人工处理 |

---

## 11. 测试策略

**单元测试（Mockito）**，每个状态流转一条：
- 闸门矩阵：`unshipped` / `shipped` / `done` / `paid` × 2 种类型 × 边界（7 天整、超一天）
- `done_at` 为 NULL 时回退 `update_time`，两者都 NULL 则拒绝（不得 NPE）
- 金额上限：等于/超过/为 0
- 并发守卫：所有状态推进的 `UPDATE` 返回 0 时抛「状态已变更」
- 退款执行：有支付单 / 无支付单 / 网关失败 / **支付单金额小于退款金额** 四条路径
- **多态外键撞号**：`order_id=17` 同时有跑腿 paid 单与商品订单 → 必须选中正确的支付单（用 `pay_user_id` + 金额）
- `pay_user_id` 全 NULL 且候选多条 → 转人工，不执行
- 裁决联动：`buyer` / `partial` / `seller` 三种 decision，且**不依赖 `refund_id`**
- 订单冻结：申请退款后订单置 `refunding`，`shipOrder` / `confirmReceipt` 失败
- 订单恢复：退款关闭后按 `prev_order_status` 恢复
- 商品去向：未发货退款回补库存 + `onsale`；已发货退款置 `offshelf`
- 超时任务：三种超时各一条，且 `seller_agreed` 用 `create_time`
- 唯一索引兜底：`DuplicateKeyException` 翻译成「该订单已有处理中的退款申请」

**Mapper 契约测试**：`active_flag` 生成列与唯一索引行为；`INSERT` 不含 `active_flag`

**E2E（本机真实库，两条路径都要跑）**：
- 仅退款路径：下单 → mock 支付 → 申请 → 卖家同意 → 自动退款 → 校验 `trade_pay.status='refunded'` + 订单 `refunded` + 商品 `onsale` + 流水 2 条
- 退货退款路径：下单 → mock 支付 → 发货 → 申请 → 卖家同意 → **买家填单号** → 卖家确认收货 → 退款成功 → 校验商品 `offshelf`

---

## 12. 实施顺序（依赖关系）

```
A. 数据层迁移 SQL：active_flag 生成列 + 唯一索引 + prev_order_status 列
   + 权限菜单 + sys_job 注册（一份幂等脚本，照 report_feature.sql 的 information_schema 守卫写法）
B. 退款核心：实体/Mapper/Service（状态机 + executeRefund + 支付单双重定位）
C. 订单联动：申请时冻结订单、关闭时恢复、成功后订单/商品去向
D. 流水写入：支付成功 + 退款成功两个 trade_pay_flow 写入点
E. 纠纷联动：judgeDispute 改造（按 order_id 查退款单 + 状态守卫）
F. 超时任务：Quartz 三类超时扫描
G. C 端 API + 卖家 API + 运营 API（含 by-order / reasons / admin detail / resolve）
H. 前端：RefundApply / RefundDetail / RefundList / admin/Refund / OrderDetail 入口
I. 隐私设置：接口 + 前端
J. 资金对账：前端接线
K. 收尾：两条 E2E 路径 + 全量测试 + 审查
```

依赖：A → B → (C, D, E, F 可并行) → G → H → (I, J 独立) → K

---

## 13. 我做的决定（你没指定，我按真实电商逻辑定的）

| 决策 | 取值 | 理由 |
|---|---|---|
| 售后期限 | 7 天 | 主流平台惯例；留配置项 |
| 卖家处理超时 | 48 小时 | 同上 |
| 退款成功订单状态 | **`refunded`**（不是 `closed`） | 用户裁决：用订单表已有的 refunding/refunded，前端早已渲染；`closed` 是「未付款超时/买家取消」专用，复用会让退款单和弃单混在一起，且退出 GMV 统计 |
| 退款进行中订单状态 | `refunding`（冻结发货/确认收货） | 用户裁决：堵住「钱货两失」 |
| 退货两个超时 | `seller_agreed` 7 天 → `closed`；`buyer_returned` 7 天 → `mediation` | 用户裁决：每个非终态都有出路 |
| 未发货退款是否回补库存 | 是（恢复 `onsale`） | 没发货商品还在卖家手里 |
| 已发货退款商品去向 | **置 `offshelf`**（不回补库存） | 用户裁决：`locked` 会让商品永久锁死（不可见/不可编辑/不可下架），置 `offshelf` 让卖家自行重新上架 |
| 默认退款金额 | 订单实付全额 | 允许改小 |
| 证据图上限 | 3 张 | 与举报一致 |
| 绑定微信/支付宝 | 改「联系客服绑定」文案 | 无开放平台资质 |
| 资金流水 | 支付/退款各写一条 `trade_pay_flow` | 用户裁决：否则对账页永远空表 |

---

## 14. 遗留与风险

1. **`trade_pay.biz_type` 未加**（§8.4 硬前置）：真实支付上线前必须补。本设计用
   `pay_user_id` + 金额双重校验规避撞号（撞号已实测存在，见 §6.2），**代码注释必须写明该事实**
2. **3 笔历史订单 `out_trade_no` 为 NULL**：走「支付单缺失」分支，置 `refunding` 转人工。属已知脏数据
3. **`pay_user_id` 存量大量为 NULL**：匹配规则退化为「候选唯一才执行」，多条一律转人工
4. **微信退款未实现**：`WechatGatewayImpl.refund` 仍是 `not implemented`，本期不改（支付仍 mock）
5. **退款不影响信用分**：真实平台退款会影响卖家信用，本期不做，留后续
6. **部分退款（`partial`）仍把订单整体置 `refunded`**：不做按行拆分，符合本期「整单退」的简化
7. **`trade_refund` 无 `tenant_id`**：运营端 SQL 需 `JOIN trade_order` 取租户（沿用
   `AdminMarketplaceMapper.xml` 头注释的既有约定）
