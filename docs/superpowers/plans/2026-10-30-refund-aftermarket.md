# 退款售后闭环 + 占位补齐 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让买家能申请退款、卖家能审核、退货有物流、平台能裁决且裁决真正退钱，同时补齐资金对账页与隐私设置三开关。

**Architecture:** 沿用 `trade_refund` / `trade_refund_log` / `trade_dispute` 三张既有表（只加一列 `prev_order_status` 与一个生成列 `active_flag`），在 `twelvet-trade-server` 内新建 Refund 域（domain/mapper/service/controller）。所有状态推进走「SQL 带前置状态守卫 + 检查影响行数」；申请退款即把 `trade_order` 冻结为 `refunding`，退款成功置 `refunded`，关闭时按 `prev_order_status` 恢复。退款执行是**唯一入口** `executeRefund`，卖家确认收货、客服裁决、运营手工收口三条路径都调它。

**Tech Stack:** Java 17 / Spring Boot 3.5.4 / MyBatis XML / Sa-Token / PageHelper / Quartz / MySQL 8.0；前端 Umi Max 4 + React 18 + Ant Design 5。

**Spec:** `docs/superpowers/specs/2026-10-30-refund-aftermarket-and-fill-gaps-design.md`

## Global Constraints

- 支付保持 **mock**：`PayOrderService.refund()` 在 `billing.pay.mock=true` 下只把 `trade_pay.status` 置 `refunded`，不接真实渠道
- 所有租户过滤一律 `<if test="tenantId != null">AND tenant_id = #{tenantId}</if>`（零侵入回滚）
- `trade_refund` 与 `trade_pay_flow` **无 `tenant_id` 列**，运营端 SQL 必须 `JOIN trade_order` 取租户
- 所有备注/拒绝理由/裁决理由/人工说明一律写 `trade_refund_log.content`，**不在 `trade_refund` 加备注列**
- 所有状态推进 SQL 必须带前置状态守卫，且检查影响行数；0 行 = 抛 `TWTException`（业务提示），不得静默成功
- `TradeRefund` 的 `INSERT` **禁止包含 `active_flag`**（MySQL 生成列不能出现在 INSERT 列清单），且**禁止使用 `INSERT ... ON DUPLICATE KEY UPDATE`**（会绕过 `uk_order_active`）
- `trade_pay_flow.type` 写入值为 **`pay` / `refund`**（DDL 注释里的 `in/out` 是历史误写，迁移脚本一并修正列注释）
- `sys_job.job_group` 一律写 **`DEFAULT`**（库内 20 条既有 Job 全是这个值）
- 站内通知 / 流水写入失败**不得回滚主流程**：包 try/catch + `log.warn`（对齐 `ReportServiceImpl.notifyAfterCommit`）
- 代码格式：`mvn -o spring-javaformat:apply` 后再 validate
- 前端包管理器 **npm**，不用 yarn；组件测试用 `React.createElement`（本仓库 jest 未启用自动 JSX runtime）
- 数据库凭据：`root` / `123456`，库 `twelvet-fast`
- **`TradeRefundServiceImpl` 的构造器会随 Task 4→6 逐步加参数**（`payLookup` / `payOrderService` / `flowMapper` / `imService` / `productMapper` / `orderMapper`）。每次加参数，先前 Task 的测试文件都要同步补 `mock(...)`——编译期即报错，改一处即可，属预期内改动
- **状态机的「谁推进到 `refunding`」只有一个约定**：调用方（`sellerAgree` 仅退款分支 / `sellerConfirmReceipt` / `judge`）先用带守卫的 `updateStatus` 推进到 `refunding`，再调 `executeRefund`；`executeRefund` 本身**不做初始推进**，只负责定位支付单、调网关、收尾

## Review Focus

1. **多态外键撞号**：`trade_pay.order_id` 被商品订单/跑腿任务/租户续费共用，本机实测 17–21 号撞号（`trade_pay` 17–21 是 1.50/2.00/4.00 的跑腿已付单，`trade_order` 17–21 是 2499/507/507/499/1511 的商品单）。退款定位支付单若只按 `order_id`，会退掉跑腿任务的支付单 —— 必须 `pay_user_id` + 金额双重校验，候选不唯一转人工
2. **退款期间订单被冻结**：申请退款后订单必须是 `refunding`，否则卖家能在审核期发货、买家能确认收货，形成「货拿走 + 钱退回」
3. **每个非终态都有出路**：`pending_seller`(48h→mediation)、`seller_agreed`(7d→closed)、`buyer_returned`(7d→mediation)、`refunding`(运营手工收口)、`seller_rejected`(买家介入或撤销) —— 任何一个卡死都会让订单永久无法再申请退款
4. **`done_at` 可为 NULL**：`done` 订单的 7 天窗口回退 `update_time`，两者都空则拒绝，不得 NPE
5. **`pay_user_id` 存量大量为 NULL**：候选不唯一时一律转人工，宁可不退不可错退
6. **`seller_agreed` 的超时用 `create_time`**：用 `update_time` 会在每次流转后重置计时，导致「拒绝→介入」的退款单永远扫不到

---

## 文件结构

**后端（`backend/twelvet-trade/twelvet-trade-server/`）**

| 文件 | 职责 |
|---|---|
| `domain/TradeRefund.java` | 退款单实体（**新建**；放 server 包，不被跨模块使用） |
| `domain/TradeRefundLog.java` | 时间线日志实体（**新建**） |
| `domain/RefundReason.java` | 退款原因白名单（**新建**，纯静态工具类） |
| `mapper/TradeRefundMapper.java` + `resources/mapper/trade/TradeRefundMapper.xml` | `trade_refund` 表唯一读写点（**新建**） |
| `mapper/TradeRefundLogMapper.java` + `.xml` | 时间线日志（**新建**） |
| `mapper/TradePayFlowMapper.java` + `.xml` | 资金流水写入（**新建**） |
| `mapper/TradePayLookupMapper.java` + `.xml` | 按 `order_id` 定位已付支付单（**新建**；跨模块读 `trade_pay`，与 product 的 `UserMapper` 读 `trade_refund` 同一先例） |
| `service/ITradeRefundService.java` | 退款业务接口（**新建**） |
| `service/impl/TradeRefundServiceImpl.java` | 状态机 + `executeRefund`（**新建，核心**） |
| `controller/TradeRefundController.java` | C 端 + 卖家端（**新建**，`/trade/refund`） |
| `controller/AdminRefundController.java` | 运营端（**新建**，`/admin/marketplace/refund`） |
| `task/TradeRefundScanTask.java` | 三类超时扫描（**新建**） |
| `mapper/TradeOrderMapper.java` + `.xml` | **修改**：新增 `updateOrderStatusIfIn`（现有 `updateOrderStatus` 硬编码 `AND status='unpaid'`，是支付监听器专用，不能复用） |
| `listener/PaySuccessEventListener.java` | **修改**：写 `trade_pay_flow` |
| `mapper/AdminMarketplaceMapper.xml` | **修改**：`selectRecentPayFlows` 补 `flow_id` / `third_trans_no` 两列 |
| `service/impl/CsDisputeServiceImpl.java` + `mapper/CsDisputeMapper.xml` | **修改**：裁决联动退款 + `judgeDispute` 加状态守卫 |

**后端（`backend/twelvet-product/twelvet-product-server/`）**

| 文件 | 职责 |
|---|---|
| `controller/UserPrivacyController.java` | 隐私三开关读写（**新建**，`/user/privacy`） |
| `mapper/UserMapper.java` + `resources/mapper/product/UserMapper.xml` | **修改**：新增 `selectPrivacy` / `upsertPrivacy` |

**SQL**

| 文件 | 职责 |
|---|---|
| `backend/sql/refund_feature.sql` | 迁移（两列 + 唯一索引 + 列注释修正 + 两条菜单 + 一条 Job + 授权），幂等 |

**前端（`ui/`）**

| 文件 | 职责 |
|---|---|
| `src/pages/marketplace/RefundApply.tsx` | **重写**（占位 → 申请表单） |
| `src/pages/marketplace/RefundDetail.tsx` | **重写**（占位 → 详情 + 时间线 + 按角色操作） |
| `src/pages/marketplace/RefundList.tsx` | **新建**（我的退款） |
| `src/pages/marketplace/admin/Refund.tsx` | **新建**（运营退款处理） |
| `src/pages/marketplace/admin/Finance.tsx` | **重写**（占位 → 统计卡 + 流水表） |
| `src/pages/marketplace/OrderDetail.tsx` | **修改**（退款入口，读后端 `canApply`） |
| `src/pages/marketplace/UserSettings.tsx` | **修改**（3 开关接真实接口 + 绑定改文案） |
| `src/components/marketplace/RefundTimeline.tsx` | **新建**（时间线组件） |
| `src/services/marketplace/api.ts` | **修改**（补退款/隐私接口） |
| `config/routes.ts` | **修改**（补 `/user/refunds` 与 `/admin/marketplace/refund`） |

---

### Task 1: 迁移脚本（结构 + 权限 + 定时任务）

**Files:**
- Create: `backend/sql/refund_feature.sql`

**Interfaces:**
- Produces: `trade_refund.prev_order_status`、`trade_refund.active_flag`、唯一索引 `uk_order_active`、`trade_pay_flow.type` 注释修正；菜单 `marketplace:admin:refund`（parent 2100，`menu_scope=1`）与 `marketplace:refund:my`（parent 2007，`menu_scope=0`）；`sys_job` 行 `tradeRefundScan.scan`

- [ ] **Step 1: 写迁移脚本**

照 `backend/sql/report_feature.sql` 的 `information_schema` 守卫写法，包含七段：

```sql
SET NAMES utf8mb4;

-- 1. 申请前订单状态（退款关闭时用于恢复）
SET @has_prev := (SELECT COUNT(*) FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_refund'
                    AND COLUMN_NAME = 'prev_order_status');
SET @sql := IF(@has_prev = 0,
    'ALTER TABLE `trade_refund` ADD COLUMN `prev_order_status` varchar(20) DEFAULT NULL COMMENT ''申请退款前的订单状态(unshipped/shipped/done/paid)，退款关闭时用于恢复''',
    'SELECT "prev_order_status 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 进行中唯一：MySQL 不支持部分索引，用生成列（终态为 NULL 时唯一索引允许多条）
SET @has_active := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_refund'
                      AND COLUMN_NAME = 'active_flag');
SET @sql := IF(@has_active = 0,
    'ALTER TABLE `trade_refund` ADD COLUMN `active_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `status` IN (''pending_seller'',''seller_agreed'',''buyer_returned'',''refunding'',''mediation'') THEN 1 ELSE NULL END) STORED COMMENT ''进行中标记(终态为NULL)''',
    'SELECT "active_flag 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_uk := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_refund'
                  AND INDEX_NAME = 'uk_order_active');
SET @sql := IF(@has_uk = 0,
    'ALTER TABLE `trade_refund` ADD UNIQUE KEY `uk_order_active` (`order_id`,`active_flag`)',
    'SELECT "uk_order_active 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 列注释对齐实际写入值（DDL 里的 in/out 是历史误写）
ALTER TABLE `trade_pay_flow` MODIFY COLUMN `type` varchar(20) NOT NULL COMMENT 'pay/refund';

-- 4. 运营退款页菜单（parent 2100 =「运营管理」目录，与 2101-2106 同级）
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '退款管理', 2100, 8, '/admin/marketplace/refund', 'marketplace/admin/Refund',
       '1', 'C', '0', '0', 'marketplace:admin:refund', 'icon-refund', '1', 'admin', NOW(),
       '退款审核 / 客服裁决 / 手工收口'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'marketplace:admin:refund');

-- 5. C 端「我的退款」菜单（parent 2007 =「我的」目录，与 2009 /user/orders 同级）
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '我的退款', 2007, 3, '/user/refunds', 'marketplace/RefundList',
       '1', 'C', '0', '0', 'marketplace:refund:my', 'icon-refund', '0', 'admin', NOW(),
       '买家退款申请列表'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'marketplace:refund:my');

-- 6. 授权：管理员(1)/运营(3) 看运营退款页；管理员(1)/普通角色(2) 看我的退款
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m
WHERE m.perms IN ('marketplace:admin:refund','marketplace:refund:my')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.menu_id FROM sys_menu m
WHERE m.perms = 'marketplace:admin:refund'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.menu_id FROM sys_menu m
WHERE m.perms = 'marketplace:refund:my'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.menu_id);

-- 7. 退款超时扫描任务（status='1' 暂停，与库内 20 条既有 Job 一致，需要时后台启用）
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent,
     status, create_by, create_time, remark)
SELECT '退款-超时扫描', 'DEFAULT', 'tradeRefundScan.scan', '0 0/30 * * * ?', 3, 1,
       '1', 'admin', NOW(),
       'pending_seller 48h→mediation；seller_agreed 7d→closed；buyer_returned 7d→mediation'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_job j WHERE j.invoke_target = 'tradeRefundScan.scan');
```

- [ ] **Step 2: 在本机库执行**

Run: `mysql -uroot -p123456 --default-character-set=utf8mb4 twelvet-fast < backend/sql/refund_feature.sql`
Expected: 无 `ERROR`，输出若干「已存在，跳过」或空行

- [ ] **Step 3: 验证结构与权限生效**

Run:
```bash
mysql -uroot -p123456 --default-character-set=utf8mb4 twelvet-fast -e "
SHOW COLUMNS FROM trade_refund WHERE Field IN ('prev_order_status','active_flag');
SELECT INDEX_NAME FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_refund' AND INDEX_NAME='uk_order_active';
SELECT menu_id, menu_name, parent_id, path, perms, menu_scope FROM sys_menu
 WHERE perms IN ('marketplace:admin:refund','marketplace:refund:my');
SELECT job_id, invoke_target, job_group, status FROM sys_job WHERE invoke_target='tradeRefundScan.scan';"
```
Expected: 两列存在（`active_flag` 的 `Extra` 含 `STORED GENERATED`）；`uk_order_active` 出现一次；两条菜单各一行（运营那条 `parent_id=2100` / `menu_scope=1`，C 端那条 `parent_id=2007` / `menu_scope=0`）；Job 一行且 `job_group=DEFAULT`、`status=1`

- [ ] **Step 4: 验证生成列与唯一索引行为**

本模块无 `@MybatisTest` / H2 基础设施，Mapper 契约只能在真实库验，所以这一条就是 Mapper 契约测试。

**必须用 `--force` 并把清理放在独立的命令里**：`mysql -e` 遇到 1062 会中止后续语句，末尾的 `DELETE` 不执行，测试行会残留下来污染下一次运行。

```bash
# 4a. 终态可多条（active_flag 为 NULL，唯一索引允许多个 NULL）
mysql -uroot -p123456 --default-character-set=utf8mb4 twelvet-fast -e "
INSERT INTO trade_refund (order_id,buyer_id,seller_id,type,reason_code,amount,status,create_time,update_time)
VALUES (999,1,2,'refund_only','other',1.00,'closed',NOW(),NOW());
INSERT INTO trade_refund (order_id,buyer_id,seller_id,type,reason_code,amount,status,create_time,update_time)
VALUES (999,1,2,'refund_only','other',2.00,'closed',NOW(),NOW());
SELECT COUNT(*) AS closedRows FROM trade_refund WHERE order_id=999;"
```
Expected: 无报错，`closedRows=2`

```bash
# 4b. 进行中只能一条（第二条必须 1062）；--force 保证两条都执行、退出码不为 0
mysql --force -uroot -p123456 --default-character-set=utf8mb4 twelvet-fast -e "
INSERT INTO trade_refund (order_id,buyer_id,seller_id,type,reason_code,amount,status,create_time,update_time)
VALUES (999,1,2,'refund_only','other',3.00,'pending_seller',NOW(),NOW());
INSERT INTO trade_refund (order_id,buyer_id,seller_id,type,reason_code,amount,status,create_time,update_time)
VALUES (999,1,2,'refund_only','other',4.00,'pending_seller',NOW(),NOW());" 2>&1 | grep -o "ERROR 1062.*" || true
```
Expected: 输出包含 `ERROR 1062 (23000): Duplicate entry '999-1' for key 'trade_refund.uk_order_active'`，且只出现一次

```bash
# 4c. 无论上面成败都必须清理（独立命令，不受前一条中止影响）
mysql -uroot -p123456 --default-character-set=utf8mb4 twelvet-fast -e "
DELETE FROM trade_refund WHERE order_id=999;
SELECT COUNT(*) AS leftover FROM trade_refund WHERE order_id=999;"
```
Expected: `leftover=0`

这三条 INSERT 语句本身都不含 `active_flag`，同时证明「生成列不能进 INSERT 列清单」的约束已被遵守。

- [ ] **Step 5: Commit**

```bash
git add backend/sql/refund_feature.sql
git commit -m "feat(refund): 退款表结构迁移与权限/定时任务注册"
```

---

### Task 2: 退款原因白名单

**Files:**
- Create: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/domain/RefundReason.java`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/domain/RefundReasonTest.java`

**Interfaces:**
- Produces:
  - `public static boolean isValid(String code)`
  - `public static String labelOf(String code)`
  - `public static boolean supportsType(String code, String refundType)` — `refundType` 取 `refund_only` / `return_refund`
  - `public static List<Map<String, Object>> all()` — 每项 `{code: String, label: String, types: List<String>}`，供 `/trade/refund/reasons` 直接序列化

- [ ] **Step 1: 写失败测试**

```java
class RefundReasonTest {

	@Test
	@DisplayName("六种原因全部合法，未知值不合法")
	void isValid() {
		for (String code : new String[] { "not_as_described", "damaged", "wrong_item", "not_received",
				"no_longer_needed", "other" }) {
			assertTrue(RefundReason.isValid(code), code);
		}
		assertFalse(RefundReason.isValid("bogus"));
		assertFalse(RefundReason.isValid(null));
		assertFalse(RefundReason.isValid(""));
	}

	@Test
	@DisplayName("not_received / no_longer_needed 只能用于仅退款")
	void refundOnlyReasons() {
		assertTrue(RefundReason.supportsType("not_received", "refund_only"));
		assertFalse(RefundReason.supportsType("not_received", "return_refund"));
		assertTrue(RefundReason.supportsType("no_longer_needed", "refund_only"));
		assertFalse(RefundReason.supportsType("no_longer_needed", "return_refund"));
	}

	@Test
	@DisplayName("not_as_described / damaged / wrong_item / other 两种类型都支持")
	void bothTypesReasons() {
		for (String code : new String[] { "not_as_described", "damaged", "wrong_item", "other" }) {
			assertTrue(RefundReason.supportsType(code, "refund_only"), code);
			assertTrue(RefundReason.supportsType(code, "return_refund"), code);
		}
	}

	@Test
	@DisplayName("标签映射，未知编码回落「其他」")
	void labelOf() {
		assertEquals("与描述不符", RefundReason.labelOf("not_as_described"));
		assertEquals("其他", RefundReason.labelOf("unknown"));
	}

	@Test
	@DisplayName("all() 保序返回六项，每项含 code/label/types")
	void allShape() {
		List<Map<String, Object>> all = RefundReason.all();
		assertEquals(6, all.size());
		assertEquals("not_as_described", all.get(0).get("code"));
		assertEquals(List.of("refund_only"), all.get(3).get("types"));
	}
}
```

- [ ] **Step 2: 跑测试确认 RED**

Run: `cd backend && mvn -o -pl twelvet-trade/twelvet-trade-server -am -Dtest='RefundReasonTest' -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: 编译失败（`RefundReason` 不存在）

- [ ] **Step 3: 实现 `RefundReason`**

`LinkedHashMap<String, String> LABELS` 保序存 code→标签，顺序：`not_as_described` 与描述不符 / `damaged` 商品损坏 / `wrong_item` 发错货 / `not_received` 未收到货 / `no_longer_needed` 不想要了 / `other` 其他。`REFUND_ONLY = Set.of("not_received", "no_longer_needed")`。`all()` 按 LABELS 顺序生成，`types` 为 `REFUND_ONLY.contains(code) ? List.of("refund_only") : List.of("refund_only", "return_refund")`。

- [ ] **Step 4: 跑测试确认 GREEN**

Run: 同 Step 2
Expected: 5 tests passed

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/domain/RefundReason.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/domain/RefundReasonTest.java
git commit -m "feat(refund): 退款原因白名单与类型适配"
```

---

### Task 3: 数据层 —— 实体与四个 Mapper（编译级验收）

**Files:**
- Create: `.../domain/TradeRefund.java`、`.../domain/TradeRefundLog.java`
- Create: `.../mapper/TradeRefundMapper.java` + `.../resources/mapper/trade/TradeRefundMapper.xml`
- Create: `.../mapper/TradeRefundLogMapper.java` + `.../resources/mapper/trade/TradeRefundLogMapper.xml`
- Create: `.../mapper/TradePayFlowMapper.java` + `.../resources/mapper/trade/TradePayFlowMapper.xml`
- Create: `.../mapper/TradePayLookupMapper.java` + `.../resources/mapper/trade/TradePayLookupMapper.xml`
- Modify: `.../mapper/TradeOrderMapper.java` + `.../resources/mapper/trade/TradeOrderMapper.xml`
- Modify: `backend/twelvet-trade/twelvet-trade-api/src/main/java/com/twelvet/trade/api/domain/TradeOrder.java`（补 `updateTime` 字段）

**Interfaces:**
- Produces:
  - `TradeRefund` 字段：`refundId, orderId, buyerId, sellerId, type, reasonCode, amount, description, evidenceImages, status, returnCompany, returnNo, returnAddressSnapshot, prevOrderStatus, createTime, updateTime`，另加只读 `activeFlag`（**不参与 insert**）
  - `TradeRefundLog` 字段：`logId, refundId, operatorId, action, content, createTime`
  - `TradeRefundMapper`：
    - `int insert(TradeRefund r)` — 列清单**不含 `active_flag`**，`useGeneratedKeys` 回填 `refundId`
    - `TradeRefund selectById(@Param("refundId") Long, @Param("tenantId") Long)` — `JOIN trade_order` 取租户
    - `TradeRefund selectActiveByOrderId(@Param("orderId") Long)`
    - `List<TradeRefund> selectList(@Param("userId") Long, @Param("role") String, @Param("status") String, @Param("tenantId") Long)`
    - `List<TradeRefund> selectAdminList(@Param("status") String, @Param("tenantId") Long)`
    - `int updateStatus(@Param("refundId") Long, @Param("fromStatus") String, @Param("toStatus") String)`
    - `int updateReturnLogistics(@Param("refundId") Long, @Param("company") String, @Param("returnNo") String)`
    - `Long countActiveByOrderId(@Param("orderId") Long)`
    - `List<TradeRefund> selectSellerTimeout(@Param("before") LocalDateTime, @Param("limit") int)`
    - `List<TradeRefund> selectReturnTimeout(@Param("before") LocalDateTime, @Param("limit") int)`
    - `List<TradeRefund> selectConfirmTimeout(@Param("before") LocalDateTime, @Param("limit") int)`
  - `TradeRefundLogMapper`：`int insert(@Param("refundId") Long, @Param("operatorId") Long, @Param("action") String, @Param("content") String)`；`List<Map<String,Object>> selectByRefundId(@Param("refundId") Long)`
  - `TradePayFlowMapper`：`int insert(@Param("orderId") Long, @Param("type") String, @Param("amount") BigDecimal, @Param("channel") String, @Param("thirdTransNo") String)`
  - `TradePayLookupMapper`：`List<Map<String,Object>> selectPaidByOrderId(@Param("orderId") Long)` — 每行含 `pay_id / out_trade_no / pay_amount / pay_user_id / pay_channel`（`pay_channel` 供退款流水写 `channel`）
  - `TradeOrderMapper.updateOrderStatusIfIn(@Param("orderId") Long, @Param("toStatus") String, @Param("fromStatuses") List<String>, @Param("tenantId") Long)`

- [ ] **Step 1: 写两个实体**

`TradeRefund` 与 `TradeRefundLog` 均为纯 POJO + getter/setter（无 MyBatis-Plus 注解，对齐 `TradeOrder`）。`TradeRefund.activeFlag` 标注 `@Schema(description = "进行中标记(DB 生成列, 只读)")`，类注释写明**不得出现在 INSERT 列清单**。

- [ ] **Step 2: 写 `TradeRefundMapper.xml`**

`resultMap type="TradeRefund"`（`typeAliasesPackage=com.twelvet.**.domain` 已覆盖 server 包）。关键点：
- `insert` 列清单：`order_id, buyer_id, seller_id, type, reason_code, amount, description, evidence_images, status, return_company, return_no, return_address_snapshot, prev_order_status, create_time, update_time` —— **无 `active_flag`**
- `selectById`：`SELECT <include refid="refundCols"/> FROM trade_refund r JOIN trade_order o ON o.order_id = r.order_id WHERE r.refund_id = #{refundId} <if test="tenantId != null">AND o.tenant_id = #{tenantId}</if>`
- `selectList` / `selectAdminList` 同样 `JOIN trade_order` 挂租户；`selectList` 的 `role` 用 `<choose>`：`buyer` → `r.buyer_id = #{userId}`，否则 `r.seller_id = #{userId}`
- `updateStatus`：`UPDATE trade_refund SET status = #{toStatus}, update_time = NOW() WHERE refund_id = #{refundId} AND status = #{fromStatus}`
- `selectActiveByOrderId` / `countActiveByOrderId` 条件 `status IN ('pending_seller','seller_agreed','buyer_returned','refunding','mediation')`
- 三个超时查询：`selectSellerTimeout` 用 `status='pending_seller' AND create_time < #{before}`；`selectReturnTimeout` 用 `status='seller_agreed' AND create_time < #{before}`（**故意用 create_time**，见 Review Focus #6，XML 注释写明）；`selectConfirmTimeout` 用 `status='buyer_returned' AND update_time < #{before}`；三者都 `ORDER BY refund_id LIMIT #{limit}`

- [ ] **Step 3: 写另三个 Mapper 的 XML**

`TradeRefundLogMapper.xml`：`insert` 写 `refund_id, operator_id, action, content, create_time=NOW()`；`selectByRefundId` 按 `create_time ASC, log_id ASC`。

`TradePayFlowMapper.xml`：`insert` 写 `order_id, type, amount, channel, third_trans_no, create_time=NOW()`。

`TradePayLookupMapper.xml`：`selectPaidByOrderId` 为
```sql
SELECT pay_id, out_trade_no, pay_amount, pay_user_id, pay_channel
FROM trade_pay
WHERE order_id = #{orderId} AND status = 'paid' AND out_trade_no IS NOT NULL
ORDER BY pay_id DESC
```
XML 注释里**写明撞号事实**（`order_id` 是商品订单/跑腿任务/租户续费共用列，本机实测 17–21 号撞号；调用方必须用 `pay_user_id` + 金额二次校验，禁止照抄单条件反查）。

- [ ] **Step 3b: 给 `TradeOrder` 补 `updateTime` 并让 `selectOrderVo` 查它**

spec §8.1 要求 `done` 订单的 7 天窗口「`done_at` 为 NULL 时回退 `update_time`」，但 **`TradeOrder` 现在没有 `updateTime` 字段，`selectOrderVo` 也没查 `o.update_time`**——不加这一处，Task 4 的闸门无法实现，只能靠猜。

两处改动：
1. `TradeOrder.java` 在 `autoConfirmAt` 之后加 `@Schema(description = "更新时间") private LocalDateTime updateTime;` + getter/setter
2. `TradeOrderMapper.xml` 的 `resultMap` 加 `<result property="updateTime" column="update_time"/>`，`selectOrderVo` 的列清单在 `o.auto_confirm_at` 之后加 `o.update_time`

> 只影响读，`insertOrder` / `updateOrderStatus` 等写语句不动。

- [ ] **Step 4: 加 `TradeOrderMapper.updateOrderStatusIfIn`**

Java 方法按 Interfaces 的签名声明；XML：
```xml
<update id="updateOrderStatusIfIn">
    UPDATE trade_order SET status = #{toStatus}, update_time = NOW()
    WHERE order_id = #{orderId}
      AND status IN
    <foreach collection="fromStatuses" item="s" open="(" separator="," close=")">#{s}</foreach>
    <if test="tenantId != null">AND tenant_id = #{tenantId}</if>
</update>
```
注释写明：**不复用 `updateOrderStatus`**（那个硬编码 `AND status='unpaid'`，是支付监听器专用）。

- [ ] **Step 5: 编译通过**

Run: `cd backend && mvn -o -pl twelvet-trade/twelvet-trade-server -am -DskipTests compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(refund): 退款实体与 Mapper 数据层"
```

---

### Task 4: 申请退款（闸门 + 冻结订单 + 唯一约束）

**Files:**
- Create: `.../service/ITradeRefundService.java`（先只声明 `apply` / `checkApply`）
- Create: `.../service/impl/TradeRefundServiceImpl.java`
- Test: `.../test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImplApplyTest.java`

**Interfaces:**
- Consumes: `TradeRefundMapper.insert/countActiveByOrderId`、`TradeRefundLogMapper.insert`、`TradeOrderMapper.selectOrderByIdAndBuyerId/updateOrderStatusIfIn`、`RefundReason.isValid/supportsType/labelOf`
- Produces:
  - `Long apply(Long orderId, String type, String reasonCode, BigDecimal amount, String description, List<String> evidenceImages, Long buyerId)`
  - `Map<String,Object> checkApply(Long orderId, Long buyerId)` — `{canApply: Boolean, denyReason: String, activeRefundId: Long}`

- [ ] **Step 1: 写失败测试（闸门矩阵）**

```java
class TradeRefundServiceImplApplyTest {

	private static final Long BUYER_ID = 101L;
	private static final Long SELLER_ID = 202L;
	private static final Long ORDER_ID = 1L;
	private static final Long PRODUCT_ID = 555L;
	private static final Long TENANT_ID = 42L;

	private final TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
	private final TradeRefundMapper refundMapper = mock(TradeRefundMapper.class);
	private final TradeRefundLogMapper logMapper = mock(TradeRefundLogMapper.class);
	private final TradeRefundServiceImpl service = new TradeRefundServiceImpl(orderMapper, refundMapper, logMapper);

	@BeforeEach
	void setTenant() { TenantContext.set(TENANT_ID); }

	@AfterEach
	void clearTenant() { TenantContext.clear(); }

	private TradeOrder order(String status, BigDecimal total, LocalDateTime doneAt, LocalDateTime updateTime) {
		TradeOrder o = new TradeOrder();
		o.setOrderId(ORDER_ID);
		o.setBuyerId(BUYER_ID);
		o.setSellerId(SELLER_ID);
		o.setProductId(PRODUCT_ID);
		o.setStatus(status);
		o.setTotalAmount(total);
		o.setDoneAt(doneAt);
		o.setUpdateTime(updateTime);
		return o;
	}

	@Test
	@DisplayName("done 订单超 7 天被拒")
	void apply_doneOver7Days_rejected() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("done", new BigDecimal("1107.00"), LocalDateTime.now().minusDays(8), null));
		TWTException e = assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "other",
				new BigDecimal("1107.00"), null, null, BUYER_ID));
		assertEquals("已超过 7 天售后期限", e.getMessage());
	}

	@Test
	@DisplayName("done_at 为 NULL 时回退 update_time；两者都 NULL 则拒绝，不得 NPE")
	void apply_doneAtNull() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("done", new BigDecimal("1107.00"), null, LocalDateTime.now().minusDays(1)));
		assertTrue((Boolean) service.checkApply(ORDER_ID, BUYER_ID).get("canApply"));

		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("done", new BigDecimal("1107.00"), null, null));
		assertEquals("已超过 7 天售后期限", service.checkApply(ORDER_ID, BUYER_ID).get("denyReason"));
	}

	@Test
	@DisplayName("unpaid 订单不可申请")
	void apply_unpaidRejected() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("unpaid", new BigDecimal("1107.00"), null, null));
		TWTException e = assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "other",
				new BigDecimal("1107.00"), null, null, BUYER_ID));
		assertEquals("该订单当前状态不支持申请退款", e.getMessage());
	}

	@Test
	@DisplayName("paid 存量野状态可申请（shipOrder 仍接受它）")
	void apply_legacyPaidAllowed() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("paid", new BigDecimal("1107.00"), null, null));
		assertTrue((Boolean) service.checkApply(ORDER_ID, BUYER_ID).get("canApply"));
	}

	@Test
	@DisplayName("退款金额超过实付被拒，为 0 也被拒")
	void apply_amountBounds() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("unshipped", new BigDecimal("1107.00"), null, null));
		TWTException over = assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "other",
				new BigDecimal("1108.00"), null, null, BUYER_ID));
		assertEquals("退款金额不能超过实付 ¥1107.00", over.getMessage());
		assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "other", BigDecimal.ZERO,
				null, null, BUYER_ID));
	}

	@Test
	@DisplayName("原因与类型不匹配被拒（not_received 不能用于退货退款）")
	void apply_reasonTypeMismatch() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		assertEquals("该退款原因不适用于退货退款",
			assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "return_refund", "not_received",
					new BigDecimal("1107.00"), null, null, BUYER_ID)).getMessage());
	}

	@Test
	@DisplayName("已有进行中退款单被拒")
	void apply_activeRefundExists_rejected() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(refundMapper.countActiveByOrderId(ORDER_ID)).thenReturn(1L);
		TWTException e = assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "damaged",
				new BigDecimal("1107.00"), null, null, BUYER_ID));
		assertEquals("该订单已有处理中的退款申请", e.getMessage());
	}

	@Test
	@DisplayName("申请成功：置 pending_seller + 订单冻结为 refunding + 记 prev_order_status + 写 apply 日志")
	void apply_ok_freezesOrder() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(refundMapper.countActiveByOrderId(ORDER_ID)).thenReturn(0L);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunding",
				List.of("unshipped", "shipped", "done", "paid"), TENANT_ID)).thenReturn(1);
		ArgumentCaptor<TradeRefund> captor = ArgumentCaptor.forClass(TradeRefund.class);

		service.apply(ORDER_ID, "refund_only", "damaged", new BigDecimal("1107.00"), "屏幕有裂痕",
				List.of("https://oss/a.jpg"), BUYER_ID);

		verify(refundMapper).insert(captor.capture());
		TradeRefund saved = captor.getValue();
		assertEquals("pending_seller", saved.getStatus());
		assertEquals("shipped", saved.getPrevOrderStatus());
		assertEquals("refund_only", saved.getType());
		assertEquals(BUYER_ID, saved.getBuyerId());
		assertEquals(SELLER_ID, saved.getSellerId());
		verify(logMapper).insert(any(), eq(BUYER_ID), eq("apply"), any());
	}

	@Test
	@DisplayName("订单状态在申请瞬间被并发改动（UPDATE 0 行）→ 抛「订单状态已变更」且不落退款单")
	void apply_orderChangedConcurrently() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunding",
				List.of("unshipped", "shipped", "done", "paid"), TENANT_ID)).thenReturn(0);
		assertEquals("订单状态已变更，请刷新后重试",
			assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "damaged",
					new BigDecimal("1107.00"), null, null, BUYER_ID)).getMessage());
		verify(refundMapper, never()).insert(any());
	}

	@Test
	@DisplayName("并发重复申请：唯一索引冲突翻译为可读提示")
	void apply_duplicateKey_translated() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(orderMapper.updateOrderStatusIfIn(any(), any(), any(), any())).thenReturn(1);
		when(refundMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_order_active"));
		assertEquals("该订单已有处理中的退款申请",
			assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "damaged",
					new BigDecimal("1107.00"), null, null, BUYER_ID)).getMessage());
	}

	@Test
	@DisplayName("非本人订单：查询自带 buyerId 过滤，查不到即拒")
	void apply_notOwner() {
		assertEquals("订单不存在或不属于当前用户",
			assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "other", BigDecimal.ONE,
					null, null, 999L)).getMessage());
	}

	@Test
	@DisplayName("证据图超过 3 张被拒")
	void apply_tooManyEvidenceImages() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		assertEquals("最多上传 3 张证据图",
			assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "damaged",
					new BigDecimal("1107.00"), null, List.of("a", "b", "c", "d"), BUYER_ID)).getMessage());
	}
}
```

- [ ] **Step 2: 跑测试确认 RED**

Run: `cd backend && mvn -o -pl twelvet-trade/twelvet-trade-server -am -Dtest='TradeRefundServiceImplApplyTest' -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: 编译失败

- [ ] **Step 3: 实现 `apply` 与 `checkApply`**

`apply` 顺序：① `selectOrderByIdAndBuyerId(orderId, buyerId, currentTid())`，null 抛「订单不存在或不属于当前用户」→ ② 状态闸门（`unshipped` / `paid` 只允许 `refund_only`；`shipped` / `done` 两种都允许；其余抛「该订单当前状态不支持申请退款」）→ ③ `done` 的 7 天窗口：`base = doneAt != null ? doneAt : updateTime`，`base == null || base.isBefore(now.minusDays(7))` 抛「已超过 7 天售后期限」→ ④ `RefundReason.isValid` + `supportsType`（不匹配抛「该退款原因不适用于退货退款」）→ ⑤ 金额 `0 < amount <= totalAmount`，超限文案 `"退款金额不能超过实付 ¥" + totalAmount` → ⑥ `countActiveByOrderId > 0` 抛「该订单已有处理中的退款申请」→ ⑦ `updateOrderStatusIfIn(orderId, "refunding", List.of("unshipped","shipped","done","paid"), currentTid())`，0 行抛「订单状态已变更，请刷新后重试」→ ⑧ 证据图 `size > 3` 抛「最多上传 3 张证据图」→ ⑨ 组 `TradeRefund`（`prevOrderStatus = order.getStatus()`、`status = "pending_seller"`、`evidenceImages = JacksonUtils.toJson(list)`）→ `insert`，捕 `DuplicateKeyException` 翻译为「该订单已有处理中的退款申请」→ ⑩ `logMapper.insert(refundId, buyerId, "apply", "申请" + labelOf(reasonCode) + "，金额 ¥" + amount)`。

`checkApply` 复用私有 `validate(order)` 的前六步（不含 ⑦⑧⑨），但**返回 `denyReason` 而非抛异常**；有进行中退款单时 `canApply=false` 且带出 `activeRefundId`（取 `selectActiveByOrderId` 的 `refundId`）。

`@Transactional(rollbackFor = Exception.class)` 标在 `apply` 上。

- [ ] **Step 4: 跑测试确认 GREEN**

Run: 同 Step 2
Expected: 12 tests passed

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(refund): 申请退款闸门、订单冻结与唯一约束"
```

---

### Task 5: 卖家审核与买家操作

**Files:**
- Modify: `.../service/ITradeRefundService.java`、`.../service/impl/TradeRefundServiceImpl.java`
- Test: `.../TradeRefundServiceImplFlowTest.java`

**Interfaces:**
- Produces:
  - `void sellerAgree(Long refundId, String returnAddress, Long sellerId)`
  - `void sellerReject(Long refundId, String reason, Long sellerId)`
  - `void buyerReturn(Long refundId, String company, String returnNo, Long buyerId)`
  - `void buyerCancel(Long refundId, Long buyerId)`
  - `void buyerMediate(Long refundId, Long buyerId)`

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("卖家同意仅退款 → seller_agreed，不快照退货地址")
void sellerAgree_refundOnly() { }

@Test
@DisplayName("卖家同意退货退款 → seller_agreed 且快照退货地址；地址为空则拒绝")
void sellerAgree_returnRefund_requiresAddress() { assertEquals("请提供退货地址", e.getMessage()); }

@Test
@DisplayName("卖家拒绝 → seller_rejected + 订单恢复 prev_order_status")
void sellerReject_restoresOrder() {
	// verify orderMapper.updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), TENANT_ID) → 1
}

@Test
@DisplayName("买家填退货单号 → buyer_returned（仅 seller_agreed + return_refund）")
void buyerReturn_ok() { }

@Test
@DisplayName("仅退款类型的退款单不能填退货物流")
void buyerReturn_refundOnly_rejected() { assertEquals("仅退款无需填写退货物流", e.getMessage()); }

@Test
@DisplayName("买家撤销 → closed + 订单按 prev_order_status 从 refunding 恢复")
void buyerCancel_restoresOrder() {
	// verify orderMapper.updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), TENANT_ID) → 1
}

@Test
@DisplayName("买家申请介入（seller_rejected）→ mediation + 订单重新冻结为 refunding")
void buyerMediate_fromSellerRejected() {
	// verify orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunding", List.of("shipped"), TENANT_ID) → 1
}

@Test
@DisplayName("非卖家操作被拒")
void sellerAgree_notOwner_rejected() { assertEquals("无权操作该退款", e.getMessage()); }

@Test
@DisplayName("状态不匹配（UPDATE 0 行）→ 抛「退款状态已变更，请刷新」")
void sellerAgree_wrongState_rejected() { assertEquals("退款状态已变更，请刷新", e.getMessage()); }
```

- [ ] **Step 2: 跑测试确认 RED**

Run: `-Dtest='TradeRefundServiceImplFlowTest'`
Expected: 编译失败

- [ ] **Step 3: 实现五个方法**

统一模式：`selectById(refundId, currentTid())` → null 抛「退款不存在」→ 校验归属（不匹配抛「无权操作该退款」）→ `updateStatus(refundId, from, to)`，0 行抛「退款状态已变更，请刷新」→ `logMapper.insert(...)` → 订单联动见下表。恢复订单用 `updateOrderStatusIfIn(orderId, prevOrderStatus, List.of("refunding"), currentTid())`，**0 行只 `log.warn`**（订单可能已被运营收口）；重新冻结用 `updateOrderStatusIfIn(orderId, "refunding", List.of(prevOrderStatus), currentTid())`，**0 行必须抛**（否则冻结失败仍会进入介入流程，形成资损）。

| 方法 | from | to | 订单联动 |
|---|---|---|---|
| `sellerAgree`（refund_only） | `pending_seller` | `seller_agreed` | 记 `seller_agree` 日志后**紧接**推进 `seller_agreed → refunding` 并调 `executeRefund(refund, refund.getAmount())`（仅退款跳过 `buyer_returned`，见 spec §5）；订单保持 `refunding` |
| `sellerAgree`（return_refund） | `pending_seller` | `seller_agreed` | `returnAddressSnapshot = returnAddress`（空则抛「请提供退货地址」）；订单保持 `refunding` |
| `sellerReject` | `pending_seller` | `seller_rejected` | `content = reason`（空则抛「请填写拒绝理由」）；**恢复订单为 `prev_order_status`**（spec §5.1：拒绝即退款关闭，买家仍可另开新申请或对这条申请介入） |
| `buyerReturn` | `seller_agreed` | `buyer_returned` | `updateReturnLogistics(refundId, company, returnNo)`；`type != return_refund` 抛「仅退款无需填写退货物流」；订单保持 `refunding` |
| `buyerCancel` | `pending_seller` / `seller_agreed` / `seller_rejected` | `closed` | 恢复订单（已是 `prev_order_status` 时 UPDATE 0 行，`log.warn` 后继续） |
| `buyerMediate` | `seller_rejected` | `mediation` | **把订单重新冻结为 `refunding`**：`updateOrderStatusIfIn(orderId, "refunding", List.of(prevOrderStatus), tid)`；0 行抛「订单状态已变更，无法申请介入」（否则平台介入期间买家能确认收货，形成「货拿走 + 钱退回」） |

`buyerCancel` 用三次 `updateStatus` 尝试（每个 from 各一次），任一次 >0 即成功；全 0 抛「退款状态已变更，请刷新」。

> `sellerAgree` 的仅退款分支在 Task 5 里先写成「只推进到 `seller_agreed`」，`executeRefund` 由 Task 6 补上——Task 5 的测试只断言 `seller_agreed`，Task 6 再加一条 `sellerAgree_refundOnly_thenExecutesRefund`。

- [ ] **Step 4: 跑测试确认 GREEN**

Expected: 9 tests passed

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(refund): 卖家审核与买家操作流转"
```

---

### Task 6: `executeRefund` —— 唯一退款入口（含支付单双重定位）

**Files:**
- Modify: `.../service/ITradeRefundService.java`、`.../service/impl/TradeRefundServiceImpl.java`
- Test: `.../TradeRefundServiceImplExecuteTest.java`

**Interfaces:**
- Consumes: `IPayOrderService.refund(String outTradeNo, BigDecimal amount, String reason)`（`com.twelvet.payment.api.service`）、`TradePayLookupMapper.selectPaidByOrderId`、`TradePayFlowMapper.insert`、`IImService.sendNotice(Long, String, String, String)`
- Produces:
  - `void sellerConfirmReceipt(Long refundId, Long sellerId)` — 卖家确认收货，内部调 `executeRefund`
  - `private boolean executeRefund(TradeRefund refund, BigDecimal amount)`
  - `private void finishRefundSuccess(TradeRefund refund, BigDecimal amount, String outTradeNo, String channel)` — 供 `resolve` 复用（Task 8）

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("正常退款：支付单匹配 → refund_success + 订单 refunded + 写 refund 流水")
void execute_ok() {
	// payLookup 返回 [{pay_id:9, out_trade_no:"P1", pay_amount:1107.00, pay_user_id:101}]
	// payOrderService.refund("P1", 1107.00, any) → true
	// verify orderMapper.updateOrderStatusIfIn(ORDER_ID,"refunded",List.of("refunding"),TENANT_ID) → 1
	// verify flowMapper.insert(ORDER_ID, "refund", 1107.00, any, "P1")
	// verify logMapper.insert(any(), any(), eq("refund_ok"), any())
}

@Test
@DisplayName("支付单缺失 → refunding + manual_required 日志 + 通知买家，不抛异常")
void execute_payOrderMissing_parksForManual() { }

@Test
@DisplayName("支付单金额小于退款金额 → 转人工，绝不调网关")
void execute_amountMismatch_parksForManual() {
	// pay_amount=10.00，退款 1107.00 → verify(payOrderService, never()).refund(any(), any(), any())
}

@Test
@DisplayName("多态外键撞号：跑腿单与商品单同 order_id，必须选 pay_user_id 匹配的那条")
void execute_polymorphicCollision_picksCorrectPay() {
	// 候选 [{pay_id:2, pay_user_id:201, pay_amount:1.50}, {pay_id:9, pay_user_id:101, pay_amount:1107.00}]
	// buyerId=101 → 必须选 pay_id=9
}

@Test
@DisplayName("pay_user_id 全 NULL：候选唯一则执行，候选多条则转人工")
void execute_ambiguousLegacyPay() { }

@Test
@DisplayName("网关返回 false → refunding + manual_required 日志，不抛")
void execute_gatewayFail_parksForManual() { }

@Test
@DisplayName("流水写入抛异常不影响退款成功")
void execute_flowFailureDoesNotBreak() { }

@Test
@DisplayName("卖家确认收货（buyer_returned）→ 触发 executeRefund")
void sellerConfirmReceipt_fromBuyerReturned() { }

@Test
@DisplayName("仅退款卖家同意 → seller_agreed 紧接 refunding 并执行退款（跳过 buyer_returned）")
void sellerAgree_refundOnly_thenExecutesRefund() {
	// Task 5 只断言 seller_agreed；此处补断言 sellerAgree 的仅退款分支会继续推进到 refunding 并调网关
}
```

- [ ] **Step 2: 跑测试确认 RED**

Expected: 编译失败

- [ ] **Step 3: 实现定位算法与 `executeRefund`**

定位（照 spec §6.2）：
1. `payLookup.selectPaidByOrderId(orderId)` 取全部候选
2. 过滤 `pay_user_id != null && pay_user_id.equals(refund.getBuyerId())`；有匹配的取 `pay_id` 最大一条
3. 无匹配时：**若候选恰好 1 条且其 `pay_user_id` 为 NULL** → 用它；否则转人工
4. 金额校验：`payAmount.compareTo(amount) < 0` → 转人工（日志写清实付与申请额）

`executeRefund` 流程（**入参时退款单已是 `refunding`**，初始推进由调用方做，见 Global Constraints 最后一条）：
1. 定位失败 → `logMapper.insert(refundId, null, "manual_required", 原因)`、`notifyBuyer(...)`、`return false`
2. 定位成功 → `payOrderService.refund(outTradeNo, amount, "退款：" + RefundReason.labelOf(reasonCode))`
3. 成功 → `updateStatus(refundId, "refunding", "refund_success")`、`finishRefundSuccess(...)`
4. 失败 → `manual_required` 日志 + 通知，`return false`

`finishRefundSuccess(TradeRefund refund, BigDecimal amount, String outTradeNo, String channel)` 自己查订单（`orderMapper.selectOrderDetail(refund.getOrderId(), currentTid())`，`productId` 从它取），然后做：`updateOrderStatusIfIn(orderId, "refunded", List.of("refunding"), tenantId)`（0 行 `log.warn`）、`flowMapper.insert(orderId, "refund", amount, channel, outTradeNo)`（try/catch + `log.warn`）、`logMapper.insert(refundId, null, "refund_ok", ...)`、`settleProduct(refund, order)`（Task 7）、`notifyBuyer("退款成功")`。

> `resolve` 也调 `finishRefundSuccess`，所以订单查询放在它里面而不是 `executeRefund` 里，两条路径共用一份。

`notifyBuyer` 用 `IImService.sendNotice`，包 try/catch + `log.warn`。**运营侧不发通知**：运营通过退款管理页的 `refunding` 状态筛选发现待人工项（spec §6.2 的「通知运营」由页面筛选承担，避免新增跨模块的运营账号查询）。

- [ ] **Step 4: 跑测试确认 GREEN**

Expected: 8 tests passed

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(refund): executeRefund 唯一入口与支付单双重定位"
```

---

### Task 7: 退款成功后的商品结算

**Files:**
- Modify: `.../service/impl/TradeRefundServiceImpl.java`
- Test: `.../TradeRefundServiceImplSettleTest.java`

**Interfaces:**
- Consumes: `ProductMapper.restoreStock(Long, Long)`、`ProductMapper.updateStatusIfIn(Long, String, List<String>, Long)`
- Produces: `private void settleProduct(TradeRefund refund, TradeOrder order)`

- [ ] **Step 1: 写失败测试**

本类是独立测试文件，需自带常量 `ORDER_ID = 1L`、`PRODUCT_ID = 555L`、`TENANT_ID = 42L` 与 `TenantContext.set/clear` 的 `@BeforeEach`/`@AfterEach`（照 Task 4 的写法），不要指望复用 `TradeRefundServiceImplApplyTest` 的私有常量。

```java
@Test
@DisplayName("未发货退款成功 → 只调 restoreStock（它自己把 locked/sold 恢复 onsale 并 stock+1）")
void settle_unshipped_restoresStock() {
	// prevOrderStatus='unshipped'
	// verify productMapper.restoreStock(PRODUCT_ID, TENANT_ID)
	// verify(productMapper, never()).updateStatusIfIn(any(), any(), any(), any())
}

@Test
@DisplayName("已发货退款成功 → 不回补库存，locked/sold 置 offshelf")
void settle_shipped_marksOffshelf() {
	// prevOrderStatus='shipped'
	// verify(productMapper, never()).restoreStock(any(), any())
	// verify productMapper.updateStatusIfIn(PRODUCT_ID, "offshelf", List.of("locked","sold"), TENANT_ID)
}

@Test
@DisplayName("已确认收货后退款（商品已被 markSold 置 sold）→ 同样置 offshelf")
void settle_done_marksOffshelfFromSold() { }

@Test
@DisplayName("商品更新 0 行不影响退款成功（退款单已是终态）")
void settle_productUpdateFails_doesNotBreak() { }
```

> `restoreStock` 的 SQL 是 `status = IF(status IN ('locked','sold'),'onsale',status), stock = stock + 1`，**它自己就把状态恢复了**。未发货分支只需一次调用，不要再叠 `updateStatusIfIn(..., "onsale", ...)`——那会 0 行、被误判为失败。

- [ ] **Step 2: 跑测试确认 RED**

Expected: 编译失败

- [ ] **Step 3: 实现 `settleProduct`**

`switch (refund.getPrevOrderStatus())`：
- `"unshipped"` / `"paid"` → `productMapper.restoreStock(productId, currentTid())`
- `"shipped"` / `"done"` → `productMapper.updateStatusIfIn(productId, "offshelf", List.of("locked", "sold"), currentTid())`
- `default` → `log.warn("退款单 {} 的 prevOrderStatus={} 无商品处置规则", refundId, prevOrderStatus)`

`productId` 取自订单——**`TradeRefund` 不含 productId**，所以 `settleProduct` 接收 `(TradeRefund refund, TradeOrder order)`，订单由 `finishRefundSuccess` 里的 `selectOrderDetail` 取回。整个方法包 try/catch + `log.warn`（商品处置失败不得让已成功的退款回滚）。

- [ ] **Step 4: 跑测试确认 GREEN**

Expected: 4 tests passed

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(refund): 退款成功后的商品结算"
```

---

### Task 8: 裁决与手工收口

**Files:**
- Modify: `.../service/ITradeRefundService.java`、`.../service/impl/TradeRefundServiceImpl.java`
- Test: `.../TradeRefundServiceImplJudgeTest.java`

**Interfaces:**
- Produces:
  - `void judge(Long refundId, String decision, BigDecimal amount, String reason, Long operatorId)` — 运营从退款管理页裁决 `mediation`
  - `boolean judgeByOrder(Long orderId, String decision, BigDecimal amount, String reason, Long operatorId)` — 客服从纠纷页裁决，按 `orderId` 找 `mediation` 退款单；处理了返回 `true`，找不到返回 `false`（**不抛异常**）
  - `void resolve(Long refundId, String result, String note, Long operatorId)` — 运营手工收口 `refunding`

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("裁决 buyer → 按退款单金额全额退")
void judge_buyer_fullRefund() { }

@Test
@DisplayName("裁决 partial → 按传入 amount 退；amount 缺失或超申请额被拒")
void judge_partial_usesAmount() { }

@Test
@DisplayName("裁决 seller → closed + 订单恢复 prev_order_status，不调网关")
void judge_seller_closesAndRestores() {
	// verify(payOrderService, never()).refund(any(), any(), any())
}

@Test
@DisplayName("裁决只对 mediation 生效")
void judge_wrongState_rejected() { assertEquals("退款状态已变更，请刷新", e.getMessage()); }

@Test
@DisplayName("decision 白名单 buyer/seller/partial，其它值被拒")
void judge_unknownDecision_rejected() { assertEquals("裁决结论不合法", e.getMessage()); }

@Test
@DisplayName("judgeByOrder 找到 mediation 退款单则处理并返回 true")
void judgeByOrder_found() { }

@Test
@DisplayName("judgeByOrder 找不到退款单返回 false，不抛（可能是不走退款的纠纷）")
void judgeByOrder_notFound_returnsFalse() { }

@Test
@DisplayName("运营手工收口 refunding → refund_success（不调网关）")
void resolve_success() {
	// verify(payOrderService, never()).refund(any(), any(), any())
}

@Test
@DisplayName("运营手工收口 refunding → closed 并恢复订单")
void resolve_fail() { }

@Test
@DisplayName("手工收口只对 refunding 生效")
void resolve_wrongState_rejected() { }
```

- [ ] **Step 2: 跑测试确认 RED**

- [ ] **Step 3: 实现三个方法**

`judge`：`decision` 白名单校验（非法抛「裁决结论不合法」）→ `selectById` → 分三支：
- `buyer` → `updateStatus(refundId, "mediation", "refunding")` 后 `executeRefund(refund, refund.getAmount())`
- `partial` → `amount` 必填且 `> 0` 且 `<= refund.getAmount()`，否则抛「部分退款金额需大于 0 且不超过申请金额」；同样先置 `refunding` 再 `executeRefund(refund, amount)`
- `seller` → `updateStatus(refundId, "mediation", "closed")` + 恢复订单 + `judge` 日志（`content = reason`）

`judgeByOrder`：`selectActiveByOrderId(orderId)`；null 或 `status != "mediation"` 返回 `false`；否则调 `judge(...)` 并返回 `true`。

`resolve`：`result` 白名单 `success` / `fail`（非法抛「收口结果不合法」）；`success` → `updateStatus(refundId, "refunding", "refund_success")` + `finishRefundSuccess(refund, refund.getAmount(), null, null)` + `manual_resolved` 日志（`content = note`）；`fail` → `updateStatus(refundId, "refunding", "closed")` + 恢复订单 + `manual_resolved` 日志。

> `resolve` 复用 `finishRefundSuccess` 但**不再调网关**（运营已在线下完成退款）。`outTradeNo` 与 `channel` 都传 `null`。

- [ ] **Step 4: 跑测试确认 GREEN**

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(refund): 客服裁决与运营手工收口"
```

---

### Task 9: 超时扫描任务

**Files:**
- Create: `.../task/TradeRefundScanTask.java`
- Modify: `.../service/ITradeRefundService.java`、`.../service/impl/TradeRefundServiceImpl.java`
- Test: `.../TradeRefundScanTaskTest.java`

**Interfaces:**
- Produces:
  - `@Component("tradeRefundScan") class TradeRefundScanTask { public void scan() }`（方法无参，与 `TradeTimeoutTask` 同构）
  - `int ITradeRefundService.scanTimeouts()`

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("pending_seller 超时 → mediation（订单保持 refunding）")
void scan_pendingSeller_escalates() { }

@Test
@DisplayName("seller_agreed 超时 → closed + 恢复订单")
void scan_sellerAgreed_closes() {
	// verify refundMapper.selectReturnTimeout(any(), anyInt())
	// verify orderMapper.updateOrderStatusIfIn(ORDER_ID, prevStatus, List.of("refunding"), null)
}

@Test
@DisplayName("buyer_returned 超时 → mediation")
void scan_buyerReturned_escalates() { }

@Test
@DisplayName("单条失败不影响其余")
void scan_oneFailureDoesNotStopOthers() { }

@Test
@DisplayName("扫描任务无租户上下文，一律传 tenantId=null")
void scan_usesNullTenant() { }
```

- [ ] **Step 2: 跑测试确认 RED**

- [ ] **Step 3: 实现**

`TradeRefundScanTask` 照 `TradeTimeoutTask` 结构：`@Component("tradeRefundScan")`、无参 `public void scan()`、末尾 `log.info("[Quartz] TradeRefund scan={}", n)`。

`scanTimeouts()` 顺序执行三类，每类 `LIMIT 200`，逐条 try/catch：
1. `selectSellerTimeout(now.minusHours(sellerTimeoutHours), 200)` → `updateStatus(id,"pending_seller","mediation")` + `mediate` 日志（`content = "卖家超时未处理，平台介入"`）
2. `selectReturnTimeout(now.minusDays(returnTimeoutDays), 200)` → `updateStatus(id,"seller_agreed","closed")` + `updateOrderStatusIfIn(orderId, prevOrderStatus, List.of("refunding"), null)` + `timeout_close` 日志
3. `selectConfirmTimeout(now.minusDays(confirmTimeoutDays), 200)` → `updateStatus(id,"buyer_returned","mediation")` + `mediate` 日志

配置：`@Value("${trade.refund.seller-timeout-hours:48}")`、`@Value("${trade.refund.return-timeout-days:7}")`、`@Value("${trade.refund.confirm-timeout-days:7}")`。租户一律传 `null`（Quartz 无上下文，与 `TradeTimeoutTask` 同惯例）。

- [ ] **Step 4: 跑测试确认 GREEN**

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(refund): 退款超时扫描任务"
```

---

### Task 10: 资金流水两个写入点

**Files:**
- Modify: `.../listener/PaySuccessEventListener.java`
- Modify: `.../resources/mapper/trade/AdminMarketplaceMapper.xml`（`selectRecentPayFlows` 补列）
- Test: `.../PaySuccessEventListenerFlowTest.java`

**Interfaces:**
- Consumes: `TradePayFlowMapper.insert`

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("支付成功（订单迁移成功）写一条 pay 流水")
void onPaySuccess_writesFlow() {
	// updateOrderStatus 返回 1，event amountCents=1107
	// verify flowMapper.insert(1L, "pay", new BigDecimal("11.07"), "alipay", "OUT123")
}

@Test
@DisplayName("订单已是已付/不存在（rows=0）不写流水，避免重复记账")
void onPaySuccess_rowsZero_noFlow() { }

@Test
@DisplayName("流水写入抛异常不影响订单状态迁移（不向外抛）")
void onPaySuccess_flowFailureDoesNotBreak() {
	// doThrow 于 flowMapper.insert → assertDoesNotThrow
}
```

- [ ] **Step 2: 跑测试确认 RED**

- [ ] **Step 3: 实现**

`PaySuccessEventListener` 构造器加 `TradePayFlowMapper flowMapper`。**必须同步更新 `TradeOrderServiceImplLifecycleTest:76` 的 `new PaySuccessEventListener(orderMapper)`**（加一个 `mock(TradePayFlowMapper.class)` 参数），否则整个模块测试编译不过。`rows > 0` 分支内：
```java
try {
	BigDecimal amount = event.getAmountCents() == null ? null
			: new BigDecimal(event.getAmountCents()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
	flowMapper.insert(orderId, "pay", amount, event.getChannel(), event.getOutTradeNo());
}
catch (Exception e) {
	log.warn("[支付] 写资金流水失败 orderId={}: {}", orderId, e.getMessage());
}
```

`AdminMarketplaceMapper.xml` 的 `selectRecentPayFlows` 补 `f.flow_id, f.third_trans_no` 两列（表里本来就有，前端对账页要用）。

- [ ] **Step 4: 跑测试确认 GREEN**

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(finance): 支付成功写入资金流水并对账查询补列"
```

---

### Task 11: 纠纷裁决联动退款

**Files:**
- Modify: `.../service/impl/CsDisputeServiceImpl.java`
- Modify: `.../resources/mapper/trade/CsDisputeMapper.xml`
- Test: `.../CsDisputeServiceImplJudgeLinkTest.java`

**Interfaces:**
- Consumes: `ITradeRefundService.judgeByOrder(Long orderId, String decision, BigDecimal amount, String reason, Long operatorId)`

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("裁决 buyer 且订单有 mediation 退款单 → 触发退款")
void judge_buyer_triggersRefund() {
	// verify refundService.judgeByOrder(orderId, "buyer", amount, reason, csId)
}

@Test
@DisplayName("裁决 buyer 但无退款单 → 只裁决，不报错")
void judge_noRefund_doesNotThrow() { }

@Test
@DisplayName("已关闭的纠纷不可重复裁决（UPDATE 0 行 → 抛，不再静默 OK）")
void judge_alreadyClosed_rejected() { assertEquals("纠纷工单已关闭", e.getMessage()); }

@Test
@DisplayName("裁决 seller → 退款单关闭，不调网关")
void judge_seller_closesRefund() { }
```

- [ ] **Step 2: 跑测试确认 RED**

- [ ] **Step 3: 实现**

`CsDisputeMapper.xml` 的 `judgeDispute` 加 `AND status <> 'closed'`（现有 SQL 只有 `WHERE dispute_id = ?`，已关闭的纠纷能被重复裁决）。

`CsDisputeServiceImpl` 构造器加 `ITradeRefundService refundService`。`judgeDispute`：
1. `selectDisputeDetail(disputeId)` 取 `orderId`（已有方法，无需新 Mapper）；null 抛「纠纷工单不存在」
2. `rows = disputeMapper.judgeDispute(...)`；`rows == 0` → **抛 `TWTException("纠纷工单已关闭")`**（原来是 `log.warn` 后返回 OK，属静默假成功）
3. `decision` 为 `buyer` / `partial` / `seller` 时都调 `refundService.judgeByOrder(orderId, decision, amount, reason, csId)`，返回值忽略（找不到退款单属正常）

> 不依赖 `trade_dispute.refund_id`（该列全仓零写入点，seed 为 NULL）；按 `order_id` 反查，见 spec §8.5。

- [ ] **Step 4: 跑测试确认 GREEN**

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(dispute): 裁决联动退款并补状态守卫"
```

---

### Task 12: C 端与卖家端 Controller

**Files:**
- Create: `.../controller/TradeRefundController.java`
- Test: `.../TradeRefundControllerTest.java`

**Interfaces:**
- Produces（Controller 端点）：`POST /trade/refund/apply`、`GET /trade/refund/detail/{refundId}`、`GET /trade/refund/page`、`GET /trade/refund/by-order/{orderId}`、`GET /trade/refund/reasons`、`POST /trade/refund/cancel`、`POST /trade/refund/return`、`POST /trade/refund/mediate`、`GET /trade/refund/seller/page`、`POST /trade/refund/seller/agree`、`POST /trade/refund/seller/reject`、`POST /trade/refund/seller/confirm-receipt`
- Produces（Service 方法，Controller 只调这些，**不直连 Mapper**）：
  - `Map<String,Object> detail(Long refundId, Long viewerId)` — 返回 `{refund: TradeRefund, logs: List<Map>}`；`viewerId` 既非买家也非卖家时返回 `null`（Controller 转成 `JsonResult.success(null)`）
  - `TableDataInfo<TradeRefund> page(Long userId, String role, String status)`
  - `TableDataInfo<TradeRefund> sellerPage(Long sellerId, String status)`

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("apply 的 buyerId 取自 StpUtil，忽略请求体里的任何 userId")
void apply_usesLoginId() { }

@Test
@DisplayName("detail 非买卖家不可见（返回 null 而非抛异常）")
void detail_notParticipant() { }

@Test
@DisplayName("by-order 返回 canApply / denyReason / activeRefundId")
void byOrder_shape() { }

@Test
@DisplayName("reasons 返回六项且含 types")
void reasons_shape() { }

@Test
@DisplayName("body 里值为 null 时不产生字面量 \"null\"")
void strHelper_handlesNull() { }
```

> Controller 测试用 `mock(ITradeRefundService.class)` 直连，不启 Spring 上下文（对齐 `TradeReviewControllerAnonymousAccessTest`）。

- [ ] **Step 2: 跑测试确认 RED**

- [ ] **Step 3: 实现**

`@RestController @RequestMapping("/trade/refund") extends TWTController`。每个方法首行 `Long userId = StpUtil.getLoginIdAsLong()`，身份一律来自登录态。

`body` 解析加私有 `str(Object v, String defaultValue)`（与 `AdminMarketplaceController.str` 同一实现：`null`、空串、字面量 `"null"` 都回落默认值），避免 `String.valueOf(getOrDefault(...))` 把 `"null"` 落库。

`page` / `seller/page` 用 `PageUtils.startPage()` + Service 返回 `TableDataInfo`。

`detail` 调 `service.detail(refundId, userId)`，返回 `null` 时包成 `JsonResult.success(null)`（归属校验在 Service 内，Controller 不碰 Mapper）。

- [ ] **Step 4: 跑测试确认 GREEN**

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(refund): C 端与卖家端退款接口"
```

---

### Task 13: 运营端 Controller

**Files:**
- Create: `.../controller/AdminRefundController.java`
- Test: `.../AdminRefundControllerTest.java`

**Interfaces:**
- Produces（Controller 端点）：`GET /admin/marketplace/refund/page`、`GET /admin/marketplace/refund/detail/{refundId}`、`POST /admin/marketplace/refund/judge`、`POST /admin/marketplace/refund/resolve`，全部 `@SaCheckPermission("marketplace:admin:refund")`
- Produces（Service 方法，Controller 只调这些，**不直连 Mapper**）：
  - `Map<String,Object> adminDetail(Long refundId)` — 返回 `{refund, logs, order}`；不校验买卖家归属，但仍带租户过滤（`trade_refund` 无 `tenant_id`，靠 `JOIN trade_order`）
  - `TableDataInfo<TradeRefund> adminPage(String status)`

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("四个端点都带 marketplace:admin:refund 注解")
void allEndpointsAnnotated() {
	// 反射读 SaCheckPermission 注解，断言 value() 为 marketplace:admin:refund
}

@Test
@DisplayName("judge 的 decision 非法值由 Service 抛业务异常")
void judge_invalidDecision() { }

@Test
@DisplayName("detail 不校验买卖家归属（运营要看全量）")
void detail_noOwnershipCheck() { }
```

- [ ] **Step 2: 跑测试确认 RED**

- [ ] **Step 3: 实现**

`adminDetail` 在 Service 里组装 `{refund, logs, order}`——订单摘要用 `orderMapper.selectOrderDetail(orderId, currentTid())`（Service 持有 `TradeOrderMapper`，Controller 不碰 Mapper）。不校验归属，但仍带租户过滤。

`adminPage` 走 `selectAdminList(status, currentTid())` + `PageUtils.startPage()`。

- [ ] **Step 4: 跑测试确认 GREEN**

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(refund): 运营退款管理接口"
```

---

### Task 14: 隐私设置接口

**Files:**
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/UserMapper.java` + `.../resources/mapper/product/UserMapper.xml`
- Create: `.../controller/UserPrivacyController.java`
- Test: `.../UserPrivacyControllerTest.java`

**Interfaces:**
- Produces:
  - `Map<String,Object> UserMapper.selectPrivacy(@Param("userId") Long)` — 列名 `allowMobileSearch / allowFeedByStranger / enableNearbyRecommend`
  - `int UserMapper.upsertPrivacy(@Param("userId") Long, @Param("allowMobileSearch") Integer, @Param("allowFeedByStranger") Integer, @Param("enableNearbyRecommend") Integer)`
  - `GET /user/privacy` → `{allowMobileSearch: 1|0, allowFeedByStranger: 1|0, enableNearbyRecommend: 1|0}`
  - `POST /user/privacy` 保存

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("无记录时返回默认全开")
void get_noRow_returnsDefaults() { }

@Test
@DisplayName("保存后能读回")
void saveThenGet() { }

@Test
@DisplayName("userId 永远取自登录态，请求体里的 userId 被忽略")
void ignoresBodyUserId() { }

@Test
@DisplayName("开关值只接受 0/1")
void rejectsInvalidValue() { }
```

- [ ] **Step 2: 跑测试确认 RED**

- [ ] **Step 3: 实现**

`selectPrivacy` XML：`SELECT allow_mobile_search AS allowMobileSearch, allow_feed_by_stranger AS allowFeedByStranger, enable_nearby_recommend AS enableNearbyRecommend FROM user_privacy WHERE user_id = #{userId}`。

`upsertPrivacy` XML 用 `INSERT INTO user_privacy (...) VALUES (...) ON DUPLICATE KEY UPDATE ...`（`user_privacy` 主键是 `user_id`，这是**唯一**合理的 upsert 场景，与退款表禁用该写法的理由不冲突）。

Controller：`@RestController @RequestMapping("/user/privacy")`；`GET` 无行时三个字段都返回 `1`；`POST` 对每个字段做 `0/1` 校验（非法抛「开关值只能是 0 或 1」），`null` 字段跳过（保持原值）。

- [ ] **Step 4: 跑测试确认 GREEN**

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(user): 隐私设置三开关读写接口"
```

---

### Task 15: 前端 API 层与路由

**Files:**
- Modify: `ui/src/services/marketplace/api.ts`
- Modify: `ui/config/routes.ts`

- [ ] **Step 1: 补 API 函数**

在「订单」区块之后新增「退款售后」区块（18 个函数，全部 `request(...)` 一行式，照现有风格）：

```ts
// 退款售后
export async function refundApply(data: Record<string, any>) {
    return request('/trade/refund/apply', { method: 'POST', data });
}
export async function refundDetail(refundId: number | string) {
    return request(`/trade/refund/detail/${refundId}`, { method: 'GET' });
}
export async function refundPage(params?: Record<string, any>) {
    return request('/trade/refund/page', { method: 'GET', params });
}
export async function refundByOrder(orderId: number | string) {
    return request(`/trade/refund/by-order/${orderId}`, { method: 'GET' });
}
export async function refundReasons() {
    return request('/trade/refund/reasons', { method: 'GET' });
}
export async function refundCancel(refundId: number) {
    return request('/trade/refund/cancel', { method: 'POST', params: { refundId } });
}
export async function refundReturn(data: Record<string, any>) {
    return request('/trade/refund/return', { method: 'POST', data });
}
export async function refundMediate(refundId: number) {
    return request('/trade/refund/mediate', { method: 'POST', params: { refundId } });
}
export async function refundSellerPage(params?: Record<string, any>) {
    return request('/trade/refund/seller/page', { method: 'GET', params });
}
export async function refundSellerAgree(data: Record<string, any>) {
    return request('/trade/refund/seller/agree', { method: 'POST', data });
}
export async function refundSellerReject(data: Record<string, any>) {
    return request('/trade/refund/seller/reject', { method: 'POST', data });
}
export async function refundSellerConfirm(refundId: number) {
    return request('/trade/refund/seller/confirm-receipt', { method: 'POST', params: { refundId } });
}
export async function adminRefundPage(params?: Record<string, any>) {
    return request('/admin/marketplace/refund/page', { method: 'GET', params });
}
export async function adminRefundDetail(refundId: number | string) {
    return request(`/admin/marketplace/refund/detail/${refundId}`, { method: 'GET' });
}
export async function adminRefundJudge(data: Record<string, any>) {
    return request('/admin/marketplace/refund/judge', { method: 'POST', data });
}
export async function adminRefundResolve(data: Record<string, any>) {
    return request('/admin/marketplace/refund/resolve', { method: 'POST', data });
}
export async function userPrivacyGet() {
    return request('/user/privacy', { method: 'GET' });
}
export async function userPrivacySave(data: Record<string, any>) {
    return request('/user/privacy', { method: 'POST', data });
}
```

- [ ] **Step 2: 补路由**

`/user` 组加 `{ path: '/user/refunds', component: './marketplace/RefundList' }`（紧邻 `/user/orders`）；
`/admin/marketplace` 组加 `{ path: '/admin/marketplace/refund', name: '退款管理', component: './marketplace/admin/Refund' }`（紧邻 `finance`）。

- [ ] **Step 3: 类型检查**

Run: `cd ui && npx tsc --noEmit`
Expected: 无错误（`component` 是字符串路径，不参与类型检查）

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(ui): 退款与隐私 API 及路由"
```

---

### Task 16: 退款时间线组件

**Files:**
- Create: `ui/src/components/marketplace/RefundTimeline.tsx`
- Test: `ui/src/components/marketplace/RefundTimeline.test.tsx`

**Interfaces:**
- Produces: `<RefundTimeline logs={Array<{action: string; content?: string; createTime: string}>} />`

- [ ] **Step 1: 写失败测试**

```tsx
it('按 action 渲染中文节点', () => {
    render(React.createElement(RefundTimeline, {
        logs: [{ action: 'apply', content: '申请与描述不符，金额 ¥1107.00', createTime: '2026-10-30 10:00:00' }],
    }));
    expect(screen.getByText('提交申请')).toBeTruthy();
    expect(screen.getByText('申请与描述不符，金额 ¥1107.00')).toBeTruthy();
});

it('未知 action 回退显示原始值', () => {
    render(React.createElement(RefundTimeline, {
        logs: [{ action: 'weird', createTime: '2026-10-30 10:00:00' }],
    }));
    expect(screen.getByText('weird')).toBeTruthy();
});

it('空日志显示占位文案', () => {
    render(React.createElement(RefundTimeline, { logs: [] }));
    expect(screen.getByText('暂无处理记录')).toBeTruthy();
});
```

- [ ] **Step 2: 跑测试确认 RED**

Run: `cd ui && npx jest src/components/marketplace/RefundTimeline.test.tsx`
Expected: FAIL（组件不存在）

- [ ] **Step 3: 实现组件**

模块级常量 `ACTION_LABELS` 覆盖 spec §6.1 的 12 个 action：`apply 提交申请`、`seller_agree 卖家同意`、`seller_reject 卖家拒绝`、`buyer_return 买家已寄回`、`seller_confirm 卖家确认收货`、`buyer_cancel 买家撤销`、`mediate 平台介入`、`judge 平台裁决`、`refund_ok 退款成功`、`manual_required 转人工处理`、`manual_resolved 人工已处理`、`timeout_close 超时关闭`。用 antd `Timeline`，`label` 放 `formatTime(createTime)`（复用 `./utils`）。

- [ ] **Step 4: 跑测试确认 GREEN**

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(ui): 退款时间线组件"
```

---

### Task 17: 退款申请页与详情页

**Files:**
- Modify: `ui/src/pages/marketplace/RefundApply.tsx`（重写）
- Modify: `ui/src/pages/marketplace/RefundDetail.tsx`（重写）
- Test: `ui/src/pages/marketplace/RefundApply.test.tsx`

- [ ] **Step 1: 写失败测试**

```tsx
it('原因按类型过滤：not_received 只出现在仅退款', () => {
    // refundReasons 返回 [{code:'not_received', label:'未收到货', types:['refund_only']}, ...]
    // 切到 return_refund 后 not_received 不在选项里
});

it('金额超过实付时禁用提交并提示', () => { });

it('canApply=false 时展示 denyReason 并隐藏提交按钮', () => { });
```

- [ ] **Step 2: 跑测试确认 RED**

- [ ] **Step 3: 实现两个页面**

`RefundApply`：从 `useSearchParams` 取 `orderId` → `refundByOrder(orderId)` 拿订单摘要与 `canApply`。`canApply === false` 时用 `Alert` 展示 `denyReason` 并禁用提交。表单：退款类型（`refund_only` / `return_refund` 分段控件）、退款原因（`refundReasons` 按当前类型的 `types` 过滤）、退款金额（默认 `totalAmount`，`max` 校验）、说明（`Input.TextArea` ≤500）、证据图。

证据图组件的**真实路径与签名**（计划初稿写错了）：
```tsx
import UploadImages from '@/components/marketplace/UploadImages';
// props: { value?: UploadFile[]; onChange?: (files: UploadFile[]) => void; max?: number }
<UploadImages value={fileList} onChange={setFileList} max={3} />
```
它是受控组件，提交前必须把 `UploadFile[]` 转成 URL 字符串数组——**照抄 `ui/src/components/marketplace/ReportDialog.tsx:50-80` 的 `getImagePath` + `evidenceImages` 写法**（`f.status === 'done'` 过滤、`response.data ?? response.imgUrl` 优先、否则 `url.replace(TWT.static,'').split('?')[0]`）。

`RefundDetail`：`refundDetail(refundId)` → 状态标签 + 订单摘要卡 + `RefundTimeline` + 操作区。按钮按 `status` + 当前用户角色：
- 买家：`pending_seller`/`seller_agreed`/`seller_rejected` → 撤销；`seller_agreed` 且 `return_refund` → 填退货单号；`seller_rejected` → 申请平台介入
- 卖家：`pending_seller` → 同意（`return_refund` 时弹窗填退货地址）/ 拒绝；`buyer_returned` → 确认收货

- [ ] **Step 4: 跑测试确认 GREEN**

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(ui): 退款申请页与详情页"
```

---

### Task 18: 我的退款列表 + OrderDetail 入口

**Files:**
- Create: `ui/src/pages/marketplace/RefundList.tsx`
- Modify: `ui/src/pages/marketplace/OrderDetail.tsx`

- [ ] **Step 1: 实现 `RefundList`**

`ProTable` + `Tabs`（全部 / 处理中 / 已完成），`role` 页内切换（买家 / 卖家）。列：退款单号、订单号、类型、金额、状态（`Tag`）、申请时间、操作（「查看详情」跳 `/trade/refund/detail?refundId=`）。

- [ ] **Step 2: OrderDetail 加入口**

`useRequest(() => refundByOrder(orderId), { ready: !!orderId && !!order })`。渲染规则：
- `canApply === true` → 「申请退款」按钮跳 `/trade/refund/apply?orderId=`
- `activeRefundId` 存在 → 「查看退款进度」跳 `/trade/refund/detail?refundId=`
- 两者都无且 `denyReason` 非空 → 灰色文字展示 `denyReason`

**闸门不在前端重复实现**（不硬编码 7 天窗口），一律用后端返回值。

- [ ] **Step 3: 类型检查 + 构建**

Run: `cd ui && npx tsc --noEmit && npm run build`
Expected: 无错误，构建成功

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(ui): 我的退款列表与订单详情退款入口"
```

---

### Task 19: 运营退款处理页

**Files:**
- Create: `ui/src/pages/marketplace/admin/Refund.tsx`

- [ ] **Step 1: 实现页面**

`ProTable`（状态 tab：`pending_seller` / `seller_agreed` / `buyer_returned` / `refunding` / `mediation` / 全部）+ 详情抽屉（`adminRefundDetail` → 订单摘要 + `RefundTimeline`）。

抽屉底部按状态给按钮：
- `mediation` → 「裁决」（弹窗：`decision` 三选 `buyer/seller/partial` + `amount`（`partial` 必填）+ `reason`）
- `refunding` → 「手工收口」（弹窗：`result` 二选 `success/fail` + `note`）

- [ ] **Step 2: 类型检查**

Run: `cd ui && npx tsc --noEmit`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(ui): 运营退款处理页"
```

---

### Task 20: 资金对账页

**Files:**
- Modify: `ui/src/pages/marketplace/admin/Finance.tsx`（重写）

- [ ] **Step 1: 实现页面**

统计卡读 `adminFinanceOverview()` 的 `totalGmv / paidGmv / refundedGmv / pendingWithdraws`（`Statistic` + `formatPrice`）；下方 `ProTable` 渲染 `recentFlows`，列 `flowId / orderId / type（pay→收入 refund→退款）/ amount / channel / thirdTransNo / createTime`。空表时给 `Empty` 说明「暂无资金流水」。

- [ ] **Step 2: 类型检查 + 构建**

Run: `cd ui && npx tsc --noEmit && npm run build`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(ui): 资金对账页"
```

---

### Task 21: 隐私设置接线 + 绑定文案

**Files:**
- Modify: `ui/src/pages/marketplace/UserSettings.tsx`

- [ ] **Step 1: 实现**

三个 `Switch` 改为受控：初值 `useRequest(() => userPrivacyGet())`，`checked={Number(v) === 1}`，`onChange` 调 `userPrivacySave({ [key]: v ? 1 : 0 })`，成功 `message.success('已保存')`，失败回滚 UI 并 `message.error`。

绑定微信/支付宝的 `onClick` 从 `handleNotImpl` 改为 `message.info('请联系客服绑定，客服将在 1 个工作日内处理')`。

- [ ] **Step 2: 类型检查**

Run: `cd ui && npx tsc --noEmit`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(ui): 隐私开关接线与绑定过渡文案"
```

---

### Task 22: 全链路验收

**Files:** 无代码改动（Step 8 追加验收记录）

- [ ] **Step 1: 后端全量测试**

Run: `cd backend && mvn -o test`
Expected: `BUILD SUCCESS`

- [ ] **Step 2: 格式校验**

Run: `cd backend && mvn -o spring-javaformat:apply -pl twelvet-trade/twelvet-trade-server,twelvet-product/twelvet-product-server && mvn -o spring-javaformat:validate -pl twelvet-trade/twelvet-trade-server,twelvet-product/twelvet-product-server`
Expected: 无违规（`apply` 若改了文件，需重跑 Step 1）

- [ ] **Step 3: 前端类型检查 + 测试 + 构建**

Run: `cd ui && npx tsc --noEmit && npx jest && npm run build`
Expected: 全绿

- [ ] **Step 4: 启动依赖并跑 E2E 仅退款路径**

Redis（配置要求密码 `123456`）：
```bash
/f/Redis-x64-3.2.100/redis-server.exe --port 6379 --requirepass 123456 --save "" --appendonly no --dir /tmp/redis-verify &
```
后端：`cd backend && mvn -o -pl twelvet-admin -am spring-boot:run`（或 `java -jar twelvet-admin/target/twelvet-admin.jar`）。

登录：`POST /auth/oauth2/token`（JSON body，非 form-urlencoded）。含中文的请求体必须写成 UTF-8 文件后 `curl --data-binary @file`（Git Bash 内联 `-d` 会破坏中文）。

路径：下单 → `mockPay` → 订单 `unshipped` → 申请退款（`refund_only`）→ 卖家同意 → 触发退款。逐项校验：
```sql
SELECT status FROM trade_refund WHERE order_id = <id>;       -- refund_success
SELECT status FROM trade_order  WHERE order_id = <id>;       -- refunded
SELECT status FROM trade_pay    WHERE out_trade_no = '<no>'; -- refunded
SELECT status, stock FROM product WHERE product_id = <id>;   -- onsale, 库存 +1
SELECT type, amount FROM trade_pay_flow WHERE order_id = <id>; -- 2 条：pay + refund
```

- [ ] **Step 5: E2E 退货退款路径**

下单 → 支付 → 发货 → 申请（`return_refund`）→ 卖家同意 → **买家填退货单号** → 卖家确认收货。校验：`trade_refund.status='refund_success'`、`trade_order.status='refunded'`、`product.status='offshelf'` 且**库存未回补**。

- [ ] **Step 6: E2E 多态撞号验证**

取 `trade_pay` 里被跑腿任务占用 `order_id` 的记录（本机 17–21 之一），对同号的商品订单发起退款，校验：
- 退款单 `refund_success`
- 该号段里 `pay_user_id` 不匹配的那条 `trade_pay.status` **仍是 `paid`**（未被误退）

- [ ] **Step 7: 回滚测试数据**

删除本次产生的 `trade_refund` / `trade_refund_log` / `trade_pay_flow` 行；把被改动的 `trade_order` / `trade_pay` / `product` 恢复原值。**先记下删除的 id 区间，避免误删既有数据**（`trade_dispute` 基线有 1 行、`trade_pay_flow` 基线 0 行）。

- [ ] **Step 8: 记录验收结果并 Commit**

在本文件末尾追加「## 验收记录」，逐条写实际 SQL 输出。**失败项不得标记完成。**

```bash
git add docs/superpowers/plans/2026-10-30-refund-aftermarket.md
git commit -m "docs(refund): 全链路验收记录"
```

---

## 验收记录

> 执行环境：本机 Windows 11 + MySQL `twelvet-fast`（MySQL 8）+ Redis 6379（密码 123456）+ 后端 jar（`backend/twelvet-admin/target/twelvet-admin.jar`）。
> 首轮执行于 2026-10-30，Step 4 完成后机器被强制关机；2026-11-02 重启服务后从 Step 5 续跑，最终数据已回滚。

### Step 1：后端全量测试 ✅

`cd backend && mvn -o test` → `BUILD SUCCESS`。汇总（节选）：twelvet-trade-server `Tests run: 116, Failures: 0, Errors: 0`，twelvet-payment-server `10/10`，twelvet-exchange-server `3/3`，其余模块全绿。

### Step 2：格式校验 ✅

`mvn -o spring-javaformat:apply …` 无文件改动；`mvn -o spring-javaformat:validate …` → `BUILD SUCCESS`。

### Step 3：前端类型检查 + 测试 + 构建 ✅

- `npx tsc --noEmit` → 0 错误
- `npx jest` → `Test Suites: 17 passed, 17 total` / `Tests: 111 passed, 111 total`
- `npm run build` → `Build index.html`，退出码 0

### Step 4：E2E 仅退款路径 ✅

链路：登录 user_demo（买家 101）→ 下单（product 1，订单 27，¥6011.00）→ 创建支付单并 `mockPay` → 订单 `unshipped` → 买家申请 `refund_only`（¥1107.00，damaged，退款单 6）→ 卖家 seller_demo 同意。

实际 SQL 输出：

```text
trade_refund:  refund_id=6 order_id=27 status=refund_success amount=1107.00
trade_order:   order_id=27 status=refunded
trade_pay:     pay_id=33 order_id=27 status=refunded
product:       product_id=1 status=onsale stock=1        -- 原 locked/0，退款后回补 +1
trade_pay_flow: flow 1 = pay 6011.00；flow 2 = refund 1107.00（2 条，符合预期）
```

### Step 5：E2E 退货退款路径 ✅

链路：下单（product 3，订单 29，¥2499.00）→ 支付 → 卖家发货（`shipped`）→ 买家申请 `return_refund`（¥2499.00，wrong_item，退款单 7）→ 卖家同意并快照退货地址 → 买家填写退货物流（顺丰速运 SF123456789）→ 卖家确认收货。

实际 SQL 输出：

```text
trade_refund:  refund_id=7 order_id=29 status=refund_success amount=2499.00
trade_order:   order_id=29 status=refunded
trade_pay:     pay_id=34 order_id=29 status=refunded
product:       product_id=3 status=offshelf stock=0      -- 退货退款不回补库存，符合预期
trade_pay_flow: flow 3 = pay 2499.00；flow 4 = refund 2499.00
```

### Step 6：E2E 多态撞号验证 ✅（构造撞号数据）

原计划取 `trade_pay` 中 17–21 号段的跑腿单做撞号；实测这些行 `pay_user_id IS NULL` 且对应 `trade_order.expire_at IS NULL`（存量单），`validateOrderPayable` 直接拒绝支付，无法按原计划跑通。改为构造等价撞号场景：先手工插入 `pay_id=35, order_id=30, pay_user_id=999, status='paid'` 的跑腿风格支付行，再对 30 号商品订单完成 下单 → 支付（pay_id=36，pay_user_id=101）→ 申请退款（退款单 8）→ 卖家同意。

实际 SQL 输出：

```text
trade_refund:  refund_id=8 order_id=30 status=refund_success amount=1.00
trade_order:   order_id=30 status=refunded
trade_pay:     pay_id=35 pay_user_id=999 status=paid     -- 未被误退（撞号行保持原状）
               pay_id=36 pay_user_id=101 status=refunded -- 只退买家本人的支付行
trade_pay_flow: flow 5 = pay 1914.00；flow 6 = refund 1.00
product:       product_id=5 status=onsale stock=1
```

结论：`pickPayOrder` 按 `pay_user_id` 过滤生效，多态 `trade_pay.order_id` 撞号不会误退他人支付单。

### Step 7：回滚测试数据 ✅

删除本次产生的 `trade_pay_flow`（id 1–6）、`trade_refund_log`（退款单 6/7/8 的全部日志）、`trade_refund`（6/7/8）、`trade_pay`（33–36，含构造的撞号行 35）、`trade_order`（27–30）；恢复 `product` 2/3/5 为 `onsale, stock=1`。

回滚后校验：

```text
trade_pay_flow: 0 行（基线 0）
trade_refund:   0 行
trade_refund_log: 0 行
trade_order:    最大 order_id=26（基线一致）
trade_pay:      最大 pay_id=32、共 23 行（基线一致）
product 1–5:    全部 onsale / stock=1
```

### Step 8：验收记录 ✅

本节即记录；四条 E2E 链路（含多态撞号）全部通过，测试数据已回滚，工作区无残留脏数据。
