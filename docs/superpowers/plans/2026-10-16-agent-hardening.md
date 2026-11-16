# 导购 Agent 深化收尾（契约测试 / 会话合并 / 租户过滤）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 钉死 Java↔Python 跨侧 JSON 契约（防 records/rows 类断裂复发）、合并 confirm_order 确认分支的重复 DB 会话、给 tools.py 的 L1 商品读查询统一补租户过滤。

**Architecture:** ① Java 侧新增 MockMvc 独立应用契约测试（不经 Service mock，走真实 Jackson 序列化，断言 `data.records` 与 `productId` 字符串形态——上轮 Critical 的根治）；② Python `confirm_order` 首次确认分支把 价格/地址/估价/风险 四个 `async_session` 合并为一个，商品行一次取齐（price/seller_id/cat_l1/cat_l2/condition）；③ `search_products`（经 product_search.py 全链路）、`get_product_detail`、风险检查的 `SELECT seller_id`、confirm_order 的商品行查询统一带 `tenant_id` 过滤（InjectedToolArg 自动注入，nodes.py:733 已有机制）。

**Tech Stack:** JUnit 5 + Spring Test（@WebMvcTest 风格独立 app + MockMvc，依赖已在根 pom 全局继承）；Python pytest + 既有 fake session 模式。

**Spec:** 上轮会话的优化提案（2026-10-16 对话确认：1+2+3 打包）；关联 spec `docs/superpowers/specs/2026-10-16-guide-agent-deepening-design.md`（租户/降级约束沿用）。

## Global Constraints

- 契约测试必须走**真实 Jackson 序列化**（Java 全局配置：Long→ToStringSerializer、JavaTimeModule、无 naming strategy——JacksonConfiguration.java:29-39），mock 只许替身 IRecommendService，不得 mock ObjectMapper 或手拼 JSON
- 语义与行为不变：②③ 是重构 + 安全加固，既有全部测试必须原样通过（不许改既有断言来迁就新实现）
- 租户过滤语义：`tenant_id` 有效（>0）时 `AND p.tenant_id = :tid`；为 0/缺失时不过滤（与 `tenant.enabled=false` 的零侵入回滚一致——tools.py 既有 `tenant > 0` 模式，tools.py:336-343）
- product 表有 `tenant_id` 列（db-export/twelvet-fast_2026-10-08.sql）；SQL 参数名统一 `tid`
- confirm_order 二次调用（携 confirm_token）路径行为完全不变；风险检查三段独立降级语义不变
- Python 测试命令：`py -3.14 -m pytest`（本机 python 为 Store 存根）
- Java 测试命令在 `backend/` 下：`mvn test -pl twelvet-product/twelvet-product-server -Dtest=...`

## Review Focus

- **契约测试假绿**：若契约测试 mock 了 ObjectMapper 或手拼 JSON 字符串，等于没测——Review Focus 第一条就是核对它走真实序列化链路 → Task 1 Step 1 的代码内注释 + 审查核对
- **租户过滤误伤 L3 热门兜底**：search_products 的 L3 兜底在无结果时无条件全库捞热门，若 tid>0 时兜底查询漏带租户，会把别租户热门推给用户 → Task 3 的 `test_l3_fallback_respects_tenant` 锁定
- **租户过滤误伤估价/风险**：estimate_price/风险检查的样本查询带 tid 过滤后，样本量骤减可能让建议价变 None → Task 3 用既有断言回归（sample_count 语义不变）
- **会话合并破坏降级**：四段查询合并后，估价段的独立 try/except 必须保留（估价失败不拖垮地址/风险）→ Task 2 的 `test_merged_session_partial_failure` 锁定
- **productId 契约双侧联动**：Task 1 契约测试钉 productId 为字符串；Python 侧 Task 3 的 `int(...)` 转换已容忍字符串——若 Task 1 失败说明 Java 改了序列化，Python 侧的 isdigit 守卫仍不崩 → 跨侧安全

---

### Task 1: Java MockMvc 契约测试（钉死 TableDataInfo 真实 JSON 形状）

**Files:**
- Create: `backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/controller/InternalRecommendControllerContractTest.java`

**Interfaces:**
- Consumes: `InternalRecommendController`（已存在）；JacksonConfiguration 全局定制（Long→String）；既有 @BeforeAll I18n 脚手架模式（InternalRecommendControllerTest.java:49-60）
- Produces: 契约锁定的 JSON 形状断言：HTTP 200 外壳 `code=200`、`data.records` 为数组、`data.records[0].productId` 为字符串 "1"、`data.records[0].reason` 存在

- [ ] **Step 1: 写契约测试**

```java
package com.twelvet.product.server.controller;

import com.twelvet.framework.core.internal.InternalApiToken;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.utils.SpringContextHolder;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.service.IRecommendService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcControllers;
import org.springframework.test.web.servlet.setup.MockMvcFactoryBuilder;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内部推荐端点 JSON 契约测试（跨侧断裂回归：Python 曾按 data.rows 解析，
 * 而 TableDataInfo 真实序列化是 data.records —— 本测试用真实 Jackson 链路钉死形状）。
 * <p>
 * 为什么不用纯 Mockito：上轮跨侧缺陷的根因正是两侧测试各自 mock、没有任何一处断言
 * 真实 JSON 形状。本测试走 Spring MockMvc + 全局 Jackson 定制（Long→ToStringSerializer，
 * JacksonConfiguration 经 spring.factories/AutoConfiguration.imports 自动装配），
 * mock 只替身 IRecommendService 的返回对象，序列化完全真实。
 * </p>
 */
class InternalRecommendControllerContractTest {

	@BeforeAll
	static void stubSpringContextForI18n() {
		// 与 InternalRecommendControllerTest 同款脚手架：JsonResult.success() 走 I18nUtils
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("messageSource", new StaticMessageSource());
		new SpringContextHolder().postProcessBeanFactory(beanFactory);
	}

	private final IRecommendService recommendService = mock(IRecommendService.class);

	private final InternalApiToken token = mock(InternalApiToken.class);

	private final TenantSupport tenantSupport = mock(TenantSupport.class);

	private final UserMapper userMapper = mock(UserMapper.class);

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		// standaloneSetup 不加载应用上下文，但 @AutoConfiguration 的 Jackson 定制不会生效——
		// 因此显式注册 JacksonConfiguration 的 customizer（与本测试类同包可见，直接 new）。
		InternalRecommendController controller = new InternalRecommendController(recommendService, token,
				tenantSupport, userMapper);
		mockMvc = MockMvcControllers.standaloneSetup(controller);
	}

	@Test
	@DisplayName("真实序列化：data.records 数组 + productId 为字符串（Long→ToStringSerializer）")
	void jsonContract() throws Exception {
		when(token.matches(Mockito.any())).thenReturn(true);
		when(tenantSupport.enabled()).thenReturn(false);
		Product p = new Product();
		p.setProductId(1L);
		p.setTitle("九成新沙发");
		p.setPrice(new java.math.BigDecimal("320.00"));
		p.setReason("看过同款");
		when(recommendService.homeRecommend(Mockito.eq(7L), Mockito.isNull(), Mockito.eq(1), Mockito.eq(8)))
				.thenReturn(TableDataInfo.page(List.of(p), 1));

		mockMvc.perform(get("/internal/recommend/for-user").header("X-Internal-Token", "t")
				.param("userId", "7").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data.records").isArray())
			.andExpect(jsonPath("$.data.records[0].productId").value("1"))
			.andExpect(jsonPath("$.data.records[0].reason").value("看过同款"));
	}
}
```

**关键实现说明（实现者必读）**：standalone MockMvc 不会加载 `JacksonConfiguration`（那是 AutoConfiguration）。要让序列化与生产一致，必须把全局定制显式装上——用 `MockMvcBuilders.standaloneSetup(controller).setMessageConverters(...)` 或更简单的 `Jackson2ObjectMapperBuilder.json().serializerByType(Long.class, ToStringSerializer.instance).build()` 构造 ObjectMapper 后经 `MappingJackson2HttpMessageConverter` 挂到 standaloneSetup（`.setMessageConverters(new MappingJackson2HttpMessageConverter(om))`）。**断言目标不变**：`data.records`、`productId=="1"`（字符串）。若实现者发现 standaloneSetup 默认 converter 已产生正确形状，也必须保留显式 Long→String 定制（生产行为由 JacksonConfiguration 提供，测试必须与之对齐而不是巧合一致）。上例 import 里 `MockMvcFactoryBuilder`/`MockMvcControllers` 以实际 Spring Test API 为准（正确类名是 `org.springframework.test.web.servlet.setup.MockMvcBuilders`），实现时修正 import 并保持断言与语义不变。

- [ ] **Step 2: 运行测试通过**

Run: `cd backend && mvn test -pl twelvet-product/twelvet-product-server -Dtest=InternalRecommendControllerContractTest`
Expected: PASS。**先故意把断言改成 `$.data.rows` 跑一次确认 FAIL（验证测试有牙齿），再改回 `$.data.records` 确认 PASS**——这一步是本任务的核心价值，必须在报告中给出两种运行的输出。

- [ ] **Step 3: 提交**

```bash
git add backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/controller/InternalRecommendControllerContractTest.java
git commit -m "test(product): 内部推荐端点 JSON 契约测试——真实 Jackson 序列化钉死 data.records 形态（跨侧断裂回归）"
```

---

### Task 2: confirm_order 首次确认分支会话合并

**Files:**
- Modify: `ai-service/app/services/agent/tools.py`（confirm_order 首次确认分支：现 4 个独立 async_session 合并为 1 个；商品行一次取齐）
- Test: `ai-service/tests/test_agent_risk_check.py`（追加 2 个用例）

**Interfaces:**
- Consumes: 既有 `confirm_order` 首次分支结构（价格查询 → 确认令牌 → 地址 → 估价 → 风险检查）；`_pre_order_risk_check(db, product_id, buyer_id, seller_id)`
- Produces: 首次分支单会话实现；行为（确认 message 内容、二次调用路径、降级语义）完全不变

- [ ] **Step 1: 写失败测试**（追加到 test_agent_risk_check.py）

```python
@pytest.mark.asyncio
async def test_merged_single_session(monkeypatch):
    """首次确认分支只开一个 DB 会话。"""
    opened = []

    class _CountingSession(_RouteSession):
        async def __aenter__(self):
            opened.append(1)
            return self

    async def _risks(db, product_id, buyer_id, seller_id):
        return []

    async def _save(token, action, params):
        pass

    monkeypatch.setattr(tools, "_pre_order_risk_check", _risks)
    monkeypatch.setattr(tools, "_save_pending_confirm", _save)
    monkeypatch.setattr(tools, "async_session", lambda: _CountingSession({
        "FROM product": (88.0, 7, "数码", "手机", "95新"),
        "user_address": ("张三", "13800138000", "广东", "深圳", "南山", "某某路1号"),
    }))
    result = json.loads(await tools.confirm_order.ainvoke(
        {"user_id": "7", "product_id": 100, "tenant_id": "1"}))
    assert result["need_confirm"] is True
    assert len(opened) == 1, f"首次确认分支应只开 1 个会话，实际 {len(opened)}"


@pytest.mark.asyncio
async def test_valuation_failure_does_not_break_confirm(monkeypatch):
    """估价段失败不拖垮确认流程（合并后独立降级语义保留）。"""

    class _FailValuationSession(_RouteSession):
        async def execute(self, sql, params=None):
            if "fetch_transactions" in str(sql):
                raise RuntimeError("valuation down")
            return await super().execute(sql, params)

    async def _risks(db, product_id, buyer_id, seller_id):
        return []

    async def _save(token, action, params):
        pass

    monkeypatch.setattr(tools, "_pre_order_risk_check", _risks)
    monkeypatch.setattr(tools, "_save_pending_confirm", _save)
    # 估价内部经 fetch_transactions/fetch_onsale_prices 函数查库，mock 之：
    import app.services.valuation as valuation
    async def _tx_fail(db, tenant, cat_l1):
        raise RuntimeError("valuation down")

    monkeypatch.setattr(valuation, "fetch_transactions", _tx_fail)
    monkeypatch.setattr(tools.async_session.__class__ if False else tools, "async_session",
                        lambda: _RouteSession({
                            "FROM product": (88.0, 7, "数码", "手机", "95新"),
                            "user_address": ("张三", "13800138000", "广东", "深圳", "南山", "某某路1号"),
                        }))
    result = json.loads(await tools.confirm_order.ainvoke(
        {"user_id": "7", "product_id": 100, "tenant_id": "1"}))
    assert result["need_confirm"] is True
    assert "议价" not in result["message"]
```

（注：`test_valuation_failure_does_not_break_confirm` 中 `monkeypatch.setattr(tools.async_session.__class__ if False else tools, ...)` 是笔误清理后的等价形式——直接 `monkeypatch.setattr(tools, "async_session", ...)`；valuation 的 patch 目标是 `app.services.valuation.fetch_transactions`，若 confirm_order 内是 `from app.services.valuation import fetch_transactions` 局部导入则 patch 源模块即可生效。实现者按可运行等价形式落地，语义不变。）

- [ ] **Step 2: 运行确认失败**

Run: `cd ai-service && py -3.14 -m pytest tests/test_agent_risk_check.py -v`
Expected: `test_merged_single_session` FAIL（当前实现开 4 个会话）

- [ ] **Step 3: 实现会话合并**

`confirm_order` 首次分支重构为单会话（保留各段独立 try/except 降级，`_pre_order_risk_check` 复用同一 db）：

```python
    if not confirm_token:
        token = _new_confirm_token("confirm_order", {"user_id": user_id, "product_id": product_id, "price": price})
        await _save_pending_confirm(token, "confirm_order",
                                    {"user_id": user_id, "product_id": product_id, "price": price,
                                     "address_id": address_id})
        # 单会话取齐：商品行（price/seller_id/cat_l1/cat_l2/condition）+ 默认地址 + 估价 + 风险检查。
        # 各段保留独立 try/except：任何一段失败只降级自己，不影响确认流程。
        addr_text = ""
        valuation_warning = ""
        risk_text = ""
        buyer = int(user_id) if str(user_id).isdigit() else -1
        tid = int(tenant_id) if str(tenant_id or "").strip().isdigit() else 0
        try:
            async with async_session() as db:
                from sqlalchemy import text
                # ① 商品行（price 为空时才需要兜底查询——若模型已传 price 仍取行供估价/风险用）
                prow = (await db.execute(
                    text("SELECT price, seller_id, cat_l1, cat_l2, `condition` FROM product "
                         "WHERE product_id = :pid" + (" AND tenant_id = :tid" if tid > 0 else "")),
                    {"pid": product_id, **({"tid": tid} if tid > 0 else {})},
                )).fetchone()
                if prow:
                    db_price = float(prow[0]) if prow[0] is not None else None
                    seller_id = prow[1]
                    cat_l1, cat_l2, cond = prow[2], prow[3], prow[4]
                    if price is None:
                        price = db_price
                else:
                    seller_id = cat_l1 = cat_l2 = cond = None
                # ② 默认地址
                try:
                    if buyer > 0:
                        addr = (await db.execute(
                            text("SELECT receiver, mobile, province, city, district, detail FROM user_address "
                                 "WHERE user_id = :u ORDER BY is_default DESC, address_id DESC LIMIT 1"),
                            {"u": buyer})).fetchone()
                        if addr:
                            addr_text = f"；收货地址：{addr[2]}{addr[3]}{addr[4] or ''}{addr[5]}（{addr[0]} {addr[1]}）"
                        else:
                            addr_text = "；未找到收货地址"
                except Exception as e:
                    logger.error("confirm_order query address error: %s", e)
                # ③ 估价偏离（独立降级）
                try:
                    if tid > 0 and prow and cat_l1 and price and float(price) > 0:
                        from app.services.valuation import (
                            L2_MIN_SAMPLES, compute_valuation, fetch_onsale_prices,
                            fetch_transactions, split_l2_samples,
                        )
                        rows = await fetch_transactions(db, tid, cat_l1)
                        l2_samples, _l1_rest = split_l2_samples(rows, cat_l2)
                        tx_prices = l2_samples if len(l2_samples) >= L2_MIN_SAMPLES else [
                            float(r["price"]) for r in rows
                        ]
                        ask_prices = await fetch_onsale_prices(db, tid, cat_l1)
                        val = compute_valuation(tx_prices, ask_prices, cond)
                        if val.get("suggestion"):
                            ratio = float(price) / float(val["suggestion"])
                            if ratio >= DEVIATION_RATIO:
                                valuation_warning = (f"；⚠ 该价格比同品类建议价 ¥{float(val['suggestion']):.0f} 高出"
                                                     f"{round((ratio - 1) * 100)}%，请确认是否愿意以此价购买")
                            elif ratio <= 1 / DEVIATION_RATIO:
                                valuation_warning = (f"；提示：该价格比同品类建议价 ¥{float(val['suggestion']):.0f} 低"
                                                     f"{round((1 - ratio) * 100)}%，疑似明显低价，请留意商品成色与卖家信誉")
                except Exception as e:
                    logger.warning("confirm_order valuation hint skipped: %s", e)
                # ④ 风险检查（独立降级，复用同一会话）
                try:
                    if buyer > 0 and prow:
                        risks = await _pre_order_risk_check(db, product_id, buyer, seller_id)
                        if risks:
                            risk_text = "；" + "；".join(risks)
                except Exception as e:
                    logger.warning("confirm_order risk hint skipped: %s", e)
        except Exception as e:
            logger.warning("confirm_order confirm-branch db error: %s", e)
        return json.dumps({
            "need_confirm": True,
            "confirm_token": token,
            "message": f"确认要执行：{action}{addr_text}{risk_text}{valuation_warning}？"
                       f"地址无误请回复确认；地址有误请直接发送新地址。",
        }, ensure_ascii=False)
```

注意：商品行 SQL 不带 `status='onsale'` 过滤（原价格查询带），保持原语义——原实现 `SELECT price FROM product WHERE product_id=:pid AND status='onsale'` 只在 price=None 时查；合并后若商品已下架，`prow` 为 None → price 保持 None → 走既有「暂无价格」分支。**若 prow 因加 tenant 过滤而为 None 且 tid>0，同样落入「暂无价格」分支**——这是租户过滤的预期行为（跨租户商品不给确认）。但 price 由模型显式传入时不走兜底查询，此时合并版仍查一行用于估价/风险——与原实现的差异是：原实现此时不查库、风险检查单独查 seller_id。**实现者必须核对：模型传 price 且商品跨租户时，租户过滤让 prow=None → seller_id=None → 风险检查跳过、估价跳过、确认照常**（风险检查本就 seller_id 可空安全）。

- [ ] **Step 4: 运行测试通过 + 既有回归**

Run: `cd ai-service && py -3.14 -m pytest tests/test_agent_risk_check.py tests/test_agent_internal_order.py tests/test_agent_recommend_tool.py -v`
Expected: 全部 PASS（既有 confirm_order 回归不得破坏——test_agent_internal_order 的 fake session 按既有 SQL 文本路由，若合并后 SQL 文本变化导致路由失配，允许**最小适配** fake session 的路由关键字，但不得改断言）

- [ ] **Step 5: 提交**

```bash
git add ai-service/app/services/agent/tools.py ai-service/tests/test_agent_risk_check.py
git commit -m "refactor(agent): confirm_order 首次确认分支合并为单 DB 会话（4→1），商品行一次取齐"
```

---

### Task 3: L1 商品读查询统一租户过滤

**Files:**
- Modify: `ai-service/app/services/product_search.py`（`search_products()` 加 `tenant_id` 参数；`_BASE_SQL` 加条件租户过滤）
- Modify: `ai-service/app/services/agent/tools.py`（`search_products` 工具传 tenant_id；`get_product_detail` 加 tenant_id 参数与过滤）
- Test: `ai-service/tests/test_product_search_tenant.py`（新建）

**Interfaces:**
- Consumes: nodes.py:726-733 的 InjectedToolArg 注入机制（`tenant_id` 已对 estimate_price/confirm_order 生效，search/get_detail 此前未声明该参数）
- Produces: `search_products(db, ..., tenant_id: Optional[int] = None)`；`get_product_detail(product_id, tenant_id: InjectedToolArg)`。tid 有效（>0）时 SQL 追加 `AND p.tenant_id = :tid`

- [ ] **Step 1: 写失败测试** `test_product_search_tenant.py`

```python
"""L1 商品读查询的租户过滤测试：tid>0 过滤、tid=0 不过滤（零侵入回滚）、L3 兜底同样带过滤。"""
import json

import pytest

from app.services import product_search
from app.services.agent import tools


class _CaptureSession:
    def __init__(self):
        self.captured = []

    async def execute(self, sql, params=None):
        self.captured.append((str(sql), dict(params or {})))

        class _R:
            def fetchall(self):
                return []

            def fetchone(self):
                return None

        return _R()

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False


@pytest.mark.asyncio
async def test_search_filters_by_tenant_when_tid_positive():
    session = _CaptureSession()
    await product_search.search_products(db=session, keyword="沙发", tenant_id=3)
    sql, params = session.captured[0]
    assert "p.tenant_id = :tid" in sql
    assert params["tid"] == 3


@pytest.mark.asyncio
async def test_search_no_tenant_filter_when_tid_missing():
    session = _CaptureSession()
    await product_search.search_products(db=session, keyword="沙发")
    sql, params = session.captured[0]
    assert "tenant_id" not in sql, "tid 缺失时不过滤（零侵入回滚）"


@pytest.mark.asyncio
async def test_l3_fallback_respects_tenant():
    """L3 热门兜底同样必须带租户过滤——否则跨租户热门会推给用户。"""
    session = _CaptureSession()
    await product_search.search_products(db=session, keyword="不存在的商品xyz", tenant_id=3)
    for sql, params in session.captured:
        assert "p.tenant_id = :tid" in sql, "每个放宽级别（含 L3 兜底）都必须带租户过滤"
        assert params["tid"] == 3


@pytest.mark.asyncio
async def test_get_product_detail_filters_by_tenant():
    out = json.loads(await tools.get_product_detail.ainvoke(
        {"product_id": 100, "tenant_id": "3"}))
    assert "error" in out, "capture session 返回 None 行 → 商品不存在（含跨租户被过滤的情形）"


@pytest.mark.asyncio
async def test_search_tool_passes_tenant():
    """工具层：search_products 工具把注入的 tenant_id 透传给 service。"""
    called = {}

    async def _fake_search(db, **kwargs):
        called.update(kwargs)
        return {"products": [], "search_note": ""}

    from unittest.mock import patch
    with patch.object(product_search, "search_products", _fake_search):
        await tools.search_products.ainvoke({"keyword": "沙发", "tenant_id": "3"})
    assert called.get("tenant_id") == 3
```

- [ ] **Step 2: 运行确认失败**

Run: `cd ai-service && py -3.14 -m pytest tests/test_product_search_tenant.py -v`
Expected: FAIL（search_products 无 tenant_id 参数 / get_product_detail 不过滤）

- [ ] **Step 3: 实现**

3a. `product_search.py`：`search_products()` 签名加 `tenant_id: Optional[int] = None`（放 limit 之前）；SQL 组装处（city 条件之后）加：

```python
        if tenant_id and tenant_id > 0:
            where.append("p.tenant_id = :tid")
            params["tid"] = int(tenant_id)
```

（位置在 cat_l1/city 恒定条件之后、`if where:` 拼接之前——保证每个放宽变体（含 L3）都带上，因 where 列表在循环内每轮从 variant 重建后统一追加恒定条件。）

3b. `tools.py` 的 `search_products` 工具：签名加 `tenant_id: Annotated[Optional[str], InjectedToolArg] = None`（插在 limit 之后），调用处透传：

```python
            results = await product_search.search_products(
                db=db, keyword=keyword, cat_l1=cat_l1, city=city,
                price_min=price_min, price_max=price_max,
                condition=condition, order_by=order_by, limit=limit,
                tenant_id=int(tenant_id) if str(tenant_id or "").strip().isdigit() else None,
            )
```

3c. `tools.py` 的 `get_product_detail`：签名加 `tenant_id: Annotated[Optional[str], InjectedToolArg] = None`；SQL 加条件过滤：

```python
    sql_text = """
        SELECT p.product_id, p.title, p.cover, p.price, p.original_price,
               p.`condition`, p.city, p.cat_l1, p.`description`, p.view_count,
               u.nick_name as seller_nick_name, u.user_id as seller_id
        FROM `product` p LEFT JOIN `sys_user` u ON p.seller_id = u.user_id
        WHERE p.product_id = :pid AND p.`status` = 'onsale'
    """
    params = {"pid": product_id}
    tid = int(tenant_id) if str(tenant_id or "").strip().isdigit() else 0
    if tid > 0:
        sql_text += " AND p.tenant_id = :tid"
        params["tid"] = tid
```

（原单条 `text(...)` 字面量替换为上述拼接；其余逻辑不变。）

- [ ] **Step 4: 运行测试通过 + 全量回归**

Run: `cd ai-service && py -3.14 -m pytest tests/test_product_search_tenant.py tests/test_agent.py tests/test_agent_polish.py -v`
Expected: PASS（test_agent/test_agent_polish 若含 search_products 的既有用例，其 fake session 返回空行 → 工具返回空 products，不破坏断言；若有失配按 Task 2 同规则最小适配 fake、不改断言）

Run: `cd ai-service && py -3.14 -m pytest tests/ -q --ignore=tests/agent_eval --ignore=tests/moderation_eval`
Expected: 全量 PASS（基线 415 passed, 1 skipped + 本计划新增用例）

- [ ] **Step 5: 提交**

```bash
git add ai-service/app/services/product_search.py ai-service/app/services/agent/tools.py ai-service/tests/test_product_search_tenant.py
git commit -m "fix(agent): L1 商品读查询统一补租户过滤（search/detail），防跨租户商品探测"
```

---

## Self-Review 结论

- **提案覆盖**：提案①（契约测试）→ Task 1；提案②（会话合并）→ Task 2；提案③（租户过滤）→ Task 3。无缺口。
- **占位符**：Task 1 的 import 名（MockMvcControllers/MockMvcFactoryBuilder）已标注以实际 Spring Test API 为准并给出正确类名 MockMvcBuilders；Task 2 测试骨架一处笔误已内联标注等价形式。其余全部为完整代码。
- **类型一致性**：`tenant_id` 在 service 层为 `Optional[int]`、工具层为 `Annotated[Optional[str], InjectedToolArg]`（字符串→int 转换在工具层，与 estimate_price 既有模式 tools.py:336 一致）；Task 2 的 `_pre_order_risk_check(db, product_id, buyer_id, seller_id)` 签名与上轮落地代码一致。
- **Review Focus 覆盖**：假绿→Task 1 Step 2 的先红后绿强制片；L3 兜底→Task 3 `test_l3_fallback_respects_tenant`；估价样本回归→Task 2 `test_valuation_failure_does_not_break_confirm` + Task 3 全量回归；合并降级→Task 2 两个新用例；productId 联动→Task 1 断言 + 既有 isdigit 守卫。
