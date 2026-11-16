package com.twelvet.trade.server.task;

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
import com.twelvet.trade.server.service.impl.TradeRefundServiceImpl;
import com.twelvet.trade.server.service.impl.TradeRefundTimeoutHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款超时扫描任务测试
 * <p>
 * 锁定不变量：
 * <ol>
 * <li>pending_seller 48h → mediation（平台介入）；seller_agreed 7d → closed 并恢复订单；buyer_returned
 * 7d → mediation</li>
 * <li>单条失败不中断整批（逐条 try/catch）</li>
 * <li>Quartz 无租户上下文，一律传 tenantId=null（全租户扫描）</li>
 * </ol>
 * </p>
 */
class TradeRefundScanTaskTest {

	private static final Long REFUND_ID = 9L;

	private static final Long ORDER_ID = 1L;

	private static final Long BUYER_ID = 101L;

	private static final Long SELLER_ID = 202L;

	private static final Long TENANT_ID = 42L;

	private static final Long PRODUCT_ID = 555L;

	private final TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);

	private final TradeRefundMapper refundMapper = mock(TradeRefundMapper.class);

	private final TradeRefundLogMapper logMapper = mock(TradeRefundLogMapper.class);

	private final TradePayLookupMapper payLookupMapper = mock(TradePayLookupMapper.class);

	private final IPayOrderService payOrderService = mock(IPayOrderService.class);

	private final TradePayFlowMapper flowMapper = mock(TradePayFlowMapper.class);

	private final IImService imService = mock(IImService.class);

	private final ProductMapper productMapper = mock(ProductMapper.class);

	private final TradeRefundTimeoutHelper timeoutHelper = mock(TradeRefundTimeoutHelper.class);

	private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);

	private final TradeRefundServiceImpl service = new TradeRefundServiceImpl(orderMapper, refundMapper, logMapper,
			payLookupMapper, payOrderService, flowMapper, imService, productMapper, timeoutHelper, funnelEventService,
			mock(ISettlementService.class));

	private final TradeRefundScanTask task = new TradeRefundScanTask(service);

	@BeforeEach
	void runHelperTransactionInline() {
		when(timeoutHelper.inTransaction(any())).thenAnswer(invocation -> {
			Supplier<Integer> action = invocation.getArgument(0);
			return action.get();
		});
		// 自动同意的「认领」走 REQUIRES_NEW 独立事务；单测里同样内联执行
		when(timeoutHelper.inNewTransaction(any())).thenAnswer(invocation -> {
			Supplier<Integer> action = invocation.getArgument(0);
			return action.get();
		});
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private TradeRefund refund(long refundId, String status) {
		TradeRefund refund = new TradeRefund();
		refund.setRefundId(refundId);
		refund.setOrderId(ORDER_ID);
		refund.setBuyerId(BUYER_ID);
		refund.setSellerId(SELLER_ID);
		refund.setType("refund_only");
		refund.setReasonCode("no_longer_needed");
		refund.setAmount(new BigDecimal("1107.00"));
		refund.setStatus(status);
		refund.setPrevOrderStatus("shipped");
		return refund;
	}

	private TradeOrder orderForSettle() {
		TradeOrder order = new TradeOrder();
		order.setOrderId(ORDER_ID);
		order.setProductId(PRODUCT_ID);
		order.setStatus("refunding");
		return order;
	}

	@Test
	@DisplayName("pending_seller 超时 → mediation（订单保持 refunding）")
	void scan_pendingSeller_escalates() {
		when(refundMapper.selectSellerTimeout(any(), anyInt())).thenReturn(List.of(refund(REFUND_ID, "pending_seller")));
		when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "mediation")).thenReturn(1);

		task.scan();

		verify(refundMapper).updateStatus(REFUND_ID, "pending_seller", "mediation");
		verify(logMapper).insert(eq(REFUND_ID), isNull(), eq("mediate"), any());
		verify(orderMapper, never()).updateOrderStatusIfIn(any(), any(), any(), any());
	}

	@Test
	@DisplayName("seller_agreed 超时 → closed + 恢复订单")
	void scan_sellerAgreed_closes() {
		when(refundMapper.selectReturnTimeout(any(), anyInt())).thenReturn(List.of(refund(REFUND_ID, "seller_agreed")));
		when(refundMapper.updateStatus(REFUND_ID, "seller_agreed", "closed")).thenReturn(1);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), null)).thenReturn(1);

		assertEquals(1, service.scanTimeouts());

		verify(refundMapper).selectReturnTimeout(any(LocalDateTime.class), eq(200));
		verify(orderMapper).updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), null);
		verify(logMapper).insert(eq(REFUND_ID), isNull(), eq("timeout_close"), any());
	}

	@Test
	@DisplayName("buyer_returned 超时 → mediation")
	void scan_buyerReturned_escalates() {
		when(refundMapper.selectConfirmTimeout(any(), anyInt())).thenReturn(List.of(refund(REFUND_ID, "buyer_returned")));
		when(refundMapper.updateStatus(REFUND_ID, "buyer_returned", "mediation")).thenReturn(1);

		assertEquals(1, service.scanTimeouts());

		verify(refundMapper).updateStatus(REFUND_ID, "buyer_returned", "mediation");
		verify(logMapper).insert(eq(REFUND_ID), isNull(), eq("mediate"), any());
	}

	@Test
	@DisplayName("单条失败不影响其余")
	void scan_oneFailureDoesNotStopOthers() {
		when(refundMapper.selectSellerTimeout(any(), anyInt()))
			.thenReturn(List.of(refund(9L, "pending_seller"), refund(10L, "pending_seller")));
		when(refundMapper.updateStatus(9L, "pending_seller", "mediation")).thenReturn(0);
		when(refundMapper.updateStatus(10L, "pending_seller", "mediation")).thenReturn(1);

		assertEquals(1, service.scanTimeouts());

		verify(refundMapper).updateStatus(9L, "pending_seller", "mediation");
		verify(refundMapper).updateStatus(10L, "pending_seller", "mediation");
	}

	@Test
	@DisplayName("扫描任务无租户上下文，一律传 tenantId=null")
	void scan_usesNullTenant() {
		// 即使线程里残留了租户上下文（如测试/误用），Quartz 语义也必须是全租户扫描
		TenantContext.set(TENANT_ID);
		when(refundMapper.selectReturnTimeout(any(), anyInt())).thenReturn(List.of(refund(REFUND_ID, "seller_agreed")));
		when(refundMapper.updateStatus(REFUND_ID, "seller_agreed", "closed")).thenReturn(1);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), null)).thenReturn(1);

		service.scanTimeouts();

		verify(orderMapper).updateOrderStatusIfIn(eq(ORDER_ID), eq("shipped"), eq(List.of("refunding")), isNull());
	}

	@Test
	@DisplayName("单条抛异常不阻断后续，且异常记录不计数")
	void scan_oneExceptionDoesNotStopOthers() {
		when(refundMapper.selectSellerTimeout(any(), anyInt()))
			.thenReturn(List.of(refund(9L, "pending_seller"), refund(10L, "pending_seller")));
		when(refundMapper.updateStatus(9L, "pending_seller", "mediation")).thenThrow(new RuntimeException("db down"));
		when(refundMapper.updateStatus(10L, "pending_seller", "mediation")).thenReturn(1);

		assertEquals(1, service.scanTimeouts());

		verify(refundMapper).updateStatus(9L, "pending_seller", "mediation");
		verify(refundMapper).updateStatus(10L, "pending_seller", "mediation");
		verify(logMapper).insert(eq(10L), isNull(), eq("mediate"), any());
		// 每条记录都必须经过事务边界：抛异常的那条由 runOne 捕获，另一条仍独立提交
		verify(timeoutHelper, times(2)).inTransaction(any());
	}

	/**
	 * 自动同意场景的公共桩：待卖家处理 + 未发货 + 仅退款，支付单可退
	 */
	private void stubAutoApprovable(boolean gatewayResult) {
		TradeRefund refund = refund(REFUND_ID, "pending_seller");
		refund.setPrevOrderStatus("unshipped");
		when(refundMapper.selectSellerTimeout(any(), anyInt())).thenReturn(List.of(refund));
		when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "refunding")).thenReturn(1);
		when(refundMapper.updateStatus(REFUND_ID, "refunding", "refund_success")).thenReturn(1);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(List
			.of(Map.of("pay_amount", new BigDecimal("1107.00"), "out_trade_no", "PAY1", "pay_channel", "alipay")));
		when(payOrderService.refund(eq("PAY1"), eq(new BigDecimal("1107.00")), any())).thenReturn(gatewayResult);
		when(orderMapper.selectOrderDetail(ORDER_ID, null)).thenReturn(orderForSettle());
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunded", List.of("refunding"), null)).thenReturn(1);
	}

	@Test
	@DisplayName("未发货 + 仅退款超时 → 认领后执行退款：网关、状态、订单、双方通知全部发生")
	void scan_pendingSeller_unshippedRefundOnly_autoApproves() {
		stubAutoApprovable(true);

		assertEquals(1, service.scanTimeouts());

		// 认领先于网关：先独立事务 pending_seller→refunding，再调退款网关（防 DB 失败回滚后重复退款）
		InOrder order = inOrder(timeoutHelper, payOrderService);
		order.verify(timeoutHelper).inNewTransaction(any());
		order.verify(payOrderService).refund(eq("PAY1"), eq(new BigDecimal("1107.00")), any());
		// 退款真正执行完：refunding→refund_success + 订单 refunded
		verify(refundMapper).updateStatus(REFUND_ID, "refunding", "refund_success");
		verify(orderMapper).updateOrderStatusIfIn(ORDER_ID, "refunded", List.of("refunding"), null);
		verify(refundMapper, never()).updateStatus(REFUND_ID, "pending_seller", "mediation");
		verify(logMapper).insert(eq(REFUND_ID), isNull(), eq("timeout_auto_agree"), any());
		// 只有钱真的退了，才对买家说「已原路退回」
		verify(imService).sendNotice(eq(BUYER_ID), eq("order"), eq("退款已自动同意"), contains("已原路退回"));
		verify(imService).sendNotice(eq(SELLER_ID), eq("order"), eq("退款已自动同意"), contains("自动退款"));
	}

	@Test
	@DisplayName("自动同意但网关失败 → 不得对买家声称已原路退回，转人工处理")
	void scan_autoApprove_gatewayFails_reportsManualNotRefunded() {
		stubAutoApprovable(false);

		assertEquals(1, service.scanTimeouts());

		// 认领已完成，退款单停在 refunding（运营「退款中」可手工收口），不再推进成功
		verify(refundMapper).updateStatus(REFUND_ID, "pending_seller", "refunding");
		verify(refundMapper, never()).updateStatus(REFUND_ID, "refunding", "refund_success");
		verify(orderMapper, never()).updateOrderStatusIfIn(any(), any(), any(), any());
		// 买家只收到 markManualRequired 的「退款处理中」，绝不能出现「已原路退回」的假到账
		verify(imService, never()).sendNotice(eq(BUYER_ID), any(), any(), contains("原路退回"));
		verify(imService).sendNotice(eq(BUYER_ID), any(), eq("退款处理中"), any());
		verify(imService).sendNotice(eq(SELLER_ID), any(), eq("退款已自动同意"), contains("转人工"));
	}

	@Test
	@DisplayName("自动同意认领失败（卖家已抢先处理）→ 不调网关，防重复退款")
	void scan_autoApprove_claimLost_skipsGateway() {
		TradeRefund refund = refund(REFUND_ID, "pending_seller");
		refund.setPrevOrderStatus("unshipped");
		when(refundMapper.selectSellerTimeout(any(), anyInt())).thenReturn(List.of(refund));
		when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "refunding")).thenReturn(0);

		assertEquals(0, service.scanTimeouts());

		verify(payOrderService, never()).refund(any(), any(), any());
		verify(imService, never()).sendNotice(any(), any(), any(), any());
	}

	@Test
	@DisplayName("已发货 + 仅退款超时 → 仍然转平台介入，不自动退款")
	void scan_pendingSeller_shipped_escalates() {
		TradeRefund refund = refund(REFUND_ID, "pending_seller");
		refund.setPrevOrderStatus("shipped");
		when(refundMapper.selectSellerTimeout(any(), anyInt())).thenReturn(List.of(refund));
		when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "mediation")).thenReturn(1);

		assertEquals(1, service.scanTimeouts());

		verify(refundMapper).updateStatus(REFUND_ID, "pending_seller", "mediation");
		verify(refundMapper, never()).updateStatus(REFUND_ID, "pending_seller", "refunding");
	}

}