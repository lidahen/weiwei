# Phase 0A — 退款收口与通知覆盖 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修完退款评审遗留的 6 个问题，补齐退款 / 订单关键状态的通知，给运营退款台加 SLA 与超时筛选，并补上 `trade_refund` 的买家 / 卖家索引。

**Architecture:** 后端在 `twelvet-trade-server` 内做小步修改：控制器参数解析抽公共工具；超时扫描改为逐条事务；通知走现有 `IImService.sendNotice` 且失败不阻断主流程。前端在 `ui` 内补空态、类型过滤、SLA 展示与超时页签。SQL 迁移保持幂等，沿用 `refund_feature.sql` 的 `information_schema` 查重模式。

**Tech Stack:** Java 17 / Spring Boot 3.5.4 / MyBatis XML / Sa-Token 1.44；React 18 / Umi Max 4 / Ant Design 5 / Jest；MySQL 8。

**Spec:** `docs/superpowers/specs/2026-11-02-product-strategy-design.md`（§8.3、§9 Phase 0）

---

## Global Constraints

- 不新增依赖；不引入 MyBatis-Plus；继续使用 XML Mapper。
- 业务参数错误统一抛 `TWTException`（`TWTExceptionHandler` 映射为 HTTP 400）；不得把 `NumberFormatException` 直接暴露给前端。
- 所有新增 SQL 保持租户过滤 `<if test="tenantId != null">`；Quartz 无租户上下文时显式传 `null`。
- 通知一律 best-effort：`imService.sendNotice` 必须包在 `try/catch` 内，通知失败不得回滚退款 / 订单事务。
- 前端只调用 Java BFF；包管理器用 npm，不用 yarn。
- Java 代码提交前运行 `mvn spring-javaformat:apply`。
- 后端单测命令：`cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=<ClassName> -Dsurefire.failIfNoSpecifiedTests=false`。
- 前端单测命令：`cd ui && npm test -- --runTestsByPath <path> --watchAll=false`。
- 本计划**不含**漏斗埋点 / 看板（Phase 0B 单独计划）与真实资金接入。
- `trade_refund` 没有 `tenant_id`，所有租户过滤一律 `JOIN trade_order o ON o.order_id = r.order_id`。

---

## Review Focus

以下 5 类输入 / 失败模式最容易在真实使用中出问题，每个都必须在对应任务的测试里钉住：

1. **缺失 / 非法 JSON body 参数**：`apply`、`buyerReturn`、`sellerAgree`、`sellerReject`、`judge`、`resolve` 收到缺字段或 `"abc"` 时，应返回清晰 400，而不是 `NumberFormatException` 或把字面量 `"null"` 落库（Task 1）。
2. **页面缺少 query 参数**：直接打开 `/trade/refund/apply` 或 `/trade/refund/detail` 时，应显示空态，而不是永久 `Spin`（Task 2）。
3. **超时扫描单条失败**：一条退款单的 `updateStatus` 失败时，该条的退款状态 / 订单恢复 / 日志必须整体回滚，且不阻断后续记录（Task 6）。
4. **通知失败**：IM 写入抛异常时，退款 / 订单状态变更仍必须提交（Task 8、Task 9）。
5. **并发状态变更**：卖家操作与超时任务同时推进时，`updateStatus` 返回 0 行必须抛业务异常，不得重复退款；自动同意只对「未发货 + 仅退款」生效（Task 7）。

---

## File Structure

| 文件 | 职责 |
|---|---|
| `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/RefundRequestParams.java` | 退款控制器 body 参数解析（新增） |
| `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/TradeRefundController.java` | C 端 / 卖家端退款入口（修改） |
| `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/AdminRefundController.java` | 运营退款入口（修改） |
| `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundTimeoutHelper.java` | 逐条事务包装（新增） |
| `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java` | 退款状态机 / 超时扫描 / 通知（修改） |
| `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeOrderServiceImpl.java` | 订单发货 / 确认收货通知（修改） |
| `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/listener/PaySuccessEventListener.java` | 支付成功通知（修改） |
| `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/ITradeRefundService.java` | `adminPage` 增加 `overdue`（修改） |
| `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/mapper/TradeRefundMapper.java` | `selectAdminList` 增加 `overdue`（修改） |
| `backend/twelvet-trade/twelvet-trade-server/src/main/resources/mapper/trade/TradeRefundMapper.xml` | 超时筛选 SQL（修改） |
| `backend/sql/refund_feature.sql` | 买家 / 卖家索引（修改） |
| `ui/src/pages/marketplace/RefundApply.tsx` | 缺参空态 + 类型过滤（修改） |
| `ui/src/pages/marketplace/RefundDetail.tsx` | 缺参空态（修改） |
| `ui/src/pages/marketplace/admin/Refund.tsx` | SLA 列 + 已超时页签（修改） |
| `ui/src/pages/marketplace/admin/refundSla.ts` | SLA 计算（新增） |
| `docs/superpowers/specs/2026-11-02-settlement-compliance-design.md` | 担保结算产品 / 合规方案（新增） |

---

### Task 1: 退款控制器参数解析返回友好 400

**Files:**
- Create: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/RefundRequestParams.java`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/TradeRefundController.java:46,115,148,160`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/AdminRefundController.java:64,78`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/controller/RefundRequestParamsTest.java`

**Interfaces:**
- Produces: `RefundRequestParams.requiredLong(Map<String,Object> body, String key): Long`
- Throws: `TWTException("缺少参数: <key>")` / `TWTException("参数格式错误: <key>")`

- [ ] **Step 1: Write the failing test**

```java
package com.twelvet.trade.server.controller;

import com.twelvet.framework.core.exception.TWTException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundRequestParamsTest {

    @Test
    @DisplayName("缺失 key → 业务提示，不是 NumberFormatException")
    void requiredLong_missing_throwsBusinessMessage() {
        TWTException e = assertThrows(TWTException.class,
                () -> RefundRequestParams.requiredLong(Map.of(), "orderId"));
        assertEquals("缺少参数: orderId", e.getMessage());
    }

    @Test
    @DisplayName("key 存在但值为 null → 同一个业务提示")
    void requiredLong_literalNull_throws() {
        Map<String, Object> body = new HashMap<>();
        body.put("refundId", null);
        TWTException e = assertThrows(TWTException.class,
                () -> RefundRequestParams.requiredLong(body, "refundId"));
        assertEquals("缺少参数: refundId", e.getMessage());
    }

    @Test
    @DisplayName("非数字 → 参数格式错误")
    void requiredLong_nonNumeric_throwsFormatMessage() {
        TWTException e = assertThrows(TWTException.class,
                () -> RefundRequestParams.requiredLong(Map.of("orderId", "abc"), "orderId"));
        assertEquals("参数格式错误: orderId", e.getMessage());
    }

    @Test
    @DisplayName("数字与带空格的数字字符串都接受")
    void requiredLong_acceptsNumberAndString() {
        assertEquals(7L, RefundRequestParams.requiredLong(Map.of("orderId", (Object) 7), "orderId"));
        assertEquals(8L, RefundRequestParams.requiredLong(Map.of("orderId", " 8 "), "orderId"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=RefundRequestParamsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `cannot find symbol: class RefundRequestParams`.

- [ ] **Step 3: Implement `RefundRequestParams`**

```java
package com.twelvet.trade.server.controller;

import com.twelvet.framework.core.exception.TWTException;

import java.util.Map;

/**
 * 退款控制器 JSON body 参数解析。
 * <p>缺失 / 非法值一律翻译为 TWTException（HTTP 400），避免 NumberFormatException 把
 * 用户输入问题伪装成服务端 500。</p>
 */
public final class RefundRequestParams {

    private RefundRequestParams() {
    }

    public static Long requiredLong(Map<String, Object> body, String key) {
        Object value = (body == null) ? null : body.get(key);
        String text = (value == null) ? null : String.valueOf(value).trim();
        if (text == null || text.isEmpty() || "null".equals(text)) {
            throw new TWTException("缺少参数: " + key);
        }
        try {
            return Long.valueOf(text);
        }
        catch (NumberFormatException e) {
            throw new TWTException("参数格式错误: " + key);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=RefundRequestParamsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (4 tests).

- [ ] **Step 5: Replace the six call sites**

In `TradeRefundController`：
- `apply`：`Long orderId = RefundRequestParams.requiredLong(body, "orderId");`
- `buyerReturn`：`Long refundId = RefundRequestParams.requiredLong(body, "refundId");`
- `sellerAgree`：同上。
- `sellerReject`：同上。

In `AdminRefundController`：
- `judge`：`Long refundId = RefundRequestParams.requiredLong(body, "refundId");`
- `resolve`：同上。

删除两个控制器里已不再使用的 `Long.valueOf(String.valueOf(...))` 行；保留 `str` / `toBigDecimal`。

- [ ] **Step 6: Run the full trade-server test suite**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: BUILD SUCCESS（现有 116+ 用例全绿）。

- [ ] **Step 7: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/RefundRequestParams.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/TradeRefundController.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/AdminRefundController.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/controller/RefundRequestParamsTest.java
git commit -m "fix(refund): 控制器参数缺失返回清晰 400"
```

---

### Task 2: 退款页面缺少 query 参数时展示空态

**Files:**
- Modify: `ui/src/pages/marketplace/RefundApply.tsx:53-80,126-132`
- Modify: `ui/src/pages/marketplace/RefundDetail.tsx:81-108`
- Test: `ui/src/pages/marketplace/RefundApply.test.tsx`
- Test: `ui/src/pages/marketplace/RefundDetail.test.tsx`（新建）

**Interfaces:**
- Produces: 无导出接口；行为契约：`orderId` / `refundId` 缺失时 `loading=false` 且渲染 `Alert`。

- [ ] **Step 1: Write the failing tests**

`RefundApply.test.tsx`：把 `@umijs/max` 的 mock 改成可控制的 jest.fn，并加一个用例：

```tsx
import { useSearchParams } from '@umijs/max';

jest.mock('@umijs/max', () => ({
    useSearchParams: jest.fn(() => [new URLSearchParams('orderId=1')]),
    history: { push: jest.fn() },
}));
const mockUseSearchParams = useSearchParams as unknown as jest.Mock;

beforeEach(() => {
    mockUseSearchParams.mockReturnValue([new URLSearchParams('orderId=1')]);
});

it('缺少 orderId 时展示空态而不是一直转圈', () => {
    mockUseSearchParams.mockReturnValue([new URLSearchParams('')]);
    renderPage();
    expect(screen.getByText('缺少订单参数，无法申请退款')).toBeTruthy();
});
```

新建 `RefundDetail.test.tsx`：

```tsx
import { refundDetail } from '@/services/marketplace/api';
import { render, screen } from '@testing-library/react';
import React from 'react';
import RefundDetail from './RefundDetail';

jest.mock('@/services/marketplace/api', () => ({
    refundDetail: jest.fn(),
    orderDetail: jest.fn(),
    refundCancel: jest.fn(),
    refundMediate: jest.fn(),
    refundReturn: jest.fn(),
    refundSellerAgree: jest.fn(),
    refundSellerConfirm: jest.fn(),
    refundSellerReject: jest.fn(),
}));

jest.mock('@umijs/max', () => ({
    useSearchParams: () => [new URLSearchParams('')],
    useModel: () => ({ initialState: { currentUser: { user: { userId: 1 } } } }),
    history: { push: jest.fn() },
}));

jest.mock('@/components/marketplace/RefundTimeline', () => ({
    __esModule: true,
    default: () => null,
}));

describe('退款详情页', () => {
    it('缺少 refundId 时展示空态', () => {
        render(React.createElement(RefundDetail));
        expect(screen.getByText('缺少退款单参数')).toBeTruthy();
        expect(refundDetail).not.toHaveBeenCalled();
    });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd ui && npm test -- --runTestsByPath src/pages/marketplace/RefundApply.test.tsx src/pages/marketplace/RefundDetail.test.tsx --watchAll=false`
Expected: FAIL — 空态文案不存在 / 页面一直 `Spin`。

- [ ] **Step 3: Implement the empty states**

`RefundApply.tsx`：
```tsx
useEffect(() => {
    if (!orderId) {
        setLoading(false);
        return;
    }
    // ...原逻辑不变
}, [orderId]);

if (!orderId) {
    return (
        <PageContainer header={{ title: '申请退款', breadcrumb: {} }}>
            <Alert type="warning" showIcon message="缺少订单参数，无法申请退款" />
        </PageContainer>
    );
}
```

`RefundDetail.tsx`：
```tsx
const load = useCallback(async () => {
    if (!refundId) {
        setLoading(false);
        return;
    }
    // ...原逻辑不变
}, [refundId]);

if (!refundId) {
    return (
        <PageContainer header={{ title: '退款详情', breadcrumb: {} }}>
            <Alert type="warning" showIcon message="缺少退款单参数" />
        </PageContainer>
    );
}
```

两处 `if (!orderId)` / `if (!refundId)` 的空态 return 都必须放在现有 `if (loading)` 之前，否则 `loading` 初始为 `true` 时仍会先渲染 `Spin`，测试与用户都看不到空态。

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd ui && npm test -- --runTestsByPath src/pages/marketplace/RefundApply.test.tsx src/pages/marketplace/RefundDetail.test.tsx --watchAll=false`
Expected: PASS.

- [ ] **Step 5: Run type check**

Run: `cd ui && npm run tsc`
Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add ui/src/pages/marketplace/RefundApply.tsx ui/src/pages/marketplace/RefundDetail.tsx \
        ui/src/pages/marketplace/RefundApply.test.tsx ui/src/pages/marketplace/RefundDetail.test.tsx
git commit -m "fix(ui): 退款页面缺参时展示空态"
```

---

### Task 3: `checkApply` 返回订单状态与允许类型，前端过滤退款类型

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java:225-250`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImplApplyTest.java`
- Modify: `ui/src/pages/marketplace/RefundApply.tsx:53-103,160-175`
- Test: `ui/src/pages/marketplace/RefundApply.test.tsx`

**Interfaces:**
- Produces: `checkApply` 返回 `orderStatus: String | null`、`allowedTypes: List<String>`（`refund_only` / `return_refund`）。
- Consumes: `TradeOrder.getStatus()`。

- [ ] **Step 1: Write the failing backend test**

在 `TradeRefundServiceImplApplyTest` 追加：

```java
@Test
@DisplayName("checkApply 返回订单状态与允许类型：未发货只能仅退款")
void checkApply_unshipped_returnsAllowedTypes() {
    when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
            .thenReturn(order("unshipped", new BigDecimal("1107.00"), null, null));
    when(refundMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);

    Map<String, Object> result = service.checkApply(ORDER_ID, BUYER_ID);

    assertEquals("unshipped", result.get("orderStatus"));
    assertEquals(List.of("refund_only"), result.get("allowedTypes"));
}

@Test
@DisplayName("checkApply 返回订单状态与允许类型：已发货两种都允许")
void checkApply_shipped_returnsBothTypes() {
    when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
            .thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
    when(refundMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);

    Map<String, Object> result = service.checkApply(ORDER_ID, BUYER_ID);

    assertEquals("shipped", result.get("orderStatus"));
    assertEquals(List.of("refund_only", "return_refund"), result.get("allowedTypes"));
}
```

- [ ] **Step 2: Run backend test to verify it fails**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=TradeRefundServiceImplApplyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `orderStatus` / `allowedTypes` 为 null。

- [ ] **Step 3: Implement the backend change**

`checkApply` 的 `LinkedHashMap` 容量从 4 改为 6，并追加：

```java
result.put("orderStatus", order == null ? null : order.getStatus());
result.put("allowedTypes", allowedTypes(order));
```

新增私有方法：

```java
private List<String> allowedTypes(TradeOrder order) {
    if (order == null) {
        return List.of();
    }
    String status = order.getStatus();
    if ("unshipped".equals(status) || "paid".equals(status)) {
        return List.of(TYPE_REFUND_ONLY);
    }
    if ("shipped".equals(status) || ORDER_STATUS_DONE.equals(status)) {
        return List.of(TYPE_REFUND_ONLY, TYPE_RETURN_REFUND);
    }
    return List.of();
}
```

- [ ] **Step 4: Run backend test to verify it passes**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=TradeRefundServiceImplApplyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Write the failing frontend test**

在 `RefundApply.test.tsx` 追加：

```tsx
it('后端只允许仅退款时隐藏退货退款选项', async () => {
    mockByOrder.mockResolvedValue({
        code: 200,
        data: { canApply: true, orderStatus: 'unshipped', allowedTypes: ['refund_only'] },
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('仅退款')).toBeTruthy());
    expect(screen.queryByText('退货退款')).toBeNull();
});

it('后端允许两种类型时展示两个选项', async () => {
    mockByOrder.mockResolvedValue({
        code: 200,
        data: { canApply: true, orderStatus: 'shipped', allowedTypes: ['refund_only', 'return_refund'] },
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('退货退款')).toBeTruthy());
    expect(screen.getByText('仅退款')).toBeTruthy();
});
```

- [ ] **Step 6: Run frontend test to verify it fails**

Run: `cd ui && npm test -- --runTestsByPath src/pages/marketplace/RefundApply.test.tsx --watchAll=false`
Expected: FAIL — 未发货场景仍渲染「退货退款」。

- [ ] **Step 7: Implement the frontend filtering**

`RefundApply.tsx`：
- 在 `useEffect` 的 `.then` 中，根据 `checkRes.allowedTypes` 校正当前类型：

```tsx
const allowed = Array.isArray(checkRes?.allowedTypes) && checkRes.allowedTypes.length
    ? checkRes.allowedTypes
    : ['refund_only', 'return_refund'];
setType((prev) => (allowed.includes(prev) ? prev : (allowed[0] as 'refund_only' | 'return_refund')));
```

- 渲染时过滤选项：

```tsx
const allowedTypes: string[] = Array.isArray(check?.allowedTypes) && check.allowedTypes.length
    ? check.allowedTypes
    : ['refund_only', 'return_refund'];

options={[
    { label: '仅退款', value: 'refund_only' },
    { label: '退货退款', value: 'return_refund' },
].filter((o) => allowedTypes.includes(o.value))}
```

- 若当前 `type` 不在 `allowedTypes` 中，`useEffect` 里已把它切到第一个允许值。

- [ ] **Step 8: Run frontend tests and type check**

Run: `cd ui && npm test -- --runTestsByPath src/pages/marketplace/RefundApply.test.tsx --watchAll=false && npm run tsc`
Expected: PASS + 0 errors.

- [ ] **Step 9: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImplApplyTest.java \
        ui/src/pages/marketplace/RefundApply.tsx ui/src/pages/marketplace/RefundApply.test.tsx
git commit -m "fix(refund): 退款类型按订单状态过滤"
```

---

### Task 4: 买家退货物流服务端非空校验

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java:399-415`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImplFlowTest.java`

**Interfaces:**
- Produces: `buyerReturn` 在 `company` / `returnNo` 为空时抛 `TWTException`，且不推进状态。

- [ ] **Step 1: Write the failing tests**

在 `TradeRefundServiceImplFlowTest` 追加：

```java
@Test
@DisplayName("退货物流公司为空 → 拒绝且不推进状态")
void buyerReturn_blankCompany_rejected() {
    when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("seller_agreed", "return_refund"));

    TWTException e = assertThrows(TWTException.class,
            () -> service.buyerReturn(REFUND_ID, " ", "SF123", BUYER_ID));

    assertEquals("请填写退货物流公司", e.getMessage());
    verify(refundMapper, never()).updateStatus(any(), any(), any());
}

@Test
@DisplayName("退货单号为空 → 拒绝且不推进状态")
void buyerReturn_blankReturnNo_rejected() {
    when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("seller_agreed", "return_refund"));

    TWTException e = assertThrows(TWTException.class,
            () -> service.buyerReturn(REFUND_ID, "顺丰", null, BUYER_ID));

    assertEquals("请填写退货单号", e.getMessage());
    verify(refundMapper, never()).updateStatus(any(), any(), any());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=TradeRefundServiceImplFlowTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — 未抛异常，`updateStatus` 被调用。

- [ ] **Step 3: Implement the validation**

在 `buyerReturn` 的归属 / 类型校验之后、`updateStatus` 之前：

```java
if (company == null || company.isBlank()) {
    throw new TWTException("请填写退货物流公司");
}
if (returnNo == null || returnNo.isBlank()) {
    throw new TWTException("请填写退货单号");
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=TradeRefundServiceImplFlowTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImplFlowTest.java
git commit -m "fix(refund): 退货物流服务端必填校验"
```

---

### Task 5: `trade_refund` 买家 / 卖家索引

**Files:**
- Modify: `backend/sql/refund_feature.sql`（在 3b 之后追加 3c）

**Interfaces:**
- Produces: `idx_buyer_id(buyer_id)`、`idx_seller_id(seller_id)`；重复执行不报错。

- [ ] **Step 1: Add idempotent index SQL**

在 `-- 3b` 段之后追加：

```sql
-- 3c. 买家 / 卖家列表索引：trade_refund 无 tenant_id，列表查询 JOIN trade_order 过滤租户；
--     没有 buyer_id / seller_id 索引时 C 端「我的退款」会全表扫。
SET @has_idx_buyer := (SELECT COUNT(*) FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_refund'
                         AND INDEX_NAME = 'idx_buyer_id');
SET @sql := IF(@has_idx_buyer = 0,
    'ALTER TABLE `trade_refund` ADD INDEX `idx_buyer_id` (`buyer_id`)',
    'SELECT "idx_buyer_id 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx_seller := (SELECT COUNT(*) FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_refund'
                          AND INDEX_NAME = 'idx_seller_id');
SET @sql := IF(@has_idx_seller = 0,
    'ALTER TABLE `trade_refund` ADD INDEX `idx_seller_id` (`seller_id`)',
    'SELECT "idx_seller_id 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

- [ ] **Step 2: Apply to the local database and verify**

Run:
```bash
mysql -uroot -p123456 twelvet-fast < backend/sql/refund_feature.sql
mysql -uroot -p123456 twelvet-fast -e "SHOW INDEX FROM trade_refund WHERE Key_name IN ('idx_buyer_id','idx_seller_id');"
```
Expected: 输出 2 行，`Key_name` 分别为 `idx_buyer_id`、`idx_seller_id`。

再执行一次导入，Expected: 输出「已存在，跳过」，无报错。

- [ ] **Step 3: Commit**

```bash
git add backend/sql/refund_feature.sql
git commit -m "fix(refund): 补买家/卖家索引"
```

---

### Task 6: 超时扫描逐条事务

**Files:**
- Create: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundTimeoutHelper.java`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java:155-166,779-823`
- Modify: 6 个测试构造器（见 Step 5）
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/task/TradeRefundScanTaskTest.java`

**Interfaces:**
- Produces: `TradeRefundTimeoutHelper.inTransaction(Supplier<T> action): T`，`@Transactional(rollbackFor = Exception.class)`。
- Consumes: `TradeRefundServiceImpl.scanTimeouts()` 的每条记录。

- [ ] **Step 1: Write the failing test**

在 `TradeRefundScanTaskTest` 追加（mock 的 `refundMapper.updateStatus` 第一单抛异常、第二单成功）：

```java
@Test
@DisplayName("单条抛异常不阻断后续，且异常记录不计数")
void scan_oneExceptionDoesNotStopOthers() {
    when(refundMapper.selectSellerTimeout(any(), anyInt()))
            .thenReturn(List.of(refund(9L, "pending_seller"), refund(10L, "pending_seller")));
    when(refundMapper.updateStatus(9L, "pending_seller", "mediation"))
            .thenThrow(new RuntimeException("db down"));
    when(refundMapper.updateStatus(10L, "pending_seller", "mediation")).thenReturn(1);

    assertEquals(1, service.scanTimeouts());

    verify(refundMapper).updateStatus(9L, "pending_seller", "mediation");
    verify(refundMapper).updateStatus(10L, "pending_seller", "mediation");
    verify(logMapper).insert(eq(10L), isNull(), eq("mediate"), any());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=TradeRefundScanTaskTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — 构造器参数不匹配 / 异常被内层 catch 吞掉但计数仍为 0（取决于当前实现）。

- [ ] **Step 3: Create the transaction helper**

```java
package com.twelvet.trade.server.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * 退款超时扫描的单条事务包装。
 * <p>为什么是独立 bean：scanTimeouts 在同一类内循环，直接内联多步写库时，
 * 中间失败会留下「退款单已改状态但订单未恢复」的半截数据；同类自调用不经过 Spring 代理，
 * 方法上的 @Transactional 不生效，因此把事务边界放到独立 bean。</p>
 */
@Component
public class TradeRefundTimeoutHelper {

    @Transactional(rollbackFor = Exception.class)
    public <T> T inTransaction(Supplier<T> action) {
        return action.get();
    }
}
```

- [ ] **Step 4: Refactor `scanTimeouts`**

`TradeRefundServiceImpl`：
- 构造器追加 `TradeRefundTimeoutHelper timeoutHelper`，赋给字段。
- `scanTimeouts` 改为：

```java
@Override
public int scanTimeouts() {
    LocalDateTime now = LocalDateTime.now();
    int processed = 0;

    for (TradeRefund refund : refundMapper.selectSellerTimeout(now.minusHours(sellerTimeoutHours), SCAN_LIMIT)) {
        processed += runOne(refund, () -> processSellerTimeout(refund));
    }
    for (TradeRefund refund : refundMapper.selectReturnTimeout(now.minusDays(returnTimeoutDays), SCAN_LIMIT)) {
        processed += runOne(refund, () -> processReturnTimeout(refund));
    }
    for (TradeRefund refund : refundMapper.selectConfirmTimeout(now.minusDays(confirmTimeoutDays), SCAN_LIMIT)) {
        processed += runOne(refund, () -> processConfirmTimeout(refund));
    }
    return processed;
}

private int runOne(TradeRefund refund, java.util.function.Supplier<Integer> action) {
    try {
        Integer result = timeoutHelper.inTransaction(action);
        return result == null ? 0 : result;
    }
    catch (Exception e) {
        log.warn("[退款] 扫描退款单 {} 失败: {}", refund.getRefundId(), e.getMessage());
        return 0;
    }
}
```

- 拆出三个方法，内部**不再 try/catch**（异常交给 `runOne` / 事务回滚）：

```java
private int processSellerTimeout(TradeRefund refund) {
    int rows = refundMapper.updateStatus(refund.getRefundId(), "pending_seller", "mediation");
    if (rows == 0) {
        throw new TWTException(DENY_REFUND_STATUS_CHANGED);
    }
    logMapper.insert(refund.getRefundId(), null, "mediate", "卖家超时未处理，平台介入");
    return 1;
}

private int processReturnTimeout(TradeRefund refund) {
    int rows = refundMapper.updateStatus(refund.getRefundId(), "seller_agreed", "closed");
    if (rows == 0) {
        throw new TWTException(DENY_REFUND_STATUS_CHANGED);
    }
    restoreOrder(refund, null);
    logMapper.insert(refund.getRefundId(), null, "timeout_close", "买家超时未寄回，退款关闭");
    return 1;
}

private int processConfirmTimeout(TradeRefund refund) {
    int rows = refundMapper.updateStatus(refund.getRefundId(), "buyer_returned", "mediation");
    if (rows == 0) {
        throw new TWTException(DENY_REFUND_STATUS_CHANGED);
    }
    logMapper.insert(refund.getRefundId(), null, "mediate", "卖家超时未确认收货，平台介入");
    return 1;
}
```

- 删除旧的 `escalateOnTimeout` 及其内部 catch。

- [ ] **Step 5: Update the six test constructors**

在以下测试中把构造器最后一个参数改为 `new TradeRefundTimeoutHelper()`（或新增字段）：
- `TradeRefundScanTaskTest`
- `TradeRefundServiceImplApplyTest`
- `TradeRefundServiceImplExecuteTest`
- `TradeRefundServiceImplFlowTest`
- `TradeRefundServiceImplSettleTest`
- `TradeRefundServiceImplJudgeTest`

示例：
```java
private final TradeRefundTimeoutHelper timeoutHelper = new TradeRefundTimeoutHelper();

private final TradeRefundServiceImpl service = new TradeRefundServiceImpl(orderMapper, refundMapper, logMapper,
        payLookupMapper, payOrderService, flowMapper, imService, productMapper, timeoutHelper);
```

- [ ] **Step 6: Run the refund test suite**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest='TradeRefund*Test' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（含新增的单条异常用例）。

- [ ] **Step 7: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundTimeoutHelper.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/task/TradeRefundScanTaskTest.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl*Test.java
git commit -m "fix(refund): 超时扫描逐条事务"
```

---

### Task 7: 未发货仅退款 48h 超时自动同意

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java:processSellerTimeout`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/task/TradeRefundScanTaskTest.java`

**Interfaces:**
- Consumes: `TradeRefund.getType()`、`TradeRefund.getPrevOrderStatus()`、`executeRefund`（已有私有方法）。
- Produces: `pending_seller` 超时分支：`refund_only` + `prevOrderStatus in (unshipped, paid)` → `refunding` → `executeRefund`；否则 → `mediation`。

- [ ] **Step 1: Write the failing tests**

在 `TradeRefundScanTaskTest` 追加（成功退款所需的支付单 / 网关 stub 可复用 `TradeRefundServiceImplExecuteTest` 的成功用例）：

```java
@Test
@DisplayName("未发货 + 仅退款超时 → 自动同意并执行退款，不转人工")
void scan_pendingSeller_unshippedRefundOnly_autoApproves() {
    TradeRefund refund = refund(REFUND_ID, "pending_seller");
    refund.setPrevOrderStatus("unshipped");
    when(refundMapper.selectSellerTimeout(any(), anyInt())).thenReturn(List.of(refund));
    when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "refunding")).thenReturn(1);
    when(refundMapper.updateStatus(REFUND_ID, "refunding", "refund_success")).thenReturn(1);
    // 复用 ExecuteTest 成功路径的 stub：payLookupMapper.selectPaidByOrderId / payOrderService.refund / orderMapper...
    when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(List.of(
            Map.of("pay_amount", new BigDecimal("1107.00"), "out_trade_no", "PAY1", "pay_channel", "alipay")));
    when(payOrderService.refund("PAY1", new BigDecimal("1107.00"), "退款：不想要了")).thenReturn(true);
    when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(orderForSettle());
    when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunded", List.of("refunding"), null)).thenReturn(1);

    assertEquals(1, service.scanTimeouts());

    verify(refundMapper).updateStatus(REFUND_ID, "pending_seller", "refunding");
    verify(refundMapper, never()).updateStatus(REFUND_ID, "pending_seller", "mediation");
    verify(logMapper).insert(eq(REFUND_ID), isNull(), eq("timeout_auto_agree"), any());
}

@Test
@DisplayName("已发货 + 仅退款超时 → 仍然转平台介入，不自动退款")
void scan_pendingSeller_shipped_escalates() {
    TradeRefund refund = refund(REFUND_ID, "pending_seller");
    refund.setPrevOrderStatus("shipped");
    when(refundMapper.selectSellerTimeout(any(), anyInt())).thenReturn(List.of(refund));
    when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "mediation")).thenReturn(1);

    assertEquals(1, service.scanTimeouts());

    verify(refundMapper).updateStatus(REFUND_ID, "pending_seller", "mediation");
    verify(refundMapper, never()).updateStatus(REFUND_ID, "pending_seller", "refunding");
}
```

在测试类中补一个 `orderForSettle()` 辅助方法（返回带 `productId` 的 `TradeOrder`），与 `ExecuteTest` 的 settle 断言保持一致。若 `ExecuteTest` 已有可复用的静态构造，直接复用；否则内联构造。

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=TradeRefundScanTaskTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — 未发货场景仍走 `mediation`。

- [ ] **Step 3: Implement the auto-approve branch**

在 `processSellerTimeout` 开头：

```java
if (TYPE_REFUND_ONLY.equals(refund.getType())
        && ("unshipped".equals(refund.getPrevOrderStatus()) || "paid".equals(refund.getPrevOrderStatus()))) {
    int rows = refundMapper.updateStatus(refund.getRefundId(), "pending_seller", "refunding");
    if (rows == 0) {
        throw new TWTException(DENY_REFUND_STATUS_CHANGED);
    }
    logMapper.insert(refund.getRefundId(), null, "timeout_auto_agree", "卖家超时未处理，自动同意退款");
    executeRefund(refund, refund.getAmount());
    notifyBuyer(refund.getBuyerId(), "退款已自动同意", "卖家超时未处理，退款 ¥" + refund.getAmount() + " 已原路退回");
    return 1;
}
```

> **终审修正（d934ee9，以此为准）**：本示例有两处缺陷，实际实现已修正。
> 1. `executeRefund` 返回 `boolean`，失败时不得发送「已原路退回」（那是对买家的假到账）；
>    失败只通知卖家「已自动同意，转人工处理」。
> 2. 认领先行写入必须在独立事务（`timeoutHelper.inNewTransaction`，`REQUIRES_NEW`）中提交，
>    再调用退款网关；否则网关成功后写库失败会回滚到 `pending_seller`，下一轮扫描重复退款。

其余走 `mediation`：
```java
int rows = refundMapper.updateStatus(refund.getRefundId(), "pending_seller", "mediation");
if (rows == 0) {
    throw new TWTException(DENY_REFUND_STATUS_CHANGED);
}
logMapper.insert(refund.getRefundId(), null, "mediate", "卖家超时未处理，平台介入");
return 1;
```

> Task 7 只加买家通知；卖家侧「退款已自动同意」通知在 Task 8 统一补齐（届时 `notifySeller` 才被引入，保证每个任务单独可编译）。

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=TradeRefundScanTaskTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/task/TradeRefundScanTaskTest.java
git commit -m "feat(refund): 未发货仅退款超时自动同意"
```

---

### Task 8: 退款状态变化通知买卖双方

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java:175-223,349-459,498-511,708-773`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImplApplyTest.java`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImplFlowTest.java`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImplJudgeTest.java`

**Interfaces:**
- Produces: 私有 `notifySeller(Long userId, String title, String content)`，与现有 `notifyBuyer` 对称；通知失败只记日志。
- 通知类型沿用现有 `"system"`。

- [ ] **Step 1: Write the failing tests**

在 `ApplyTest` 先补常量 `private static final Long REFUND_ID = 9L;`，再追加：
```java
@Test
@DisplayName("申请退款后通知卖家")
void apply_notifiesSeller() {
    when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
            .thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
    when(refundMapper.countActiveByOrderId(ORDER_ID)).thenReturn(0L);
    when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunding", List.of("shipped"), TENANT_ID)).thenReturn(1);
    when(refundMapper.insert(any(TradeRefund.class))).thenAnswer(inv -> {
        inv.getArgument(0, TradeRefund.class).setRefundId(REFUND_ID);
        return 1;
    });

    service.apply(ORDER_ID, "refund_only", "not_as_described", new BigDecimal("1107.00"), null, null, BUYER_ID);

    verify(imService).sendNotice(eq(SELLER_ID), eq("system"), eq("新的退款申请"), any());
}
```

在 `FlowTest` 追加：
```java
@Test
@DisplayName("卖家同意仅退款后通知买家")
void sellerAgree_notifiesBuyer() {
    when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("pending_seller", "refund_only"));
    when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "seller_agreed")).thenReturn(1);
    when(refundMapper.updateStatus(REFUND_ID, "seller_agreed", "refunding")).thenReturn(1);

    service.sellerAgree(REFUND_ID, null, SELLER_ID);

    verify(imService).sendNotice(eq(BUYER_ID), eq("system"), eq("卖家已同意退款"), any());
}

@Test
@DisplayName("买家填写退货物流后通知卖家")
void buyerReturn_notifiesSeller() {
    when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("seller_agreed", "return_refund"));
    when(refundMapper.updateStatus(REFUND_ID, "seller_agreed", "buyer_returned")).thenReturn(1);

    service.buyerReturn(REFUND_ID, "顺丰", "SF123", BUYER_ID);

    verify(imService).sendNotice(eq(SELLER_ID), eq("system"), eq("买家已寄回退货"), any());
}
```

在 `JudgeTest` 追加（该类的 `refund(String status)` 只接收状态一个参数）：
```java
@Test
@DisplayName("裁决支持卖家后通知买卖双方")
void judge_sellerDecision_notifiesBoth() {
    when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("mediation"));
    when(refundMapper.updateStatus(REFUND_ID, "mediation", "closed")).thenReturn(1);

    service.judge(REFUND_ID, "seller", null, "证据不足", 1L);

    verify(imService).sendNotice(eq(BUYER_ID), eq("system"), eq("平台裁决结果"), any());
    verify(imService).sendNotice(eq(SELLER_ID), eq("system"), eq("平台裁决结果"), any());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest='TradeRefundServiceImplApplyTest,TradeRefundServiceImplFlowTest,TradeRefundServiceImplJudgeTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `Wanted but not invoked: imService.sendNotice(...)`.

- [ ] **Step 3: Implement `notifySeller` and calls**

在 `notifyBuyer` 旁新增：

```java
private void notifySeller(Long userId, String title, String content) {
    try {
        imService.sendNotice(userId, "system", title, content);
    }
    catch (Exception e) {
        log.warn("[退款] 卖家通知失败 userId={}: {}", userId, e.getMessage());
    }
}
```

在以下位置加通知（标题固定，内容可含金额 / 物流）：

| 位置 | 收件人 | 标题 | 内容 |
|---|---|---|---|
| `apply` 成功后 | 卖家 | `新的退款申请` | `买家申请退款，金额 ¥<amount>，请及时处理` |
| `sellerAgree` 成功后 | 买家 | `卖家已同意退款` | 仅退款：`退款处理中`；退货退款：`请按退货地址寄回：<returnAddress>` |
| `sellerReject` 成功后 | 买家 | `卖家已拒绝退款` | `如不认可，可申请平台介入` |
| `buyerReturn` 成功后 | 卖家 | `买家已寄回退货` | `物流：<company> <returnNo>，请确认收货` |
| `buyerCancel` 成功后 | 卖家 | `买家已撤销退款` | `退款申请已撤销` |
| `buyerMediate` 成功后 | 卖家 | `买家已申请平台介入` | `请等待平台处理` |
| `sellerConfirmReceipt` 成功后 | 买家 | `卖家已确认收到退货` | `退款处理中` |
| `judge` 驳回分支 | 买家 + 卖家 | `平台裁决结果` | `平台已驳回退款申请` |
| `judge` 支持买家 / 部分退款 | 买家 + 卖家 | `平台裁决结果` | `平台裁决退款 ¥<judgeAmount>` |
| `resolve` success / fail | 买家 + 卖家 | `退款已收口` | `<note>` |
| 超时升级 / 自动同意 / 超时关闭 | 买家 + 卖家 | `退款已转平台介入` / `退款已自动同意` / `退款已关闭` | 对应原因 |

所有调用都必须经过 `notifyBuyer` / `notifySeller`（内部 try/catch），不得直接 `imService.sendNotice`。

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest='TradeRefundServiceImplApplyTest,TradeRefundServiceImplFlowTest,TradeRefundServiceImplJudgeTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Add the IM-failure regression test**

在 `ApplyTest` 追加：
```java
@Test
@DisplayName("IM 通知失败不影响退款申请落库")
void apply_imFailure_doesNotRollback() {
    when(imService.sendNotice(any(), any(), any(), any())).thenThrow(new RuntimeException("im down"));
    // 复用 apply 成功路径的 stub
    // assertDoesNotThrow(() -> service.apply(...));
    // verify(refundMapper).insert(any(TradeRefund.class));
}
```
按现有 `apply` 成功用例补齐 stub；断言不抛异常且 `refundMapper.insert` 被调用。

- [ ] **Step 6: Run the refund suite**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest='TradeRefund*Test' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl*Test.java
git commit -m "feat(refund): 退款状态通知买卖双方"
```

---

### Task 9: 订单发货 / 确认收货 / 支付成功通知

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeOrderServiceImpl.java:41-78,286-326`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/listener/PaySuccessEventListener.java:28-72`
- Modify: 4 个订单测试构造器 + 2 个监听器测试构造器
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeOrderServiceImplLifecycleTest.java`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/listener/PaySuccessEventListenerFlowTest.java`

**Interfaces:**
- Produces: `TradeOrderServiceImpl` / `PaySuccessEventListener` 构造器新增 `IImService`；通知失败只记日志。

- [ ] **Step 1: Write the failing tests**

在 `TradeOrderServiceImplLifecycleTest` 里新增 `private final IImService imService = mock(IImService.class);`，把它传入 `TradeOrderServiceImpl` 和 `PaySuccessEventListener` 的构造器。然后：

- `ship_success_writesAutoConfirm`：先 `TradeOrder notifyOrder = shippedOrder();`（该类的 `shippedOrder()` 已 mock 出 buyerId / sellerId），再 `when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(notifyOrder);`，末尾追加：
```java
verify(imService).sendNotice(eq(BUYER_ID), eq("system"), eq("卖家已发货"), contains("顺丰"));
```
- `confirm_success_marksProductSold`：末尾追加：
```java
verify(imService).sendNotice(eq(SELLER_ID), eq("system"), eq("买家已确认收货"), any());
```

在 `PaySuccessEventListenerFlowTest` 里新增 `private final IImService imService = mock(IImService.class);` 并传入 3 参构造器。`onPaySuccess_writesFlow` 中先构造一个 `TradeOrder`（`orderId=ORDER_ID`、`buyerId=101L`、`sellerId=202L`），`when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(order);`，末尾追加：
```java
verify(imService).sendNotice(eq(101L), eq("system"), eq("支付成功"), any());
verify(imService).sendNotice(eq(202L), eq("system"), eq("买家已付款"), any());
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest='TradeOrderServiceImplLifecycleTest,PaySuccessEventListenerFlowTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — 构造器参数不匹配 / 未调用 `sendNotice`。

- [ ] **Step 3: Implement the notifications**

`TradeOrderServiceImpl`：
- 构造器追加 `IImService imService`，字段赋值。
- `shipOrder` 在 `logisticsMapper.insertLogistics(...)` 之后：
```java
TradeOrder order = orderMapper.selectOrderDetail(orderId, currentTid());
notifyBuyer(order == null ? null : order.getBuyerId(), "卖家已发货",
        "物流公司：" + company + "，单号：" + trackingNo);
```
- `confirmReceipt` 在 `productMapper.markSold(...)` 之后：
```java
notifySeller(order.getSellerId(), "买家已确认收货", "订单已完成");
```
- 新增私有方法（与退款服务相同风格，try/catch）：
```java
private void notifyBuyer(Long userId, String title, String content) { ... }
private void notifySeller(Long userId, String title, String content) { ... }
```

`PaySuccessEventListener`：
- 构造器追加 `IImService imService`。
- `rows > 0` 分支内：
```java
TradeOrder order = orderMapper.selectOrderDetail(orderId, null);
if (order != null) {
    notify(order.getBuyerId(), "支付成功", "订单已支付，等待卖家发货");
    notify(order.getSellerId(), "买家已付款", "请尽快发货");
}
```
- 新增私有 `notify`，try/catch。

- [ ] **Step 4: Update the six test constructors**

- `TradeOrderServiceImplTimeoutTest`
- `TradeOrderServiceImplCreateTest`
- `TradeOrderServiceImplLifecycleTest`
- `TradeOrderServiceImplInternalTest`
- `PaySuccessEventListenerFlowTest`
- `TradeOrderServiceImplLifecycleTest` 中的 `PaySuccessEventListener`

每处追加 `imService`（mock）。

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest='TradeOrder*Test,PaySuccessEventListenerFlowTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 6: Add the IM-failure regression test**

在 `TradeOrderServiceImplLifecycleTest` 的发货用例中：
```java
when(imService.sendNotice(any(), any(), any(), any())).thenThrow(new RuntimeException("im down"));
// 断言 shipOrder 不抛异常，且订单 / 物流仍成功
```
Expected: PASS（通知失败不阻断发货）。

- [ ] **Step 7: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeOrderServiceImpl.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/listener/PaySuccessEventListener.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeOrderServiceImpl*Test.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/listener/PaySuccessEventListenerFlowTest.java
git commit -m "feat(trade): 订单关键状态通知买卖双方"
```

---

### Task 10: 运营退款台超时筛选（后端）

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/ITradeRefundService.java:152`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java:279-282`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/AdminRefundController.java:41-44`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/mapper/TradeRefundMapper.java:selectAdminList`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/resources/mapper/trade/TradeRefundMapper.xml:81-89`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImplAdminPageTest.java`（新建）

**Interfaces:**
- Produces: `ITradeRefundService.adminPage(String status, Boolean overdue)`；`TradeRefundMapper.selectAdminList(String status, Boolean overdue, Long tenantId)`。

- [ ] **Step 1: Write the failing test**

```java
package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import com.twelvet.trade.server.mapper.TradePayFlowMapper;
import com.twelvet.trade.server.mapper.TradePayLookupMapper;
import com.twelvet.trade.server.mapper.TradeRefundLogMapper;
import com.twelvet.trade.server.mapper.TradeRefundMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeRefundServiceImplAdminPageTest {

    private static final Long TENANT_ID = 42L;

    private final TradeRefundMapper refundMapper = mock(TradeRefundMapper.class);

    private final TradeRefundServiceImpl service = new TradeRefundServiceImpl(mock(TradeOrderMapper.class),
            refundMapper, mock(TradeRefundLogMapper.class), mock(TradePayLookupMapper.class),
            mock(IPayOrderService.class), mock(TradePayFlowMapper.class), mock(IImService.class),
            mock(ProductMapper.class), new TradeRefundTimeoutHelper());

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        // PageUtils.startPage() 通过 ServletUtils 读 current / pageSize，单测必须提供请求上下文
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("current", "1");
        request.setParameter("pageSize", "10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        TenantContext.clear();
    }

    @Test
    void adminPage_overdue_passesFlagToMapper() {
        when(refundMapper.selectAdminList("mediation", true, TENANT_ID)).thenReturn(List.of());

        service.adminPage("mediation", true);

        verify(refundMapper).selectAdminList("mediation", true, TENANT_ID);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=TradeRefundServiceImplAdminPageTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — 方法签名不匹配 / mapper 未收到 `overdue`。

- [ ] **Step 3: Implement the backend changes**

`ITradeRefundService`：
```java
TableDataInfo<TradeRefund> adminPage(String status, Boolean overdue);
```

`TradeRefundServiceImpl`：
```java
@Override
public TableDataInfo<TradeRefund> adminPage(String status, Boolean overdue) {
    PageUtils.startPage();
    return PageUtils.getDataTable(refundMapper.selectAdminList(status, overdue, currentTid()));
}
```

`AdminRefundController`：
```java
@GetMapping("/page")
public JsonResult<TableDataInfo<TradeRefund>> page(@RequestParam(required = false) String status,
        @RequestParam(required = false) Boolean overdue) {
    return JsonResult.success(refundService.adminPage(status, overdue));
}
```

`TradeRefundMapper`：
```java
List<TradeRefund> selectAdminList(@Param("status") String status, @Param("overdue") Boolean overdue,
        @Param("tenantId") Long tenantId);
```

`TradeRefundMapper.xml` 的 `selectAdminList`：
```xml
<if test="overdue != null and overdue">
    AND (
        (r.status = 'pending_seller' AND r.create_time &lt; DATE_SUB(NOW(), INTERVAL 48 HOUR))
        OR (r.status IN ('seller_agreed','buyer_returned')
            AND r.update_time &lt; DATE_SUB(NOW(), INTERVAL 7 DAY))
        OR (r.status IN ('mediation','refunding')
            AND r.update_time &lt; DATE_SUB(NOW(), INTERVAL 24 HOUR))
    )
</if>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=TradeRefundServiceImplAdminPageTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/ITradeRefundService.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/AdminRefundController.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/mapper/TradeRefundMapper.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/resources/mapper/trade/TradeRefundMapper.xml \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImplAdminPageTest.java
git commit -m "feat(refund): 运营退款台支持超时筛选"
```

---

### Task 11: 运营退款台 SLA 列与「已超时」页签（前端）

**Files:**
- Create: `ui/src/pages/marketplace/admin/refundSla.ts`
- Test: `ui/src/pages/marketplace/admin/refundSla.test.ts`
- Modify: `ui/src/pages/marketplace/admin/Refund.tsx:62-69,158-230`

**Interfaces:**
- Produces: `refundSla(row, nowMs?): { text: string; color: string; overdue: boolean } | null`
- Produces: `adminRefundPage` 接受 `overdue?: boolean`。

- [ ] **Step 1: Write the failing test**

```ts
import { refundSla } from './refundSla';

const NOW = Date.UTC(2026, 10, 2, 12, 0, 0);

describe('refundSla', () => {
    it('待卖家处理 48h 内显示剩余时间', () => {
        const row = { status: 'pending_seller', createTime: '2026-11-02T00:00:00.000Z' };
        expect(refundSla(row, NOW)).toEqual({ text: '剩余 36h', color: 'green', overdue: false });
    });

    it('待卖家处理超过 48h 显示超时', () => {
        const row = { status: 'pending_seller', createTime: '2026-10-30T00:00:00.000Z' };
        expect(refundSla(row, NOW)).toEqual({ text: '超时 12h', color: 'red', overdue: true });
    });

    it('buyer_returned 用 updateTime 起算 7 天', () => {
        // 10-28 12:00 + 7 天 = 11-04 12:00；当前 11-02 12:00 → 剩余 48h
        const row = { status: 'buyer_returned', updateTime: '2026-10-28T12:00:00.000Z' };
        expect(refundSla(row, NOW)?.text).toBe('剩余 48h');
    });

    it('终态返回 null', () => {
        expect(refundSla({ status: 'refund_success', createTime: '2026-11-01T00:00:00.000Z' }, NOW)).toBeNull();
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ui && npm test -- --runTestsByPath src/pages/marketplace/admin/refundSla.test.ts --watchAll=false`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `refundSla`**

```ts
const SLA_HOURS: Record<string, number> = {
    pending_seller: 48,
    seller_agreed: 168,
    buyer_returned: 168,
    mediation: 24,
    refunding: 24,
};

export function refundSla(row: any, nowMs: number = Date.now()) {
    const hours = SLA_HOURS[row?.status];
    if (!hours) return null;
    const base = row.status === 'pending_seller' ? row.createTime : row.updateTime || row.createTime;
    const baseMs = new Date(base).getTime();
    if (!Number.isFinite(baseMs)) return null;
    const remainMs = baseMs + hours * 3600_000 - nowMs;
    const absHours = Math.ceil(Math.abs(remainMs) / 3600_000);
    if (remainMs < 0) {
        return { text: `超时 ${absHours}h`, color: 'red', overdue: true };
    }
    return {
        text: `剩余 ${absHours}h`,
        color: remainMs < 6 * 3600_000 ? 'orange' : 'green',
        overdue: false,
    };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ui && npm test -- --runTestsByPath src/pages/marketplace/admin/refundSla.test.ts --watchAll=false`
Expected: PASS.

- [ ] **Step 5: Wire the SLA column and tab**

`Refund.tsx`：
```tsx
import { refundSla } from './refundSla';

const STATUS_TABS = [
    // ...原有项
    { key: 'overdue', label: '已超时' },
];

// columns 追加
{
    title: 'SLA',
    key: 'sla',
    width: 110,
    render: (_, row) => {
        const sla = refundSla(row);
        return sla ? <Tag color={sla.color}>{sla.text}</Tag> : '-';
    },
},

// request 改为
const res: any = await adminRefundPage({
    status: status === 'all' || status === 'overdue' ? undefined : status,
    overdue: status === 'overdue' ? true : undefined,
    current: params.current,
    pageSize: params.pageSize,
});
```

`api.ts` 的 `adminRefundPage` 已接受 `Record<string, any>`，无需改签名；若补类型，保持 `overdue?: boolean` 可选。

- [ ] **Step 6: Run tests and type check**

Run: `cd ui && npm test -- --runTestsByPath src/pages/marketplace/admin/refundSla.test.ts --watchAll=false && npm run tsc`
Expected: PASS + 0 errors.

- [ ] **Step 7: Commit**

```bash
git add ui/src/pages/marketplace/admin/refundSla.ts ui/src/pages/marketplace/admin/refundSla.test.ts \
        ui/src/pages/marketplace/admin/Refund.tsx
git commit -m "feat(ui): 运营退款台 SLA 与超时筛选"
```

---

### Task 12: 担保交易与结算产品 / 合规方案

**Files:**
- Create: `docs/superpowers/specs/2026-11-02-settlement-compliance-design.md`

**Interfaces:**
- Produces: 一份可直接给 Phase 1 开发与法务评审的文档，不写代码。

- [ ] **Step 1: Write the document**

至少包含以下章节，每节必须有明确结论或待确认项：

1. 资金流与合规路径：支付宝担保交易 / 分账 vs 微信电商收付通；平台不得沉淀资金的理由。
2. 状态机：`holding → pending_settle → settled → withdraw / clawback`，以及窗口内退款的原路退回。
3. 数据模型：`seller_account`、`settlement_order`、`seller_balance_flow`、`trade_withdraw` 的字段、索引、租户键。
4. 结算时点：`hold_until = done_at + 7d`；T+1 打款；窗口内退款不走结算。
5. 退款与结算互斥 / 扣回：结算前原路退；结算后余额扣回；余额不足冻结后续结算并转人工，不做平台垫付。
6. 对账：每日三方对账（`settlement_order` + `seller_balance_flow` + `trade_pay`），差异队列。
7. Phase 1 mock 边界：人工实名核验、银行卡提现、mock 打款、验收标准。
8. 待法务 / 业务确认：真实渠道、商户资质、二清结论、佣金费率。

- [ ] **Step 2: Verify the document**

Run:
```bash
grep -nE '^## ' docs/superpowers/specs/2026-11-02-settlement-compliance-design.md
```
Expected: 至少 8 个二级标题，且第 1、3、4、5 节都有明确结论（不是 TBD）。

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-11-02-settlement-compliance-design.md
git commit -m "docs(refund): 担保结算产品与合规方案"
```

---

## Phase 0A 验收

全部 Task 完成后，按顺序跑：

```bash
# 1. 后端交易模块全量单测
cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test

# 2. 前端全量单测 + 类型检查
cd ui && npm test -- --watchAll=false && npm run tsc

# 3. SQL 幂等复核：重复执行两遍，第二遍全部输出「已存在，跳过」
mysql -uroot -p123456 twelvet-fast < backend/sql/refund_feature.sql
```

人工冒烟（本地环境，mock 支付）：

1. 买家对 `unshipped` 订单发起仅退款 → 卖家端收到站内通知 → 不处理，把退款单 `create_time` 改到 49 小时前，触发任务 → 退款自动成功、订单 `refunded`。
2. 买家对 `shipped` 订单发起退货退款 → 卖家同意并填地址 → 买家填物流 → 卖家确认收货 → 退款成功；每一步双方都收到站内通知。
3. 运营退款台出现 SLA 列；「已超时」页签能筛出超时单。

对应的 E2E 脚本复跑与看板验收在 Phase 0B 计划中执行。

---

## Out of Scope（Phase 0B，单独计划）

以下内容不在本计划内，避免把新子系统塞进退款收口：

- 漏斗事件表 / 埋点 SDK / 试点社区看板 v0。
- 结算代码实现（Phase 1）。
- 售后 7 天窗口语义调整（`seller_agreed_at`、done 仅退款举证）——Phase 1 的售后矩阵任务。
- 运营侧 IM 推送（Phase 0A 只做买家 / 卖家；运营通过 SLA 台与看板发现）。
- 真实支付 / 合规渠道接入。

---

## Self-Review

- **Spec coverage**：Phase 0 的 6 项退款遗留 → Task 1–6；买家 / 卖家通知覆盖 → Task 8–9；运营侧「通知」由 Task 10–11 的 SLA 倒计时 + 超时队列承担（无运营用户定向机制，不做 IM 群发，这是 0A 的产品取舍）；索引 → Task 5；结算方案 → Task 12。漏斗埋点与看板明确拆到 Phase 0B。
- **Placeholder scan**：无 `TBD`；Task 9 的构造器修改已列出具体测试文件与 `IImService` 注入点，Task 10 给出了完整可编译测试类。
- **Type consistency**：`adminPage(String, Boolean)`、`selectAdminList(String, Boolean, Long)`、`refundSla(row, nowMs?)` 在任务间保持一致。
- **Review Focus**：五类风险分别由 Task 1、2、6、8/9、7 的测试覆盖；SLA 时间基准由 Task 11 的固定时间用例覆盖。
