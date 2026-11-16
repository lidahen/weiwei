package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.im.server.service.IImService;
import com.twelvet.payment.api.event.PaySuccessEvent;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.product.server.mapper.UserAddressMapper;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.listener.PaySuccessEventListener;
import com.twelvet.trade.server.mapper.AdminMarketplaceMapper;
import com.twelvet.trade.server.mapper.TradeLogisticsMapper;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import com.twelvet.trade.server.mapper.TradePayFlowMapper;
import com.twelvet.trade.server.service.ISettlementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 订单状态机修正测试（发货/收货/取消 + 支付监听器 + 库存释放）
 * <p>
 * 锁定的不变量：
 * <ol>
 * <li>支付成功置 unshipped（打通发货链路；paid 仅作为存量野状态被兼容读取，不再写入）</li>
 * <li>发货：先原子 UPDATE（rows=0 直接抛异常），成功才写物流表——失败不留物流脏数据</li>
 * <li>确认收货：rows=0 抛异常而非静默 OK；成功后商品标记 sold</li>
 * <li>买家取消：rows=0 抛异常且不释放库存（防与超时关单并发双倍回补）；成功则回补库存</li>
 * <li>发货环节商品状态不被改动（商品状态流转只发生在 done/closed）</li>
 * </ol>
 * </p>
 */
class TradeOrderServiceImplLifecycleTest {

	private static final Long BUYER_ID = 7L;

	private static final Long SELLER_ID = 99L;

	private static final Long ORDER_ID = 1L;

	private static final Long PRODUCT_ID = 100L;

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

	private final ISettlementService settlementService = mock(ISettlementService.class);

	private final IPayOrderService payOrderService = mock(IPayOrderService.class);

	private final TradeOrderServiceImpl service = new TradeOrderServiceImpl(orderMapper, logisticsMapper, adminMapper,
			productMapper, userAddressMapper, tenantSupport, timeoutHelper, imService, funnelEventService);

	private final TradePayFlowMapper flowMapper = mock(TradePayFlowMapper.class);

	private final PaySuccessEventListener listener = new PaySuccessEventListener(orderMapper, flowMapper, imService,
			funnelEventService, payOrderService, settlementService);

	@BeforeEach
	void stubWriteTidLikeRealImplementation() {
		// 与 CreateTest 相同的对齐：writeTid() 返回 TenantContext.get()，避免 mock 对 Long 默认给 0
		when(tenantSupport.writeTid()).thenAnswer(invocation -> TenantContext.get());
		when(tenantSupport.enabled()).thenReturn(true);
		TenantContext.set(TENANT_ID);
	}

	@AfterEach
	void clearContext() {
		// 防御：任何一个用例失败都不应把上下文残留给下一个用例
		TenantContext.clear();
	}

	private TradeOrder unpaidOrder() {
		TradeOrder order = mock(TradeOrder.class);
		when(order.getOrderId()).thenReturn(ORDER_ID);
		when(order.getBuyerId()).thenReturn(BUYER_ID);
		when(order.getProductId()).thenReturn(PRODUCT_ID);
		when(order.getStatus()).thenReturn("unpaid");
		// 新链路订单必有 expire_at；NULL 表示存量单（不回补库存），存量单场景在各用例单独覆盖
		when(order.getExpireAt()).thenReturn(LocalDateTime.now().plusMinutes(10));
		return order;
	}

	private TradeOrder shippedOrder() {
		TradeOrder order = mock(TradeOrder.class);
		when(order.getOrderId()).thenReturn(ORDER_ID);
		when(order.getBuyerId()).thenReturn(BUYER_ID);
		when(order.getSellerId()).thenReturn(SELLER_ID);
		when(order.getProductId()).thenReturn(PRODUCT_ID);
		when(order.getTenantId()).thenReturn(TENANT_ID);
		when(order.getStatus()).thenReturn("shipped");
		return order;
	}

	@Test
	@DisplayName("支付成功事件：订单状态置 unshipped（不再是野状态 paid）并写 paid_at")
	void payListener_setsUnshipped() {
		PaySuccessEvent event = new PaySuccessEvent("OUT123", 1L, "trade", "alipay", 1000L, LocalDateTime.now(), "TRADE123",
				"{}");
		when(orderMapper.updateOrderStatus(1L, "unshipped", "paid_at", null)).thenReturn(1);
		TradeOrder paidOrder = new TradeOrder();
		paidOrder.setOrderId(ORDER_ID);
		paidOrder.setTenantId(TENANT_ID);
		paidOrder.setBuyerId(BUYER_ID);
		paidOrder.setSellerId(SELLER_ID);
		when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(paidOrder);

		listener.onPaySuccess(event);

		// 回调无租户上下文：tenantId=null 跳过过滤，按 orderId 全局定位
		verify(orderMapper).updateOrderStatus(1L, "unshipped", "paid_at", null);
		// 漏斗租户必须来自订单（回调线程没有 TenantContext）
		ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(funnelEventService).record(captor.capture());
		assertEquals(FunnelEvent.TYPE_PAY_SUCCESS, captor.getValue().getEventType());
		assertEquals(TENANT_ID, captor.getValue().getTenantId());
		assertEquals("pay_success:" + ORDER_ID, captor.getValue().getEventKey());
	}

	@Test
	@DisplayName("回调晚于取消（订单已 closed）：迟到支付收口——自动原路退款并通知买家")
	void payListener_lateCallbackAfterCancel_refunds() {
		// 买家取消（closed、库存已回补）后支付回调才到达：迁移 SQL 带前置 status='unpaid'，
		// UPDATE 命中 0 行。监听器不抛错（避免回调重试风暴），且显式查单后自动退款（2026-11-03 收口）
		PaySuccessEvent event = new PaySuccessEvent("OUT123", 1L, "trade", "alipay", 1000L, LocalDateTime.now(), "TRADE123",
				"{}");
		when(orderMapper.updateOrderStatus(1L, "unshipped", "paid_at", null)).thenReturn(0);
		TradeOrder closed = new TradeOrder();
		closed.setOrderId(ORDER_ID);
		closed.setStatus("closed");
		closed.setBuyerId(BUYER_ID);
		when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(closed);

		listener.onPaySuccess(event);

		verify(orderMapper).updateOrderStatus(1L, "unshipped", "paid_at", null);
		verify(payOrderService).refund(eq("OUT123"), eq(new BigDecimal("10.00")), anyString());
	}

	@Test
	@DisplayName("发货失败（rows=0）：抛异常且不写物流表")
	void ship_rowsZero_throws() {
		when(orderMapper.shipOrder(eq(ORDER_ID), eq(SELLER_ID), anyInt(), eq(TENANT_ID))).thenReturn(0);

		assertThrows(TWTException.class, () -> service.shipOrder(ORDER_ID, SELLER_ID, "顺丰", "SF001"));

		verify(logisticsMapper, never()).insertLogistics(anyLong(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("发货成功：写物流且商品状态不被改动（商品状态流转只发生在 done/closed）")
	void ship_success_writesAutoConfirm() {
		when(orderMapper.shipOrder(eq(ORDER_ID), eq(SELLER_ID), anyInt(), eq(TENANT_ID))).thenReturn(1);
		TradeOrder notifyOrder = shippedOrder();
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(notifyOrder);

		service.shipOrder(ORDER_ID, SELLER_ID, "顺丰", "SF001");

		verify(orderMapper).shipOrder(eq(ORDER_ID), eq(SELLER_ID), anyInt(), eq(TENANT_ID));
		verify(logisticsMapper).insertLogistics(eq(ORDER_ID), eq("顺丰"), eq("SF001"), eq("已揽件"), any(),
				eq(TENANT_ID));
		verify(imService).sendNotice(eq(BUYER_ID), eq("order"), eq("卖家已发货"), contains("顺丰"));
		verify(productMapper, never()).markSold(anyLong(), any());
		verify(productMapper, never()).restoreStock(anyLong(), any());
		ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(funnelEventService).record(captor.capture());
		assertEquals(FunnelEvent.TYPE_SHIP, captor.getValue().getEventType());
		assertEquals("seller", captor.getValue().getRole());
		assertEquals("ship:" + ORDER_ID, captor.getValue().getEventKey());
	}

	@Test
	@DisplayName("IM 通知失败不阻断发货")
	void ship_imFailure_doesNotBreak() {
		when(orderMapper.shipOrder(eq(ORDER_ID), eq(SELLER_ID), anyInt(), eq(TENANT_ID))).thenReturn(1);
		// mock 必须在 when(...) 之外完成创建与 stub（嵌套 stubbing 会触发 UnfinishedStubbing）
		TradeOrder notifyOrder = shippedOrder();
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(notifyOrder);
		doThrow(new RuntimeException("im down")).when(imService).sendNotice(any(), any(), any(), any());

		assertDoesNotThrow(() -> service.shipOrder(ORDER_ID, SELLER_ID, "顺丰", "SF001"));

		verify(orderMapper).shipOrder(eq(ORDER_ID), eq(SELLER_ID), anyInt(), eq(TENANT_ID));
		verify(logisticsMapper).insertLogistics(eq(ORDER_ID), eq("顺丰"), eq("SF001"), eq("已揽件"), any(),
				eq(TENANT_ID));
	}

	@Test
	@DisplayName("确认收货失败（rows=0）：抛异常而非静默 OK，且不标记商品")
	void confirm_rowsZero_throws() {
		// mock 必须在 when(...) 之外完成创建与 stub（嵌套 stubbing 会触发 UnfinishedStubbing，
		// 与 InternalTest 记录的陷阱相同）
		TradeOrder order = shippedOrder();
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(order);
		when(orderMapper.confirmReceipt(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(0);

		assertThrows(TWTException.class, () -> service.confirmReceipt(ORDER_ID, BUYER_ID));

		verify(productMapper, never()).markSold(anyLong(), any());
	}

	@Test
	@DisplayName("确认收货成功：商品标记 sold（productId 来自订单）")
	void confirm_success_marksProductSold() {
		TradeOrder order = shippedOrder();
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(order);
		when(orderMapper.confirmReceipt(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(1);

		service.confirmReceipt(ORDER_ID, BUYER_ID);

		verify(productMapper).markSold(eq(PRODUCT_ID), any());
		verify(imService).sendNotice(eq(SELLER_ID), eq("order"), eq("买家已确认收货"), any());
		ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(funnelEventService).record(captor.capture());
		assertEquals(FunnelEvent.TYPE_CONFIRM_RECEIPT, captor.getValue().getEventType());
		assertEquals("buyer", captor.getValue().getRole());
		assertEquals("confirm_receipt:" + ORDER_ID, captor.getValue().getEventKey());
	}

	@Test
	@DisplayName("取消失败（rows=0，如已支付或被超时关单抢先）：抛异常且不释放库存（防双倍回补）")
	void cancel_rowsZero_throws() {
		TradeOrder order = unpaidOrder();
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(order);
		when(orderMapper.cancelOrder(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(0);

		assertThrows(TWTException.class, () -> service.cancelOrder(ORDER_ID, BUYER_ID));

		verify(productMapper, never()).restoreStock(anyLong(), any());
	}

	@Test
	@DisplayName("取消成功：回补库存（productId 来自订单）")
	void cancel_success_restoresStock() {
		TradeOrder order = unpaidOrder();
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(order);
		when(orderMapper.cancelOrder(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(1);

		service.cancelOrder(ORDER_ID, BUYER_ID);

		verify(productMapper).restoreStock(PRODUCT_ID, TENANT_ID);
	}

	@Test
	@DisplayName("取消成功：存量单（expire_at 为 NULL，旧链路下单未扣库存）不回补库存——回补即凭空虚增")
	void cancel_legacyOrderWithoutExpire_doesNotRestoreStock() {
		// 旧链路（2026-10-19 之前）下单不扣库存：这些订单没有 expire_at。
		// 若按新链路语义回补，stock 1→2，一件实体可被卖出两次（超卖）。
		TradeOrder order = unpaidOrder();
		when(order.getExpireAt()).thenReturn(null);
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(order);
		when(orderMapper.cancelOrder(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(1);

		service.cancelOrder(ORDER_ID, BUYER_ID);

		verify(productMapper, never()).restoreStock(anyLong(), any());
	}

	@Test
	@DisplayName("validateOrderPayable：unpaid 且未过期 → 返回订单号与金额")
	void validatePayable_unpaidNotExpired_returnsInfo() {
		TradeOrder order = unpaidOrder();
		when(order.getExpireAt()).thenReturn(LocalDateTime.now().plusMinutes(10));
		when(order.getOrderNo()).thenReturn("NO123");
		when(order.getTotalAmount()).thenReturn(new java.math.BigDecimal("13.00"));
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(order);

		Map<String, Object> result = service.validateOrderPayable(ORDER_ID, BUYER_ID);

		org.junit.jupiter.api.Assertions.assertNotNull(result, "可支付订单必须返回非空");
		assertEquals("NO123", result.get("order_no"));
	}

	@Test
	@DisplayName("validateOrderPayable：已关闭/已发货等非 unpaid 状态 → 返回 null（与不存在同响应，防探测）")
	void validatePayable_closed_returnsNull() {
		TradeOrder order = unpaidOrder();
		when(order.getStatus()).thenReturn("closed");
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(order);

		org.junit.jupiter.api.Assertions.assertNull(service.validateOrderPayable(ORDER_ID, BUYER_ID),
				"已关订单绝不能再进入收银台——否则支付成功后钱货两空");
	}

	@Test
	@DisplayName("validateOrderPayable：unpaid 但 expire_at 已过 → 返回 null（超时单等待 quartz 关单，不再接受支付）")
	void validatePayable_expired_returnsNull() {
		TradeOrder order = unpaidOrder();
		when(order.getExpireAt()).thenReturn(LocalDateTime.now().minusMinutes(1));
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(order);

		org.junit.jupiter.api.Assertions.assertNull(service.validateOrderPayable(ORDER_ID, BUYER_ID));
	}

	@Test
	@DisplayName("validateOrderPayable：存量单（expire_at NULL）不可支付——旧链路无库存扣减语义，须先人工迁移")
	void validatePayable_legacyWithoutExpire_returnsNull() {
		TradeOrder order = unpaidOrder();
		when(order.getExpireAt()).thenReturn(null);
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID)).thenReturn(order);

		org.junit.jupiter.api.Assertions.assertNull(service.validateOrderPayable(ORDER_ID, BUYER_ID));
	}

}
