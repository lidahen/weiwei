package com.twelvet.trade.server.listener;

import com.twelvet.im.server.service.IImService;
import com.twelvet.payment.api.event.PaySuccessEvent;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.domain.SettlementOrder;
import com.twelvet.trade.server.mapper.SellerAccountMapper;
import com.twelvet.trade.server.mapper.SettlementOrderMapper;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import com.twelvet.trade.server.mapper.TradePayFlowMapper;
import com.twelvet.trade.server.service.ISellerAccountService;
import com.twelvet.trade.server.service.impl.SettlementServiceImpl;
import com.twelvet.trade.server.settlement.PayoutGateway;
import com.twelvet.trade.server.settlement.SettlementPayoutHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付成功创建结算单测试
 * <p>
 * 锁定的不变量：支付回调只有一次成功流转会创建结算单；租户取订单（回调无租户上下文）；重复回调靠 uk_order_id 幂等； 订单不存在或状态已流转不写结算单。
 * </p>
 */
class SettlementCreateOnPayTest {

	private static final Long ORDER_ID = 9L;

	private final TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);

	private final TradePayFlowMapper flowMapper = mock(TradePayFlowMapper.class);

	private final IImService imService = mock(IImService.class);

	private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);

	private final SettlementOrderMapper settlementMapper = mock(SettlementOrderMapper.class);

	private final PayoutGateway payoutGateway = mock(PayoutGateway.class);

	private final SettlementPayoutHelper payoutHelper = mock(SettlementPayoutHelper.class);

	private final SellerAccountMapper sellerAccountMapper = mock(SellerAccountMapper.class);

	private final ISellerAccountService sellerAccountService = mock(ISellerAccountService.class);

	private final SettlementServiceImpl settlementService = new SettlementServiceImpl(settlementMapper, payoutGateway,
			payoutHelper, sellerAccountMapper, sellerAccountService);

	private final PaySuccessEventListener listener = new PaySuccessEventListener(orderMapper, flowMapper, imService,
			funnelEventService, mock(IPayOrderService.class), settlementService);

	private PaySuccessEvent event() {
		return new PaySuccessEvent("OUT123", ORDER_ID, "trade", "alipay", 10000L, LocalDateTime.now(), "TRADE123", "{}");
	}

	private TradeOrder order() {
		TradeOrder order = new TradeOrder();
		order.setOrderId(ORDER_ID);
		order.setTenantId(42L);
		order.setSellerId(7L);
		order.setBuyerId(8L);
		order.setProductId(5L);
		order.setTotalAmount(new BigDecimal("100.00"));
		return order;
	}

	@Test
	@DisplayName("支付成功：创建 holding 结算单，佣金按 0 记账，租户取订单")
	void paySuccess_createsHoldingSettlement() {
		when(orderMapper.updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null)).thenReturn(1);
		when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(order());
		when(settlementMapper.insertIgnore(any(SettlementOrder.class))).thenReturn(1);

		listener.onPaySuccess(event());

		ArgumentCaptor<SettlementOrder> captor = ArgumentCaptor.forClass(SettlementOrder.class);
		verify(settlementMapper).insertIgnore(captor.capture());
		SettlementOrder created = captor.getValue();
		assertEquals("holding", created.getStatus());
		assertEquals(new BigDecimal("100.00"), created.getGrossAmount());
		assertEquals(new BigDecimal("0.00"), created.getCommissionAmount());
		assertEquals(new BigDecimal("100.00"), created.getNetAmount());
		assertEquals(42L, created.getTenantId());
		assertEquals(7L, created.getSellerId());
		assertEquals(8L, created.getBuyerId());
		assertEquals("alipay", created.getChannel());
	}

	@Test
	@DisplayName("重复支付回调：唯一键命中不抛异常、不产生第二条")
	void paySuccess_duplicateCallback_insertsIgnored() {
		when(orderMapper.updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null)).thenReturn(1);
		when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(order());
		when(settlementMapper.insertIgnore(any(SettlementOrder.class))).thenReturn(0);

		assertDoesNotThrow(() -> listener.onPaySuccess(event()));
		verify(settlementMapper).insertIgnore(any(SettlementOrder.class));
	}

	@Test
	@DisplayName("订单状态未流转（重复回调/已支付）：不创建结算单")
	void paySuccess_rowsZero_noSettlement() {
		when(orderMapper.updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null)).thenReturn(0);

		listener.onPaySuccess(event());

		verify(settlementMapper, never()).insertIgnore(any());
	}

	@Test
	@DisplayName("订单查不到：不创建结算单、不 NPE")
	void paySuccess_nullOrder_noSettlement() {
		when(orderMapper.updateOrderStatus(ORDER_ID, "unshipped", "paid_at", null)).thenReturn(1);
		when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(null);

		assertDoesNotThrow(() -> listener.onPaySuccess(event()));
		verify(settlementMapper, never()).insertIgnore(any());
	}

}