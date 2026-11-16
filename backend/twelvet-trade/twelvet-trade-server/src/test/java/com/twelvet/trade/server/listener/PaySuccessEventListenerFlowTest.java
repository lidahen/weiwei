package com.twelvet.trade.server.listener;

import com.twelvet.im.server.service.IImService;
import com.twelvet.payment.api.event.PaySuccessEvent;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import com.twelvet.trade.server.mapper.TradePayFlowMapper;
import com.twelvet.trade.server.service.ISettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 支付成功写资金流水测试
 * <p>
 * 锁定不变量：
 * <ol>
 * <li>订单迁移成功（rows=1）才写 pay 流水，避免重复回调重复记账</li>
 * <li>金额按分转元（1107 分 → 11.07）</li>
 * <li>流水写入失败不影响订单状态迁移（不得向外抛）</li>
 * </ol>
 * </p>
 */
class PaySuccessEventListenerFlowTest {

	private static final Long ORDER_ID = 1L;

	private final TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);

	private final TradePayFlowMapper flowMapper = mock(TradePayFlowMapper.class);

	private final IImService imService = mock(IImService.class);

	private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);

	private final ISettlementService settlementService = mock(ISettlementService.class);

	private final IPayOrderService payOrderService = mock(IPayOrderService.class);

	private final PaySuccessEventListener listener = new PaySuccessEventListener(orderMapper, flowMapper, imService,
			funnelEventService, payOrderService, settlementService);

	private PaySuccessEvent event(long amountCents) {
		return new PaySuccessEvent("OUT123", ORDER_ID, "trade", "alipay", amountCents, LocalDateTime.now(), "TRADE123", "{}");
	}

	@Test
	@DisplayName("支付成功（订单迁移成功）写一条 pay 流水")
	void onPaySuccess_writesFlow() {
		when(orderMapper.updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null)).thenReturn(1);
		TradeOrder order = new TradeOrder();
		order.setOrderId(ORDER_ID);
		order.setTenantId(42L);
		order.setBuyerId(101L);
		order.setSellerId(202L);
		order.setProductId(100L);
		when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(order);

		listener.onPaySuccess(event(1107L));

		verify(orderMapper).updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null);
		verify(flowMapper).insert(ORDER_ID, "pay", new BigDecimal("11.07"), "alipay", "OUT123");
		verify(imService).sendNotice(eq(101L), eq("order"), eq("支付成功"), any());
		verify(imService).sendNotice(eq(202L), eq("order"), eq("买家已付款"), any());
		ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(funnelEventService).record(captor.capture());
		assertEquals(FunnelEvent.TYPE_PAY_SUCCESS, captor.getValue().getEventType());
		assertEquals(42L, captor.getValue().getTenantId());
		assertEquals("pay_success:" + ORDER_ID, captor.getValue().getEventKey());
		assertEquals(101L, captor.getValue().getUserId());
	}

	@Test
	@DisplayName("订单已是已付/不存在（rows=0）不写流水，避免重复记账")
	void onPaySuccess_rowsZero_noFlow() {
		when(orderMapper.updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null)).thenReturn(0);

		listener.onPaySuccess(event(1107L));

		verify(flowMapper, never()).insert(any(), any(), any(), any(), any());
		verifyNoInteractions(funnelEventService);
	}

	@Test
	@DisplayName("流水写入抛异常不影响订单状态迁移（不向外抛）")
	void onPaySuccess_flowFailureDoesNotBreak() {
		when(orderMapper.updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null)).thenReturn(1);
		when(flowMapper.insert(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("db down"));

		assertDoesNotThrow(() -> listener.onPaySuccess(event(1107L)));

		verify(orderMapper).updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null);
	}

	// ===== biz_type 门卫 + 迟到支付收口（2026-11-03 biz_type 收口） =====

	private PaySuccessEvent eventOfBizType(String bizType, Long orderId) {
		return new PaySuccessEvent("OUT123", orderId, bizType, "alipay", 10000L, LocalDateTime.now(), "TRADE123", "{}");
	}

	private TradeOrder orderWithStatus(String status) {
		TradeOrder order = new TradeOrder();
		order.setOrderId(ORDER_ID);
		order.setStatus(status);
		order.setBuyerId(1L);
		return order;
	}

	@Test
	@DisplayName("runner 支付事件：trade 监听器跳过（不迁移订单、不退款）")
	void runnerEvent_skipped() {
		listener.onPaySuccess(eventOfBizType("runner", ORDER_ID));

		verify(orderMapper, never()).updateOrderStatus(any(), any(), any(), any());
		verify(payOrderService, never()).refund(any(), any(), any());
	}

	@Test
	@DisplayName("renew 支付事件（orderId=null）：trade 监听器跳过")
	void renewEvent_skipped() {
		listener.onPaySuccess(eventOfBizType("renew", null));

		verify(orderMapper, never()).updateOrderStatus(any(), any(), any(), any());
		verify(payOrderService, never()).refund(any(), any(), any());
	}

	@Test
	@DisplayName("迟到支付：订单已 closed → 自动原路退款并通知买家")
	void latePay_orderClosed_refunds() {
		when(orderMapper.updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null)).thenReturn(0);
		when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(orderWithStatus("closed"));
		when(payOrderService.refund(any(), any(), any())).thenReturn(true);

		listener.onPaySuccess(eventOfBizType("trade", ORDER_ID));

		verify(payOrderService).refund(eq("OUT123"), eq(new BigDecimal("100.00")), contains("超时关闭"));
		verify(imService).sendNotice(eq(1L), eq("order"), eq("支付时订单已关闭"), contains("退回"));
	}

	@Test
	@DisplayName("迟到支付：订单已 cancelled → 自动原路退款")
	void latePay_orderCancelled_refunds() {
		when(orderMapper.updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null)).thenReturn(0);
		when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(orderWithStatus("cancelled"));

		listener.onPaySuccess(eventOfBizType("trade", ORDER_ID));

		verify(payOrderService).refund(eq("OUT123"), eq(new BigDecimal("100.00")), contains("超时关闭"));
	}

	@Test
	@DisplayName("事件重发（订单已 unshipped）：绝不退款（迟到支付收口缺陷回归）")
	void latePay_orderUnshipped_neverRefunds() {
		when(orderMapper.updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null)).thenReturn(0);
		when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(orderWithStatus("unshipped"));

		listener.onPaySuccess(eventOfBizType("trade", ORDER_ID));

		verify(payOrderService, never()).refund(any(), any(), any());
	}

	@Test
	@DisplayName("rows=0 且订单查不到：不退款（宁可不退不可错退）")
	void latePay_orderNotFound_neverRefunds() {
		when(orderMapper.updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null)).thenReturn(0);
		when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(null);

		listener.onPaySuccess(eventOfBizType("trade", ORDER_ID));

		verify(payOrderService, never()).refund(any(), any(), any());
	}

}