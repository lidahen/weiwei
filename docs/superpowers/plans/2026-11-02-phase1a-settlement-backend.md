# Phase 1A 模拟结算后端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 mock 渠道上跑通「支付→holding→7 天售后期→pending_settle→mock 打款→settled→提现」，退款走 closed / clawback，并带每日对账与账本自检。

**Architecture:** 新增 5 张表（`seller_account` / `settlement_order` / `seller_balance_flow` / 重建 `trade_withdraw` / `settlement_recon_diff`），账本只追加、余额靠 `version` 乐观锁；所有状态推进一律「SQL 带前置状态守卫 + 影响行数判断」；幂等靠唯一键 + `INSERT IGNORE`（`uk_order_id`、`uk_biz_key`、`uk_diff`）。结算单在支付成功监听器写入，`done_at`/`hold_until` 由扫描任务从 `trade_order.done_at` 派生（两处确认收货 SQL 都写 `done_at`，无需改动订单链路）。`PayoutGateway` 隔离渠道，Phase 1 只实现 `MockPayoutGateway`。

**Tech Stack:** Java 17 + Spring Boot 3.5.4（Undertow）、MyBatis XML + PageHelper、Sa-Token、Quartz（`sys_job.invoke_target`）、MySQL 8（InnoDB/utf8mb4）、JUnit 5 + Mockito、Spring Java Format。

**Spec:** `docs/superpowers/specs/2026-11-02-settlement-compliance-design.md`（§1–§7 为绑定依据；§8 法务项不进入本期）

## Global Constraints

- 金额列一律 `DECIMAL(12,2)`；Java 类型 `BigDecimal`，比较用 `compareTo`，四舍五入 `RoundingMode.HALF_UP` 保留 2 位。
- 状态落库为字符串常量（`com.twelvet.trade.server.constant` 下的 final class），禁止 enum ordinal；状态推进必须是 `UPDATE ... WHERE status=<前置>` + 行数检查，0 行视为并发变更（跳过，不报错）。
- 幂等：`settlement_order.uk_order_id`、`seller_balance_flow.uk_biz_key`、`settlement_recon_diff.uk_diff(biz_date,type,biz_key)`，写入一律 `INSERT IGNORE`。
- 租户：`settlement_order.tenant_id` 取**订单**的 `tenant_id`；支付回调/Quartz 线程没有 `TenantContext`，任何事件/记账不得依赖上下文。`trade_refund` 无 `tenant_id`，退款侧从订单取。
- 佣金：`trade.settlement.commission-rate` 默认 `0`（§8.4 费率未定前按 0 记账）；`commission = gross × rate`（HALF_UP 2 位），`net = gross - commission`。
- 售后期：`trade.settlement.hold-days` 默认 `7`；`hold_until = done_at + hold-days`，与售后期同源，禁止第二处硬编码。
- 打款失败：`trade.settlement.payout-max-fail` 默认 `3`；`fail_count >= 3` 的结算单不进入打款队列（等人工）。
- `PayoutGateway` 只有 mock 实现；**不得**接入真实资金渠道，不得在平台账户沉淀货款。
- KYC：证件号/银行卡号只存密文（AES/GCM，密钥 `trade.settlement.crypto-key`，Base64，32 字节）；任何列表/详情只输出脱敏值（`DesensitizationUtil` 或保留后四位的等价实现）。
- 全部新增 Java 代码走 `mvn spring-javaformat:apply`；测试命令统一 `mvn -f backend/pom.xml -pl twelvet-trade/twelvet-trade-server -am test -Dtest=<类名> -Dsurefire.failIfNoSpecifiedTests=false`。
- 本地开发：不部署、不推送；SQL 脚本必须在本地 `twelvet-fast` 库执行并二次复核幂等。

## Decisions（相对 spec 的实现裁量，执行时按此为准）

- **`done_at`/`hold_until` 在扫描时派生**，不在订单完成时回写 `settlement_order`：`confirmReceipt` 与 `autoConfirmOrder` 两条 SQL 都已写 `trade_order.done_at`，扫描用 `DATE_ADD(o.done_at, hold-days)` 判定并补写两列。数据结果与 spec §2 一致，但不改动订单链路（少 2 个构造器改动、少一轮回归）。
- **结算单 `withdrawn` 状态本期不使用**：提现是账户级（一次提现可覆盖多张结算单），spec §2 的 `settled→withdrawn` 按单流转无法表达；结算后退款一律从 `settled` 走 clawback（`markClawback` 也接受 `withdrawn`，为未来按单提现留口）。状态常量保留 `WITHDRAWN`。
- **mock 打款同步成功即置 `settled`**：spec §4 要求真实渠道等异步回调/对账确认；`PayoutGateway.submit` 的 `PayoutResult` 已把「提交成功」与「可重试失败」分开，真实渠道接入时在回调里调 `markSettled + applyBalance` 即可，账本模型不变。
- **结算单创建失败不阻断支付**：监听器无事务，`createOnPaySuccess` 抛错只记 error；补偿靠对账 `missing_settlement`（不是静默丢失）。
- **`duplicate_settlement` 是对账的代码缺陷哨兵**（`uk_order_id` 下应为 0 行），spec §6 的第五类差异保留；账本自检另作 `ledger_mismatch`。
- **失败重试只有 `fail_count`**：超过 `payout-max-fail` 不再入选打款队列，运营通过结算单页看到（Phase 1 不建独立人工队列）。
- **提现扣减发生在申请时**（`withdraw_out`），驳回用 `adjust` 退回：spec §6.3 只说提现扣减走账本，未定扣减时点；申请即扣防止同一余额重复申请，`paid` 不再动余额。
- **并发验收用守卫分支覆盖，不做 100 线程压测**：仓库没有 DB 集成测试底座，单测逐一覆盖 `markClosed/markPending/markSettled/addBalance` 返回 0 行的分支；真实并发由 SQL 前置状态守卫保证，留给首次联调压测（与 spec 验收 2 的意图一致）。
- **`PayoutGateway.submit` 必须按 `settlementId` 幂等**：打款事务回滚后下一批次会对同一结算单再次提交，真实渠道需自带幂等键去重；mock 实现用 `settlementId` 记忆单号，避免「渠道已出款、账本回滚后重复出款」。

## Review Focus

以下 5 类失败模式最容易在真实使用中出事，每一条都必须在对应任务的测试里钉住：

1. **重复结算 / 重复入账**：支付回调重试、扫描重试、退款收尾重复调用、提现重复提交时，同一订单只能有 1 张结算单、同一 `biz_key` 只能有 1 条流水、余额只加一次（Task 5、7、8、9）。
2. **退款与结算/打款并发互斥**：窗口内退款与扫描/打款并发时，不得出现「已 settled 且已原路退」；状态守卫 0 行必须走另一分支（Task 6、7、8）。
3. **无租户上下文落错租户**：支付监听器与 Quartz 线程无 `TenantContext`，结算单租户必须来自订单；退款侧 `trade_refund` 无租户列，必须取订单租户（Task 5、6、8）。
4. **账本不自洽 / 负余额未冻结**：`SUM(flow.amount)` 必须等于 `seller_account.balance`，`balance_after` 等于变动后余额；扣回导致负余额时卖家必须 `frozen=1` 且后续打款被跳过（Task 7、8、9、10）。
5. **KYC 明文泄露**：证件号/银行卡号不得明文落库；C 端与运营端返回必须是脱敏值；日志不得打印明文（Task 3、4、11）。

---

## File Structure

**新建（SQL）**
- `backend/sql/settlement_schema.sql` — 5 张表 + `trade_withdraw` 重建 + 索引；幂等。
- `backend/sql/settlement_menu.sql` — 4 个运营菜单 + 1 个 C 端「卖家中心」菜单 + 角色授权 + 3 条 `sys_job`；幂等。

**新建（Java，`twelvet-trade-server`）**
- `.../server/constant/SettlementStatus.java`、`BalanceFlowType.java`、`WithdrawStatus.java` — 字符串常量 + 合法值集合。
- `.../server/domain/SellerAccount.java`、`SettlementOrder.java`、`SellerBalanceFlow.java`、`TradeWithdraw.java`、`SettlementReconDiff.java` — POJO。
- `.../server/mapper/SellerAccountMapper.java`、`SettlementOrderMapper.java`、`SellerBalanceFlowMapper.java`、`TradeWithdrawMapper.java`、`SettlementReconDiffMapper.java` + `resources/mapper/trade/*.xml`。
- `.../server/crypto/SensitiveDataCipher.java` — AES/GCM 字段加解密。
- `.../server/settlement/PayoutGateway.java`、`PayoutResult.java`、`MockPayoutGateway.java`。
- `.../server/service/ISellerAccountService.java`、`ISettlementService.java`、`IWithdrawService.java`、`IReconService.java` + `.../service/impl/` 四个实现。
- `.../server/controller/SellerSettlementController.java`、`AdminKycController.java`、`AdminSettlementController.java`、`AdminWithdrawController.java`、`AdminReconController.java`。
- `.../server/timer/task/SettlementScanTask.java`、`SettlementPayoutTask.java`、`SettlementReconTask.java`。

**修改（现有文件）**
- `.../server/listener/PaySuccessEventListener.java` — 支付成功后创建结算单（构造器 +1 依赖）。
- `.../server/service/impl/TradeRefundServiceImpl.java` — `finishRefundSuccess` 调结算互斥（构造器 +1 依赖）。
- `.../server/service/ITradeRefundService.java` 无需改（只在实现类内部集成）。
- `backend/twelvet-admin/src/main/resources/application-dev.yml` — `trade.settlement.*` 四项配置。
- `CLAUDE.md` — 数据库初始化步骤追加 `settlement_schema.sql`、`settlement_menu.sql`（Task 12）。

---

### Task 1: 结算 Schema（5 张表 + 重建 trade_withdraw）

**Files:**
- Create: `backend/sql/settlement_schema.sql`

**Interfaces:**
- Consumes: 基线 `docs/db/twelvet-fast_2026-10-21.sql`（`trade_order`、`trade_pay`、`trade_pay_flow`、`trade_refund`、`sys_tenant` 已存在）
- Produces: 表 `seller_account`、`settlement_order`、`seller_balance_flow`、`trade_withdraw`（新结构）、`settlement_recon_diff`，供 Task 2–11 使用

- [ ] **Step 1: 写 `settlement_schema.sql`**

按以下 DDL 落文件（含顶部注释：设计文档路径、前置基线、幂等说明）。字段与索引逐列照抄，不得增删：

```sql
-- seller_account
CREATE TABLE IF NOT EXISTS `seller_account` (
  `account_id` bigint NOT NULL AUTO_INCREMENT,
  `seller_id` bigint NOT NULL COMMENT '卖家 sys_user.user_id',
  `tenant_id` bigint NOT NULL COMMENT '租户ID(sys_tenant.id)，与订单对齐',
  `account_name` varchar(64) DEFAULT NULL COMMENT '实名姓名',
  `id_card_no` varchar(255) DEFAULT NULL COMMENT '证件号密文(AES/GCM)',
  `bank_card_no` varchar(255) DEFAULT NULL COMMENT '银行卡号密文(AES/GCM)',
  `bank_name` varchar(64) DEFAULT NULL COMMENT '开户行',
  `kyc_status` varchar(16) NOT NULL DEFAULT 'unverified' COMMENT 'unverified/pending/verified/rejected',
  `balance` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '可用余额（账本结果，非资金池）',
  `frozen` tinyint NOT NULL DEFAULT '0' COMMENT '1=冻结（负余额/对账差异），禁止提现与新打款',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_seller_tenant` (`seller_id`,`tenant_id`),
  KEY `idx_kyc_status` (`kyc_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卖家收款账户';
```

```sql
-- settlement_order
CREATE TABLE IF NOT EXISTS `settlement_order` (
  `settlement_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '商品订单ID',
  `refund_id` bigint DEFAULT NULL COMMENT '关联退款单（closed/clawback 时写）',
  `seller_id` bigint NOT NULL,
  `buyer_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `gross_amount` decimal(12,2) NOT NULL COMMENT '订单实付金额',
  `commission_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '平台佣金',
  `net_amount` decimal(12,2) NOT NULL COMMENT '应结给卖家=gross-commission',
  `status` varchar(20) NOT NULL DEFAULT 'holding' COMMENT 'holding/pending_settle/settled/withdrawn/closed/clawback',
  `done_at` datetime DEFAULT NULL COMMENT '订单完成时间（扫描时从 trade_order 派生）',
  `hold_until` datetime DEFAULT NULL COMMENT 'done_at + hold-days',
  `settled_at` datetime DEFAULT NULL,
  `channel` varchar(16) DEFAULT NULL COMMENT 'mock/alipay/wechat',
  `channel_trans_no` varchar(64) DEFAULT NULL COMMENT '渠道结算流水号',
  `fail_count` int NOT NULL DEFAULT '0' COMMENT '打款失败次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`settlement_id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  UNIQUE KEY `uk_channel_trans` (`channel`,`channel_trans_no`),
  KEY `idx_status_hold` (`status`,`hold_until`),
  KEY `idx_seller_tenant` (`seller_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算单（一单一结算）';
```

```sql
-- seller_balance_flow
CREATE TABLE IF NOT EXISTS `seller_balance_flow` (
  `flow_id` bigint NOT NULL AUTO_INCREMENT,
  `account_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `settlement_id` bigint DEFAULT NULL,
  `refund_id` bigint DEFAULT NULL,
  `withdraw_id` bigint DEFAULT NULL,
  `type` varchar(16) NOT NULL COMMENT 'settle_in/clawback/withdraw_out/adjust',
  `amount` decimal(12,2) NOT NULL COMMENT '正数入账，负数扣减',
  `balance_after` decimal(12,2) NOT NULL COMMENT '变动后余额（对账用）',
  `biz_key` varchar(64) NOT NULL COMMENT '幂等键 settle:{id}/clawback:{refundId}/withdraw:{id}/withdraw_reject:{id}',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`flow_id`),
  UNIQUE KEY `uk_biz_key` (`biz_key`),
  KEY `idx_account_time` (`account_id`,`create_time`),
  KEY `idx_settlement` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卖家余额账本（只追加）';
```

```sql
-- trade_withdraw：基线存在同名空表（旧结构，无任何代码引用），先重命名存档再建新表
SET @has_account := (SELECT COUNT(*) FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_withdraw'
                       AND COLUMN_NAME = 'account_id');
SET @sql := IF(@has_account = 0,
    'RENAME TABLE `trade_withdraw` TO `trade_withdraw_legacy_baseline`',
    'SELECT "trade_withdraw 已是新结构，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `trade_withdraw` (
  `withdraw_id` bigint NOT NULL AUTO_INCREMENT,
  `withdraw_no` varchar(64) NOT NULL COMMENT '对外提现单号 W+uuid（W+32 位 = 33 字符）',
  `account_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/paid/rejected',
  `operator_id` bigint DEFAULT NULL COMMENT '审核/打款人',
  `voucher_url` varchar(255) DEFAULT NULL COMMENT '线下打款凭证',
  `fail_reason` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`withdraw_id`),
  UNIQUE KEY `uk_withdraw_no` (`withdraw_no`),
  KEY `idx_account_status` (`account_id`,`status`),
  KEY `idx_seller_tenant` (`seller_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现申请';
```

```sql
-- settlement_recon_diff
CREATE TABLE IF NOT EXISTS `settlement_recon_diff` (
  `diff_id` bigint NOT NULL AUTO_INCREMENT,
  `biz_date` date NOT NULL COMMENT '对账业务日',
  `type` varchar(24) NOT NULL COMMENT 'missing_settlement/phantom_settlement/amount_mismatch/status_mismatch/ledger_mismatch',
  `biz_key` varchar(96) NOT NULL COMMENT '本类型内幂等键',
  `order_id` bigint DEFAULT NULL,
  `settlement_id` bigint DEFAULT NULL,
  `seller_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `channel_amount` decimal(12,2) DEFAULT NULL,
  `platform_amount` decimal(12,2) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'open' COMMENT 'open/resolved',
  `resolved_by` bigint DEFAULT NULL,
  `resolved_note` varchar(255) DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`diff_id`),
  UNIQUE KEY `uk_diff` (`biz_date`,`type`,`biz_key`),
  KEY `idx_status_date` (`status`,`biz_date`),
  KEY `idx_seller_tenant` (`seller_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算对账差异队列';
```

- [ ] **Step 2: 在本地库执行并验证结构**

Run:
```bash
'/f/MySQL/MySQL Server/bin/mysql' -uroot -p123456 -N -e "
USE \`twelvet-fast\`;
SHOW TABLES LIKE 'seller_account'; SHOW TABLES LIKE 'settlement_order';
SHOW TABLES LIKE 'seller_balance_flow'; SHOW TABLES LIKE 'trade_withdraw';
SHOW TABLES LIKE 'settlement_recon_diff';
SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='settlement_order' AND COLUMN_NAME IN ('uk_order_id');
SHOW INDEX FROM settlement_order WHERE Key_name='uk_order_id';"
```
Expected: 5 张表各返回 1 行；`uk_order_id` 索引存在（Non_unique=0）。

- [ ] **Step 3: 幂等复核（第二遍）**

Run: 再次执行整个脚本。
Expected: 无报错；`trade_withdraw_legacy_baseline` 已存在；`SHOW TABLES` 结果不变、行数不变。

- [ ] **Step 4: Commit**

```bash
git add backend/sql/settlement_schema.sql
git commit -m "feat(settlement): 结算五表与提现表结构"
```

---

### Task 2: 领域对象与 Mapper

**Files:**
- Create: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/constant/SettlementStatus.java`
- Create: `.../constant/BalanceFlowType.java`
- Create: `.../constant/WithdrawStatus.java`
- Create: `.../domain/SellerAccount.java`, `SettlementOrder.java`, `SellerBalanceFlow.java`, `TradeWithdraw.java`, `SettlementReconDiff.java`
- Create: `.../mapper/SellerAccountMapper.java`, `SettlementOrderMapper.java`, `SellerBalanceFlowMapper.java`, `TradeWithdrawMapper.java`, `SettlementReconDiffMapper.java`
- Create: `backend/twelvet-trade/twelvet-trade-server/src/main/resources/mapper/trade/SellerAccountMapper.xml`, `SettlementOrderMapper.xml`, `SellerBalanceFlowMapper.xml`, `TradeWithdrawMapper.xml`, `SettlementReconDiffMapper.xml`

**Interfaces:**
- Consumes: Task 1 的表结构
- Produces（后续任务只依赖这些签名，不得改名）:
  - `SettlementStatus`: `HOLDING="holding"`, `PENDING_SETTLE="pending_settle"`, `SETTLED="settled"`, `WITHDRAWN="withdrawn"`, `CLOSED="closed"`, `CLAWBACK="clawback"`
  - `BalanceFlowType`: `SETTLE_IN="settle_in"`, `CLAWBACK="clawback"`, `WITHDRAW_OUT="withdraw_out"`, `ADJUST="adjust"`
  - `WithdrawStatus`: `PENDING="pending"`, `APPROVED="approved"`, `PAID="paid"`, `REJECTED="rejected"`
  - `SellerAccountMapper`: `SellerAccount selectBySeller(Long sellerId, Long tenantId)`; `SellerAccount selectById(Long accountId)`; `int insert(SellerAccount account)`; `int submitKyc(Long accountId, String accountName, String idCardNo, String bankCardNo, String bankName, Long tenantId)`; `int reviewKyc(Long accountId, String kycStatus, Long tenantId)`; `int addBalance(Long accountId, BigDecimal amount, Integer version)`; `int setFrozen(Long accountId, Integer frozen, Long tenantId)`; `List<SellerAccount> selectKycPage(String kycStatus, Long tenantId)`
  - `SettlementOrderMapper`: `int insertIgnore(SettlementOrder settlement)`; `SettlementOrder selectByOrderId(Long orderId)`; `SettlementOrder selectById(Long settlementId, Long tenantId)`; `List<SettlementOrder> selectDueHoldings(int limit, int holdDays)`; `int markPending(Long settlementId, int holdDays)`; `List<SettlementOrder> selectPendingPayout(int limit, int maxFailCount)`; `int markSettled(Long settlementId, String channel, String channelTransNo)`; `int markClosed(Long orderId, Long refundId)`; `int markClawback(Long settlementId, Long refundId)`; `int incrementFailCount(Long settlementId)`; `List<SettlementOrder> selectAdminPage(String status, Long tenantId)`
  - `SellerBalanceFlowMapper`: `int insertIgnore(SellerBalanceFlow flow)`; `BigDecimal sumAmount(Long accountId)`; `SellerBalanceFlow selectLast(Long accountId)`
  - `TradeWithdrawMapper`: `int insert(TradeWithdraw withdraw)`; `List<TradeWithdraw> selectBySeller(Long sellerId, Long tenantId)`; `TradeWithdraw selectById(Long withdrawId, Long tenantId)`; `int review(Long withdrawId, String fromStatus, String toStatus, Long operatorId, String failReason, Long tenantId)`; `int markPaid(Long withdrawId, String voucherUrl, Long operatorId, Long tenantId)`; `List<TradeWithdraw> selectAdminPage(String status, Long tenantId)`
  - `SettlementReconDiffMapper`: `int insertIgnore(SettlementReconDiff diff)`; `List<SettlementReconDiff> selectPage(String bizDate, String status, Long tenantId)`; `int resolve(Long diffId, Long resolvedBy, String resolvedNote, Long tenantId)`; `int countOpenBySeller(Long sellerId, Long tenantId)`; `int freezeSellersWithOpenDiffs(String bizDate)`

- [ ] **Step 1: 写常量类与 POJO**

字段严格按 Task 1 列名转驼峰（例如 `SettlementOrder`: `settlementId, orderId, refundId, sellerId, buyerId, tenantId, grossAmount, commissionAmount, netAmount, status, doneAt, holdUntil, settledAt, channel, channelTransNo, failCount, createTime, updateTime`；`SettlementReconDiff`: `diffId, bizDate(LocalDate), type, bizKey, orderId, settlementId, sellerId, tenantId, channelAmount, platformAmount, status, resolvedBy, resolvedNote, resolvedAt, createTime`）。POJO 不继承 `BaseEntity`（与 `TradeOrder` 一致），提供 getter/setter；`BigDecimal` 金额、`LocalDateTime` 时间、`Integer frozen/failCount/version`。

- [ ] **Step 2: 写 Mapper 接口与 XML**

XML 必须包含下列守卫 SQL（逐条照抄语义，参数名与接口一致）：

`SettlementOrderMapper.xml`:
```xml
<insert id="insertIgnore" parameterType="com.twelvet.trade.server.domain.SettlementOrder"
        useGeneratedKeys="true" keyProperty="settlementId">
  INSERT IGNORE INTO settlement_order
    (order_id, seller_id, buyer_id, tenant_id, gross_amount, commission_amount, net_amount,
     status, channel, create_time, update_time)
  VALUES (#{orderId}, #{sellerId}, #{buyerId}, #{tenantId}, #{grossAmount}, #{commissionAmount},
          #{netAmount}, #{status}, #{channel}, NOW(), NOW())
</insert>

<select id="selectDueHoldings" resultType="com.twelvet.trade.server.domain.SettlementOrder">
  SELECT s.settlement_id, s.order_id, s.seller_id, s.tenant_id
  FROM settlement_order s
  JOIN trade_order o ON o.order_id = s.order_id
  JOIN seller_account a ON a.seller_id = s.seller_id AND a.tenant_id = s.tenant_id
  WHERE s.status = 'holding'
    AND o.status = 'done' AND o.done_at IS NOT NULL
    AND DATE_ADD(o.done_at, INTERVAL #{holdDays} DAY) &lt;= NOW()
    AND a.kyc_status = 'verified'
    AND NOT EXISTS (SELECT 1 FROM trade_refund r
                    WHERE r.order_id = s.order_id
                      AND r.status IN ('pending_seller','seller_agreed','buyer_returned','refunding','mediation'))
  ORDER BY s.settlement_id LIMIT #{limit}
</select>

<update id="markPending">
  UPDATE settlement_order s JOIN trade_order o ON o.order_id = s.order_id
  SET s.status = 'pending_settle', s.done_at = o.done_at,
      s.hold_until = DATE_ADD(o.done_at, INTERVAL #{holdDays} DAY), s.update_time = NOW()
  WHERE s.settlement_id = #{settlementId} AND s.status = 'holding'
    AND o.status = 'done'
    AND NOT EXISTS (SELECT 1 FROM trade_refund r
                    WHERE r.order_id = s.order_id
                      AND r.status IN ('pending_seller','seller_agreed','buyer_returned','refunding','mediation'))
</update>
```
（MySQL 的 `UPDATE ... JOIN` 内 `NOT EXISTS` 子查询可用；若实现时出现「不能先 SELECT 再 UPDATE 同表」错误，改为在外层 `UPDATE settlement_order SET ... WHERE settlement_id=? AND status='holding'`，条件在 service 选中前已校验，行数守卫不变。）

`selectPendingPayout`:
```sql
SELECT s.* FROM settlement_order s
JOIN seller_account a ON a.seller_id = s.seller_id AND a.tenant_id = s.tenant_id
WHERE s.status = 'pending_settle' AND a.kyc_status = 'verified' AND a.frozen = 0
  AND s.fail_count &lt; #{maxFailCount}
ORDER BY s.settlement_id LIMIT #{limit}
```

`markSettled`: `UPDATE settlement_order SET status='settled', settled_at=NOW(), channel=#{channel}, channel_trans_no=#{channelTransNo}, update_time=NOW() WHERE settlement_id=#{settlementId} AND status='pending_settle'`

`markClosed`: `UPDATE settlement_order SET status='closed', refund_id=#{refundId}, update_time=NOW() WHERE order_id=#{orderId} AND status IN ('holding','pending_settle')`

`markClawback`: `UPDATE settlement_order SET status='clawback', refund_id=#{refundId}, update_time=NOW() WHERE settlement_id=#{settlementId} AND status IN ('settled','withdrawn')`（`settled_at`/`channel_trans_no` 保留，结算事实不丢）

`SellerAccountMapper.xml`:
```xml
<update id="addBalance">
  UPDATE seller_account SET balance = balance + #{amount}, version = version + 1, update_time = NOW()
  WHERE account_id = #{accountId} AND version = #{version}
</update>
<update id="submitKyc">
  UPDATE seller_account
  SET account_name = #{accountName}, id_card_no = #{idCardNo}, bank_card_no = #{bankCardNo},
      bank_name = #{bankName}, kyc_status = 'pending', update_time = NOW()
  WHERE account_id = #{accountId} AND tenant_id = #{tenantId} AND kyc_status IN ('unverified','rejected')
</update>
<update id="reviewKyc">
  UPDATE seller_account SET kyc_status = #{kycStatus}, update_time = NOW()
  WHERE account_id = #{accountId} AND tenant_id = #{tenantId} AND kyc_status = 'pending'
</update>
<update id="setFrozen">
  UPDATE seller_account SET frozen = #{frozen}, update_time = NOW()
  WHERE account_id = #{accountId} AND tenant_id = #{tenantId}
</update>
```

`SellerBalanceFlowMapper.xml`: `insertIgnore` 写全列；`sumAmount`: `SELECT COALESCE(SUM(amount),0) FROM seller_balance_flow WHERE account_id=#{accountId}`；`selectLast`: `... ORDER BY flow_id DESC LIMIT 1`。

`TradeWithdrawMapper.xml`:
```sql
<update id="review">
  UPDATE trade_withdraw SET status = #{toStatus}, operator_id = #{operatorId},
         fail_reason = #{failReason}, update_time = NOW()
  WHERE withdraw_id = #{withdrawId} AND status = #{fromStatus} AND tenant_id = #{tenantId}
</update>
<update id="markPaid">
  UPDATE trade_withdraw SET status = 'paid', voucher_url = #{voucherUrl}, operator_id = #{operatorId}, update_time = NOW()
  WHERE withdraw_id = #{withdrawId} AND status = 'approved' AND tenant_id = #{tenantId}
</update>
```

`SettlementReconDiffMapper.xml`: `insertIgnore`（`INSERT IGNORE` 全列，`biz_date` 传 `LocalDate`）；`resolve`: `UPDATE ... SET status='resolved', resolved_by=#{resolvedBy}, resolved_note=#{resolvedNote}, resolved_at=NOW() WHERE diff_id=#{diffId} AND status='open' AND tenant_id=#{tenantId}`；`countOpenBySeller`: `SELECT COUNT(*) FROM settlement_recon_diff WHERE seller_id=#{sellerId} AND tenant_id=#{tenantId} AND status='open'`；`freezeSellersWithOpenDiffs`: `UPDATE seller_account a JOIN (SELECT DISTINCT seller_id, tenant_id FROM settlement_recon_diff WHERE biz_date=#{bizDate} AND status='open') d ON d.seller_id=a.seller_id AND d.tenant_id=a.tenant_id SET a.frozen=1 WHERE a.frozen=0`。

- [ ] **Step 3: 编译验证**

Run: `mvn -f backend/pom.xml -pl twelvet-trade/twelvet-trade-server -am test-compile -q`
Expected: BUILD SUCCESS（无测试，仅为后续任务提供契约）。

- [ ] **Step 4: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/constant \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/domain \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/mapper \
        backend/twelvet-trade/twelvet-trade-server/src/main/resources/mapper/trade
git commit -m "feat(settlement): 结算领域对象与 Mapper"
```

---

### Task 3: 敏感字段加解密

**Files:**
- Create: `.../server/crypto/SensitiveDataCipher.java`
- Modify: `backend/twelvet-admin/src/main/resources/application-dev.yml`（新增 `trade.settlement.crypto-key`）
- Test: `.../src/test/java/com/twelvet/trade/server/crypto/SensitiveDataCipherTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `@Component public class SensitiveDataCipher`，构造器 `SensitiveDataCipher(@Value("${trade.settlement.crypto-key:}") String keyBase64)`；`String encrypt(String plain)`；`String decrypt(String cipher)`；密钥非法/为空时构造器抛 `IllegalStateException`

- [ ] **Step 1: 写失败测试**

```java
class SensitiveDataCipherTest {
    private final SensitiveDataCipher cipher = new SensitiveDataCipher(
            Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void encrypt_thenDecrypt_roundTrips() {
        String enc = cipher.encrypt("110101199001011234");
        assertNotEquals("110101199001011234", enc);
        assertEquals("110101199001011234", cipher.decrypt(enc));
    }

    @Test
    void encrypt_samePlainTwice_producesDifferentCiphertext() {
        assertNotEquals(cipher.encrypt("abc"), cipher.encrypt("abc"));
    }

    @Test
    void decrypt_tamperedCiphertext_throws() {
        String enc = cipher.encrypt("abc");
        assertThrows(RuntimeException.class, () -> cipher.decrypt(enc.substring(0, enc.length() - 2)));
    }

    @Test
    void blankKey_failsFast() {
        assertThrows(IllegalStateException.class, () -> new SensitiveDataCipher(""));
    }
}
```

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -pl twelvet-trade/twelvet-trade-server -am test -Dtest=SensitiveDataCipherTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败「找不到符号 SensitiveDataCipher」。

- [ ] **Step 3: 实现 AES/GCM**

`AES/GCM/NoPadding`，12 字节随机 IV，128 位 tag，输出 `Base64(iv || ciphertext+tag)`；密钥 Base64 解码后必须 32 字节，否则 `IllegalStateException`；`decrypt` 捕获 `GeneralSecurityException` 后抛 `IllegalStateException("敏感字段解密失败")`。
`application-dev.yml` 追加（放在 `billing` 同级）：
```yaml
trade:
  settlement:
    crypto-key: "ZGV2LW9ubHktc2V0dGxlbWVudC1rZXktMzJieXRlcyE="  # 仅本地开发；生产必须用环境变量覆盖
```

- [ ] **Step 4: 运行并确认 GREEN**

Run: 同 Step 2。
Expected: `Tests run: 4, Failures: 0`。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/crypto \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/crypto \
        backend/twelvet-admin/src/main/resources/application-dev.yml
git commit -m "feat(settlement): KYC 敏感字段 AES/GCM 加解密"
```

---

### Task 4: 卖家账户与 KYC 审核

**Files:**
- Create: `.../service/ISellerAccountService.java`、`.../service/impl/SellerAccountServiceImpl.java`
- Test: `.../src/test/java/com/twelvet/trade/server/service/impl/SellerAccountServiceKycTest.java`

**Interfaces:**
- Consumes: `SellerAccountMapper`、`SensitiveDataCipher`（Task 2、3）
- Produces:
  - `SellerAccount getOrCreate(Long sellerId, Long tenantId)` — 不存在则插入 `unverified` 空账户
  - `void submitKyc(Long sellerId, Long tenantId, String accountName, String idCardNo, String bankCardNo, String bankName)`
  - `TableDataInfo<SellerAccount> adminKycPage(String kycStatus, Long tenantId)`
  - `void reviewKyc(Long accountId, String result, String note, Long operatorId, Long tenantId)` — `result ∈ {"verified","rejected"}`
  - `Map<String, Object> myAccount(Long sellerId, Long tenantId)` — 返回 `accountName/idCardNo/bankCardNo/bankName/kycStatus/balance/frozen`，其中 `accountName` 用 `DesensitizationUtil.chineseName`，证件/卡号解密后保留后四位（`****1234`）
  - `boolean unfreezeBySeller(Long sellerId, Long tenantId, Long operatorId, String reason)` — 原因必填；账户存在且 `countOpenBySeller == 0` 时 `unfreeze(accountId, tenantId, operatorId, reason)` 落库原因/操作人/时间；仍有未闭环差异抛 `TWTException`；账户不存在返回 false

- [ ] **Step 1: 写失败测试**

`SellerAccountServiceKycTest`（Mockito mock mapper + cipher 使用真实实例）：
- `submit_createsPendingAccountWithEncryptedFields`: mapper 无账户 → `getOrCreate` 插 `unverified`；`submitKyc` 捕获 ciphertext 断言 `!= plaintext` 且 `cipher.decrypt(stored)==plaintext`，状态 `pending`。
- `submit_afterVerified_rejected`: 账户 `verified` → 抛 `TWTException`，不调用 `submitKyc`。
- `review_verified_updatesStatus`: `pending` → `reviewKyc("verified")` 调用，且 `reviewKyc` mapper 返回 1；0 行时抛 `TWTException("状态已变更")`。
- `review_invalidResult_rejected`: `result="ok"` → `TWTException`。
- `myAccount_masksSensitiveFields`: 账户密文字段可解密，返回 `idCardNo` 匹配 `\*+1234`（与原文等长）且以 `1234` 结尾、`bankCardNo` 匹配 `\*+3445`、`accountName == "李**"`（用 `DesensitizationUtil` 的真实输出断言，不硬编码星号个数）。

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -pl twelvet-trade/twelvet-trade-server -am test -Dtest=SellerAccountServiceKycTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败「找不到符号 ISellerAccountService / SellerAccountServiceImpl」。

- [ ] **Step 3: 实现**

- `getOrCreate`: 先 `selectBySeller`，null 则 `insert`（`balance=0, frozen=0, version=0, kycStatus=unverified`）；插入撞 `uk_seller_tenant` 时捕获 `DataIntegrityViolationException` 后重新查询返回（并发首单）。
- `submitKyc`: `getOrCreate`；仅 `unverified/rejected` 可提交，否则 `TWTException("实名审核中或已通过，不能重复提交")`；`cipher.encrypt` 后调 `submitKyc`；行数 0 抛 `TWTException`。
- `adminKycPage`: `PageUtils.startPage()` + mapper `selectKycPage` + `PageUtils.getDataTable(...)`；返回前对每条记录做脱敏副本（不修改实体明文，构造 `Map` 或复制 POJO）。
- `reviewKyc`: 校验 result；`reviewKyc` mapper 行数 0 → `TWTException("状态已变更")`。
- `unfreeze`: `setFrozen(accountId, 0, tenantId)` 行数 0 → 返回 false。
- 日志只打印 `accountId`，禁止打印明文。

- [ ] **Step 4: 运行并确认 GREEN**

Run: 同 Step 2。
Expected: `Tests run: 5, Failures: 0`。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/SellerAccountServiceKycTest.java
git commit -m "feat(settlement): 卖家账户与 KYC 审核"
```

---

### Task 5: 支付成功创建结算单（holding）

**Files:**
- Create: `.../service/ISettlementService.java`（本期先声明 `createOnPaySuccess`，后续 Task 6–8 补方法）
- Create: `.../service/impl/SettlementServiceImpl.java`
- Modify: `.../listener/PaySuccessEventListener.java`（构造器追加 `ISettlementService`）
- Test: `.../src/test/java/com/twelvet/trade/server/listener/SettlementCreateOnPayTest.java`
- Test（构造器适配）: `.../listener/PaySuccessEventListenerFlowTest.java`、其它直接 new 该监听器的测试（若编译报错则一并更新）

**Interfaces:**
- Consumes: `SettlementOrderMapper.insertIgnore`；`@Value("${trade.settlement.commission-rate:0}") BigDecimal commissionRate`
- Produces: `void createOnPaySuccess(TradeOrder order, String channel)` — 固定 `status=holding`，`gross=order.totalAmount`，`commission=gross*rate HALF_UP 2位`，`net=gross-commission`，`tenant=s order.tenantId`，`buyer/seller` 取订单；重复调用不新增

- [ ] **Step 1: 写失败测试**

`SettlementCreateOnPayTest`（mock `TradeOrderMapper`、`TradePayFlowMapper`、`IFunnelEventService`、`SettlementOrderMapper`）：
- `paySuccess_createsHoldingSettlement`: 事件 `orderId=9`，订单 `totalAmount=100.00, tenantId=42, sellerId=7, buyerId=8`，`updateOrderStatus` 返回 1 → `insertIgnore` 捕获：`status=holding`、`gross=100.00`、`commission=0.00`、`net=100.00`、`tenantId=42`、`channel` 取事件 channel。
- `paySuccess_duplicateCallback_insertsIgnored`: `insertIgnore` 返回 0（唯一键命中）→ 不抛异常、不重复任何副作用。
- `paySuccess_rowsZero_noSettlement`: `updateOrderStatus` 返回 0 → `verify(settlementMapper, never()).insertIgnore(any())`。
- `paySuccess_nullOrder_noSettlement`: `selectOrderDetail` 返回 null → 不写结算单、不 NPE。

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -pl twelvet-trade/twelvet-trade-server -am test -Dtest=SettlementCreateOnPayTest,PaySuccessEventListenerFlowTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（监听器构造器不匹配 / 方法不存在）。

- [ ] **Step 3: 实现**

- `SettlementServiceImpl.createOnPaySuccess`: 构造 `SettlementOrder`，`orderId/sellerId/buyerId/tenantId` 全取订单；`channel` 取事件（null 时 `mock`）；`insertIgnore`；捕获 `DataIntegrityViolationException` 仅 warn（唯一键兜底），其它异常照常传播？——**不**：埋点式失败不应阻断支付后处理，但结算单是业务事实，写失败必须抛出让事务/重试发现。监听器无事务，明确：`createOnPaySuccess` 异常向上抛，由监听器 `try/catch` 记 error 日志（支付状态已更新，下轮对账会以 `missing_settlement` 发现）。
- `PaySuccessEventListener`: 在 `rows > 0` 且 `order != null` 分支的最后调用 `settlementService.createOnPaySuccess(order, event.getChannel())`，包在 try/catch 中（`log.error("[结算] 创建结算单失败 orderId={}", ...)`）。
- 构造器参数顺序：`(orderMapper, flowMapper, funnelEventService, notificationService, settlementService)`（以现有实际顺序为基础追加末位）。

- [ ] **Step 4: 运行并确认 GREEN**

Run: 同 Step 2。
Expected: `Tests run: 5, Failures: 0`（含原 `PaySuccessEventListenerFlowTest` 全绿）。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/ISettlementService.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/SettlementServiceImpl.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/listener/PaySuccessEventListener.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/listener
git commit -m "feat(settlement): 支付成功创建结算单"
```

---

### Task 6: 结算扫描 holding → pending_settle

**Files:**
- Create: `.../timer/task/SettlementScanTask.java`
- Modify: `.../service/ISettlementService.java`、`.../service/impl/SettlementServiceImpl.java`
- Test: `.../src/test/java/com/twelvet/trade/server/service/impl/SettlementScanTest.java`

**Interfaces:**
- Consumes: `SettlementOrderMapper.selectDueHoldings/markPending`
- Produces: `int scanDueHoldings(int limit)` — 返回成功推进数；`SettlementScanTask`（`@Component("settlementScan")`）方法 `void scan()` 调 `scanDueHoldings(200)`

- [ ] **Step 1: 写失败测试**

`SettlementScanTest`：
- `dueHolding_isPromoted`: `selectDueHoldings(200,7)` 返回 2 条，`markPending` 各返回 1 → 返回 2，`markPending` 调用 2 次（参数含 `holdDays=7`）。
- `markPendingRowsZero_concurrentRefund_skipped`: 第一条 `markPending` 返回 0（退款抢占）→ 返回 1，且不抛异常。
- `scan_usesConfiguredHoldDays`: 构造器/字段注入 holdDays=10 → `selectDueHoldings(200,10)`。
- `scan_noCandidates_returnsZero`: 空列表 → 0，不调用 `markPending`。

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -pl twelvet-trade/twelvet-trade-server -am test -Dtest=SettlementScanTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（方法不存在）。

- [ ] **Step 3: 实现**

`SettlementServiceImpl` 追加 `@Value("${trade.settlement.hold-days:7}") int holdDays`；`scanDueHoldings(limit)`：`selectDueHoldings(limit, holdDays)` 遍历，逐条 `markPending(id, holdDays)`，行数 1 计数；不 catch（Quartz 调度线程自行记录），单条失败不影响其余（try/catch 记 warn 后继续）。`SettlementScanTask` 结构照抄 `TradeRefundScanTask`（`@Component("settlementScan")`、`public void scan()`）。

- [ ] **Step 4: 运行并确认 GREEN**

Run: 同 Step 2。
Expected: `Tests run: 4, Failures: 0`。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/timer/task/SettlementScanTask.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/ISettlementService.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/SettlementServiceImpl.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/SettlementScanTest.java
git commit -m "feat(settlement): 售后期届满扫描推进待结算"
```

---

### Task 7: 打款渠道与批处理（pending_settle → settled）

**Files:**
- Create: `.../settlement/PayoutGateway.java`、`PayoutResult.java`、`MockPayoutGateway.java`
- Create: `.../settlement/SettlementPayoutHelper.java` — 单笔 `@Transactional`（markSettled + 入账同事务，失败整体回滚）
- Create: `.../service/ISellerAccountService.java` 追加 `boolean applyBalance(SellerBalanceFlow flow)`；`.../impl/SellerAccountServiceImpl.java`
- Modify: `.../service/ISettlementService.java`、`.../impl/SettlementServiceImpl.java`
- Create: `.../timer/task/SettlementPayoutTask.java`
- Test: `.../src/test/java/com/twelvet/trade/server/service/impl/SettlementPayoutTest.java`
- Test: `.../src/test/java/com/twelvet/trade/server/service/impl/SellerBalanceApplyTest.java`

**Interfaces:**
- Consumes: Task 2/4 的 Mapper 与账户服务
- Produces:
  - `PayoutResult`: `boolean success, String channelTransNo, boolean retryable, String message`；静态工厂 `PayoutResult.ok(String)`, `PayoutResult.retry(String)`, `PayoutResult.fail(String)`
  - `PayoutGateway`: `PayoutResult submit(SettlementOrder settlement)`；`String query(String channelTransNo)`
  - `@Service("mockPayoutGateway") MockPayoutGateway`：`submit` 按 `settlementId` 幂等（`ConcurrentHashMap` 记忆 `channelTransNo = "MOCK" + settlementId`，重复提交返回同一单号），`query` 固定 `"success"`
  - `ISettlementService.payoutBatch(int limit)` — 返回 settled 数
  - `ISellerAccountService.applyBalance(SellerBalanceFlow flow)` — 返回 true=记账成功；false=重复 `biz_key` 已处理；成功时把变动后余额写回 `flow.balanceAfter`；余额乐观锁 0 行抛 `TWTException("余额更新冲突")`（不重试，交给下一批次）
  - `SettlementPayoutHelper`（`@Component`）`@Transactional(rollbackFor=Exception.class) boolean payoutOne(SettlementOrder settlement, String channelTransNo)` — `markSettled` 0 行返回 false；账户缺失抛 `TWTException`（回滚 `markSettled`）；`applyBalance` 加入同一事务
  - `SettlementPayoutTask`（`@Component("settlementPayout")`）`void payout()` 调 `payoutBatch(200)`

- [ ] **Step 1: 写失败测试**

`SettlementPayoutTest`：
- `payoutOne_marksSettledAndCreditsBalance`（helper）: `markSettled` 返回 1；账户 `version=3`；`addBalance(accountId, 95.00, 3)` 返回 1；`flowMapper.insertIgnore` 捕获：`type=settle_in, amount=95.00, balanceAfter=..., bizKey=settle:11`，`channel=mock`。
- `payoutOne_markSettledRowsZero_returnsFalse`: `markSettled` 返回 0 → false，不记账（防重复入账）。
- `payoutOne_accountMissing_throws`: 账户查不到 → `TWTException`（`markSettled` 随事务回滚）。
- `gatewayRetry_incrementsFailCount_noBalance`（batch）: `gateway.submit` 返回 `retry("timeout")` → `incrementFailCount(11)`，不入账。
- `balanceConflict_batchIncrementsFailCount`: helper 抛 `TWTException("余额更新冲突")` → `payoutBatch` 捕获、`incrementFailCount(11)`、返回 0（事务回滚，下一批次重来）。
- `frozenOrUnverified_notSelected`: 由 `selectPendingPayout` SQL 保证；本测试只断言 `payoutBatch` 不自行过滤（查询返回空 → 0）。

`SellerBalanceApplyTest`：重复 `biz_key` 返回 false 且不改余额；负数金额允许（扣回/提现）；成功时 `flow.balanceAfter == 旧余额 + amount`。

- [ ] **Step 2: 运行并确认 RED**

Run: 同 Task 6 命令，`-Dtest=SettlementPayoutTest,SellerBalanceApplyTest`。
Expected: 编译失败（PayoutGateway / payoutBatch / applyBalance 不存在）。

- [ ] **Step 3: 实现**

`applyBalance`（`@Transactional`）：查账户，`newBalance = balance + amount`，**先**设 `flow.balanceAfter = newBalance`（`balance_after` NOT NULL，先插 NULL 会被 `INSERT IGNORE` 静默写成 0.00）；再 `flowMapper.insertIgnore(flow)` → 0 返回 false（幂等键已被消费）；`addBalance(accountId, amount, version)` 返回 0 → 抛 `TWTException("余额更新冲突")`（事务回滚已插流水，下一批次重来）；返回 true。`flow.sellerId/tenantId` 由调用方填。

`payoutBatch(limit)`：`selectPendingPayout(limit, maxFail)`；每条先 `gateway.submit`：`retryable`/失败 → `incrementFailCount` + warn；`success` → `payoutHelper.payoutOne(settlement, channelTransNo)`（`@Transactional` 内 `markSettled` + `applyBalance`，任一步失败整体回滚，下一批次重试）；helper 返回 false（并发已结算）不计数；helper 抛 `TWTException`（余额冲突/账户缺失）→ 捕获 + `incrementFailCount` + warn。渠道固定 `mock`，`channelTransNo` 取 `PayoutResult.getChannelTransNo()`。

- [ ] **Step 4: 运行并确认 GREEN**

Run: 同 Step 2。
Expected: 两个测试类全绿（≥9 用例）。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/settlement \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/timer/task/SettlementPayoutTask.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/SettlementPayoutTest.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/SellerBalanceApplyTest.java
git commit -m "feat(settlement): mock 打款批次与余额入账"
```

---

### Task 8: 退款成功的结算互斥（closed / clawback）

**Files:**
- Modify: `.../service/ISettlementService.java`、`.../impl/SettlementServiceImpl.java`
- Modify: `.../service/impl/TradeRefundServiceImpl.java`（`finishRefundSuccess` 末尾调用；构造器追加 `ISettlementService`）
- Test: `.../src/test/java/com/twelvet/trade/server/service/impl/RefundSettlementTest.java`
- Test（构造器适配）: `TradeRefundServiceImplApplyTest`、`ExecuteTest`、`FlowTest`、`JudgeTest`、`SettleTest`、`AdminPageTest`、`TradeRefundScanTaskTest`（全部 +1 ctor 参数）

**Interfaces:**
- Consumes: `SettlementOrderMapper.markClosed/selectByOrderId/markClawback`；`SellerAccountMapper.selectBySeller`；`ISellerAccountService.applyBalance`
- Produces: `void onRefundSuccess(TradeRefund refund, TradeOrder order, BigDecimal refundedAmount)` — 部分退款时 `refundedAmount` 是实际执行金额，小于 `refund.amount`；扣回以它为准

- [ ] **Step 1: 写失败测试**

`RefundSettlementTest`：
- `holdingRefund_closesSettlement`: `markClosed(orderId, refundId)` 返回 1 → 不产生流水、不冻结。
- `pendingSettleRefund_closesSettlement`: 同上（SQL 覆盖两态，测试断言只调一次 markClosed）。
- `settledRefund_writesClawbackAndMarks`: `markClosed` 返回 0；`selectByOrderId` 返回 `status=settled, net=100`；账户 `balance=30`；`markClawback` 返回 1 → `applyBalance` 捕获 `type=clawback, amount=-refundAmount, bizKey=clawback:{refundId}`（`balanceAfter` 由 applyBalance 计算，本测试断言 amount 与 bizKey，不重复断言余额算术）。
- `negativeBalance_freezesAccount`: applyBalance 后账户余额为负 → `setFrozen(accountId,1,tenantId)` 被调用。
- `withdrawnSettlement_stillClawback`: `status=withdrawn` → 同样扣回。
- `noSettlement_noop`: `selectByOrderId` 返回 null → 不抛异常、无流水。
- `duplicateRefundCallback_idempotent`: `applyBalance` 返回 false（biz_key 重复）→ 不冻结、不重复 markClawback（markClawback 仍可 0 行）。

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -pl twelvet-trade/twelvet-trade-server -am test -Dtest=RefundSettlementTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（`onRefundSuccess` 不存在 / 退款实现构造器不匹配）。

- [ ] **Step 3: 实现**

`onRefundSuccess(refund, order, refundedAmount)`：
1. `markClosed(order.getOrderId(), refund.getRefundId())`；行数 1 → return。
2. `SettlementOrder s = selectByOrderId(orderId)`；null 或 `closed` → return（已 closed 但 markClosed 因并发返回 0 的情况也在此覆盖）。
3. `s.status ∈ {settled, withdrawn}` → 构造 `SellerBalanceFlow`（account 由 `selectBySeller(s.sellerId, s.tenantId)` 取；不存在则 warn 并 return），`type=clawback, amount=-refundedAmount（实际退款额，不是 refund.amount 申请额）, bizKey="clawback:"+refundId, settlementId/refundId`；`applyBalance`；`markClawback(settlementId, refundId)`；若 `flow.balanceAfter.signum() < 0`（applyBalance 已写回变动后余额）→ `setFrozen(account.getAccountId(), 1, tenantId)` + warn。
4. 其它状态（holding 但 markClosed 0 行）→ warn 跳过（并发已处理）。
`TradeRefundServiceImpl.finishRefundSuccess` 在 `settleProduct` 之前调用（`order` 已在方法内 `selectOrderDetail` 取得），整段 try/catch 记 error，绝不因结算异常让退款失败。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `mvn -f backend/pom.xml -pl twelvet-trade/twelvet-trade-server -am test -Dtest=RefundSettlementTest,TradeRefund*Test,TradeRefundScanTaskTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 全绿（含 7 个既有退款测试的构造器更新）。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl
git commit -m "feat(settlement): 退款成功互斥结算单与余额扣回"
```

---

### Task 9: 余额与提现

**Files:**
- Create: `.../service/IWithdrawService.java`、`.../service/impl/WithdrawServiceImpl.java`
- Test: `.../src/test/java/com/twelvet/trade/server/service/impl/WithdrawServiceTest.java`

**Interfaces:**
- Consumes: `ISellerAccountService.getOrCreate/applyBalance`、`TradeWithdrawMapper`、`SellerBalanceFlowMapper`
- Produces:
  - `BigDecimal apply(Long sellerId, Long tenantId, BigDecimal amount)` — 返回扣减后余额
  - `TableDataInfo<TradeWithdraw> myPage(Long sellerId, Long tenantId)`
  - `TableDataInfo<TradeWithdraw> adminPage(String status, Long tenantId)`
  - `void review(Long withdrawId, String action, String reason, Long operatorId, Long tenantId)` — `action ∈ {"approve","reject"}`
  - `void markPaid(Long withdrawId, String voucherUrl, Long operatorId, Long tenantId)`

- [ ] **Step 1: 写失败测试**

`WithdrawServiceTest`：
- `apply_deductsBalanceAndWritesFlow`: 账户 `balance=100, verified`；`amount=40` → insert 提现单（`withdrawNo` 以 `W` 开头、`status=pending`）；`applyBalance` 捕获 `type=withdraw_out, amount=-40, bizKey=withdraw:{id}`；返回 60。
- `apply_insufficientBalance_rejected`: `amount=120` → `TWTException`，不插单。
- `apply_kycNotVerified_rejected`: `kyc_status=pending` → `TWTException`。
- `apply_nonPositive_rejected`: `amount=0` → `TWTException`。
- `reject_refundsBalance`: `review("reject")` → `review(pending→rejected)` 行数 1，`applyBalance(adjust, +40, withdraw_reject:{id})`。
- `approve_guarded`: `review("approve")` → `review(pending→approved)`；行数 0 → `TWTException`。
- `markPaid_requiresVoucher`: 空 voucher → `TWTException`；正常 → `markPaid` 调用。
- `duplicateApply_sameWithdrawId`：不适用（每次新单），改断言同一 `biz_key` 的 applyBalance 重复返回 false 时不再扣减（由 applyBalance 保证）。

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -pl twelvet-trade/twelvet-trade-server -am test -Dtest=WithdrawServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`apply`（`@Transactional`）：账户校验（实名、未冻结、`balance.compareTo(amount) >= 0`）；金额 `> 0` 且 `scale <= 2`（否则 `TWTException`）；插入提现单 `withdrawNo = "W" + IdUtils.fastSimpleUUID()`（`useGeneratedKeys` 拿 `withdrawId`）；`applyBalance` 负数流水；返回流水 `balanceAfter`。
`review`（`@Transactional`）：仅 `approve/reject`；reject 时 `applyBalance(+amount, adjust, biz_key withdraw_reject:{id})`，`failReason=reason`；行数 0 → `TWTException`。
`markPaid`（`@Transactional`）：voucher 非空；`approved→paid` 行数 0 → `TWTException`。
`myPage/adminPage`：`PageUtils.startPage()` + mapper + `PageUtils.getDataTable`。

- [ ] **Step 4: 运行并确认 GREEN**

Run: 同 Step 2。
Expected: `Tests run: 8, Failures: 0`。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/WithdrawServiceTest.java
git commit -m "feat(settlement): 卖家余额提现与审核"
```

---

### Task 10: 每日对账与差异队列

**Files:**
- Create: `.../service/IReconService.java`、`.../service/impl/ReconServiceImpl.java`
- Create: `.../timer/task/SettlementReconTask.java`
- Modify: `.../mapper/SettlementOrderMapper.java` + XML（新增对账查询）
- Modify: `.../mapper/SellerBalanceFlowMapper.java` + XML（新增 `selectAccountsWithLedgerDrift`）
- Test: `.../src/test/java/com/twelvet/trade/server/service/impl/ReconServiceTest.java`

**Interfaces:**
- Consumes: 全部结算 Mapper
- Produces:
  - `SettlementOrderMapper.selectMissingSettlement(LocalDate bizDate)` → `List<SettlementOrder>`（仅用 orderId/sellerId/tenantId，金额可为空）
  - `SettlementOrderMapper.selectPhantomSettlement(LocalDate bizDate)`；`selectAmountMismatch(LocalDate bizDate)`；`selectRefundStatusMismatch(LocalDate bizDate)`；`selectDuplicateSettlement(LocalDate bizDate)`
  - `SellerBalanceFlowMapper.selectAccountsWithLedgerDrift()` → `List<SellerAccount>`（或 `List<Map<String,Object>>`）
  - `IReconService.int reconcile(LocalDate bizDate)` — 写差异并冻结，返回新增差异数
  - `TableDataInfo<SettlementReconDiff> adminPage(String bizDate, String status, Long tenantId)`
  - `void resolve(Long diffId, String note, Long operatorId, Long tenantId)`
  - 解冻走账户服务 `unfreezeBySeller`（Task 4），对账服务不自动解冻
  - `SettlementReconTask`（`@Component("settlementRecon")`）`void reconcile()` 调 `reconcile(LocalDate.now().minusDays(1))`

- [ ] **Step 1: 写失败测试**

`ReconServiceTest`（mock 五个 mapper）：
- `detectsSixDiffTypes`: 各 select 返回 1 条 → `insertIgnore` 捕获 6 个不同 `type`：`missing_settlement/phantom_settlement/amount_mismatch/status_mismatch/duplicate_settlement/ledger_mismatch`（spec §6 的「重复结算」保留为第 5 类；`uk_order_id` 本应使其永不出现在真实数据，查询仍保留作为代码缺陷哨兵），`bizDate` 为入参日期。
- `duplicateDiff_insertIgnored`: `insertIgnore` 返回 0 → 不抛异常，返回计数只统计 1 行。
- `freezeAfterInsert`: `freezeSellersWithOpenDiffs("2026-11-01")` 被调用 1 次。
- `resolve_setsResolved`: 行数 1；0 行 → `TWTException("差异已处理")`。
- `unfreeze_onlyWhenNoOpenDiff`: 账户服务 `unfreeze` 直接调用（本测试覆盖 Recon 不自动解冻：`resolve` 后不调用 `setFrozen(0)`）。

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -pl twelvet-trade/twelvet-trade-server -am test -Dtest=ReconServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败。

- [ ] **Step 3: 实现**

6 条对账 SQL（照抄语义，列名以 Task 1 DDL 为准）：
```sql
-- 注意：trade_pay.order_id 是商品/跑腿/续费多态列（17–21 撞号），关联支付单必须加 pay_user_id + tenant_id
-- missing_settlement：已支付订单无结算单（biz_key=missing:{orderId}）
SELECT o.order_id, o.seller_id, o.tenant_id, p.pay_amount
FROM trade_order o JOIN trade_pay p ON p.order_id = o.order_id AND p.status = 'paid'
    AND p.pay_user_id = o.buyer_id AND p.tenant_id = o.tenant_id
LEFT JOIN settlement_order s ON s.order_id = o.order_id
WHERE s.settlement_id IS NULL AND DATE(o.paid_at) = #{bizDate}
LIMIT 1000

-- phantom_settlement：有结算单但无已支付流水（biz_key=phantom:{settlementId}）
SELECT s.settlement_id, s.order_id, s.seller_id, s.tenant_id, s.gross_amount
FROM settlement_order s LEFT JOIN trade_pay p ON p.order_id = s.order_id AND p.status = 'paid'
    AND p.pay_user_id = s.buyer_id AND p.tenant_id = s.tenant_id
WHERE p.pay_id IS NULL AND DATE(s.create_time) = #{bizDate}
LIMIT 1000

-- amount_mismatch：结算 gross 与实付不一致（biz_key=amount:{settlementId}）
SELECT s.settlement_id, s.order_id, s.seller_id, s.tenant_id, s.gross_amount, p.pay_amount
FROM settlement_order s JOIN trade_pay p ON p.order_id = s.order_id AND p.status = 'paid'
    AND p.pay_user_id = s.buyer_id AND p.tenant_id = s.tenant_id
WHERE s.gross_amount <> p.pay_amount AND DATE(s.create_time) = #{bizDate}
LIMIT 1000

-- status_mismatch：退款成功但结算单未 closed/clawback（biz_key=status:{refundId}）
SELECT r.refund_id, r.order_id, s.settlement_id, s.seller_id, s.tenant_id, s.status
FROM trade_refund r JOIN settlement_order s ON s.order_id = r.order_id
WHERE r.status = 'refund_success' AND s.status NOT IN ('closed','clawback')
  AND DATE(r.update_time) = #{bizDate}
LIMIT 1000

-- duplicate_settlement：同一订单出现多张结算单（biz_key=duplicate:{orderId}；uk_order_id 下应为 0 行，保留作代码缺陷哨兵）
SELECT order_id, COUNT(*) AS cnt, MIN(tenant_id) AS tenant_id, MIN(seller_id) AS seller_id
FROM settlement_order WHERE DATE(create_time) = #{bizDate}
GROUP BY order_id HAVING COUNT(*) > 1
LIMIT 1000

-- ledger_mismatch：账本求和/最后余额与账户不一致（biz_key=ledger:{accountId}:{bizDate}）
SELECT a.account_id, a.seller_id, a.tenant_id, a.balance,
       COALESCE(SUM(f.amount),0) AS flow_sum
FROM seller_account a LEFT JOIN seller_balance_flow f ON f.account_id = a.account_id
GROUP BY a.account_id, a.seller_id, a.tenant_id, a.balance
HAVING a.balance <> COALESCE(SUM(f.amount),0)
LIMIT 1000
```
`reconcile`: 逐类查询→构造 `SettlementReconDiff`（金额列按类型填 `channelAmount/platformAmount`）→ `insertIgnore` 计数→最后 `freezeSellersWithOpenDiffs(bizDate.toString())`。`ledger_mismatch` 的最后一条 `balance_after` 检查独立于合计漂移：`selectAccountsWithLastBalanceDrift()` 对所有账户比对最后一条 `balance_after` 与账户余额，不符记一条（biz_key 加 `:last`）。

- [ ] **Step 4: 运行并确认 GREEN**

Run: 同 Step 2。
Expected: `Tests run: 5, Failures: 0`。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/mapper \
        backend/twelvet-trade/twelvet-trade-server/src/main/resources/mapper/trade \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/timer/task/SettlementReconTask.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/ReconServiceTest.java
git commit -m "feat(settlement): 每日对账与差异冻结"
```

---

### Task 11: 运营与卖家端 Controller

**Files:**
- Create: `.../controller/SellerSettlementController.java`、`AdminKycController.java`、`AdminSettlementController.java`、`AdminWithdrawController.java`、`AdminReconController.java`
- Test: `.../src/test/java/com/twelvet/trade/server/controller/SellerSettlementControllerTest.java`、`AdminSettlementControllersTest.java`、`.../service/impl/SettlementAdminQueryTest.java`

**Interfaces:**
- Consumes: Task 4/6/7/9/10 的服务方法
- Produces（路径与权限逐字固定）:
  - `GET /trade/seller/account` → `JsonResult<Map<String,Object>>`（`StpUtil.getLoginIdAsLong()` + `TenantContext.get()`）
  - `POST /trade/seller/account/apply` body `{accountName,idCardNo,bankCardNo,bankName}` → `JsonResult<Void>`
  - `POST /trade/seller/withdraw/apply` body `{amount}` → `JsonResult<BigDecimal>`
  - `GET /trade/seller/withdraw/page` → `JsonResult<TableDataInfo<TradeWithdraw>>`
  - `GET /admin/marketplace/kyc/page?kycStatus=` → `JsonResult<TableDataInfo<SellerAccount>>`，perm `marketplace:admin:kyc`
  - `POST /admin/marketplace/kyc/review` body `{accountId,result,note}` → perm `marketplace:admin:kyc`
  - `GET /admin/marketplace/settlement/page?status=`、`GET /admin/marketplace/settlement/detail/{settlementId}` → perm `marketplace:admin:settlement`
  - `GET /admin/marketplace/withdraw/page?status=`、`POST /admin/marketplace/withdraw/review` body `{withdrawId,action,reason}`、`POST /admin/marketplace/withdraw/pay` body `{withdrawId,voucherUrl}` → perm `marketplace:admin:withdraw`
  - `GET /admin/marketplace/recon/page?bizDate=&status=`、`POST /admin/marketplace/recon/resolve` body `{diffId,note}`、`POST /admin/marketplace/recon/unfreeze` body `{sellerId,reason}`（controller 调 `sellerAccountService.unfreezeBySeller(sellerId, tenantId, operatorId, reason)`，租户取上下文且缺失即拒绝；差异行只有 sellerId 没有 accountId） → perm `marketplace:admin:recon`

- [ ] **Step 1: 写失败测试**

- `SellerSettlementControllerTest`: 静态 mock `StpUtil.getLoginIdAsLong()` 返回 7；`account/apply` 调 `submitKyc(7, tenant, ...)`；`withdraw/apply` 调 `withdrawService.apply(7, tenant, 40)` 并返回金额；未登录（StpUtil 抛 `NotLoginException`）向上传播。使用 `mockStatic(JsonResult.class)` 避免 `I18nUtils` 初始化（Phase 0B 既有做法）。
- `AdminSettlementControllersTest`: 每个 controller 方法有 `@SaCheckPermission` 注解且值精确（反射断言 `method.getAnnotation(SaCheckPermission.class).value()`）；`kyc/review` 传 `operatorId=StpUtil.getLoginIdAsLong()`；`withdraw/pay` 空 voucher 由服务抛错、controller 不吞。
- `SettlementAdminQueryTest`: `adminDetail` 查不到 → `TWTException("结算单不存在")`；`adminPage` 调 `selectAdminPage(status, tenantId)` 并返回 `TableDataInfo`（PageUtils 用静态 mock，参照既有 controller 测试的 `mockStatic` 做法）。

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -pl twelvet-trade/twelvet-trade-server -am test -Dtest=SellerSettlementControllerTest,AdminSettlementControllersTest,SettlementAdminQueryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（controller 不存在）。

- [ ] **Step 3: 实现**

照 `AdminRefundController` 风格：`@Tag`、`@RestController`、`@RequestMapping`、`extends TWTController`、显式构造器、每方法 `@Operation` + `@SaCheckPermission`。C 端 controller 不标注权限（`StpUtil` 登录由全局拦截器保证）。租户：C 端 `TenantContext.get()`（缺失时抛 `TWTException("缺少租户上下文")`，不落 DEFAULT 1）；运营端查询可选 `tenantId` 参数，缺省 null = 全租户（平台运营视角）。
本任务同时补齐 `ISettlementService` 的两个查询方法：`TableDataInfo<SettlementOrder> adminPage(String status, Long tenantId)`（`PageUtils.startPage()` + `selectAdminPage` + `PageUtils.getDataTable`）与 `Map<String,Object> adminDetail(Long settlementId, Long tenantId)`（`selectById`，null 抛 `TWTException("结算单不存在")`）。

- [ ] **Step 4: 运行并确认 GREEN**

Run: 同 Step 2。
Expected: 全绿（≥9 用例）。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/controller \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/SettlementAdminQueryTest.java
git commit -m "feat(settlement): 卖家与运营结算接口"
```

---

### Task 12: 菜单、权限与定时任务 SQL

**Files:**
- Create: `backend/sql/settlement_menu.sql`
- Modify: `CLAUDE.md`（数据库初始化步骤追加第 8、9 步）

**Interfaces:**
- Consumes: Task 11 的 4 个运营权限、C 端卖家中心菜单
- Produces: `sys_menu` 5 行、`sys_role_menu` 授权、`sys_job` 3 行（暂停态）

- [ ] **Step 1: 写 `settlement_menu.sql`**

照抄 `backend/sql/funnel_event.sql` 的幂等 INSERT 模式（`INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS` + 每个角色一条 grant）：
- 运营菜单（parent 2100，`menu_scope='1'`，`menu_type='C'`，`status='0'`）：
  - `实名审核` `order_num=9`, path `/admin/marketplace/kyc`, component `marketplace/admin/Kyc`, perms `marketplace:admin:kyc`
  - `结算单` `order_num=10`, path `/admin/marketplace/settlement`, component `marketplace/admin/Settlement`, perms `marketplace:admin:settlement`
  - `提现审核` `order_num=11`, path `/admin/marketplace/withdraw`, component `marketplace/admin/Withdraw`, perms `marketplace:admin:withdraw`
  - `结算对账` `order_num=12`, path `/admin/marketplace/recon`, component `marketplace/admin/Recon`, perms `marketplace:admin:recon`
  - 角色 1 全授；角色 3 全授（运营/客服）
- C 端菜单（parent 2007，`menu_scope='0'`）：`卖家中心` `order_num=8`, path `/user/settlement`, component `marketplace/MySettlement`, perms `marketplace:seller:settlement`；角色 2 授（C 端用户）
- `sys_job`（`status='1'` 暂停，本地惯例）：
  - `('结算-售后期扫描','DEFAULT','settlementScan.scan','0 50 1 * * ?',3,1,'1','admin',NOW(),'holding→pending_settle：hold_until 到期且订单 done、无进行中退款、卖家已实名')`
  - `('结算-每日打款批次','DEFAULT','settlementPayout.payout','0 0 2 * * ?',3,1,'1','admin',NOW(),'pending_settle→settled：MockPayoutGateway，冻结/未实名/失败超限跳过')`
  - `('结算-每日对账','DEFAULT','settlementRecon.reconcile','0 30 2 * * ?',3,1,'1','admin',NOW(),'五类差异入队并冻结相关卖家')`
  每条同样 `WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target='...')`。

- [ ] **Step 2: 执行并验证**

Run:
```bash
'/f/MySQL/MySQL Server/bin/mysql' -uroot -p123456 -N -e "
USE \`twelvet-fast\`;
SELECT perms, path FROM sys_menu WHERE perms LIKE 'marketplace:admin:%' AND perms IN
 ('marketplace:admin:kyc','marketplace:admin:settlement','marketplace:admin:withdraw','marketplace:admin:recon');
SELECT invoke_target FROM sys_job WHERE invoke_target LIKE 'settlement%';"
```
Expected: 4 个 perms 各 1 行；3 个 invoke_target 各 1 行。

- [ ] **Step 3: 幂等复核 + 权限漂移检查**

Run: 再次执行 `settlement_menu.sql`；然后 `powershell -File scripts/check-perms.ps1 -DbPassword 123456`
Expected: 第二次执行无报错、行数不变；`无漂移 ✅`（C 端 `marketplace:seller:settlement` 无 `@SaCheckPermission`，脚本只检查代码→菜单方向，允许菜单多出）。

- [ ] **Step 4: 更新 `CLAUDE.md`**

数据库初始化顺序追加：
```
8. `backend/sql/settlement_schema.sql`（结算五表；重建空的 trade_withdraw）
9. `backend/sql/settlement_menu.sql`（结算菜单/权限/定时任务）
```

- [ ] **Step 5: Commit**

```bash
git add backend/sql/settlement_menu.sql CLAUDE.md
git commit -m "feat(settlement): 结算菜单权限与定时任务"
```

---

### Task 13: Phase 1A 验收

**Files:**
- Test: 无新增；复跑全量

- [ ] **Step 1: 交易模块全量测试**

Run: `mvn -f backend/pom.xml -pl twelvet-trade/twelvet-trade-server -am test`
Expected: BUILD SUCCESS，0 failures（新增结算测试 + 全部既有交易测试）。

- [ ] **Step 2: 三模块回归**

Run: `mvn -f backend/pom.xml -pl twelvet-product/twelvet-product-server,twelvet-trade/twelvet-trade-server,twelvet-im/twelvet-im-server -am test`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 数据库幂等复核**

Run: 依次再次执行 `backend/sql/settlement_schema.sql`、`backend/sql/settlement_menu.sql`。
Expected: 无报错；表结构与菜单/任务行数不变。

- [ ] **Step 4: 账本与结构自检**

Run:
```bash
'/f/MySQL/MySQL Server/bin/mysql' -uroot -p123456 -N -e "
USE \`twelvet-fast\`;
SELECT COUNT(*) FROM settlement_order;
SELECT COUNT(*) FROM seller_balance_flow;
SELECT COUNT(*) FROM settlement_recon_diff;
SHOW INDEX FROM seller_balance_flow WHERE Key_name='uk_biz_key';"
```
Expected: 计数不报错（本地未跑业务时可为 0）；`uk_biz_key` 唯一索引存在。

- [ ] **Step 5: 记录结果并收尾**

把实际输出写入最终汇报；全部通过后按 superpowers:finishing-a-development-branch 处理分支（不推送、不合并除非用户选择）。

---

## Acceptance（Phase 1A 完成定义）

1. 重复支付回调只产生 1 张结算单、1 条 `settle_in` 流水（Task 5、7 测试钉住）。
2. 退款与扫描/打款并发：退款侧 `markClosed` 0 行时走 clawback；打款侧 `markSettled` 0 行时不入账（Task 6、7、8 测试钉住）。
3. 结算后退款必然产生 `clawback`；负余额卖家 `frozen=1` 且后续打款查询排除（Task 8、7 测试钉住）。
4. 五类对账差异全部识别并进队列，差异冻结卖家，解冻需运营操作（Task 10、11 测试钉住）。
5. 账本自检：`SUM(flow.amount) == seller_account.balance`（Task 7、10 测试与对账 SQL 钉住）。
6. 运营/卖家接口齐备、权限无漂移（Task 11、12 验证）；UI 全流程在 Phase 1B。

## Out of Scope

- 真实渠道（支付宝担保交易 / 微信收付通）、自动分账、法务确认项（spec §8）。
- 提现「paying」中间态：Phase 1 用 `approved → paid` 一步；对公/个体户限制、代扣税。
- 佣金费率非 0 的计费策略与开票。
- UI 页面（Phase 1B）、手工冒烟（需完整栈与种子数据）。
- 结算单批量导出、账期报表。

## Self-Review

- **Spec coverage**：§1 资金流（mock 边界）→ Global Constraints；§2 状态机 → Task 5–9；§3 四表 → Task 1/2；§4 结算时点 → Task 6（hold-days 配置）；§5 互斥/扣回 → Task 8；§6 对账 → Task 10；§7 验收 1–5 → Acceptance 1–5，验收 6 的 UI 在 1B。
- **类型一致性**：`TradeRefund.amount`（不是 refundAmount）、`TradeOrder.doneAt/totalAmount/paidAt`、`settlement_order` 无 `account_id`（账户由 `seller_id+tenant_id` 解析）——全局统一。
- **Review Focus 落点**：1 → Task 5/7/8/9；2 → Task 6/7/8；3 → Task 5/6/8；4 → Task 7/8/10；5 → Task 3/4/11。
- **比例**：本计划约 1.6 倍于 spec 正文；代码块集中在 DDL 与守卫 SQL（spec 明确定义、不可自由发挥），实现体不转录。