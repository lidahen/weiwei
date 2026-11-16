# 导购 Agent 深化（P0）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agent 商品检索接入 Java 六路召回推荐（新内部端点 + L1 工具），并在下单确认前自动前置议价/纠纷/卖家活跃度三项只读风险检查。

**Architecture:** Java 新增 `InternalRecommendController`（三道安全防线，服务端解析租户后复用 `homeRecommend` 六路召回，推荐逻辑零改动）；Python 新增 `recommend_products` L1 工具经 `X-Internal-Token` 调用该端点，`confirm_order` 首次确认分支注入 `_pre_order_risk_check` 只读风险提示。融合发生在模型层（LLM 合并两个工具结果、按 product_id 去重），Python 不重排序。

**Tech Stack:** Spring Boot 3.5.4 / JUnit 5 + Mockito 纯单测（跟随 `TradeOrderServiceImplInternalTest`）；Python 3.11+ / pytest + respx + monkeypatch（跟随 `tests/test_agent_internal_order.py`）。

**Spec:** `docs/superpowers/specs/2026-10-16-guide-agent-deepening-design.md`

## Global Constraints

- 租户开关关闭（`tenantSupport.enabled()` 为 false）时，新路径行为与公开路径完全一致（零侵入回滚能力不被破坏，同 `TradeOrderServiceImpl.createOrderForUser` 开头直通分支）
- 租户来源只能是服务端 `sys_user.tenant_id` 引导查询，**绝不取调用方传参**（`TradeOrderMapper.xml:127` 注释原文：租户 ID 以 sys_user.tenant_id 为唯一权威来源）
- `TenantContext.set` 后必须 `try { ... } finally { TenantContext.clear(); }`（`TradeOrderServiceImpl.java:180-187` 模式）
- 内部端点三道防线：`/internal/**` 前缀 + `InternalApiToken`（`X-Internal-Token`）常量时间比对 + 失败关闭（未配置一律 403，不区分未配置/令牌错误，同 `InternalOrderController`）
- Python 侧任何 Java 调用失败 → 工具返回 `{"error": ...}` 如实降级，不阻塞对话、不编造数据、不回退直写数据库
- 风险检查只读、永不阻塞下单；单项失败记 `logger.warning` 跳过（同 `tools.py:749` 估价提示降级模式）
- 常量：`RISK_DISPUTE_WINDOW_DAYS = 90`、`RISK_SELLER_INACTIVE_DAYS = 30`、recommend `pageSize` 上限 20（Java/Python 两侧对齐）
- Java 代码格式：Spring Java Format（提交前 `mvn spring-javaformat:apply -pl twelvet-product/twelvet-product-server`）；Python 遵循 tools.py 现有风格

## Review Focus

- **租户上下文泄漏**：内部端点设 TenantContext 后任何异常路径漏 clear，线程复用会把租户上下文带给后续无关请求 → Task 1 Step 1 的 `tenantResolvedAndCleared` / `tenantMissingFailsClosedAndClears` 锁定
- **未配置令牌即放行**：`twelvet.internal.token` 未配置时若放行，内网暴露即裸奔 → Task 1 `tokenRejected`（Java）+ Task 2 `test_token_missing_fails_closed_without_request`（Python 不发请求）锁定
- **Python 拿到非 200/超时后静默返回空推荐**：用户会把空结果当"没有好货" → Task 2 的 timeout/403/business_rejection 三态测试锁定 error 透传
- **risk check 误伤下单**：任一风险查询抛异常不得拦住用户下单 → Task 3 `test_partial_failure_skips_item` 锁定
- **卖家 login_date 为 NULL**：不得出现"已 0 天未登录"式荒谬提示 → Task 3 `test_login_date_null_skipped` 锁定

---

### Task 1: Java InternalRecommendController + UserMapper 租户查询

**Files:**
- Create: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/controller/InternalRecommendController.java`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/UserMapper.java`（追加一个方法声明）
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/resources/mapper/product/UserMapper.xml`（追加一个 select）
- Test: `backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/controller/InternalRecommendControllerTest.java`（新建）

**Interfaces:**
- Consumes: `IRecommendService.homeRecommend(Long userId, String catL1, int current, int pageSize)` 返回 `TableDataInfo<Product>`（已存在，RecommendServiceImpl.java:188）；`InternalApiToken.matches(String)` / `HEADER`（已存在）；`TenantSupport.enabled()`（已存在）
- Produces: `GET /internal/recommend/for-user?userId=&catL1=&pageSize=` → 200 `JsonResult<TableDataInfo<Product>>`（`data.rows` 为 Product 列表，含 `productId/title/price/cover/condition/city/reason`）；403 鉴权失败；400 参数缺失/用户无租户。Task 2 的 Python 工具消费此契约
- Produces: `UserMapper.selectUserTenantId(Long userId) -> Long`（引导查询，用户不存在返回 null）

- [ ] **Step 1: 写失败测试** `InternalRecommendControllerTest.java`

```java
package com.twelvet.product.server.controller;

import com.twelvet.framework.core.internal.InternalApiToken;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.service.IRecommendService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 内部推荐接口测试。锁定六条不变量：
 * <ol>
 * <li>令牌不通过 → 403，且不触达推荐服务</li>
 * <li>userId 缺失/非数字 → 400</li>
 * <li>租户开关开启：homeRecommend 执行期间 TenantContext 为买家租户，调用后清理（异常路径同样清）</li>
 * <li>租户开关关闭：不解析租户、不设上下文（零侵入回滚）</li>
 * <li>pageSize 上限 20</li>
 * <li>homeRecommend 以 current=1 调用（内部端点固定第一页）</li>
 * </ol>
 */
class InternalRecommendControllerTest {

	private final IRecommendService recommendService = mock(IRecommendService.class);

	private final InternalApiToken token = mock(InternalApiToken.class);

	private final TenantSupport tenantSupport = mock(TenantSupport.class);

	private final UserMapper userMapper = mock(UserMapper.class);

	private final InternalRecommendController controller = new InternalRecommendController(recommendService, token,
			tenantSupport, userMapper);

	@BeforeEach
	void setUp() {
		TenantContext.clear();
	}

	@AfterEach
	void clearContext() {
		// 防御：任何一个用例失败都不应把上下文残留给下一个用例
		TenantContext.clear();
	}

	private TableDataInfo<Product> fakePage() {
		Product p = new Product();
		p.setProductId(1L);
		p.setReason("看过同款");
		return TableDataInfo.page(List.of(p), 1);
	}

	@Test
	@DisplayName("令牌不通过 → 403 且不触达推荐服务")
	void tokenRejected() {
		when(token.matches(any())).thenReturn(false);
		ResponseEntity<?> resp = controller.forUser("bad-token", "7", null, 8);
		assertEquals(403, resp.getStatusCode().value());
		verifyNoInteractions(recommendService);
	}

	@Test
	@DisplayName("userId 缺失 → 400")
	void userIdRequired() {
		when(token.matches(any())).thenReturn(true);
		ResponseEntity<?> resp = controller.forUser("t", null, null, 8);
		assertEquals(400, resp.getStatusCode().value());
	}

	@Test
	@DisplayName("userId 非数字 → 400")
	void userIdMustBeNumeric() {
		when(token.matches(any())).thenReturn(true);
		ResponseEntity<?> resp = controller.forUser("t", "abc", null, 8);
		assertEquals(400, resp.getStatusCode().value());
	}

	@Test
	@DisplayName("租户开启：homeRecommend 执行期间上下文为买家租户，调用后已清理")
	void tenantResolvedAndCleared() {
		when(token.matches(any())).thenReturn(true);
		when(tenantSupport.enabled()).thenReturn(true);
		when(userMapper.selectUserTenantId(7L)).thenReturn(42L);
		when(recommendService.homeRecommend(eq(7L), isNull(), eq(1), eq(8))).thenAnswer(inv -> {
			// 执行期间断言：上下文必须是买家租户（服务端解析，不是调用方传参）
			assertEquals(42L, TenantContext.get(), "homeRecommend 执行期间 TenantContext 应为买家租户");
			return fakePage();
		});
		ResponseEntity<?> resp = controller.forUser("t", "7", null, 8);
		assertEquals(200, resp.getStatusCode().value());
		assertNull(TenantContext.get(), "调用结束后上下文必须已清理");
	}

	@Test
	@DisplayName("租户开启但用户查不到 → 400 且上下文已清理（失败路径 finally）")
	void tenantMissingFailsClosedAndClears() {
		when(token.matches(any())).thenReturn(true);
		when(tenantSupport.enabled()).thenReturn(true);
		when(userMapper.selectUserTenantId(7L)).thenReturn(null);
		ResponseEntity<?> resp = controller.forUser("t", "7", null, 8);
		assertEquals(400, resp.getStatusCode().value());
		assertNull(TenantContext.get(), "失败路径上下文同样必须清理");
		verify(recommendService, never()).homeRecommend(anyLong(), any(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("租户关闭：不解析租户、不设上下文，行为与公开路径一致")
	void tenantDisabledSkipsResolution() {
		when(token.matches(any())).thenReturn(true);
		when(tenantSupport.enabled()).thenReturn(false);
		when(recommendService.homeRecommend(eq(7L), isNull(), eq(1), eq(8))).thenReturn(fakePage());
		ResponseEntity<?> resp = controller.forUser("t", "7", null, 8);
		assertEquals(200, resp.getStatusCode().value());
		assertNull(TenantContext.get());
		verify(userMapper, never()).selectUserTenantId(anyLong());
	}

	@Test
	@DisplayName("pageSize 超上限截为 20")
	void pageSizeClamped() {
		when(token.matches(any())).thenReturn(true);
		when(tenantSupport.enabled()).thenReturn(false);
		when(recommendService.homeRecommend(anyLong(), any(), anyInt(), anyInt())).thenReturn(fakePage());
		controller.forUser("t", "7", null, 99);
		ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
		verify(recommendService).homeRecommend(eq(7L), isNull(), eq(1), size.capture());
		assertEquals(20, size.getValue());
	}

}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && mvn test -pl twelvet-product/twelvet-product-server -Dtest=InternalRecommendControllerTest`
Expected: 编译失败（`InternalRecommendController` 不存在）

- [ ] **Step 3: 实现 Controller**（完整代码，无占位）

```java
package com.twelvet.product.server.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.internal.InternalApiToken;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.service.IRecommendService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部推荐接口（服务间调用：Python ai-service → Java）
 * <p>
 * 存在的理由：AI 导购 Agent 没有用户登录态，公开端点 {@code GET /product/home/recommend} 的
 * userId 取自 {@code StpUtil.isLogin()}（ProductController），直调只能拿到匿名推荐，
 * 六路召回中的兴趣/协同/同城三路会失效。本端点由服务端从 sys_user 解析租户后调用
 * {@link IRecommendService#homeRecommend}，六路召回/打分/推荐理由全部复用，推荐逻辑零改动。
 * </p>
 * <p>
 * 安全设计（三道，复刻 InternalOrderController）：
 * <ol>
 * <li>接口置于 {@code /internal/**} 前缀下，便于反向代理（Caddy）统一 deny，不暴露到公网</li>
 * <li>必须携带 {@code X-Internal-Token}，常量时间比对；未配置令牌一律拒绝（失败关闭）</li>
 * <li>租户由服务端从 sys_user.tenant_id 解析，绝不取调用方传参</li>
 * </ol>
 * 本接口 {@code @SaIgnore}（绕过 Sa-Token 登录校验），因此第 2 条是它唯一的鉴权手段，不可省略。
 * </p>
 *
 * @author twelvet
 * @WebSite twelvet.cn
 */
@Hidden
@Tag(description = "内部推荐接口(服务间调用)", name = "InternalRecommendController")
@RestController
@RequestMapping("/internal/recommend")
public class InternalRecommendController {

	private static final Logger log = LoggerFactory.getLogger(InternalRecommendController.class);

	private static final int DEFAULT_PAGE_SIZE = 8;

	private static final int MAX_PAGE_SIZE = 20;

	private final IRecommendService recommendService;

	private final InternalApiToken internalApiToken;

	private final TenantSupport tenantSupport;

	private final UserMapper userMapper;

	public InternalRecommendController(IRecommendService recommendService, InternalApiToken internalApiToken,
			TenantSupport tenantSupport, UserMapper userMapper) {
		this.recommendService = recommendService;
		this.internalApiToken = internalApiToken;
		this.tenantSupport = tenantSupport;
		this.userMapper = userMapper;
	}

	/**
	 * 服务端解析租户后调用六路召回推荐（推荐逻辑零改动复用）。
	 * @param token 请求头 {@code X-Internal-Token}
	 * @param userId 被推荐的用户 ID（必填数字）
	 * @param catL1 一级分类（可选）
	 * @param pageSize 条数（默认 8，上限 20）
	 * @return 推荐分页；鉴权失败 403、参数缺失或用户无租户 400
	 */
	@Operation(summary = "内部个性化推荐（服务间调用）")
	@SaIgnore
	@GetMapping("/for-user")
	public ResponseEntity<JsonResult<TableDataInfo<Product>>> forUser(
			@RequestHeader(value = InternalApiToken.HEADER, required = false) String token,
			@RequestParam(required = false) String userId,
			@RequestParam(required = false) String catL1,
			@RequestParam(required = false, defaultValue = "8") Integer pageSize) {
		if (!internalApiToken.matches(token)) {
			// 失败关闭且不区分"未配置"与"令牌错误"，避免向外泄露内部接口的配置状态
			log.warn("内部推荐接口鉴权失败，已拒绝");
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(JsonResult.<TableDataInfo<Product>>error(HttpStatus.FORBIDDEN.value(), "内部接口鉴权失败"));
		}
		Long uid = toLong(userId);
		if (uid == null) {
			return ResponseEntity.badRequest().body(JsonResult
				.<TableDataInfo<Product>>error(HttpStatus.BAD_REQUEST.value(), "参数缺失：userId 必填且必须为数字"));
		}
		int size = (pageSize == null || pageSize <= 0) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
		if (!tenantSupport.enabled()) {
			// 租户开关关闭：与公开路径完全一致（上下文为 null），零侵入回滚能力不被破坏
			return ResponseEntity.ok(JsonResult.success(recommendService.homeRecommend(uid, catL1, 1, size)));
		}
		// 引导查询（上下文尚未建立，查询本身不带租户过滤）；绝不使用调用方传入的租户标识
		Long tid = userMapper.selectUserTenantId(uid);
		if (tid == null) {
			log.warn("内部推荐：用户 {} 不存在或未归属任何租户，失败关闭", uid);
			return ResponseEntity.badRequest().body(JsonResult.<TableDataInfo<Product>>error(
					HttpStatus.BAD_REQUEST.value(), "用户不存在或未归属任何租户"));
		}
		TenantContext.set(tid);
		try {
			return ResponseEntity.ok(JsonResult.success(recommendService.homeRecommend(uid, catL1, 1, size)));
		}
		finally {
			// 必须清理：否则上下文会随线程复用泄漏到后续无关请求
			TenantContext.clear();
		}
	}

	private Long toLong(String value) {
		if (value == null) {
			return null;
		}
		try {
			return Long.valueOf(value.trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
```

- [ ] **Step 4: UserMapper 追加租户引导查询**

接口 `UserMapper.java` 追加（放在 `selectUserById` 之后）：

```java
	/**
	 * 引导查询：按用户 ID 查租户（此时租户上下文尚未建立，查询本身不带租户过滤）。
	 * 与 TradeOrderMapper.selectUserTenantId 同名同义；绝不使用调用方传入的租户标识。
	 * @param userId 用户ID
	 * @return 租户ID；用户不存在时为 null
	 */
	Long selectUserTenantId(@Param("userId") Long userId);
```

`UserMapper.xml` 在 `</mapper>` 前追加：

```xml
    <!-- 引导查询：租户 ID 以 sys_user.tenant_id 为唯一权威来源（不信任任何调用方传入的租户标识）。 -->
    <!-- 与 TradeOrderMapper.xml 的 selectUserTenantId 同名同义。 -->
    <select id="selectUserTenantId" resultType="java.lang.Long">
        SELECT tenant_id FROM sys_user WHERE user_id = #{userId} LIMIT 1
    </select>
```

- [ ] **Step 5: 运行测试通过 + 格式化**

Run: `cd backend && mvn spring-javaformat:apply -pl twelvet-product/twelvet-product-server && mvn test -pl twelvet-product/twelvet-product-server -Dtest=InternalRecommendControllerTest`
Expected: 全部 PASS（7 个用例）

- [ ] **Step 6: 提交**

```bash
git add backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/controller/InternalRecommendController.java backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/UserMapper.java backend/twelvet-product/twelvet-product-server/src/main/resources/mapper/product/UserMapper.xml backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/controller/InternalRecommendControllerTest.java
git commit -m "feat(product): 内部推荐端点——服务端解析租户复用六路召回（导购 Agent 深化 Java 侧）"
```

---

### Task 2: Python recommend_products 工具

**Files:**
- Modify: `ai-service/app/services/agent/tools.py`（新工具插在 `estimate_price` 之前，约 214 行处；`PRE_SALE_TOOLS` 追加）
- Test: `ai-service/tests/test_agent_recommend_tool.py`（新建）

**Interfaces:**
- Consumes: Task 1 契约 `GET /internal/recommend/for-user`（200 + `code=200` + `data.rows`；403/400 带 `msg`）；既有 `settings.internal_api_token` / `settings.java_base_url` / `settings.java_internal_timeout`；`tools.py` 顶部已有 `httpx`/`json`/`Optional/Annotated/Dict/Any` 导入
- Produces: `recommend_products(cat_l1: Optional[str] = None, limit: int = 8, user_id: InjectedToolArg) -> str`。成功返回 `{"products": [{product_id, title, price, cover, condition, city, reason}], "source": "recommend"}`；失败 `{"error": ...}`。`PRE_SALE_TOOLS = [search_products, recommend_products, get_product_detail, estimate_price, search_knowledge]`

- [ ] **Step 1: 写失败测试** `test_agent_recommend_tool.py`

```python
"""recommend_products 工具测试：请求契约、失败降级、结果映射。

模式跟随 test_agent_internal_order.py：respx mock Java 端点 + monkeypatch settings。
锁定四条不变量：
  1. 必须携带 X-Internal-Token，userId 进 query（服务端解析租户用）；
  2. 未配置令牌失败关闭，且不发出任何网络请求；
  3. 超时/403/业务拒绝一律返回 error（不静默给空推荐——空结果会被用户当成"没有好货"）；
  4. 未登录（无 user_id）不给推荐；limit 上限 20 与 Java 侧对齐。
"""
import json

import httpx
import pytest
import respx
from httpx import Response

from app.services.agent import tools

BASE = "http://java.test:8080"
TOKEN = "unit-test-internal-token"
PATH = "/internal/recommend/for-user"


def _java_ok(rows):
    return Response(200, json={"code": 200, "msg": "操作成功", "data": {"total": len(rows), "rows": rows}})


@pytest.fixture(autouse=True)
def _java_settings(monkeypatch):
    monkeypatch.setattr(tools.settings, "java_base_url", BASE)
    monkeypatch.setattr(tools.settings, "internal_api_token", TOKEN)
    monkeypatch.setattr(tools.settings, "java_internal_timeout", 5)


@pytest.mark.asyncio
async def test_calls_java_with_token_and_user_id():
    with respx.mock(base_url=BASE) as mock:
        route = mock.get(PATH).mock(return_value=_java_ok([{
            "productId": 1, "title": "九成新沙发", "price": 320.0,
            "cover": "http://c/1.jpg", "condition": "九成新",
            "city": "深圳", "reason": "你看过同款",
        }]))
        out = json.loads(await tools.recommend_products.ainvoke(
            {"user_id": "7", "cat_l1": None, "limit": 8}))
    assert out["source"] == "recommend"
    assert out["products"][0]["product_id"] == 1
    assert out["products"][0]["reason"] == "你看过同款"
    req = route.calls.last.request
    assert req.headers["X-Internal-Token"] == TOKEN, "必须携带服务间令牌"
    assert "userId=7" in str(req.url), "userId 必须进 query（服务端解析租户用）"


@pytest.mark.asyncio
async def test_token_missing_fails_closed_without_request(monkeypatch):
    monkeypatch.setattr(tools.settings, "internal_api_token", "")
    # assert_all_called=False：本用例的断言点恰恰是"这个 mock 路由一次都不该被调用"
    with respx.mock(base_url=BASE, assert_all_called=False) as mock:
        mock.get(PATH).mock(return_value=_java_ok([]))
        out = json.loads(await tools.recommend_products.ainvoke({"user_id": "7"}))
    assert "error" in out
    assert "令牌未配置" in out["error"]
    assert not mock.calls, "未配置令牌时不应发起网络请求"


@pytest.mark.asyncio
async def test_timeout_returns_error():
    with respx.mock(base_url=BASE) as mock:
        mock.get(PATH).mock(side_effect=httpx.ReadTimeout("timeout"))
        out = json.loads(await tools.recommend_products.ainvoke({"user_id": "7"}))
    assert "error" in out
    assert "暂时不可用" in out["error"]
    assert "products" not in out, "失败时不得携带空 products（会被当成没有好货）"


@pytest.mark.asyncio
async def test_java_403_maps_to_error():
    with respx.mock(base_url=BASE) as mock:
        mock.get(PATH).mock(return_value=Response(403, json={"code": 403, "msg": "内部接口鉴权失败"}))
        out = json.loads(await tools.recommend_products.ainvoke({"user_id": "7"}))
    assert out.get("error") == "内部接口鉴权失败"


@pytest.mark.asyncio
async def test_business_rejection_surfaces_msg():
    with respx.mock(base_url=BASE) as mock:
        mock.get(PATH).mock(return_value=Response(400, json={"code": 400, "msg": "用户不存在或未归属任何租户"}))
        out = json.loads(await tools.recommend_products.ainvoke({"user_id": "7"}))
    assert out.get("error") == "用户不存在或未归属任何租户"


@pytest.mark.asyncio
async def test_missing_user_id_returns_error():
    out = json.loads(await tools.recommend_products.ainvoke({"user_id": ""}))
    assert "error" in out, "未登录用户不给推荐（个性化推荐依赖 userId）"


@pytest.mark.asyncio
async def test_limit_clamped_to_20():
    with respx.mock(base_url=BASE) as mock:
        route = mock.get(PATH).mock(return_value=_java_ok([]))
        await tools.recommend_products.ainvoke({"user_id": "7", "limit": 99})
    assert "pageSize=20" in str(route.calls.last.request.url), "limit 上限 20 与 Java 侧对齐"
```

- [ ] **Step 2: 运行确认失败**

Run: `cd ai-service && python -m pytest tests/test_agent_recommend_tool.py -v`
Expected: FAIL（`recommend_products` 不存在，AttributeError）

- [ ] **Step 3: 实现** `tools.py` 在 `estimate_price`（`@tool` 装饰，214 行附近）之前插入：

```python
@tool
async def recommend_products(
    cat_l1: Optional[str] = None,
    limit: int = 8,
    user_id: Annotated[Optional[str], InjectedToolArg] = None,
) -> str:
    """[L1] 个性化商品推荐（平台六路召回：兴趣/热门/新品/向量/协同/同城，带推荐理由）。
    用户想买某类东西但没明确目标、说"随便看看/有什么推荐"时，与 search_products 同时调用：
    search 给精确匹配，本工具给"为你推荐"。reason 是推荐理由，必须如实转述给用户。
    参数：cat_l1 一级分类（可空）、limit 条数（默认 8，最大 20）。
    返回 JSON：{products: [{product_id, title, price, cover, condition, city, reason}], source: "recommend"}。
    调用失败返回 error，此时改用 search_products 即可，不要向用户编造推荐结果。
    """
    uid = str(user_id or "").strip()
    if not uid.isdigit():
        return json.dumps(
            {"error": "用户未登录，个性化推荐不可用；请使用商品搜索"}, ensure_ascii=False
        )
    token = (settings.internal_api_token or "").strip()
    if not token:
        return json.dumps(
            {"error": "服务间令牌未配置（INTERNAL_API_TOKEN），推荐服务不可用"}, ensure_ascii=False
        )
    page_size = max(1, min(int(limit or 8), 20))
    url = f"{(settings.java_base_url or '').rstrip('/')}/internal/recommend/for-user"
    params: Dict[str, Any] = {"userId": uid, "pageSize": page_size}
    if cat_l1:
        params["catL1"] = cat_l1
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(settings.java_internal_timeout)) as client:
            resp = await client.get(url, params=params, headers={"X-Internal-Token": token})
    except httpx.HTTPError as e:
        # 日志保留细节便于排障，但不把底层异常原文抛给用户
        logger.error("调用 Java 内部推荐接口失败: %s", e)
        return json.dumps({"error": "推荐服务暂时不可用，请稍后再试"}, ensure_ascii=False)
    try:
        payload = resp.json()
    except ValueError:
        payload = {}
    if resp.status_code != 200 or payload.get("code") != 200:
        msg = payload.get("msg") or f"推荐服务返回 HTTP {resp.status_code}"
        return json.dumps({"error": msg}, ensure_ascii=False)
    data = payload.get("data") or {}
    rows = data.get("rows") or []
    products = [
        {
            "product_id": r.get("productId"),
            "title": r.get("title"),
            "price": float(r["price"]) if r.get("price") is not None else None,
            "cover": r.get("cover"),
            "condition": r.get("condition"),
            "city": r.get("city"),
            "reason": r.get("reason") or "",
        }
        for r in rows
    ]
    return json.dumps({"products": products, "source": "recommend"}, ensure_ascii=False)
```

并更新工具注册表（tools.py:911）：

```python
PRE_SALE_TOOLS = [search_products, recommend_products, get_product_detail, estimate_price, search_knowledge]
```

- [ ] **Step 4: 运行测试通过**

Run: `cd ai-service && python -m pytest tests/test_agent_recommend_tool.py -v`
Expected: 全部 PASS（7 个用例）

- [ ] **Step 5: 提交**

```bash
git add ai-service/app/services/agent/tools.py ai-service/tests/test_agent_recommend_tool.py
git commit -m "feat(agent): recommend_products 工具——Agent 接入 Java 六路召回个性化推荐"
```

---

### Task 3: _pre_order_risk_check + confirm_order 注入

**Files:**
- Modify: `ai-service/app/services/agent/tools.py`：① 顶部 import 区（`import time` 之后）加 `from datetime import datetime`；② `DEVIATION_RATIO`（41 行）后加两个风险常量；③ `confirm_order` 定义之前插入 `_pre_order_risk_check`；④ `confirm_order` 首次确认分支（690-755 行）注入
- Test: `ai-service/tests/test_agent_risk_check.py`（新建）

**Interfaces:**
- Consumes: DB 表 `trade_bargain(product_id, buyer_id, current_price, status, update_time)`、`trade_dispute(seller_id, status, create_time)`、`sys_user(user_id, login_date)`（表结构以 `db-export/twelvet-fast_2026-10-08.sql` 为准，已确认存在：2679/2712/2599 行）
- Produces: `async def _pre_order_risk_check(db, product_id: int, buyer_id: int, seller_id: Optional[int]) -> List[str]`（风险文案列表，空列表=无风险；永不抛异常）；`confirm_order` 确认 message 拼接顺序固定 `{action}{addr_text}{risk_text}{valuation_warning}`，`risk_text = "；" + "；".join(risks)` 或空串

- [ ] **Step 1: 写失败测试** `test_agent_risk_check.py`

```python
"""下单前风险检查测试：三项命中/不命中/单项失败跳过/NULL 跳过/confirm_order 集成。

锁定四条不变量：
  1. 三项检查独立降级——任何一项查询抛异常，其余项照常返回，整体不抛异常；
  2. login_date 为 NULL（未知）跳过活跃度提示；
  3. 无风险返回空列表；
  4. confirm_order 首次确认话术必须带出风险文案（且不阻塞确认流程）。
"""
import json
from datetime import datetime, timedelta

import pytest

from app.services.agent import tools


class _RouteResult:
    def __init__(self, row):
        self._row = row

    def fetchone(self):
        return self._row


class _RouteSession:
    """假 session：按 SQL 关键字路由到预设行，未命中返回 None 行（模拟查无数据）。"""

    def __init__(self, route):
        self._route = route

    async def execute(self, sql, params=None):
        s = str(sql)
        for key, row in self._route.items():
            if key in s:
                return _RouteResult(row)
        return _RouteResult(None)

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False


class _FailSomeSession(_RouteSession):
    """对指定关键字的 SQL 抛异常（模拟单表故障）。"""

    def __init__(self, route, fail_key):
        super().__init__(route)
        self._fail_key = fail_key

    async def execute(self, sql, params=None):
        if self._fail_key in str(sql):
            raise RuntimeError("db down")
        return await super().execute(sql, params)


@pytest.mark.asyncio
async def test_bargain_pending_detected():
    session = _RouteSession({"trade_bargain": (320.0,)})
    risks = await tools._pre_order_risk_check(session, 100, 7, 7)
    assert any("议价进行中" in r and "320" in r for r in risks)


@pytest.mark.asyncio
async def test_dispute_count_detected():
    session = _RouteSession({"trade_dispute": (2,)})
    risks = await tools._pre_order_risk_check(session, 100, 7, 7)
    assert any("2 笔纠纷未结" in r for r in risks)


@pytest.mark.asyncio
async def test_seller_inactive_detected():
    # 45 天 + 5 分钟：扣除微秒取整后 days 恰为 45（直接减 45 天会得 44）
    session = _RouteSession({"sys_user": (datetime.now() - timedelta(days=45, minutes=5),)})
    risks = await tools._pre_order_risk_check(session, 100, 7, 7)
    assert any("45 天未登录" in r for r in risks)


@pytest.mark.asyncio
async def test_no_risks_returns_empty():
    session = _RouteSession({})
    risks = await tools._pre_order_risk_check(session, 100, 7, 7)
    assert risks == []


@pytest.mark.asyncio
async def test_login_date_null_skipped():
    session = _RouteSession({"sys_user": (None,)})
    risks = await tools._pre_order_risk_check(session, 100, 7, 7)
    assert not any("未登录" in r for r in risks), "login_date 未知时不得提示活跃度风险"


@pytest.mark.asyncio
async def test_partial_failure_skips_item():
    session = _FailSomeSession({"trade_bargain": (320.0,)}, fail_key="trade_dispute")
    risks = await tools._pre_order_risk_check(session, 100, 7, 7)
    assert any("议价进行中" in r for r in risks), "其他项照常"
    assert not any("纠纷" in r for r in risks), "失败项被跳过"


@pytest.mark.asyncio
async def test_confirm_order_message_includes_risk(monkeypatch):
    """集成：首次确认话术必须带出风险文案。"""

    async def _risks(db, product_id, buyer_id, seller_id):
        return ["你有一笔 ¥320 的议价进行中（卖家尚未回复），可等待议价结果再下单"]

    async def _save(token, action, params):
        pass

    monkeypatch.setattr(tools, "_pre_order_risk_check", _risks)
    monkeypatch.setattr(tools, "_save_pending_confirm", _save)
    monkeypatch.setattr(tools, "async_session", lambda: _RouteSession({
        "user_address": ("张三", "13800138000", "广东", "深圳", "南山", "某某路1号"),
        "seller_id": (7,),
    }))
    result = json.loads(await tools.confirm_order.ainvoke(
        {"user_id": "7", "product_id": 100, "price": 88.0}))
    assert result["need_confirm"] is True
    assert "议价进行中" in result["message"], "确认话术必须如实带出风险提示"
    assert "确认" in result["message"], "风险提示不得阻塞确认流程"
```

- [ ] **Step 2: 运行确认失败**

Run: `cd ai-service && python -m pytest tests/test_agent_risk_check.py -v`
Expected: FAIL（`_pre_order_risk_check` 不存在）

- [ ] **Step 3: 实现常量与 `_pre_order_risk_check`**

`tools.py` import 区（`import time` 之后）加：

```python
from datetime import datetime
```

`DEVIATION_RATIO = 1.4`（41 行）之后加：

```python
RISK_DISPUTE_WINDOW_DAYS = 90  # 卖家纠纷统计窗口（天）
RISK_SELLER_INACTIVE_DAYS = 30  # 卖家超过该天数未登录则提示活跃度风险
```

`confirm_order` 定义之前插入：

```python
async def _pre_order_risk_check(db, product_id: int, buyer_id: int, seller_id: Optional[int]) -> List[str]:
    """下单确认前的只读风险检查（L1，永不抛异常、永不阻塞下单）。

    三项独立查询，任何一项失败记 warning 并跳过（与 confirm_order 估价提示的降级模式一致）。
    返回面向用户的风险文案列表，空列表 = 无风险。
    """
    risks: List[str] = []
    from sqlalchemy import text

    # ① 买家在该商品上的进行中议价
    try:
        row = (await db.execute(
            text("SELECT current_price FROM trade_bargain "
                 "WHERE product_id = :pid AND buyer_id = :bid AND status = 'pending' "
                 "ORDER BY update_time DESC LIMIT 1"),
            {"pid": product_id, "bid": buyer_id},
        )).fetchone()
        if row and row[0] is not None:
            risks.append(f"你有一笔 ¥{float(row[0]):.0f} 的议价进行中（卖家尚未回复），可等待议价结果再下单")
    except Exception as e:
        logger.warning("risk_check bargain skipped: %s", e)

    # ② 卖家近 N 天未结纠纷数
    if seller_id:
        try:
            row = (await db.execute(
                text("SELECT COUNT(*) FROM trade_dispute WHERE seller_id = :sid "
                     "AND status IN ('pending', 'in_progress') "
                     "AND create_time >= DATE_SUB(NOW(), INTERVAL :days DAY)"),
                {"sid": int(seller_id), "days": RISK_DISPUTE_WINDOW_DAYS},
            )).fetchone()
            if row and row[0]:
                risks.append(f"该卖家近 {RISK_DISPUTE_WINDOW_DAYS} 天有 {row[0]} 笔纠纷未结，请留意")
        except Exception as e:
            logger.warning("risk_check dispute skipped: %s", e)

    # ③ 卖家活跃度（login_date 为 NULL 视为未知，跳过不提示）
    if seller_id:
        try:
            row = (await db.execute(
                text("SELECT login_date FROM sys_user WHERE user_id = :sid"),
                {"sid": int(seller_id)},
            )).fetchone()
            if row and row[0] is not None:
                days = (datetime.now() - row[0]).days
                if days >= RISK_SELLER_INACTIVE_DAYS:
                    risks.append(f"卖家已 {days} 天未登录，回复可能较慢")
        except Exception as e:
            logger.warning("risk_check login skipped: %s", e)

    return risks
```

- [ ] **Step 4: 注入 confirm_order 首次确认分支**

`confirm_order` 内、`valuation_warning` 块结束（`except Exception as e: logger.warning("confirm_order valuation hint skipped..."` 之后）、`return json.dumps({"need_confirm": True, ...` 之前插入：

```python
        # 下单前风险检查（只读，不阻塞下单）：议价/卖家纠纷/卖家活跃度，命中才提示
        risk_text = ""
        buyer = int(user_id) if str(user_id).isdigit() else -1
        if buyer > 0:
            try:
                async with async_session() as db:
                    from sqlalchemy import text
                    srow = (await db.execute(
                        text("SELECT seller_id FROM product WHERE product_id = :pid"),
                        {"pid": product_id},
                    )).fetchone()
                    seller_id = srow[0] if srow else None
                    risks = await _pre_order_risk_check(db, product_id, buyer, seller_id)
                if risks:
                    risk_text = "；" + "；".join(risks)
            except Exception as e:
                logger.warning("confirm_order risk hint skipped: %s", e)
```

并把确认 message 改为（插入 `{risk_text}`）：

```python
        return json.dumps({
            "need_confirm": True,
            "confirm_token": token,
            "message": f"确认要执行：{action}{addr_text}{risk_text}{valuation_warning}？"
                       f"地址无误请回复确认；地址有误请直接发送新地址。",
        }, ensure_ascii=False)
```

- [ ] **Step 5: 运行测试通过 + 既有回归**

Run: `cd ai-service && python -m pytest tests/test_agent_risk_check.py tests/test_agent_internal_order.py -v`
Expected: 全部 PASS——`test_agent_internal_order.py` 是 confirm_order 的既有回归（跨租户下单缺陷），改动后必须保持全绿

- [ ] **Step 6: 提交**

```bash
git add ai-service/app/services/agent/tools.py ai-service/tests/test_agent_risk_check.py
git commit -m "feat(agent): 下单确认前风险检查——议价/卖家纠纷/活跃度只读提示"
```

---

### Task 4: SKILL.md 更新 + 全量回归

**Files:**
- Modify: `ai-service/skills/guide-agent/SKILL.md`（文件末尾追加一节，不改既有条目）
- Test: 无新测试文件；运行既有回归命令

**Interfaces:**
- Consumes: Task 2 的 `recommend_products`（SKILL 指导模型何时调用）、Task 3 的风险文案
- Produces: SKILL.md 两条行为约束（经 `skill_loader.py` 热加载对 Agent 生效）

- [ ] **Step 1: SKILL.md 末尾追加**

```markdown
## 行为约束（2026-10-16 导购 Agent 深化）

1. 推荐场景：用户想买某类东西但目标模糊（"随便看看/有什么推荐/想买X但不知道选哪个"）时，
   同时调用 search_products 与 recommend_products，按 product_id 去重后融合呈现：
   搜索结果给精确匹配，推荐结果给"为你推荐"。recommend_products 返回的 reason 是推荐
   理由，必须如实转述；该工具返回 error 时改用 search_products，不得编造推荐结果。
2. 下单确认话术必须如实带出系统返回的全部风险提示（议价进行中/卖家纠纷/卖家活跃度/
   估价偏离），一条不得略过；用户确认后正常走确认流程，不得因存在风险提示而拒绝执行确认。
```

- [ ] **Step 2: 路由评测回归（离线校验）**

Run: `cd ai-service && python tests/agent_eval/run_router_eval.py`
Expected: exit 0、无校验错误（路由 prompt 未改，预期无回归）

- [ ] **Step 3: 全量 Python 测试**

Run: `cd ai-service && python -m pytest tests/ -q --ignore=tests/agent_eval --ignore=tests/moderation_eval`
Expected: 全部 PASS（重点观察 test_agent_internal_order / test_agent_recommend_tool / test_agent_risk_check / test_agent_polish）

- [ ] **Step 4: 提交**

```bash
git add ai-service/skills/guide-agent/SKILL.md
git commit -m "feat(agent): SKILL 增加推荐融合与风险转述约束（导购 Agent 深化收尾）"
```

---

## Self-Review 结论

- **Spec 覆盖**：spec §4 Java 端点 → Task 1（含租户上下文检查点——已实锤 `doRecommend` L193 依赖 `TenantContext.get()`，方案落地为服务端引导查询，spec 声明的"实现期验证"在 Task 1 测试中闭环）；§5.1 recommend_products → Task 2；§5.2 风险检查 → Task 3；§5.3 SKILL.md → Task 4；§6 错误处理 → Global Constraints + Task 2/3 降级测试；§7 测试策略 → 各 Task TDD 步骤 + Task 4 Step 2/3；§8 验收标准 → Task 2/3 测试 + Task 1 tokenRejected + Task 4 全量回归。无缺口。
- **占位符扫描**：无 TBD/TODO/省略号；所有代码步骤含完整可运行代码。
- **类型一致性**：`_pre_order_risk_check(db, product_id: int, buyer_id: int, seller_id: Optional[int]) -> List[str]` 定义（Task 3 Step 3）与调用（Task 3 Step 4、测试）一致；`recommend_products` 签名 Task 2 定义与测试 ainvoke 参数一致；Java `selectUserTenantId(Long) -> Long` 接口声明、XML、Controller 调用、测试 mock 四处一致；`forUser(String, String, String, Integer)` 与测试调用一致。
- **Review Focus 覆盖**：租户泄漏→Task 1 `tenantResolvedAndCleared`/`tenantMissingFailsClosedAndClears`；令牌→Task 1 `tokenRejected` + Task 2 `test_token_missing_fails_closed_without_request`；静默空推荐→Task 2 `test_timeout_returns_error`（断言无 products 键）+ 403/business；risk 误伤→Task 3 `test_partial_failure_skips_item`；NULL login_date→Task 3 `test_login_date_null_skipped`。
