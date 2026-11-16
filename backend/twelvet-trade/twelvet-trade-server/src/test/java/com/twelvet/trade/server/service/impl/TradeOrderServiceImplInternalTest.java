package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.im.server.service.IImService;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.product.server.mapper.UserAddressMapper;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.mapper.AdminMarketplaceMapper;
import com.twelvet.trade.server.mapper.TradeLogisticsMapper;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内部接口下单（{@link TradeOrderServiceImpl#createOrderForUser}）测试
 * <p>
 * 修复的缺陷：AI 导购 Agent 原先直接 INSERT trade_order（列清单无 tenant_id）， 在
 * {@code tenant.enabled=true} 的当前配置下，订单会落到 DEFAULT 1，造成跨租户混入。 本测试锁定修复后的四条不变量：
 * <ol>
 * <li>租户来自 sys_user.tenant_id，而不是调用方传参</li>
 * <li>商品查询带上该租户 → 跨租户商品"不存在"</li>
 * <li>ThreadLocal 上下文无论成功或失败都必须清理</li>
 * <li>租户开关关闭时行为与公开路径一致（零侵入回滚能力不被破坏）</li>
 * </ol>
 * </p>
 */
class TradeOrderServiceImplInternalTest {

	private static final Long BUYER_ID = 7L;

	private static final Long PRODUCT_ID = 100L;

	private static final Long BUYER_TENANT_ID = 42L;

	private final TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);

	private final TradeLogisticsMapper logisticsMapper = mock(TradeLogisticsMapper.class);

	private final AdminMarketplaceMapper adminMapper = mock(AdminMarketplaceMapper.class);

	private final ProductMapper productMapper = mock(ProductMapper.class);

	private final UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);

	private final TenantSupport tenantSupport = mock(TenantSupport.class);

	private final TradeOrderTimeoutHelper timeoutHelper = mock(TradeOrderTimeoutHelper.class);

	private final IImService imService = mock(IImService.class);

	private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);

	private final TradeOrderServiceImpl service = new TradeOrderServiceImpl(orderMapper, logisticsMapper, adminMapper,
			productMapper, userAddressMapper, tenantSupport, timeoutHelper, imService, funnelEventService);

	@BeforeEach
	void stubWriteTidLikeRealImplementation() {
		// 真实 TenantSupport.writeTid() 返回 TenantContext.get()（开关关闭时即 null）。
		// 必须显式对齐：Mockito 对 Long 返回值默认给 0，否则"开关关闭时不写租户列"的断言会因
		// mock 假象而失真（本测试第二次运行正是因此报 expected null but was 0）。
		when(tenantSupport.writeTid()).thenAnswer(invocation -> TenantContext.get());
	}

	@AfterEach
	void clearContext() {
		// 防御：任何一个用例失败都不应把上下文残留给下一个用例
		TenantContext.clear();
	}

	private Product onsaleProduct() {
		Product product = mock(Product.class);
		// 下单校验链要求 onsale；本测试聚焦租户不变量，配送方式取 self_pickup 免去地址依赖
		when(product.getStatus()).thenReturn("onsale");
		when(product.getShippingMethod()).thenReturn("self_pickup");
		when(product.getPrice()).thenReturn(new BigDecimal("10.00"));
		when(product.getShippingFee()).thenReturn(new BigDecimal("3.00"));
		when(product.getTitle()).thenReturn("测试商品");
		when(product.getCover()).thenReturn("cover.png");
		when(product.getSellerId()).thenReturn(99L);
		return product;
	}

	private void tenantSwitchOn() {
		// writeTid() 已在 @BeforeEach 中对齐真实实现，这里只需打开开关
		when(tenantSupport.enabled()).thenReturn(true);
	}

	@Test
	@DisplayName("开关开启：订单租户取自 sys_user.tenant_id，且运费/快照齐全")
	void switchOn_usesBuyerTenantFromDb() {
		tenantSwitchOn();
		when(orderMapper.selectUserTenantId(BUYER_ID)).thenReturn(BUYER_TENANT_ID);
		// 注意：Product mock 必须在 when(...) 之外完成创建与 stub。若写成
		// thenReturn(onsaleProduct())，会在外层 stubbing 尚未结束时嵌套 stubbing，
		// 触发 Mockito 的 UnfinishedStubbing（本测试首次运行即因此失败）。
		Product product = onsaleProduct();
		when(productMapper.selectById(PRODUCT_ID, BUYER_TENANT_ID)).thenReturn(product);
		// 下单链路新增的原子扣库存：必须成功（rows=1）才能走到插订单
		when(productMapper.deductStock(eq(PRODUCT_ID), any())).thenReturn(1);

		Map<String, Object> result = service.createOrderForUser(BUYER_ID, PRODUCT_ID);

		// 商品查询必须带买家租户——这正是"跨租户商品查不到"的机制所在
		verify(productMapper).selectById(PRODUCT_ID, BUYER_TENANT_ID);

		ArgumentCaptor<TradeOrder> captor = ArgumentCaptor.forClass(TradeOrder.class);
		verify(orderMapper).insertOrder(captor.capture());
		TradeOrder saved = captor.getValue();
		assertEquals(BUYER_TENANT_ID, saved.getTenantId(), "订单租户必须等于买家租户，而不是 DEFAULT 1");
		assertEquals(0, saved.getFreight().compareTo(BigDecimal.ZERO), "自提单运费必须归零（买家不为自提付运费）；商品 shippingFee 不得照抄进自提订单");
		assertNotNull(saved.getProductSnapshot(), "必须写入商品快照");
		assertEquals(new BigDecimal("10.00"), result.get("totalAmount"), "总价 = 售价 + 自提 0 运费");
		assertEquals("unpaid", result.get("status"));

		// AI 导购内部下单：漏斗事件必须带 aiAssisted=true，供后续算 AI 辅助成交占比
		ArgumentCaptor<FunnelEvent> funnelCaptor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(funnelEventService).record(funnelCaptor.capture());
		assertEquals(FunnelEvent.TYPE_ORDER_CREATE, funnelCaptor.getValue().getEventType());
		assertEquals(Boolean.TRUE, funnelCaptor.getValue().getAiAssisted());
		assertEquals(BUYER_TENANT_ID, funnelCaptor.getValue().getTenantId());

		assertNull(TenantContext.get(), "调用结束后必须清理租户上下文，避免线程复用导致泄漏");
	}

	@Test
	@DisplayName("开关开启：跨租户商品被拒绝（与公开路径一致，不抛更不落库）")
	void switchOn_crossTenantProductRejected() {
		tenantSwitchOn();
		when(orderMapper.selectUserTenantId(BUYER_ID)).thenReturn(BUYER_TENANT_ID);
		// 带租户过滤后查不到 = 该商品不属于买家租户
		when(productMapper.selectById(PRODUCT_ID, BUYER_TENANT_ID)).thenReturn(null);

		TWTException ex = assertThrows(TWTException.class, () -> service.createOrderForUser(BUYER_ID, PRODUCT_ID));
		assertNotNull(ex.getMessage());
		verify(orderMapper, never()).insertOrder(any(TradeOrder.class));
		assertNull(TenantContext.get(), "失败路径同样必须清理上下文");
	}

	@Test
	@DisplayName("开关开启：买家不属于任何租户时拒绝，且不查商品")
	void switchOn_userWithoutTenantRejected() {
		tenantSwitchOn();
		when(orderMapper.selectUserTenantId(BUYER_ID)).thenReturn(null);

		assertThrows(TWTException.class, () -> service.createOrderForUser(BUYER_ID, PRODUCT_ID));
		verify(productMapper, never()).selectById(anyLong(), any());
		assertNull(TenantContext.get());
	}

	@Test
	@DisplayName("开关关闭：不建立上下文、不查租户，行为与公开路径一致（零侵入回滚）")
	void switchOff_fallsBackToPlainCreateOrder() {
		when(tenantSupport.enabled()).thenReturn(false);
		Product product = onsaleProduct();
		when(productMapper.selectById(PRODUCT_ID, null)).thenReturn(product);
		// 下单链路新增的原子扣库存（租户开关关闭时 tenantId 为 null）
		when(productMapper.deductStock(eq(PRODUCT_ID), any())).thenReturn(1);

		service.createOrderForUser(BUYER_ID, PRODUCT_ID);

		verify(orderMapper, never()).selectUserTenantId(anyLong());
		verify(productMapper).selectById(eq(PRODUCT_ID), eq(null));
		ArgumentCaptor<TradeOrder> captor = ArgumentCaptor.forClass(TradeOrder.class);
		verify(orderMapper).insertOrder(captor.capture());
		assertNull(captor.getValue().getTenantId(), "开关关闭时不写租户列，由 DB 缺省兜底");
		assertNull(TenantContext.get());
	}

}
