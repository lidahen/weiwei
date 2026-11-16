package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.mapper.ProductMapper;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客服裁决与运营手工收口测试
 * <p>
 * 锁定不变量：
 * <ol>
 * <li>judge 只对 mediation 生效；buyer 全额退、partial 按传入金额退、seller 关闭并恢复订单</li>
 * <li>judgeByOrder 按 order_id 反查（不依赖 trade_dispute.refund_id），找不到返回 false 不抛</li>
 * <li>resolve 是运营手工收口：success 不调网关直接收尾，fail 关闭并恢复订单</li>
 * <li>所有决策/结果取值走白名单</li>
 * </ol>
 * </p>
 */
class TradeRefundServiceImplJudgeTest {

	private static final Long REFUND_ID = 9L;

	private static final Long ORDER_ID = 1L;

	private static final Long BUYER_ID = 101L;

	private static final Long SELLER_ID = 202L;

	private static final Long OPERATOR_ID = 900L;

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

	private TradeRefund refund(String status) {
		TradeRefund refund = new TradeRefund();
		refund.setRefundId(REFUND_ID);
		refund.setOrderId(ORDER_ID);
		refund.setBuyerId(BUYER_ID);
		refund.setSellerId(SELLER_ID);
		refund.setType("return_refund");
		refund.setReasonCode("damaged");
		refund.setAmount(new BigDecimal("1107.00"));
		refund.setStatus(status);
		refund.setPrevOrderStatus("shipped");
		return refund;
	}

	private Map<String, Object> paidPay() {
		Map<String, Object> pay = new HashMap<>();
		pay.put("pay_id", 9L);
		pay.put("out_trade_no", "P1");
		pay.put("pay_amount", new BigDecimal("1107.00"));
		pay.put("pay_user_id", BUYER_ID);
		pay.put("pay_channel", "alipay");
		return pay;
	}

	private void stubRefundSuccessTail() {
		TradeOrder order = new TradeOrder();
		order.setOrderId(ORDER_ID);
		order.setProductId(PRODUCT_ID);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(List.of(paidPay()));
		when(payOrderService.refund(eq("P1"), any(), any())).thenReturn(true);
		when(refundMapper.updateStatus(REFUND_ID, "refunding", "refund_success")).thenReturn(1);
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(order);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunded", List.of("refunding"), TENANT_ID)).thenReturn(1);
	}

	@Test
	@DisplayName("裁决 buyer → 按退款单金额全额退")
	void judge_buyer_fullRefund() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("mediation"));
		when(refundMapper.updateStatus(REFUND_ID, "mediation", "refunding")).thenReturn(1);
		stubRefundSuccessTail();

		service.judge(REFUND_ID, "buyer", null, "买家举证充分", OPERATOR_ID);

		verify(payOrderService).refund(eq("P1"), eq(new BigDecimal("1107.00")), any());
		verify(refundMapper).updateStatus(REFUND_ID, "refunding", "refund_success");
		verify(logMapper).insert(eq(REFUND_ID), eq(OPERATOR_ID), eq("judge"), eq("买家举证充分"));
	}

	@Test
	@DisplayName("裁决 partial → 按传入 amount 退；amount 缺失或超申请额被拒")
	void judge_partial_usesAmount() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("mediation"));

		assertEquals("部分退款金额需大于 0 且不超过申请金额", assertThrows(TWTException.class,
				() -> service.judge(REFUND_ID, "partial", null, "各让一步", OPERATOR_ID)).getMessage());
		assertEquals("部分退款金额需大于 0 且不超过申请金额", assertThrows(TWTException.class,
				() -> service.judge(REFUND_ID, "partial", new BigDecimal("1107.01"), "各让一步", OPERATOR_ID))
			.getMessage());

		when(refundMapper.updateStatus(REFUND_ID, "mediation", "refunding")).thenReturn(1);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(List.of(paidPay()));
		when(payOrderService.refund(eq("P1"), eq(new BigDecimal("500.00")), any())).thenReturn(true);
		when(refundMapper.updateStatus(REFUND_ID, "refunding", "refund_success")).thenReturn(1);
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(new TradeOrder());
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunded", List.of("refunding"), TENANT_ID)).thenReturn(1);

		service.judge(REFUND_ID, "partial", new BigDecimal("500.00"), "各让一步", OPERATOR_ID);

		verify(payOrderService).refund(eq("P1"), eq(new BigDecimal("500.00")), any());
	}

	@Test
	@DisplayName("裁决 seller → closed + 订单恢复 prev_order_status，不调网关")
	void judge_seller_closesAndRestores() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("mediation"));
		when(refundMapper.updateStatus(REFUND_ID, "mediation", "closed")).thenReturn(1);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), TENANT_ID)).thenReturn(1);

		service.judge(REFUND_ID, "seller", null, "买家证据不足", OPERATOR_ID);

		verify(refundMapper).updateStatus(REFUND_ID, "mediation", "closed");
		verify(orderMapper).updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), TENANT_ID);
		verify(payOrderService, never()).refund(any(), any(), any());
		verify(logMapper).insert(eq(REFUND_ID), eq(OPERATOR_ID), eq("judge"), eq("买家证据不足"));
	}

	@Test
	@DisplayName("裁决只对 mediation 生效")
	void judge_wrongState_rejected() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("mediation"));
		when(refundMapper.updateStatus(REFUND_ID, "mediation", "refunding")).thenReturn(0);

		assertEquals("退款状态已变更，请刷新", assertThrows(TWTException.class,
				() -> service.judge(REFUND_ID, "buyer", null, "理由", OPERATOR_ID)).getMessage());
	}

	@Test
	@DisplayName("decision 白名单 buyer/seller/partial，其它值被拒")
	void judge_unknownDecision_rejected() {
		assertEquals("裁决结论不合法",
				assertThrows(TWTException.class, () -> service.judge(REFUND_ID, "bogus", null, "理由", OPERATOR_ID))
					.getMessage());
		verify(refundMapper, never()).selectById(any(), any());
	}

	@Test
	@DisplayName("judgeByOrder 找到 mediation 退款单则处理并返回 true")
	void judgeByOrder_found() {
		when(refundMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(refund("mediation"));
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("mediation"));
		when(refundMapper.updateStatus(REFUND_ID, "mediation", "refunding")).thenReturn(1);
		stubRefundSuccessTail();

		assertTrue(service.judgeByOrder(ORDER_ID, "buyer", null, "纠纷裁决：退款给买家", OPERATOR_ID));
		verify(payOrderService).refund(eq("P1"), any(), any());
	}

	@Test
	@DisplayName("judgeByOrder 找不到退款单返回 false，不抛（可能是不走退款的纠纷）")
	void judgeByOrder_notFound_returnsFalse() {
		when(refundMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);
		assertFalse(service.judgeByOrder(ORDER_ID, "buyer", null, "理由", OPERATOR_ID));

		when(refundMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(refund("refunding"));
		assertFalse(service.judgeByOrder(ORDER_ID, "buyer", null, "理由", OPERATOR_ID));
		verify(refundMapper, never()).updateStatus(any(), any(), any());
	}

	@Test
	@DisplayName("运营手工收口 refunding → refund_success（不调网关）")
	void resolve_success() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("refunding"));
		when(refundMapper.updateStatus(REFUND_ID, "refunding", "refund_success")).thenReturn(1);
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(new TradeOrder());

		service.resolve(REFUND_ID, "success", "线下已退款（支付宝转账）", OPERATOR_ID);

		verify(payOrderService, never()).refund(any(), any(), any());
		verify(refundMapper).updateStatus(REFUND_ID, "refunding", "refund_success");
		verify(logMapper).insert(eq(REFUND_ID), eq(OPERATOR_ID), eq("manual_resolved"),
				eq("线下已退款（支付宝转账）"));
	}

	@Test
	@DisplayName("运营手工收口 refunding → closed 并恢复订单")
	void resolve_fail() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("refunding"));
		when(refundMapper.updateStatus(REFUND_ID, "refunding", "closed")).thenReturn(1);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), TENANT_ID)).thenReturn(1);

		service.resolve(REFUND_ID, "fail", "买家放弃退款", OPERATOR_ID);

		verify(refundMapper).updateStatus(REFUND_ID, "refunding", "closed");
		verify(orderMapper).updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), TENANT_ID);
		verify(payOrderService, never()).refund(any(), any(), any());
	}

	@Test
	@DisplayName("手工收口只对 refunding 生效")
	void resolve_wrongState_rejected() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("refunding"));
		when(refundMapper.updateStatus(REFUND_ID, "refunding", "refund_success")).thenReturn(0);

		assertEquals("退款状态已变更，请刷新", assertThrows(TWTException.class,
				() -> service.resolve(REFUND_ID, "success", "已处理", OPERATOR_ID)).getMessage());
		assertEquals("收口结果不合法", assertThrows(TWTException.class,
				() -> service.resolve(REFUND_ID, "bogus", "已处理", OPERATOR_ID)).getMessage());
	}

	@Test
	@DisplayName("裁决支持卖家后通知买卖双方")
	void judge_sellerDecision_notifiesBoth() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("mediation"));
		when(refundMapper.updateStatus(REFUND_ID, "mediation", "closed")).thenReturn(1);

		service.judge(REFUND_ID, "seller", null, "证据不足", 1L);

		verify(imService).sendNotice(eq(BUYER_ID), eq("order"), eq("平台裁决结果"), any());
		verify(imService).sendNotice(eq(SELLER_ID), eq("order"), eq("平台裁决结果"), any());
	}

}