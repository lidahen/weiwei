package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.api.domain.TradeReview;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import com.twelvet.trade.server.mapper.TradeReviewMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价漏斗事件：review_create 必须带角色/商品/租户，供社区看板计算评价率。
 */
class TradeReviewServiceFunnelTest {

	private static final Long ORDER_ID = 1L;

	private static final Long BUYER_ID = 7L;

	private static final Long SELLER_ID = 9L;

	private static final Long PRODUCT_ID = 100L;

	private static final Long REVIEW_ID = 55L;

	private static final Long TENANT_ID = 42L;

	private final TradeReviewMapper reviewMapper = mock(TradeReviewMapper.class);

	private final TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);

	private final TenantSupport tenantSupport = mock(TenantSupport.class);

	private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);

	private final TradeReviewServiceImpl service = new TradeReviewServiceImpl(reviewMapper, orderMapper, tenantSupport,
			funnelEventService);

	@BeforeEach
	void setTenant() {
		TenantContext.set(TENANT_ID);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	@DisplayName("评价成功写 review_create，角色取评价的 role")
	void submit_recordsReviewCreate() {
		TradeOrder order = new TradeOrder();
		order.setOrderId(ORDER_ID);
		order.setBuyerId(BUYER_ID);
		order.setSellerId(SELLER_ID);
		order.setProductId(PRODUCT_ID);
		order.setStatus("done");
		order.setTenantId(TENANT_ID);
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(order);
		when(reviewMapper.selectByOrderAndRole(ORDER_ID, "buyer", TENANT_ID)).thenReturn(null);
		when(tenantSupport.writeTid()).thenReturn(TENANT_ID);
		doAnswer(invocation -> {
			invocation.getArgument(0, TradeReview.class).setReviewId(REVIEW_ID);
			return 1;
		}).when(reviewMapper).insertReview(any(TradeReview.class));

		service.submit(ORDER_ID, BUYER_ID, 5, "好", null, null, false);

		ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(funnelEventService).record(captor.capture());
		FunnelEvent event = captor.getValue();
		assertEquals(FunnelEvent.TYPE_REVIEW_CREATE, event.getEventType());
		assertEquals("review_create:" + REVIEW_ID, event.getEventKey());
		assertEquals("buyer", event.getRole());
		assertEquals(BUYER_ID, event.getUserId());
		assertEquals(PRODUCT_ID, event.getProductId());
		assertEquals(TENANT_ID, event.getTenantId());
	}

}