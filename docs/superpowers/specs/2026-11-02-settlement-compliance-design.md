# 担保交易与结算产品 / 合规方案

> 状态：Phase 1 设计草案（产品 + 技术），第 8 节的合规项需法务书面确认后方可接真实渠道。
> 适用：Phase 1（结算实现）；Phase 0A 只落地退款收口与运营 SLA，不含本文代码。
> 依据：`docs/superpowers/specs/2026-11-02-product-strategy-design.md`（Phase 1 结算与合规）。

## 1. 资金流与合规路径

**结论：Phase 1 首选支付宝担保交易（`alipay.trade.create` + 交易完成后分账/结算），备选微信电商收付通；两者都不允许平台自有账户沉淀买家货款。**

- 买家付款进入持牌机构的交易托管账户，平台只持有「指令 + 记账」权限；订单完成、售后期结束后，由渠道把货款结算给卖家，平台佣金作为分账方同时收取。
- **平台不得沉淀资金的理由**：无支付牌照做「先收后付」构成二清（二次清算），《非银行支付机构监督管理条例》与央行历次整治口径均将其列为无证经营支付业务。本文所有设计以「平台账户余额只记账、不实际收款」为前提，`seller_balance_flow` 是账本，不是资金池。
- 若渠道不支持分账（如仅普通即时到账），**不得降级为平台代收**；必须改用支持担保/分账的产品，或退回「线下交易 + 平台仅撮合」模式，由业务重新评估。
- 现实约束：当前 `AlipayGateway` 走的是普通支付（`trade_pay` 已有流水），Phase 1 先在 mock 渠道上把状态机、账本、对账跑通，真实渠道接入只替换 `PayoutGateway` / 支付产品码，不改账本模型。

## 2. 状态机

**结论：结算单状态为 `holding → pending_settle → settled → withdrawn`，异常路径为 `closed`（窗口内退款）与 `clawback`（结算后扣回）；状态推进一律「SQL 带前置状态守卫 + 检查影响行数」，0 行视为并发变更。**

| 当前状态 | 触发 | 守卫 | 下一状态 | 副作用 |
|---|---|---|---|---|
| （无） | 买家支付成功，订单置 `unshipped` | 订单 `unpaid→unshipped` 影响 1 行 | `holding` | 按订单金额 + 佣金生成结算单，`hold_until = done_at 待定`，`uk_order_id` 防重复 |
| `holding` | 订单完成（`done` 或自动确认收货） | 订单状态为 `done` | `holding`（补写 `done_at` / `hold_until`） | `hold_until = done_at + 7d` |
| `holding` | 售后期内发生退款成功 | 退款单 `refund_success` | `closed` | 结算单作废；退款原路退回，不进余额 |
| `pending_settle` | Quartz 扫描 `hold_until <= NOW()` 且订单 `done` 且无进行中退款 | `holding→pending_settle` 影响 1 行 | `pending_settle` | 冻结该笔结算，进入 T+1 打款队列 |
| `pending_settle` | T+1 打款任务提交渠道成功 | `pending_settle→settled` 影响 1 行 | `settled` | 写 `seller_balance_flow(+amount)`、`settled_at`、渠道流水号 |
| `settled` | 卖家发起提现并通过审核 | `settled→withdrawing` 影响 1 行 | `withdrawing` → `withdrawn` | 生成 `trade_withdraw`，余额账本扣减 |
| `settled` | 结算后发生退款（人工裁决/售后） | 退款单 `refund_success` | `clawback` | 写 `seller_balance_flow(-amount)`；余额不足转 `frozen` + 人工 |
| `pending_settle` | 提交渠道失败（可重试） | 渠道返回可重试错误 | `pending_settle` | 记失败次数，下一轮重试；超过 3 次转人工 |

- **窗口内退款的原路退回**：`refunding` 期间结算单仍为 `holding`，退款成功直接 `closed`，资金由渠道原路退回买家，卖家余额从未增加，因此不存在扣回。
- `clawback` 是**账本状态**而非独立结算单状态：结算单保留 `settled` 事实，扣回记在 `seller_balance_flow`（`type=clawback`），余额可为负；负余额冻结该卖家后续结算，直到补足或人工处理。
- 状态枚举落库为字符串（与 `trade_refund.status` 一致），Java 侧用常量类而非 enum ordinal，避免历史数据迁移成本。

## 3. 数据模型

**结论：新增 4 张表，全部带 `tenant_id`、审计字段（`create_time` / `update_time`）、金额 `DECIMAL(12,2)`；唯一键承担幂等，不靠应用层先查后写。**

### 3.1 `seller_account`（卖家收款账户与余额）

| 字段 | 类型 | 说明 |
|---|---|---|
| `account_id` | BIGINT PK | 账户ID |
| `seller_id` | BIGINT | 卖家用户ID |
| `tenant_id` | BIGINT | 租户键，与订单 `tenant_id` 对齐 |
| `account_name` | VARCHAR(64) | 实名姓名（与证件一致） |
| `id_card_no` | VARCHAR(64) | 证件号，加密存储（Jasypt / 应用层加密） |
| `bank_card_no` | VARCHAR(64) | 银行卡号，加密存储 |
| `bank_name` | VARCHAR(64) | 开户行 |
| `kyc_status` | VARCHAR(16) | `unverified` / `pending` / `verified` / `rejected` |
| `balance` | DECIMAL(12,2) | 可用余额（账本结果，非资金池） |
| `frozen` | TINYINT | 1=冻结（负余额/风控），禁止提现与新结算 |
| `version` | INT | 乐观锁，余额变动 `WHERE version = ?` |

索引：`uk_seller_tenant (seller_id, tenant_id)`；`idx_kyc_status (kyc_status)`。

### 3.2 `settlement_order`（结算单，一单一结算）

| 字段 | 类型 | 说明 |
|---|---|---|
| `settlement_id` | BIGINT PK | 结算单ID |
| `order_id` | BIGINT | 商品订单ID |
| `refund_id` | BIGINT NULL | 关联退款单（`clawback` 时必填） |
| `seller_id` / `buyer_id` | BIGINT | 双方 |
| `tenant_id` | BIGINT | 租户键 |
| `gross_amount` | DECIMAL(12,2) | 订单实付金额 |
| `commission_amount` | DECIMAL(12,2) | 平台佣金 |
| `net_amount` | DECIMAL(12,2) | 应结给卖家 = gross - commission |
| `status` | VARCHAR(20) | `holding` / `pending_settle` / `settled` / `withdrawing` / `withdrawn` / `closed` / `clawback` |
| `done_at` | DATETIME NULL | 订单完成时间 |
| `hold_until` | DATETIME NULL | `done_at + 7d` |
| `settled_at` | DATETIME NULL | 渠道结算成功时间 |
| `channel` | VARCHAR(16) | `alipay` / `wechat` / `mock` |
| `channel_trans_no` | VARCHAR(64) NULL | 渠道结算流水号 |
| `fail_count` | INT | 打款失败次数 |

索引：`uk_order_id (order_id)`（一单一结算，重复回调幂等）；`uk_channel_trans (channel, channel_trans_no)`；`idx_status_hold (status, hold_until)`（扫描待结算）；`idx_seller_tenant (seller_id, tenant_id)`。

### 3.3 `seller_balance_flow`（卖家余额账本，只追加）

| 字段 | 类型 | 说明 |
|---|---|---|
| `flow_id` | BIGINT PK | 流水ID |
| `account_id` | BIGINT | 卖家账户 |
| `seller_id` / `tenant_id` | BIGINT | 冗余，便于对账与租户过滤 |
| `settlement_id` | BIGINT NULL | 结算入账 |
| `refund_id` | BIGINT NULL | 扣回来源 |
| `withdraw_id` | BIGINT NULL | 提现扣减 |
| `type` | VARCHAR(16) | `settle_in` / `clawback` / `withdraw_out` / `adjust` |
| `amount` | DECIMAL(12,2) | 正数为入账，负数为扣减 |
| `balance_after` | DECIMAL(12,2) | 变动后余额（对账用，必须与 `seller_account.balance` 一致） |
| `biz_key` | VARCHAR(64) | 幂等键：`settle:{settlementId}` / `clawback:{refundId}` / `withdraw:{withdrawId}` |

索引：`uk_biz_key (biz_key)`（重复消费直接命中唯一键，不重复记账）；`idx_account_time (account_id, create_time)`；`idx_settlement (settlement_id)`。

### 3.4 `trade_withdraw`（提现申请）

| 字段 | 类型 | 说明 |
|---|---|---|
| `withdraw_id` | BIGINT PK | 提现单ID |
| `account_id` / `seller_id` / `tenant_id` | BIGINT | 账户与租户 |
| `amount` | DECIMAL(12,2) | 提现金额 |
| `status` | VARCHAR(16) | `pending` / `approved` / `paying` / `paid` / `rejected` |
| `operator_id` | BIGINT NULL | 审核人 |
| `voucher_url` | VARCHAR(255) NULL | 线下打款凭证（Phase 1 mock） |
| `fail_reason` | VARCHAR(255) NULL | 驳回/失败原因 |

索引：`uk_withdraw_no (withdraw_no)`（对外单号）；`idx_account_status (account_id, status)`；`idx_seller_tenant (seller_id, tenant_id)`。

**租户键**：四张表全部冗余 `tenant_id`，且唯一键包含 `seller_id + tenant_id`；跨租户同名卖家互不影响。`settlement_order` 的租户键必须**取自订单**（`trade_order.tenant_id`），不得取调用方上下文，避免回调线程无上下文时落 DEFAULT 1（与 `TradeOrderServiceImpl.createOrderForUser` 踩过的坑一致）。

## 4. 结算时点

**结论：`hold_until = done_at + 7d`（与售后窗口同长，窗口内退款不会发生「已结算再退」）；T+1 打款（次日 02:00 批次）；售后期内退款不进入结算。**

- `done_at` 取订单 `done` 的完成时间；自动确认收货的订单取 `auto_confirm_at` 实际执行时间，不取发货时间。
- 结算扫描条件：`status='holding' AND hold_until <= NOW() AND 订单 done AND 不存在 status IN ('pending_seller','seller_agreed','buyer_returned','refunding','mediation') 的退款单`。任一条件不满足则本轮到该单即跳过，下一轮重扫。
- 打款批次：每日 02:00（Asia/Shanghai）把 `pending_settle` 提交渠道；渠道成功回调（或 T+1 对账确认）后置 `settled`。**不以渠道请求返回成功直接置 settled**，避免渠道异步失败造成账实不符。
- 窗口口径：`hold_until` 必须 **≥** `AFTER_SALE_DAYS`（7 天），Phase 1 两者同为 7 天；若未来调整售后窗口，`hold_until` 必须同步调整并由配置项 `trade.settlement.hold-days` 承载，禁止硬编码两处。
- 人工裁决的退款若发生在 `settled` 之后，按第 5 节扣回，不影响 `hold_until` 规则。

## 5. 退款与结算互斥 / 扣回

**结论：结算前退款原路退（结算单 `closed`）；结算后走余额扣回（`clawback`）；余额不足冻结卖家后续结算并转人工，平台不垫付。**

- **并发互斥**：退款成功与结算扫描可能同时发生。退款侧用 `UPDATE settlement_order SET status='closed' WHERE order_id=? AND status IN ('holding','pending_settle')` 抢占；结算侧用 `UPDATE ... SET status='pending_settle' WHERE status='holding' AND hold_until<=NOW()` 抢占。两边都检查影响行数：结算侧 0 行说明退款已抢占，直接跳过；退款侧 0 行说明结算已提交渠道，转入扣回分支。
- **结算前退款**：退款单 `refund_success` → 结算单 `closed`；资金由渠道原路退回买家。卖家余额不产生入账，因此没有扣回。
- **结算后退款**：退款单 `refund_success` 且结算单 `settled` → 写 `seller_balance_flow(type='clawback', amount=-refundAmount, biz_key='clawback:{refundId}')`，并把结算单标记 `clawback`（保留结算事实）。余额允许为负。
- **余额不足**：扣回后 `balance < 0` 或扣回金额超过可用余额时，仍记负余额并把 `seller_account.frozen=1`；冻结期间该卖家新的结算单全部停在 `pending_settle`，不提交打款；运营在退款台/结算台看到「余额不足待人工」队列，通过线下补款或后续成交抵扣解除。**不做平台垫付**，避免平台成为实际付款方。
- **幂等**：扣回靠 `uk_biz_key` 唯一键；重复退款回调只会命中重复键，不重复扣减。余额变动一律 `WHERE version = ?` 乐观锁 + `balance_after` 记账，任何不一致由对账发现而不是靠重试修数。

## 6. 对账

**结论：每日 02:30（打款批次之后）做三方对账：渠道账单、`trade_pay`、`settlement_order` + `seller_balance_flow`；差异进队列，未闭环的差异冻结相关卖家结算。**

- **三方口径**：
  1. 渠道账单（支付宝/微信对账单或 mock 渠道导出）→ 实收、实结、退款、手续费；
  2. `trade_pay`（买家支付流水）+ `trade_pay_flow`（支付成功记账）→ 应收；
  3. `settlement_order`（应结/已结）+ `seller_balance_flow`（余额账本）→ 应付/已付。
- **差异类型**：渠道有平台无（漏单）、平台有渠道无（虚单）、金额不一致、状态不一致（渠道已退平台未退）、重复结算（`uk_order_id` 本应兜底，出现即代码缺陷）。
- **差异队列**：新增 `settlement_recon_diff`（`diff_id`、`biz_date`、`type`、`order_id`、`settlement_id`、`channel_amount`、`platform_amount`、`status`、`resolved_by`、`resolved_note`），每日对账任务写入，运营结算台按 `biz_date + type` 处理。
- **冻结规则**：同一卖家在同一 `biz_date` 存在未闭环差异时，`seller_account.frozen=1`，其 `pending_settle` 不提交打款；差异闭环后人工解冻并记录原因。
- **账本自检**：每日对账同时校验 `SUM(seller_balance_flow.amount) == seller_account.balance` 且最后一条 `balance_after == balance`；不一致直接告警，不自动修数。

## 7. Phase 1 mock 边界

**结论：Phase 1 只做「人工实名 + 人工提现 + mock 打款」，真实渠道与自动分账留到 Phase 2；验收以「账本自洽 + 幂等 + 并发互斥」为准。**

- **实名核验（人工）**：卖家在 C 端提交姓名、证件号、银行卡；运营在后台逐条审核（`kyc_status`），通过后才可结算与提现。Phase 1 不对接实名三要素接口，证件号/卡号加密存储，列表脱敏展示。
- **提现（人工）**：卖家申请 → 运营审核 → 线下银行转账 → 回填 `voucher_url` 并置 `paid`；提现扣减走 `seller_balance_flow(type='withdraw_out')`，与结算入账同一账本。
- **mock 打款**：定义 `PayoutGateway` 接口（`submit(settlement) -> channelTransNo`、`query(channelTransNo) -> status`），Phase 1 提供 `MockPayoutGateway`（固定延迟成功、可注入失败/超时），不打真实资金。真实渠道接入只新增实现类。
- **验收标准**（Phase 1 完成定义）：
  1. 同一订单重复支付回调只产生 1 张结算单、1 条 `settle_in` 流水（`uk_order_id` + `uk_biz_key` 双保险）；
  2. 窗口内退款与结算扫描并发 100 次，不出现「已结算且已原路退」双重结果（状态守卫 + 行数检查）；
  3. 结算后退款必然产生 `clawback`，余额为负时卖家被冻结且后续结算不提交打款；
  4. 注入 5 类对账差异，对账任务全部识别并进队列，闭环后解冻；
  5. 账本自检 0 差异（`SUM(flow.amount) == balance`）；
  6. 运营后台可完成「审核实名 → 看结算单 → 审核提现 → 回填凭证」全流程，无 SQL 手工改数。

## 8. 待法务 / 业务确认

**结论：以下 6 项在接真实渠道前必须有书面结论；未确认前只允许 mock 渠道运行，不得对外宣传「平台担保」。**

1. **真实渠道选型**：支付宝担保交易 vs 微信电商收付通。需确认平台主体资质、可开通的产品码、分账比例上限与到账时效。（技术偏好支付宝：现有 `AlipayGateway` 可复用；最终以商务/费率结论为准。）
2. **商户资质**：营业执照经营范围、ICP 备案、支付业务许可（或与持牌机构合作的协议模板）。缺一项则不能接真实渠道。
3. **二清结论**：在「渠道托管 + 平台仅分账指令」模式下，平台是否仍被认定为二清；需法务出具书面意见，并据此决定是否需要持牌机构作为结算通道。
4. **佣金费率与开票主体**：佣金比例、由谁向买家开票、卖家收入是否需平台代开/代扣税。费率未定前，`commission_amount` 先按 0 记账，不阻塞账本联调。
5. **卖家个税与发票**：个人卖家经营所得如何申报；是否需要平台代扣代缴。未确认前，提现仅对公/个体户开放，个人卖家只记账不提现。
6. **用户协议与隐私**：实名信息收集的告知同意、留存期限、跨境传输；协议需在 C 端实名页面前置展示并由用户勾选。