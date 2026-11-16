package com.twelvet.trade.server.service.impl;

import com.twelvet.im.server.service.IImService;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.domain.TradeRefund;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import com.twelvet.trade.server.mapper.TradePayFlowMapper;
import com.twelvet.trade.server.mapper.TradePayLookupMapper;
import com.twelvet.trade.server.mapper.TradeRefundLogMapper;
import com.twelvet.trade.server.mapper.TradeRefundMapper;
import com.twelvet.trade.server.service.ISettlementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款执行（executeRefund）测试
 * <p>
 * 锁定的不变量：
 * <ol>
 * <li>支付单定位必须双重校验（pay_user_id + 金额）：trade_pay.order_id 是商品订单/跑腿任务/租户续费共用的多态外键，撞号已实测</li>
 * <li>定位失败/金额不足/网关失败一律转人工（refunding + manual_required 日志 + 通知），不抛异常</li>
 * <li>退款成功才推进 refund_success、订单 refunded、写 refund 流水、写 refund_ok 日志</li>
 * <li>流水写入失败不得让已成功的退款回滚</li>
 * </ol>
 * </p>
 */
class TradeRefundServiceImplExecuteTest {

	private static final Long REFUND_ID = 9L;

	private static final Long ORDER_ID = 1L;

	private static final Long BUYER_ID = 101L;

	private static final Long SELLER_ID = 202L;

	private static final Long PRODUCT_ID = 555L;

	private static final Long TENANT_ID = 42L;

	private final TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);

	private final TradeRefundMapper refundMapper = mock(TradeRefundMapper.class);

	private final TradeRefundLogMapper logMapper = mock(TradeRefundLogMapper.class);

	private final TradePayLookupMapper payLookupMapper = mock(TradePayLookupMapper.class);

	private final IPayOrderService payOrderService = mock(IPayOrderService.class);

	private final TradePayFlowMapper flowMapper = mock(TradePayFlowMapper.class);

	private final IImService imService = mock(IImService.class);

	private final ProductMapper productMapper = mock(ProductMapper.class);

	private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);

	private final TradeRefundServiceImpl service = new TradeRefundServiceImpl(orderMapper, refundMapper, logMapper,
			payLookupMapper, payOrderService, flowMapper, imService, productMapper, new TradeRefundTimeoutHelper(),
			funnelEventService, mock(ISettlementService.class));

	@BeforeEach
	void setTenant() {
		TenantContext.set(TENANT_ID);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private TradeRefund refund(String status, String type) {
		TradeRefund refund = new TradeRefund();
		refund.setRefundId(REFUND_ID);
		refund.setOrderId(ORDER_ID);
		refund.setBuyerId(BUYER_ID);
		refund.setSellerId(SELLER_ID);
		refund.setType(type);
		refund.setReasonCode("damaged");
		refund.setAmount(new BigDecimal("1107.00"));
		refund.setStatus(status);
		refund.setPrevOrderStatus("shipped");
		return refund;
	}

	private TradeOrder order() {
		TradeOrder order = new TradeOrder();
		order.setOrderId(ORDER_ID);
		order.setProductId(PRODUCT_ID);
		order.setTenantId(TENANT_ID);
		order.setStatus("refunding");
		return order;
	}

	private Map<String, Object> pay(long payId, String outTradeNo, String payAmount, Long payUserId) {
		Map<String, Object> pay = new HashMap<>();
		pay.put("pay_id", payId);
		pay.put("out_trade_no", outTradeNo);
		pay.put("pay_amount", new BigDecimal(payAmount));
		pay.put("pay_user_id", payUserId);
		pay.put("pay_channel", "alipay");
		return pay;
	}

	@Test
	@DisplayName("正常退款：支付单匹配 → refund_success + 订单 refunded + 写 refund 流水")
	void execute_ok() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("buyer_returned", "return_refund"));
		when(refundMapper.updateStatus(REFUND_ID, "buyer_returned", "refunding")).thenReturn(1);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(List.of(pay(9, "P1", "1107.00", BUYER_ID)));
		when(payOrderService.refund(eq("P1"), eq(new BigDecimal("1107.00")), any())).thenReturn(true);
		when(refundMapper.updateStatus(REFUND_ID, "refunding", "refund_success")).thenReturn(1);
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(order());
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunded", List.of("refunding"), TENANT_ID)).thenReturn(1);

		service.sellerConfirmReceipt(REFUND_ID, SELLER_ID);

		verify(payOrderService).refund(eq("P1"), eq(new BigDecimal("1107.00")), any());
		verify(refundMapper).updateStatus(REFUND_ID, "refunding", "refund_success");
		verify(orderMapper).updateOrderStatusIfIn(ORDER_ID, "refunded", List.of("refunding"), TENANT_ID);
		verify(flowMapper).insert(eq(ORDER_ID), eq("refund"), eq(new BigDecimal("1107.00")), eq("alipay"), eq("P1"));
		verify(logMapper).insert(eq(REFUND_ID), isNull(), eq("refund_ok"), any());
		verify(imService).sendNotice(eq(BUYER_ID), eq("order"), eq("卖家已确认收到退货"), any());
		verify(imService).sendNotice(eq(BUYER_ID), eq("order"), eq("退款成功"), any());
		ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(funnelEventService).record(captor.capture());
		assertEquals(FunnelEvent.TYPE_REFUND_SUCCESS, captor.getValue().getEventType());
		assertEquals("refund_success:" + REFUND_ID, captor.getValue().getEventKey());
		assertEquals(TENANT_ID, captor.getValue().getTenantId());
	}

	@Test
	@DisplayName("支付单缺失 → refunding + manual_required 日志 + 通知买家，不抛异常")
	void execute_payOrderMissing_parksForManual() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("buyer_returned", "return_refund"));
		when(refundMapper.updateStatus(REFUND_ID, "buyer_returned", "refunding")).thenReturn(1);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(List.of());

		assertDoesNotThrow(() -> service.sellerConfirmReceipt(REFUND_ID, SELLER_ID));

		verify(payOrderService, never()).refund(any(), any(), any());
		verify(refundMapper, never()).updateStatus(eq(REFUND_ID), eq("refunding"), eq("refund_success"));
		verify(logMapper).insert(eq(REFUND_ID), isNull(), eq("manual_required"), any());
		verify(imService).sendNotice(eq(BUYER_ID), eq("order"), eq("卖家已确认收到退货"), any());
		verify(imService).sendNotice(eq(BUYER_ID), eq("order"), eq("退款处理中"), any());
	}

	@Test
	@DisplayName("支付单金额小于退款金额 → 转人工，绝不调网关")
	void execute_amountMismatch_parksForManual() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("buyer_returned", "return_refund"));
		when(refundMapper.updateStatus(REFUND_ID, "buyer_returned", "refunding")).thenReturn(1);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(List.of(pay(9, "P1", "10.00", BUYER_ID)));

		assertDoesNotThrow(() -> service.sellerConfirmReceipt(REFUND_ID, SELLER_ID));

		verify(payOrderService, never()).refund(any(), any(), any());
		verify(logMapper).insert(eq(REFUND_ID), isNull(), eq("manual_required"), any());
	}

	@Test
	@DisplayName("多态外键撞号：跑腿单与商品单同 order_id，必须选 pay_user_id 匹配的那条")
	void execute_polymorphicCollision_picksCorrectPay() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("buyer_returned", "return_refund"));
		when(refundMapper.updateStatus(REFUND_ID, "buyer_returned", "refunding")).thenReturn(1);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID))
			.thenReturn(List.of(pay(2, "P-RUN", "1.50", 201L), pay(9, "P1", "1107.00", BUYER_ID)));
		when(payOrderService.refund(eq("P1"), any(), any())).thenReturn(true);
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(order());

		service.sellerConfirmReceipt(REFUND_ID, SELLER_ID);

		verify(payOrderService).refund(eq("P1"), any(), any());
		verify(payOrderService, never()).refund(eq("P-RUN"), any(), any());
	}

	@Test
	@DisplayName("pay_user_id 全 NULL：候选唯一则执行，候选多条则转人工")
	void execute_ambiguousLegacyPay() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("buyer_returned", "return_refund"),
				refund("buyer_returned", "return_refund"));
		when(refundMapper.updateStatus(REFUND_ID, "buyer_returned", "refunding")).thenReturn(1, 1);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(
				List.of(pay(9, "P1", "1107.00", null)),
				List.of(pay(9, "P1", "1107.00", null), pay(10, "P2", "1107.00", null)));
		when(payOrderService.refund(eq("P1"), any(), any())).thenReturn(true);
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(order());

		// 候选唯一且 pay_user_id 为 NULL → 允许执行
		service.sellerConfirmReceipt(REFUND_ID, SELLER_ID);
		// 候选两条且 pay_user_id 均为 NULL → 宁可不退，转人工
		service.sellerConfirmReceipt(REFUND_ID, SELLER_ID);

		verify(payOrderService, times(1)).refund(any(), any(), any());
		verify(logMapper).insert(eq(REFUND_ID), isNull(), eq("manual_required"), any());
	}

	@Test
	@DisplayName("网关返回 false → refunding + manual_required 日志，不抛")
	void execute_gatewayFail_parksForManual() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("buyer_returned", "return_refund"));
		when(refundMapper.updateStatus(REFUND_ID, "buyer_returned", "refunding")).thenReturn(1);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(List.of(pay(9, "P1", "1107.00", BUYER_ID)));
		when(payOrderService.refund(eq("P1"), any(), any())).thenReturn(false);

		assertDoesNotThrow(() -> service.sellerConfirmReceipt(REFUND_ID, SELLER_ID));

		verify(refundMapper, never()).updateStatus(eq(REFUND_ID), eq("refunding"), eq("refund_success"));
		verify(logMapper).insert(eq(REFUND_ID), isNull(), eq("manual_required"), any());
		org.mockito.Mockito.verify(funnelEventService, never())
			.record(org.mockito.ArgumentMatchers.argThat(
					e -> e != null && FunnelEvent.TYPE_REFUND_SUCCESS.equals(e.getEventType())));
	}

	@Test
	@DisplayName("流水写入抛异常不影响退款成功")
	void execute_flowFailureDoesNotBreak() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("buyer_returned", "return_refund"));
		when(refundMapper.updateStatus(REFUND_ID, "buyer_returned", "refunding")).thenReturn(1);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(List.of(pay(9, "P1", "1107.00", BUYER_ID)));
		when(payOrderService.refund(eq("P1"), any(), any())).thenReturn(true);
		when(refundMapper.updateStatus(REFUND_ID, "refunding", "refund_success")).thenReturn(1);
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(order());
		when(flowMapper.insert(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("db down"));

		assertDoesNotThrow(() -> service.sellerConfirmReceipt(REFUND_ID, SELLER_ID));

		verify(refundMapper).updateStatus(REFUND_ID, "refunding", "refund_success");
		verify(logMapper).insert(eq(REFUND_ID), isNull(), eq("refund_ok"), any());
	}

	@Test
	@DisplayName("卖家确认收货（buyer_returned）→ 触发 executeRefund")
	void sellerConfirmReceipt_fromBuyerReturned() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("buyer_returned", "return_refund"));
		when(refundMapper.updateStatus(REFUND_ID, "buyer_returned", "refunding")).thenReturn(1);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(List.of(pay(9, "P1", "1107.00", BUYER_ID)));
		when(payOrderService.refund(eq("P1"), any(), any())).thenReturn(true);
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(order());

		service.sellerConfirmReceipt(REFUND_ID, SELLER_ID);

		verify(refundMapper).updateStatus(REFUND_ID, "buyer_returned", "refunding");
		verify(logMapper).insert(eq(REFUND_ID), eq(SELLER_ID), eq("seller_confirm"), any());
		verify(payOrderService).refund(eq("P1"), any(), any());
	}

	@Test
	@DisplayName("仅退款卖家同意 → seller_agreed 紧接 refunding 并执行退款（跳过 buyer_returned）")
	void sellerAgree_refundOnly_thenExecutesRefund() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("pending_seller", "refund_only"));
		when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "seller_agreed")).thenReturn(1);
		when(refundMapper.updateStatus(REFUND_ID, "seller_agreed", "refunding")).thenReturn(1);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(List.of(pay(9, "P1", "1107.00", BUYER_ID)));
		when(payOrderService.refund(eq("P1"), any(), any())).thenReturn(true);
		when(refundMapper.updateStatus(REFUND_ID, "refunding", "refund_success")).thenReturn(1);
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(order());

		service.sellerAgree(REFUND_ID, null, SELLER_ID);

		verify(refundMapper).updateStatus(REFUND_ID, "seller_agreed", "refunding");
		verify(payOrderService).refund(eq("P1"), eq(new BigDecimal("1107.00")), any());
		verify(refundMapper).updateStatus(REFUND_ID, "refunding", "refund_success");
	}

}