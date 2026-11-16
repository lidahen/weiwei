# Phase 0B 漏斗埋点与试点社区看板 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 spec §6.3 的 10 个核心漏斗事件全部服务端落库，并交付按社区（租户）切片的运营看板 v0。

**Architecture:** 新增 `funnel_event` 单表与 `IFunnelEventService`（接口在 product-api，实现在 product-server）。所有事件在权威业务方法的同一次调用内记录，写入尽力而为、失败只记日志；服务端事件用 `event_key` 唯一键做幂等。看板只做原始事件按日聚合，不建汇总表。

**Tech Stack:** Java 17 / Spring Boot 3.5.4 / MyBatis XML / Sa-Token；Umi Max 4 + React 18 + antd 5 + Jest；MySQL。

**Spec:** `docs/superpowers/specs/2026-11-02-product-strategy-design.md`（§6.1 北极星、§6.3 埋点要求、§5 D7/D8、§11 Phase 0 验收）

## Global Constraints

- **服务端采集，不收客户端上报**：10 个事件全部在后端权威方法内记录，禁止新增公开上报端点（防伪造漏斗）。
- **写入绝不阻断业务**：`record`/`recordBatch` 内部 try/catch，异常只 `log.warn`；不得向外抛。
- **事件必须带租户**：`tenant_id` 优先取业务对象（订单 / 评价的 `tenant_id`），其次 `TenantContext.get()`；Quartz 无上下文时必须用订单租户。
- **幂等**：服务端事件 `event_key = "<event_type>:<业务ID>"`（订单 / 退款 / 评价 / 会话），Mapper 用 `INSERT IGNORE` + `uk_event_key`；支付回调与超时扫描重试不得重复计数。
- **事件类型固定 10 个**（snake_case）：`product_exposure`、`product_detail_view`、`im_start`、`order_create`、`pay_success`、`ship`、`confirm_receipt`、`refund_apply`、`refund_success`、`review_create`。
- **看板按租户切片（D7/D8）**：`tenantId` 为空时只返回租户选项，绝不返回跨租户聚合数据。
- **权限**：接口 `marketplace:admin:funnel`；菜单挂 `parent_id=2100`，授权角色 1 与 3；SQL 幂等。
- **SQL 顺序**：`backend/sql/funnel_event.sql` 在基线 `docs/db/twelvet-fast_2026-10-21.sql` 之后手工执行一次。
- **格式**：Java 提交前 `mvn spring-javaformat:apply`；UI 用 npm，不用 yarn。

## Review Focus

以下 5 类失败模式最容易在真实使用中出问题，每个都必须在对应任务的测试里钉住：

1. **埋点异常回滚业务**：`funnel_event` 写入抛异常时，下单 / 支付回调 / 发货 / 确认收货 / 退款 / 评价事务必须照常提交（Task 1–5）。
2. **重复回调重复计数**：支付回调重试、超时扫描重试、退款收尾重复调用时，同一 `event_key` 只能有 1 行（Task 3、4）。
3. **无租户上下文落错租户**：Quartz 自动确认收货、支付回调线程没有 `TenantContext`，事件租户必须来自订单（Task 3）。
4. **看板串租**：`tenantId=null` 时必须返回空统计（只有租户选项），不得返回全租户聚合（Task 6）。
5. **曝光写放大拖慢首页**：一次列表请求产生 N 条曝光，必须一次批量 insert，异常不拖慢主流程（Task 2）。

---

## File Structure

| 文件 | 职责 |
|---|---|
| `backend/sql/funnel_event.sql` | 建表 + 索引 + 菜单/授权（幂等，手工执行） |
| `backend/twelvet-product/twelvet-product-api/.../domain/FunnelEvent.java` | 事件 DTO 与 10 个类型常量 |
| `backend/twelvet-product/twelvet-product-api/.../service/IFunnelEventService.java` | 写入与看板查询接口 |
| `backend/twelvet-product/twelvet-product-server/.../mapper/FunnelEventMapper.java` + `resources/mapper/product/FunnelEventMapper.xml` | 单条/批量写入、日聚合、租户选项 |
| `backend/twelvet-product/twelvet-product-server/.../service/impl/FunnelEventServiceImpl.java` | 补租户/类目、吞异常、看板组装 |
| `backend/twelvet-product/twelvet-product-server/.../controller/AdminFunnelController.java` | 看板接口 |
| `backend/twelvet-product/.../RecommendServiceImpl.java`、`ProductController.java` | 曝光 / 详情事件 |
| `backend/twelvet-trade/.../TradeOrderServiceImpl.java`、`TradeOrderTimeoutHelper.java`、`PaySuccessEventListener.java` | 下单 / 支付 / 发货 / 确认事件 |
| `backend/twelvet-trade/.../TradeRefundServiceImpl.java`、`TradeReviewServiceImpl.java` | 退款 / 评价事件 |
| `backend/twelvet-im/.../ImServiceImpl.java` + `pom.xml` | 会话开始事件 |
| `ui/src/pages/marketplace/admin/Funnel.tsx` + `Funnel.test.tsx`、`ui/config/routes.ts`、`ui/src/services/marketplace/api.ts` | 运营看板 |

---

### Task 1: 事件表与写入服务

**Files:**
- Create: `backend/sql/funnel_event.sql`
- Create: `backend/twelvet-product/twelvet-product-api/src/main/java/com/twelvet/product/api/domain/FunnelEvent.java`
- Create: `backend/twelvet-product/twelvet-product-api/src/main/java/com/twelvet/product/api/service/IFunnelEventService.java`
- Create: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/FunnelEventMapper.java`
- Create: `backend/twelvet-product/twelvet-product-server/src/main/resources/mapper/product/FunnelEventMapper.xml`
- Create: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/service/impl/FunnelEventServiceImpl.java`
- Test: `backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/service/impl/FunnelEventServiceImplTest.java`

**Interfaces:**
- Produces: `FunnelEvent`（字段 `tenantId/eventType/userId/role/productId/category/aiAssisted/scene/bizId/eventKey`）；常量 `FunnelEvent.TYPE_*` 共 10 个。
- Produces: `IFunnelEventService.record(FunnelEvent)`、`recordBatch(List<FunnelEvent>)`（`adminStats` 在 Task 6 才加入接口，避免先写未测试的生产方法）。
- Produces: `FunnelEventMapper.insertIgnore/insertBatch`（三个统计查询在 Task 6 加入）。

- [ ] **Step 1: Write the failing test**

```java
class FunnelEventServiceImplTest {

    private final FunnelEventMapper eventMapper = mock(FunnelEventMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final FunnelEventServiceImpl service = new FunnelEventServiceImpl(eventMapper, productMapper);

    @AfterEach void clear() { TenantContext.clear(); }

    private FunnelEvent event(String type) {
        FunnelEvent e = new FunnelEvent();
        e.setEventType(type);
        e.setUserId(7L);
        e.setRole("buyer");
        e.setProductId(100L);
        e.setEventKey(type + ":1");
        return e;
    }

    @Test
    @DisplayName("租户取上下文，类目由商品补齐，aiAssisted 空值落 0")
    void record_enrichesTenantAndCategory() {
        TenantContext.set(42L);
        Product p = mock(Product.class);
        when(p.getCatL1()).thenReturn("数码");
        when(productMapper.selectById(100L, 42L)).thenReturn(p);

        service.record(event(FunnelEvent.TYPE_ORDER_CREATE));

        ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
        verify(eventMapper).insertIgnore(captor.capture());
        assertEquals(42L, captor.getValue().getTenantId());
        assertEquals("数码", captor.getValue().getCategory());
        assertEquals(Boolean.FALSE, captor.getValue().getAiAssisted());
    }

    @Test
    @DisplayName("事件自带租户优先于上下文（回调/Quartz 场景）")
    void record_explicitTenantWins() {
        TenantContext.set(42L);
        FunnelEvent e = event(FunnelEvent.TYPE_PAY_SUCCESS);
        e.setTenantId(7L);

        service.record(e);

        ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
        verify(eventMapper).insertIgnore(captor.capture());
        assertEquals(7L, captor.getValue().getTenantId());
        verify(productMapper).selectById(100L, 7L);
    }

    @Test
    @DisplayName("写入抛异常不向外传播（埋点绝不回滚业务）")
    void record_failureSwallowed() {
        TenantContext.set(42L);
        when(eventMapper.insertIgnore(any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> service.record(event(FunnelEvent.TYPE_REVIEW_CREATE)));
    }

    @Test
    @DisplayName("批次写入：一次 mapper 调用，按上下文补租户")
    void recordBatch_singleInsert() {
        TenantContext.set(42L);
        List<FunnelEvent> events = List.of(event(FunnelEvent.TYPE_PRODUCT_EXPOSURE),
                event(FunnelEvent.TYPE_PRODUCT_EXPOSURE));

        service.recordBatch(events);

        ArgumentCaptor<List<FunnelEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventMapper).insertBatch(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(42L, captor.getValue().get(0).getTenantId());
        verify(eventMapper, never()).insertIgnore(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=FunnelEventServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `FunnelEventServiceImpl` / `FunnelEvent` 不存在（编译失败）。

- [ ] **Step 3: Implement schema + domain + interface + mapper + service**

`backend/sql/funnel_event.sql`（幂等）：

```sql
CREATE TABLE IF NOT EXISTS `funnel_event` (
  `event_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '租户/社区ID',
  `event_type` varchar(32) NOT NULL COMMENT '10 类漏斗事件之一',
  `user_id` bigint DEFAULT NULL COMMENT '匿名曝光为空',
  `role` varchar(16) DEFAULT NULL COMMENT 'buyer/seller/visitor',
  `product_id` bigint DEFAULT NULL,
  `category` varchar(20) DEFAULT NULL COMMENT 'product.cat_l1 快照',
  `ai_assisted` tinyint NOT NULL DEFAULT '0' COMMENT '1=AI 辅助产生',
  `scene` varchar(32) DEFAULT NULL COMMENT 'home_recommend/nearby/search/detail',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '订单/退款/评价/会话ID',
  `event_key` varchar(96) DEFAULT NULL COMMENT '幂等键，服务端事件必填',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_event_key` (`event_key`),
  KEY `idx_tenant_type_time` (`tenant_id`,`event_type`,`create_time`),
  KEY `idx_product_time` (`product_id`,`create_time`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核心漏斗事件';
```

`FunnelEvent`：普通 POJO + 10 个 `public static final String TYPE_*`；`aiAssisted` 用 `Boolean`。

`IFunnelEventService`：

```java
public interface IFunnelEventService {

    void record(FunnelEvent event);

    void recordBatch(List<FunnelEvent> events);

    /** tenantId 为空：只返回 tenants，不返回任何聚合数据 */
    Map<String, Object> adminStats(Integer days, Long tenantId);
}
```

`FunnelEventMapper`（本任务只写两个写入方法；三个统计查询与 SQL 在 Task 6 加入）：

```java
int insertIgnore(@Param("e") FunnelEvent event);
int insertBatch(@Param("list") List<FunnelEvent> list);
```

XML：`insertIgnore` 用 `INSERT IGNORE INTO funnel_event (...) VALUES (...)`；`insertBatch` 用 `<foreach>`。

`FunnelEventServiceImpl.record` 逻辑：

1. `eventType` 为空或不在 10 类白名单 → 直接 return。
2. `tenantId == null` → `TenantContext.get()`。
3. `category == null && productId != null` → `productMapper.selectById(productId, tenantId)`，取 `getCatL1()`。
4. `aiAssisted == null` → `false`。
5. `try { eventMapper.insertIgnore(e) } catch (Exception e) { log.warn(...) }`。

`recordBatch`：先按上下文补租户 / 默认 aiAssisted，再一次 `insertBatch`，整体 try/catch。

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=FunnelEventServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（4 tests）。

- [ ] **Step 5: Apply schema locally**

Run: `mysql -uroot -p twelvet-fast < backend/sql/funnel_event.sql`（本机按 `application-dev.yml` 的账号执行；脚本用 `CREATE TABLE IF NOT EXISTS`，重复执行无副作用）
Expected: 无错误；`SHOW INDEX FROM funnel_event` 含 `uk_event_key`、`idx_tenant_type_time`。

- [ ] **Step 6: Commit**

```bash
git add backend/sql/funnel_event.sql \
        backend/twelvet-product/twelvet-product-api/src/main/java/com/twelvet/product/api/domain/FunnelEvent.java \
        backend/twelvet-product/twelvet-product-api/src/main/java/com/twelvet/product/api/service/IFunnelEventService.java \
        backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/FunnelEventMapper.java \
        backend/twelvet-product/twelvet-product-server/src/main/resources/mapper/product/FunnelEventMapper.xml \
        backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/service/impl/FunnelEventServiceImpl.java \
        backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/service/impl/FunnelEventServiceImplTest.java
git commit -m "feat(analytics): 漏斗事件表与写入服务"
```

---

### Task 2: 商品曝光 / 详情浏览埋点

**Files:**
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/service/impl/RecommendServiceImpl.java:170-184,581-602`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/controller/ProductController.java:28-38,148-162`
- Test: `backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/service/impl/RecommendExposureEventTest.java`
- Test: `backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/controller/ProductControllerFunnelTest.java`

**Interfaces:**
- Consumes: `IFunnelEventService.recordBatch(List<FunnelEvent>)`、`record(FunnelEvent)`（Task 1）。
- Produces: `RecommendServiceImpl` 构造器追加 `IFunnelEventService`；`ProductController` 构造器追加 `IFunnelEventService`。

- [ ] **Step 1: Write the failing tests**

`RecommendExposureEventTest`（同包直接调用包级可见的 `logImpressions`）：

```java
@Test
@DisplayName("推荐流每张卡片写一条 product_exposure，带租户/类目/场景")
void logImpressions_writesExposureEvents() {
    TenantContext.set(42L);
    Product p1 = product(1L, "数码");
    Product p2 = product(2L, "家具");

    service.logImpressions(42L, 7L, List.of(p1, p2), 0, "home_recommend", 0);

    ArgumentCaptor<List<FunnelEvent>> captor = ArgumentCaptor.forClass(List.class);
    verify(funnelEventService).recordBatch(captor.capture());
    List<FunnelEvent> events = captor.getValue();
    assertEquals(2, events.size());
    assertEquals(FunnelEvent.TYPE_PRODUCT_EXPOSURE, events.get(0).getEventType());
    assertEquals(42L, events.get(0).getTenantId());
    assertEquals("数码", events.get(0).getCategory());
    assertEquals("home_recommend", events.get(0).getScene());
    assertEquals("buyer", events.get(0).getRole());
}
```

`ProductControllerFunnelTest`：

```java
@Test
@DisplayName("商品详情写 product_detail_view，class 取商品 catL1")
void detail_recordsViewEvent() {
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
        stp.when(StpUtil::isLogin).thenReturn(true);
        stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
        Product product = product(1L, "数码");
        when(productService.selectProductById(1L)).thenReturn(product);

        controller.detail(1L);

        ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
        verify(funnelEventService).record(captor.capture());
        assertEquals(FunnelEvent.TYPE_PRODUCT_DETAIL_VIEW, captor.getValue().getEventType());
        assertEquals("数码", captor.getValue().getCategory());
        assertEquals(7L, captor.getValue().getUserId());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest='RecommendExposureEventTest,ProductControllerFunnelTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — 构造器参数不匹配 / `logImpressions` 不可见。

- [ ] **Step 3: Implement**

- `RecommendServiceImpl`：构造器末尾追加 `IFunnelEventService funnelEventService`（10 参数 → 11 参数；测试里前 10 个用 `mock(...)`，第 11 个传 `funnelEventService`）；`logImpressions` 从 `private` 改为**包级可见**（仅放宽可见性以便同包测试直接调用，不改行为），在 `impressionMapper.batchInsert(rows)` 之后：

```java
List<FunnelEvent> events = new ArrayList<>(page.size());
for (Product p : page) {
    FunnelEvent e = new FunnelEvent();
    e.setTenantId(tenantId);
    e.setEventType(FunnelEvent.TYPE_PRODUCT_EXPOSURE);
    e.setUserId(userId);
    e.setRole(userId == null ? "visitor" : "buyer");
    e.setProductId(p.getProductId());
    e.setCategory(p.getCatL1());
    e.setScene(scene);
    events.add(e);
}
funnelEventService.recordBatch(events);
```

- `ProductController`：构造器追加 `IFunnelEventService`（2 参数 → 3 参数，测试 `new ProductController(productService, recommendService, funnelEventService)`）；`detail` 在 `recommendService.recordView(id, userId)` 之后：

```java
FunnelEvent e = new FunnelEvent();
e.setEventType(FunnelEvent.TYPE_PRODUCT_DETAIL_VIEW);
e.setUserId(userId);
e.setRole(userId == null ? "visitor" : "buyer");
e.setProductId(id);
e.setCategory(product.getCatL1());
e.setScene("detail");
funnelEventService.record(e);
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest='RecommendExposureEventTest,ProductControllerFunnelTest,FunnelEventServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/service/impl/RecommendServiceImpl.java \
        backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/controller/ProductController.java \
        backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/service/impl/RecommendExposureEventTest.java \
        backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/controller/ProductControllerFunnelTest.java
git commit -m "feat(analytics): 商品曝光与详情浏览事件"
```

---

### Task 3: 订单 / 支付 / 发货 / 确认收货埋点

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeOrderServiceImpl.java`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeOrderTimeoutHelper.java`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/listener/PaySuccessEventListener.java`
- Test: 5 个既有测试补充构造器 + 断言（见 Step 4 列表）

**Interfaces:**
- Consumes: `IFunnelEventService.record(FunnelEvent)`。
- Produces: 构造器追加参数：`TradeOrderServiceImpl(... 8 args ..., IFunnelEventService)` → 9 参；`TradeOrderTimeoutHelper(TradeOrderMapper, ProductMapper, IFunnelEventService)` → 3 参；`PaySuccessEventListener(TradeOrderMapper, TradePayFlowMapper, IImService, IFunnelEventService)` → 4 参。
- Produces: 事件键 `order_create:{orderId}`、`pay_success:{orderId}`、`ship:{orderId}`、`confirm_receipt:{orderId}`。

- [ ] **Step 1: Write the failing assertions**

在既有测试类追加/修改：

`TradeOrderServiceImplCreateTest`（构造器加 `funnelEventService`）：

```java
@Test
@DisplayName("下单成功写 order_create（买家、商品、类目、非 AI）")
void create_recordsOrderCreateEvent() {
    // 复用既有 happyPath 桩
    Map<String, Object> created = service.createOrder(BUYER_ID, PRODUCT_ID, ADDRESS_ID, null, null);
    Long orderId = (Long) created.get("orderId");

    ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
    verify(funnelEventService).record(captor.capture());
    assertEquals(FunnelEvent.TYPE_ORDER_CREATE, captor.getValue().getEventType());
    assertEquals("order_create:" + orderId, captor.getValue().getEventKey());
    assertEquals("buyer", captor.getValue().getRole());
    assertEquals(Boolean.FALSE, captor.getValue().getAiAssisted());
}
```

`TradeOrderServiceImplInternalTest`：断言 `aiAssisted == true`（AI 导购内部下单）。

`TradeOrderServiceImplLifecycleTest`：
- `ship_success_writesAutoConfirm` 末尾 `verify(funnelEventService).record(argThat(e -> TYPE_SHIP.equals(e.getEventType()) && "seller".equals(e.getRole())));`
- `confirm_success_marksProductSold` 末尾断言 `confirm_receipt`、`role=buyer`。
- `payListener_setsUnshipped` 断言 `pay_success`，且 `tenantId` 来自订单（桩里 `selectOrderDetail` 返回订单带 tenant）。

`TradeOrderTimeoutHelperTest`：自动确认成功后 `orderMapper.selectOrderDetail(orderId, null)` 返回 tenant=42 的订单，断言 `confirm_receipt` 事件的 `tenantId=42`（Quartz 无上下文）。

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest='TradeOrderServiceImplCreateTest,TradeOrderServiceImplInternalTest,TradeOrderServiceImplLifecycleTest,TradeOrderTimeoutHelperTest,PaySuccessEventListenerFlowTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — 构造器参数不匹配（先改测试构造器，未改生产）。

- [ ] **Step 3: Implement**

- `TradeOrderServiceImpl` 构造器末尾追加 `IFunnelEventService`。新增私有重载：

```java
@Override
public Map<String, Object> createOrder(Long buyerId, Long productId, Long addressId, String buyerRemark,
        String deliveryType) {
    return createOrder(buyerId, productId, addressId, buyerRemark, deliveryType, false);
}

private Map<String, Object> createOrder(..., boolean aiAssisted) { ... 原逻辑 ... }
```

`insertOrder` 之后记录：

```java
FunnelEvent e = new FunnelEvent();
e.setEventType(FunnelEvent.TYPE_ORDER_CREATE);
e.setTenantId(order.getTenantId());
e.setUserId(buyerId);
e.setRole("buyer");
e.setProductId(productId);
e.setCategory(product.getCatL1());
e.setAiAssisted(aiAssisted);
e.setBizId(String.valueOf(order.getOrderId()));
e.setEventKey("order_create:" + order.getOrderId());
funnelEventService.record(e);
```

`createOrderForUser` 的两个重载（2 参与 3 参）最终都走私有重载并传 `true`：2 参委托给 3 参；3 参在 `TenantContext.set(...)` 的 `try` 内调用 `createOrder(buyerId, productId, addressId, null, null, true)`。

- `shipOrder`：`selectOrderDetail` 之后记录 `ship`（`tenantId=order.getTenantId()`、`userId=sellerId`、`role=seller`、`scene=null`、键 `ship:{orderId}`）。
- `confirmReceipt`：`markSold` 之后记录 `confirm_receipt`（`tenantId=order.getTenantId()`、`userId=buyerId`、`role=buyer`、键 `confirm_receipt:{orderId}`）。
- `TradeOrderTimeoutHelper.confirmSingleOrder`：`autoConfirmOrder` 成功后 `selectOrderDetail(orderId, null)`，非空则记录 `confirm_receipt`（租户来自订单，`userId` 为空、`role=buyer`、键 `confirm_receipt:{orderId}`）。构造器追加 `IFunnelEventService`。
- `PaySuccessEventListener`：`order != null` 分支记录 `pay_success`（`tenantId=order.getTenantId()`、`userId=order.getBuyerId()`、`role=buyer`、键 `pay_success:{orderId}`）。

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest='TradeOrder*Test,PaySuccessEventListenerFlowTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS。**同时更新这 5 个测试类的构造器**：`TradeOrderServiceImplCreateTest`、`TradeOrderServiceImplInternalTest`、`TradeOrderServiceImplLifecycleTest`、`TradeOrderServiceImplTimeoutTest`、`TradeOrderTimeoutHelperTest`、`PaySuccessEventListenerFlowTest`。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeOrderServiceImpl.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeOrderTimeoutHelper.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/listener/PaySuccessEventListener.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeOrderServiceImpl*Test.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeOrderTimeoutHelperTest.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/listener/PaySuccessEventListenerFlowTest.java
git commit -m "feat(analytics): 订单/支付/发货/确认漏斗事件"
```

---

### Task 4: 退款 / 评价埋点

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeReviewServiceImpl.java`
- Test: `TradeRefundServiceImplApplyTest`、`TradeRefundServiceImplExecuteTest`（追加断言）
- Test: `backend/.../service/impl/TradeReviewServiceFunnelTest.java`（新建）
- 构造器更新：7 个退款测试类（Apply/Execute/Flow/Judge/Settle/AdminPage/ScanTask）

**Interfaces:**
- Consumes: `IFunnelEventService.record`。
- Produces: 构造器追加参数：`TradeRefundServiceImpl(... 9 args ..., IFunnelEventService)` → 10 参；`TradeReviewServiceImpl(TradeReviewMapper, TradeOrderMapper, TenantSupport, IFunnelEventService)` → 4 参；键 `refund_apply:{refundId}`、`refund_success:{refundId}`、`review_create:{reviewId}`。

- [ ] **Step 1: Write the failing tests**

`TradeRefundServiceImplApplyTest` 追加：

```java
@Test
@DisplayName("申请退款成功写 refund_apply（租户取订单）")
void apply_recordsRefundApplyEvent() {
    // 既有 apply 桩
    ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
    verify(funnelEventService).record(captor.capture());
    assertEquals(FunnelEvent.TYPE_REFUND_APPLY, captor.getValue().getEventType());
    assertEquals("refund_apply:" + REFUND_ID, captor.getValue().getEventKey());
    assertEquals(ORDER_TENANT_ID, captor.getValue().getTenantId());
}
```

`TradeRefundServiceImplExecuteTest` 追加：退款成功路径断言 `refund_success:{REFUND_ID}`；网关失败路径 `verify(funnelEventService, never()).record(argThat(e -> TYPE_REFUND_SUCCESS.equals(e.getEventType())))`。

`TradeReviewServiceFunnelTest`（新建，3 个 mapper + funnel mock）：

```java
@Test
@DisplayName("评价成功写 review_create，角色取评价的 role")
void submit_recordsReviewCreate() {
    // order 已 done、reviewMapper.insertReview 设置 reviewId
    service.submit(ORDER_ID, BUYER_ID, 5, "好", null, null, false);

    ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
    verify(funnelEventService).record(captor.capture());
    assertEquals(FunnelEvent.TYPE_REVIEW_CREATE, captor.getValue().getEventType());
    assertEquals("buyer", captor.getValue().getRole());
    assertEquals("review_create:" + REVIEW_ID, captor.getValue().getEventKey());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest='TradeRefundServiceImplApplyTest,TradeRefundServiceImplExecuteTest,TradeReviewServiceFunnelTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — 构造器不匹配 / `TradeReviewServiceFunnelTest` 编译失败。

- [ ] **Step 3: Implement**

- `TradeRefundServiceImpl`：构造器末尾追加 `IFunnelEventService`。
  - `apply`：`insert` 之后记录 `refund_apply`（`tenantId=order.getTenantId()`、`userId=buyerId`、`role=buyer`、`productId=order.getProductId()`、键 `refund_apply:{refundId}`）。
  - `finishRefundSuccess`：`notifyBuyer` 之前记录 `refund_success`（`tenantId = order == null ? null : order.getTenantId()`、`userId=refund.getBuyerId()`、`role=buyer`、`productId = order == null ? null : order.getProductId()`；键 `refund_success:{refundId}`）。`order == null` 时租户留空，由 `FunnelEventServiceImpl` 回退到 `TenantContext`。
- `TradeReviewServiceImpl`：构造器末尾追加 `IFunnelEventService`；`insertReview` 之后记录 `review_create`（`tenantId=review.getTenantId()`、`userId`、`role=review.getRole()`、`productId`、键 `review_create:{reviewId}`）。

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest='TradeRefund*Test,TradeReview*Test' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS。构造器更新：`TradeRefundServiceImplApplyTest/ExecuteTest/FlowTest/JudgeTest/SettleTest/AdminPageTest`、`TradeRefundScanTaskTest`。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl.java \
        backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/TradeReviewServiceImpl.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeRefundServiceImpl*Test.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/TradeReviewServiceFunnelTest.java \
        backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/task/TradeRefundScanTaskTest.java
git commit -m "feat(analytics): 退款与评价漏斗事件"
```

---

### Task 5: 会话开始（im_start）埋点

**Files:**
- Modify: `backend/twelvet-im/twelvet-im-server/pom.xml`（追加 `twelvet-product-api`）
- Modify: `backend/twelvet-im/twelvet-im-server/src/main/java/com/twelvet/im/server/service/impl/ImServiceImpl.java:40-58,84-105`
- Test: `backend/twelvet-im/twelvet-im-server/src/test/java/com/twelvet/im/server/service/impl/ImServiceFunnelTest.java`（新建）
- Test: `ImServiceSendNoticeTest.java`（构造器追加 mock）

**Interfaces:**
- Consumes: `IFunnelEventService.record`（product-api）。
- Produces: `ImServiceImpl` 构造器追加 `IFunnelEventService`；键 `im_start:{convId}`。

- [ ] **Step 1: Write the failing test**

```java
class ImServiceFunnelTest {

    private final ImConversationMapper conversationMapper = mock(ImConversationMapper.class);
    private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);
    private final ImServiceImpl service = new ImServiceImpl(conversationMapper, mock(ImMessageMapper.class),
            mock(ImNoticeMapper.class), mock(ImUnreadMapper.class), mock(ImUserMapper.class),
            mock(TenantSupport.class), funnelEventService);

    @BeforeEach void tenant() { TenantContext.set(42L); }
    @AfterEach void clear() { TenantContext.clear(); }

    @Test
    @DisplayName("新建会话写 im_start（发起人、商品）")
    void newConversation_recordsImStart() {
        when(conversationMapper.selectConversationByUsers(7L, 9L, 42L)).thenReturn(null);
        doAnswer(inv -> { inv.getArgument(0, ImConversation.class).setConvId(55L); return 1; })
            .when(conversationMapper).insertConversation(any());

        service.ensureConversation(7L, 9L, 100L);

        ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
        verify(funnelEventService).record(captor.capture());
        assertEquals(FunnelEvent.TYPE_IM_START, captor.getValue().getEventType());
        assertEquals("im_start:55", captor.getValue().getEventKey());
        assertEquals(100L, captor.getValue().getProductId());
        assertEquals(7L, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("已有会话不重复写 im_start")
    void existingConversation_noEvent() {
        ImConversation existing = new ImConversation();
        existing.setConvId(55L);
        when(conversationMapper.selectConversationByUsers(7L, 9L, 42L)).thenReturn(existing);

        service.ensureConversation(7L, 9L, 100L);

        verifyNoInteractions(funnelEventService);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -pl twelvet-im/twelvet-im-server -am test -Dtest=ImServiceFunnelTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — 编译失败（`IFunnelEventService` 未在 im 模块 classpath / 构造器不匹配）。

- [ ] **Step 3: Implement**

- `twelvet-im-server/pom.xml` 追加：

```xml
<dependency>
    <groupId>com.twelvet</groupId>
    <artifactId>twelvet-product-api</artifactId>
    <version>${twelvet.version}</version>
</dependency>
```

- `ImServiceImpl` 构造器末尾追加 `IFunnelEventService`；`ensureConversation` 在 `insertConversation` 之后：

```java
FunnelEvent e = new FunnelEvent();
e.setEventType(FunnelEvent.TYPE_IM_START);
e.setTenantId(currentTid());
e.setUserId(userId);
e.setRole("buyer");
e.setProductId(productId);
e.setBizId(String.valueOf(conversation.getConvId()));
e.setEventKey("im_start:" + conversation.getConvId());
funnelEventService.record(e);
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -pl twelvet-im/twelvet-im-server -am test -Dtest='ImServiceFunnelTest,ImServiceSendNoticeTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-im/twelvet-im-server/pom.xml \
        backend/twelvet-im/twelvet-im-server/src/main/java/com/twelvet/im/server/service/impl/ImServiceImpl.java \
        backend/twelvet-im/twelvet-im-server/src/test/java/com/twelvet/im/server/service/impl/ImServiceFunnelTest.java \
        backend/twelvet-im/twelvet-im-server/src/test/java/com/twelvet/im/server/service/impl/ImServiceSendNoticeTest.java
git commit -m "feat(analytics): 会话开始漏斗事件"
```

---

### Task 6: 看板查询服务与接口

**Files:**
- Modify: `backend/twelvet-product/twelvet-product-api/src/main/java/com/twelvet/product/api/service/IFunnelEventService.java`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/FunnelEventMapper.java`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/resources/mapper/product/FunnelEventMapper.xml`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/service/impl/FunnelEventServiceImpl.java`
- Create: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/controller/AdminFunnelController.java`
- Test: `backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/service/impl/FunnelEventStatsTest.java`
- Test: `backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/controller/AdminFunnelControllerTest.java`

**Interfaces:**
- Produces: `IFunnelEventService.adminStats(Integer days, Long tenantId)`。
- Produces: `FunnelEventMapper.selectDaily/selectSummary/selectTenants` + 对应 XML。
- Produces: `GET /admin/marketplace/funnel/stats?days&tenantId` → `JsonResult<Map<String,Object>>`。
- Produces: `adminStats` 返回 `{days, tenantId, tenants, summary, funnel, refundFunnel, daily}`。

- [ ] **Step 1: Write the failing tests**

```java
@Test
@DisplayName("tenantId 为空只返回租户选项，不查聚合数据（防串租）")
void adminStats_withoutTenant_returnsOptionsOnly() {
    when(eventMapper.selectTenants(7))
        .thenReturn(List.of(Map.<String, Object>of("tenantId", 2, "tenantName", "试点社区")));

    Map<String, Object> result = service.adminStats(7, null);

    assertEquals(1, ((List<?>) result.get("tenants")).size());
    assertEquals(0, ((List<?>) result.get("funnel")).size());
    verify(eventMapper, never()).selectSummary(anyInt(), any());
    verify(eventMapper, never()).selectDaily(anyInt(), any());
}

@Test
@DisplayName("有租户：组装漏斗转化率（支付→确认）")
void adminStats_buildsConversion() {
    when(eventMapper.selectTenants(7)).thenReturn(List.of());
    when(eventMapper.selectSummary(7, 2L)).thenReturn(List.of(
            Map.<String, Object>of("eventType", "product_exposure", "count", 100L, "uv", 50L),
            Map.<String, Object>of("eventType", "pay_success", "count", 10L, "uv", 8L),
            Map.<String, Object>of("eventType", "confirm_receipt", "count", 8L, "uv", 7L)));
    when(eventMapper.selectDaily(7, 2L)).thenReturn(List.of());

    Map<String, Object> result = service.adminStats(7, 2L);

    List<Map<String, Object>> funnel = (List<Map<String, Object>>) result.get("funnel");
    Map<String, Object> confirm = funnel.stream()
        .filter(r -> "confirm_receipt".equals(r.get("key"))).findFirst().orElseThrow();
    assertEquals(0.8, ((Number) confirm.get("conversion")).doubleValue(), 0.001);
}
```

`AdminFunnelControllerTest`：方法与类上必须有 `@SaCheckPermission("marketplace:admin:funnel")`；`stats(7, 2L)` 透传给 service。

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest='FunnelEventStatsTest,AdminFunnelControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `adminStats` 返回空 / Controller 不存在。

- [ ] **Step 3: Implement**

先在 `IFunnelEventService` 增加 `Map<String, Object> adminStats(Integer days, Long tenantId);`，在 `FunnelEventMapper` 与 XML 增加三个统计查询（SQL 见 Task 1 早期版本，`tenantId` 条件统一写 `<if test="tenantId != null">`）：

```sql
-- selectDaily
SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate, event_type AS eventType,
       COUNT(*) AS count, COUNT(DISTINCT user_id) AS uv
FROM funnel_event
WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
<if test="tenantId != null">AND tenant_id = #{tenantId}</if>
GROUP BY statDate, eventType ORDER BY statDate DESC, eventType

-- selectSummary
SELECT event_type AS eventType, COUNT(*) AS count, COUNT(DISTINCT user_id) AS uv
FROM funnel_event
WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
<if test="tenantId != null">AND tenant_id = #{tenantId}</if>
GROUP BY eventType

-- selectTenants
SELECT e.tenant_id AS tenantId, t.tenant_name AS tenantName
FROM funnel_event e JOIN sys_tenant t ON t.id = e.tenant_id
WHERE e.tenant_id IS NOT NULL
  AND e.create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
GROUP BY e.tenant_id, t.tenant_name ORDER BY e.tenant_id
```

`adminStats`：

1. `days = days == null ? 7 : Math.max(1, Math.min(90, days))`。
2. `tenants = eventMapper.selectTenants(days)`。
3. `tenantId == null` → 返回 `{days, tenantId: null, tenants, summary: Map.of(), funnel: List.of(), refundFunnel: List.of(), daily: List.of()}`。
4. 查询 summary/daily，转成 `Map<String, Map<String,Object>> summaryByType`。
5. `funnel` 顺序固定：`product_exposure → product_detail_view → im_start → order_create → pay_success → confirm_receipt`；每步 `{key, count, uv, conversion}`，`conversion = count / 前一步 count`（前一步 0 或缺失 → `null`，保留两位小数）。
6. `refundFunnel`：`refund_apply → refund_success`，同规则。
7. `daily` 原样返回。

Controller：

```java
@SaCheckPermission("marketplace:admin:funnel")
@GetMapping("/stats")
public JsonResult<Map<String, Object>> stats(@RequestParam(required = false) Integer days,
        @RequestParam(required = false) Long tenantId) {
    return JsonResult.success(funnelEventService.adminStats(days, tenantId));
}
```

类上 `@RestController @RequestMapping("/admin/marketplace/funnel")`。

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest='FunnelEventStatsTest,AdminFunnelControllerTest,FunnelEventServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/service/impl/FunnelEventServiceImpl.java \
        backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/controller/AdminFunnelController.java \
        backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/service/impl/FunnelEventStatsTest.java \
        backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/controller/AdminFunnelControllerTest.java
git commit -m "feat(analytics): 社区漏斗看板接口"
```

---

### Task 7: 菜单与权限 SQL

**Files:**
- Modify: `backend/sql/funnel_event.sql`（追加菜单/授权段）

**Interfaces:**
- Produces: `sys_menu` 行 `perms='marketplace:admin:funnel'`、`parent_id=2100`；`sys_role_menu` 授予角色 1、3。

- [ ] **Step 1: Append idempotent menu SQL**

```sql
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status,
     perms, icon, menu_scope, create_by, create_time, remark)
SELECT '社区漏斗', 2100, 7, '/admin/marketplace/funnel', 'marketplace/admin/Funnel',
       '1', 'C', '0', '0', 'marketplace:admin:funnel', 'icon-chart', '0', 'admin', NOW(),
       '核心漏斗事件与转化看板（按社区切片）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'marketplace:admin:funnel');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m
WHERE m.perms = 'marketplace:admin:funnel'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.menu_id FROM sys_menu m
WHERE m.perms = 'marketplace:admin:funnel'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.menu_id);
```

- [ ] **Step 2: Verify idempotency**

Run: `mysql -uroot -p twelvet-fast < backend/sql/funnel_event.sql`（连跑两遍）
Expected: 两遍均无错误；`SELECT COUNT(*) FROM sys_menu WHERE perms='marketplace:admin:funnel'` = 1；`sys_role_menu` 对应行各 1。

- [ ] **Step 3: Commit**

```bash
git add backend/sql/funnel_event.sql
git commit -m "feat(analytics): 漏斗看板菜单与权限"
```

---

### Task 8: 运营看板前端

**Files:**
- Modify: `ui/src/services/marketplace/api.ts`（追加 `adminFunnelStats`）
- Create: `ui/src/pages/marketplace/admin/Funnel.tsx`
- Create: `ui/src/pages/marketplace/admin/Funnel.test.tsx`
- Modify: `ui/config/routes.ts:560-570`（在 RecommendStats 之后插入路由）

**Interfaces:**
- Consumes: `GET /admin/marketplace/funnel/stats`。
- Produces: 路由 `/admin/marketplace/funnel`，菜单名「社区漏斗」。

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import Funnel from './Funnel';
import { adminFunnelStats } from '@/services/marketplace/api';

jest.mock('@/services/marketplace/api', () => ({ adminFunnelStats: jest.fn() }));

const payload = {
    data: {
        days: 7,
        tenantId: 2,
        tenants: [{ tenantId: 2, tenantName: '试点社区' }],
        summary: { product_exposure: { count: 120, uv: 40 }, product_detail_view: { count: 60, uv: 30 } },
        funnel: [
            { key: 'product_exposure', count: 120, uv: 40, conversion: null },
            { key: 'product_detail_view', count: 60, uv: 30, conversion: 0.5 },
        ],
        refundFunnel: [],
        daily: [],
    },
};

it('加载租户后自动选中并展示漏斗计数', async () => {
    (adminFunnelStats as jest.Mock).mockResolvedValue(payload);
    render(<Funnel />);
    await waitFor(() => expect(screen.getByText('商品曝光')).toBeTruthy());
    expect(screen.getByText('120')).toBeTruthy();
    expect(screen.getByText('50.0%')).toBeTruthy();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ui && npm test -- --runTestsByPath src/pages/marketplace/admin/Funnel.test.tsx --watchAll=false`
Expected: FAIL — module not found。

- [ ] **Step 3: Implement**

`api.ts`：

```ts
export async function adminFunnelStats(params?: Record<string, any>) {
    return request('/admin/marketplace/funnel/stats', { method: 'GET', params });
}
```

`Funnel.tsx`（useEffect + useState，风格对齐 `RefundApply.tsx`）：

- 状态：`days=7`、`tenantId?`、`data`、`loading`。
- 首次请求 `tenantId` 为空 → 只拿租户选项；有租户且未选 → `setTenantId(tenants[0].tenantId)` 触发第二次请求。
- 顶部：租户 `Select`、周期 `Select`（7/14/30）。
- 卡片：完成交易数、详情→IM 转化、支付→确认完成率（从 `funnel` 取对应项，无数据显示 `-`）。
- 漏斗表：`EVENT_LABELS` 映射 10 个类型中文名；列：环节 / 次数 / 用户数 / 转化率。
- 转化率格式：`conversion == null ? '-' : (conversion * 100).toFixed(1) + '%'`（测试断言 `50.0%`）。
- 空态：无租户时 `<Alert message="暂无社区数据" />`。

`EVENT_LABELS`：

```ts
const EVENT_LABELS: Record<string, string> = {
    product_exposure: '商品曝光',
    product_detail_view: '详情浏览',
    im_start: '发起会话',
    order_create: '创建订单',
    pay_success: '支付成功',
    ship: '卖家发货',
    confirm_receipt: '确认收货',
    refund_apply: '申请退款',
    refund_success: '退款成功',
    review_create: '提交评价',
};
```

`routes.ts` 在 `recommend-stats` 之后：

```ts
{
    path: '/admin/marketplace/funnel',
    name: '社区漏斗',
    component: './marketplace/admin/Funnel',
},
```

- [ ] **Step 4: Run test + type check**

Run: `cd ui && npm test -- --runTestsByPath src/pages/marketplace/admin/Funnel.test.tsx --watchAll=false && npm run tsc`
Expected: PASS + 0 errors。

- [ ] **Step 5: Commit**

```bash
git add ui/src/services/marketplace/api.ts ui/src/pages/marketplace/admin/Funnel.tsx \
        ui/src/pages/marketplace/admin/Funnel.test.tsx ui/config/routes.ts
git commit -m "feat(ui): 社区漏斗看板"
```

---

### Task 9: Phase 0B 验收

- [ ] **Step 1: 后端三模块全量测试**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server,twelvet-trade/twelvet-trade-server,twelvet-im/twelvet-im-server -am test`
Expected: BUILD SUCCESS，0 failures。

- [ ] **Step 2: 前端全量测试 + 类型检查**

Run: `cd ui && npm test -- --watchAll=false && npm run tsc`
Expected: 全部通过，0 errors。

- [ ] **Step 3: SQL 幂等复核**

Run: `mysql -uroot -p twelvet-fast < backend/sql/funnel_event.sql`（第二遍）
Expected: 无错误、菜单行数不变。

- [ ] **Step 4: 人工冒烟（本地）**

1. 首页推荐流加载 → `SELECT event_type, COUNT(*) FROM funnel_event WHERE event_type='product_exposure' GROUP BY event_type;` 有新增。
2. 打开商品详情 → `product_detail_view` 新增 1。
3. 从详情发起聊天 → `im_start` 新增 1；再次打开同一会话不新增。
4. 下单 → `order_create`；mock 支付 → `pay_success`；发货 → `ship`；确认收货 → `confirm_receipt`。
5. 申请退款 → `refund_apply`；退款完成 → `refund_success`；评价 → `review_create`。
6. 运营「社区漏斗」页：选租户后能看到上述计数与转化率；不选租户时只显示社区选项。

- [ ] **Step 5: 记录结果并收尾**

把冒烟结果写入最终汇报；全部通过后按 superpowers:finishing-a-development-branch 处理分支。

---

## Out of Scope（Phase 1 / 后续）

- 结算实现（`seller_account` / `settlement_order` / 提现）：Phase 1 单独计划，设计见 `docs/superpowers/specs/2026-11-02-settlement-compliance-design.md`。
- 漏斗汇总表 / 实时看板 / 类目下钻 / 角色切片 UI：v0 只做按日原始聚合。
- 客户端 beacon / 第三方分析 SDK：事件全部服务端采集。
- AI 辅助发布标记：发布助手整合在 Phase 1，`ai_assisted` 目前只由 AI 导购下单路径置 1。
- 历史数据回填：`funnel_event` 从上线时刻开始计数。

## Self-Review

- **Spec coverage**：§6.3 的 10 个事件 → Task 2–5；§6.1 北极星（确认收货数）→ Task 6/8 卡片；§5 D7/D8 租户切片 → Task 6 的 tenant 必选与 Task 8 选择器；§11「漏斗事件可在看板查询」→ Task 6–8。
- **Placeholder scan**：无 TBD；每个事件写明触发方法、键、租户来源。Task 4 `finishRefundSuccess` 的 productId 取值需实现时用 `order.getProductId()`（order 非空），`order == null` 时 productId 留空——已写明。
- **Type consistency**：`FunnelEvent` 字段、`IFY` 方法、Mapper 五个方法在 Task 1 定义，Task 2–6 只消费；事件键格式统一 `<type>:<bizId>`。
- **Review Focus**：五条分别由 Task 1（吞异常）、Task 3/4（幂等键）、Task 3（Quartz 租户）、Task 6（空租户）、Task 2（批量）覆盖。