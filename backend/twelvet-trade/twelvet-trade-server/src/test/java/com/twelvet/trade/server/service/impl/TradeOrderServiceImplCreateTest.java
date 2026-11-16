package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.framework.utils.JacksonUtils;
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
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单链路重构（校验 + 扣库存 + 地址 + 过期时间 + 快照）测试
 * <p>
 * 锁定的不变量：
 * <ol>
 * <li>仅 onsale 商品可下单；自购拦截；库存原子扣减（rows=0 即拒绝且不落订单）——防超卖</li>
 * <li>express 必须有归属本人的收货地址；self_pickup 无需地址且运费仍取商品</li>
 * <li>商品快照/地址快照用 Jackson 序列化（title 含引号仍是合法 JSON，可还原）</li>
 * <li>订单写入支付过期时间 expireAt</li>
 * <li>租户开关关闭时 tenantId 传 null（零侵入回滚）</li>
 * </ol>
 * </p>
 */
class TradeOrderServiceImplCreateTest {

	private static final Long BUYER_ID = 7L;

	private static final Long PRODUCT_ID = 100L;

	private static final Long ADDRESS_ID = 5L;

	private static final Long TENANT_ID = 42L;

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
		// 与 InternalTest 相同的对齐：writeTid() 返回 TenantContext.get()，避免 mock 对 Long 默认给 0
		when(tenantSupport.writeTid()).thenAnswer(invocation -> TenantContext.get());
		when(tenantSupport.enabled()).thenReturn(true);
		// 公开路径 createOrder 的租户来源是登录态透传的 TenantContext；测试直接等价模拟
		TenantContext.set(TENANT_ID);
	}

	@AfterEach
	void clearContext() {
		TenantContext.clear();
	}

	/**
	 * 默认在售商品：express + 运费 3.00 + 售价 10.00 + 库存 1，卖家 99（非买家）
	 */
	private Product onsaleProduct() {
		Product product = mock(Product.class);
		when(product.getStatus()).thenReturn("onsale");
		when(product.getSellerId()).thenReturn(99L);
		when(product.getPrice()).thenReturn(new BigDecimal("10.00"));
		when(product.getShippingFee()).thenReturn(new BigDecimal("3.00"));
		when(product.getShippingMethod()).thenReturn("express");
		when(product.getStock()).thenReturn(1);
		when(product.getTitle()).thenReturn("测试商品");
		when(product.getCover()).thenReturn("cover.png");
		return product;
	}

	private Map<String, Object> ownedAddress() {
		Map<String, Object> address = new HashMap<>();
		address.put("receiver", "张三");
		address.put("mobile", "13800000000");
		address.put("province", "广东省");
		address.put("city", "深圳市");
		address.put("district", "南山区");
		address.put("detail", "科技园路 1 号");
		return address;
	}

	@Test
	@DisplayName("正常下单：扣库存、写快照（title 含引号仍可还原）、写地址与过期时间")
	void create_happyPath_deductsStockAndSetsExpire() {
		Product product = onsaleProduct();
		// title 含双引号：String.format 拼 JSON 必坏，Jackson 序列化后必须仍可还原（Review Focus #2）
		when(product.getTitle()).thenReturn("含\"引号\"的商品");
		when(productMapper.selectById(PRODUCT_ID, TENANT_ID)).thenReturn(product);
		when(productMapper.deductStock(eq(PRODUCT_ID), eq(TENANT_ID))).thenReturn(1);
		when(userAddressMapper.selectAddressById(ADDRESS_ID, BUYER_ID)).thenReturn(ownedAddress());

		Map<String, Object> result = service.createOrder(BUYER_ID, PRODUCT_ID, ADDRESS_ID, "留言", null);

		verify(productMapper).deductStock(eq(PRODUCT_ID), eq(TENANT_ID));

		ArgumentCaptor<TradeOrder> captor = ArgumentCaptor.forClass(TradeOrder.class);
		verify(orderMapper).insertOrder(captor.capture());
		TradeOrder saved = captor.getValue();

		assertNotNull(saved.getExpireAt(), "必须写入支付过期时间");
		assertEquals("留言", saved.getBuyerRemark());
		assertNotNull(saved.getAddressSnapshot(), "express 必须写地址快照");
		Map<String, Object> addressSnapshot = JacksonUtils.readValue(saved.getAddressSnapshot(), Map.class);
		assertNotNull(addressSnapshot, "地址快照必须是合法 JSON");
		assertEquals("张三", addressSnapshot.get("receiver"));
		assertEquals("express", saved.getDeliveryType());

		assertNotNull(saved.getProductSnapshot(), "必须写商品快照");
		Map<String, Object> productSnapshot = JacksonUtils.readValue(saved.getProductSnapshot(), Map.class);
		assertNotNull(productSnapshot, "商品快照必须是合法 JSON（title 含引号也不能坏）");
		assertTrue(String.valueOf(productSnapshot.get("title")).contains("引号"), "快照 title 必须还原含引号原文");
		assertEquals("unpaid", result.get("status"));
	}

	@Test
	@DisplayName("下单成功写 order_create（买家、商品、类目、非 AI）")
	void create_recordsOrderCreateEvent() {
		Product product = onsaleProduct();
		when(product.getCatL1()).thenReturn("数码");
		when(productMapper.selectById(PRODUCT_ID, TENANT_ID)).thenReturn(product);
		when(productMapper.deductStock(eq(PRODUCT_ID), eq(TENANT_ID))).thenReturn(1);
		when(userAddressMapper.selectAddressById(ADDRESS_ID, BUYER_ID)).thenReturn(ownedAddress());
		doAnswer(invocation -> {
			invocation.getArgument(0, TradeOrder.class).setOrderId(500L);
			return 1;
		}).when(orderMapper).insertOrder(any(TradeOrder.class));

		Map<String, Object> result = service.createOrder(BUYER_ID, PRODUCT_ID, ADDRESS_ID, null, null);

		ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(funnelEventService).record(captor.capture());
		FunnelEvent event = captor.getValue();
		assertEquals(FunnelEvent.TYPE_ORDER_CREATE, event.getEventType());
		assertEquals("order_create:" + result.get("orderId"), event.getEventKey());
		assertEquals(TENANT_ID, event.getTenantId());
		assertEquals(BUYER_ID, event.getUserId());
		assertEquals("buyer", event.getRole());
		assertEquals(PRODUCT_ID, event.getProductId());
		assertEquals("数码", event.getCategory());
		assertEquals(Boolean.FALSE, event.getAiAssisted());
	}

	@Test
	@DisplayName("商品非在售：拒绝且不扣库存、不落订单")
	void create_productNotOnsale_rejected() {
		Product product = onsaleProduct();
		when(product.getStatus()).thenReturn("sold");
		when(productMapper.selectById(PRODUCT_ID, TENANT_ID)).thenReturn(product);

		assertThrows(TWTException.class, () -> service.createOrder(BUYER_ID, PRODUCT_ID, ADDRESS_ID, null, null));
		verify(productMapper, never()).deductStock(anyLong(), any());
		verify(orderMapper, never()).insertOrder(any(TradeOrder.class));
	}

	@Test
	@DisplayName("自购拦截：买家即卖家时拒绝")
	void create_selfPurchaseRejected() {
		Product product = onsaleProduct();
		when(product.getSellerId()).thenReturn(BUYER_ID);
		when(productMapper.selectById(PRODUCT_ID, TENANT_ID)).thenReturn(product);

		assertThrows(TWTException.class, () -> service.createOrder(BUYER_ID, PRODUCT_ID, ADDRESS_ID, null, null));
		verify(productMapper, never()).deductStock(anyLong(), any());
		verify(orderMapper, never()).insertOrder(any(TradeOrder.class));
	}

	@Test
	@DisplayName("并发丢库存：扣库存影响行数为 0 时拒绝且不落订单（防超卖兜底，Review Focus #1）")
	void create_stockConcurrentLoss_rejected() {
		Product product = onsaleProduct();
		when(productMapper.selectById(PRODUCT_ID, TENANT_ID)).thenReturn(product);
		// 两个买家同时拍库存 1：后到者 UPDATE stock>0 条件不命中，rows=0
		when(productMapper.deductStock(eq(PRODUCT_ID), eq(TENANT_ID))).thenReturn(0);

		assertThrows(TWTException.class, () -> service.createOrder(BUYER_ID, PRODUCT_ID, ADDRESS_ID, null, null));
		verify(orderMapper, never()).insertOrder(any(TradeOrder.class));
	}

	@Test
	@DisplayName("express 无地址：拒绝")
	void create_expressWithoutAddress_rejected() {
		Product product = onsaleProduct();
		when(productMapper.selectById(PRODUCT_ID, TENANT_ID)).thenReturn(product);

		assertThrows(TWTException.class, () -> service.createOrder(BUYER_ID, PRODUCT_ID, null, null, null));
		verify(productMapper, never()).deductStock(anyLong(), any());
		verify(orderMapper, never()).insertOrder(any(TradeOrder.class));
	}

	@Test
	@DisplayName("地址不归属本人（不存在/非本人地址）：拒绝")
	void create_addressNotOwned_rejected() {
		Product product = onsaleProduct();
		when(productMapper.selectById(PRODUCT_ID, TENANT_ID)).thenReturn(product);
		when(userAddressMapper.selectAddressById(ADDRESS_ID, BUYER_ID)).thenReturn(null);

		assertThrows(TWTException.class, () -> service.createOrder(BUYER_ID, PRODUCT_ID, ADDRESS_ID, null, null));
		verify(orderMapper, never()).insertOrder(any(TradeOrder.class));
	}

	@Test
	@DisplayName("self_pickup：无需地址，运费归零（对齐 Checkout 展示：买家不为自提付运费）")
	void create_selfPickup_noAddressNeeded() {
		Product product = onsaleProduct();
		when(product.getShippingMethod()).thenReturn("self_pickup");
		when(productMapper.selectById(PRODUCT_ID, TENANT_ID)).thenReturn(product);
		when(productMapper.deductStock(eq(PRODUCT_ID), eq(TENANT_ID))).thenReturn(1);

		Map<String, Object> result = service.createOrder(BUYER_ID, PRODUCT_ID, null, null, null);

		verify(userAddressMapper, never()).selectAddressById(anyLong(), anyLong());
		ArgumentCaptor<TradeOrder> captor = ArgumentCaptor.forClass(TradeOrder.class);
		verify(orderMapper).insertOrder(captor.capture());
		TradeOrder saved = captor.getValue();
		assertNull(saved.getAddressSnapshot(), "自提单不写地址快照");
		assertEquals("self_pickup", saved.getDeliveryType());
		assertEquals(0, saved.getFreight().compareTo(BigDecimal.ZERO), "自提单运费必须归零——展示金额与扣款金额必须一致");
		assertEquals(0, saved.getTotalAmount().compareTo(new BigDecimal("10.00")), "总价 = 售价 + 0");
		assertEquals("unpaid", result.get("status"));
	}

	@Test
	@DisplayName("free（包邮）：运费归零，即使发布页误留 shippingFee；仍按 express 校验地址")
	void create_freeShipping_freightZeroed() {
		Product product = onsaleProduct();
		when(product.getShippingMethod()).thenReturn("free");
		when(productMapper.selectById(PRODUCT_ID, TENANT_ID)).thenReturn(product);
		when(productMapper.deductStock(eq(PRODUCT_ID), eq(TENANT_ID))).thenReturn(1);
		when(userAddressMapper.selectAddressById(ADDRESS_ID, BUYER_ID)).thenReturn(ownedAddress());

		service.createOrder(BUYER_ID, PRODUCT_ID, ADDRESS_ID, null, null);

		ArgumentCaptor<TradeOrder> captor = ArgumentCaptor.forClass(TradeOrder.class);
		verify(orderMapper).insertOrder(captor.capture());
		assertEquals(0, captor.getValue().getFreight().compareTo(BigDecimal.ZERO), "包邮单运费必须归零");
	}

	@Test
	@DisplayName("显式 deliveryType=self_pickup 不被采信：express 商品仍按商品自身方式推导并校验地址")
	void create_forgedSelfPickupDeliveryType_ignored() {
		// UI 从不传 deliveryType；显式传 self_pickup 试图把 express 商品伪造成面交、跳过地址校验。
		// 修复后：effective deliveryType 必须以商品 shippingMethod 为准，不得采信客户端。
		Product product = onsaleProduct();
		when(productMapper.selectById(PRODUCT_ID, TENANT_ID)).thenReturn(product);

		assertThrows(TWTException.class, () -> service.createOrder(BUYER_ID, PRODUCT_ID, null, null, "self_pickup"));
		verify(productMapper, never()).deductStock(anyLong(), any());
		verify(orderMapper, never()).insertOrder(any(TradeOrder.class));
	}

	@Test
	@DisplayName("租户开关关闭：deductStock 的租户条件传 null（零侵入回滚，Review Focus #5）")
	void create_tenantOff_usesNullTid() {
		// 开关关闭即无租户上下文，先清掉 @BeforeEach 建立的上下文
		TenantContext.clear();
		when(tenantSupport.enabled()).thenReturn(false);
		Product product = onsaleProduct();
		when(productMapper.selectById(PRODUCT_ID, null)).thenReturn(product);
		when(productMapper.deductStock(eq(PRODUCT_ID), isNull())).thenReturn(1);
		when(userAddressMapper.selectAddressById(eq(ADDRESS_ID), eq(BUYER_ID))).thenReturn(ownedAddress());

		service.createOrder(BUYER_ID, PRODUCT_ID, ADDRESS_ID, null, null);

		verify(productMapper).deductStock(eq(PRODUCT_ID), isNull());
	}

}
