package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.product.api.domain.FunnelEvent;
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
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 申请退款闸门测试
 * <p>
 * 锁定不变量：状态闸门（unshipped/paid 仅退款、shipped/done 两种、其余拒绝）、done 的 7 天窗口 （done_at 为 NULL 回退
 * update_time，两者都 NULL 拒绝且不 NPE）、金额上限、原因与类型匹配、 进行中唯一、订单冻结为 refunding（UPDATE 0
 * 行视为并发变更）、唯一索引冲突翻译。
 * </p>
 */
class TradeRefundServiceImplApplyTest {

	private static final Long BUYER_ID = 101L;

	private static final Long SELLER_ID = 202L;

	private static final Long ORDER_ID = 1L;

	private static final Long PRODUCT_ID = 555L;

	private static final Long TENANT_ID = 42L;

	private static final Long REFUND_ID = 9L;

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

	private TradeOrder order(String status, BigDecimal total, LocalDateTime doneAt, LocalDateTime updateTime) {
		TradeOrder o = new TradeOrder();
		o.setOrderId(ORDER_ID);
		o.setBuyerId(BUYER_ID);
		o.setSellerId(SELLER_ID);
		o.setProductId(PRODUCT_ID);
		o.setTenantId(TENANT_ID);
		o.setStatus(status);
		o.setTotalAmount(total);
		o.setDoneAt(doneAt);
		o.setUpdateTime(updateTime);
		return o;
	}

	@Test
	@DisplayName("done 订单超 7 天被拒")
	void apply_doneOver7Days_rejected() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("done", new BigDecimal("1107.00"), LocalDateTime.now().minusDays(8), null));
		TWTException e = assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "other",
				new BigDecimal("1107.00"), null, null, BUYER_ID));
		assertEquals("已超过 7 天售后期限", e.getMessage());
	}

	@Test
	@DisplayName("done_at 为 NULL 时回退 update_time；两者都 NULL 则拒绝，不得 NPE")
	void apply_doneAtNull() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("done", new BigDecimal("1107.00"), null, LocalDateTime.now().minusDays(1)));
		assertTrue((Boolean) service.checkApply(ORDER_ID, BUYER_ID).get("canApply"));

		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("done", new BigDecimal("1107.00"), null, null));
		assertEquals("已超过 7 天售后期限", service.checkApply(ORDER_ID, BUYER_ID).get("denyReason"));
	}

	@Test
	@DisplayName("unpaid 订单不可申请")
	void apply_unpaidRejected() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("unpaid", new BigDecimal("1107.00"), null, null));
		TWTException e = assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "other",
				new BigDecimal("1107.00"), null, null, BUYER_ID));
		assertEquals("该订单当前状态不支持申请退款", e.getMessage());
	}

	@Test
	@DisplayName("paid 存量野状态可申请（shipOrder 仍接受它）")
	void apply_legacyPaidAllowed() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("paid", new BigDecimal("1107.00"), null, null));
		assertTrue((Boolean) service.checkApply(ORDER_ID, BUYER_ID).get("canApply"));
	}

	@Test
	@DisplayName("退款金额超过实付被拒，为 0 也被拒")
	void apply_amountBounds() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("unshipped", new BigDecimal("1107.00"), null, null));
		TWTException over = assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "other",
				new BigDecimal("1108.00"), null, null, BUYER_ID));
		assertEquals("退款金额不能超过实付 ¥1107.00", over.getMessage());
		assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "other", BigDecimal.ZERO, null,
				null, BUYER_ID));
	}

	@Test
	@DisplayName("原因与类型不匹配被拒（not_received 不能用于退货退款）")
	void apply_reasonTypeMismatch() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		assertEquals("该退款原因不适用于退货退款", assertThrows(TWTException.class,
				() -> service.apply(ORDER_ID, "return_refund", "not_received", new BigDecimal("1107.00"), null, null,
						BUYER_ID))
			.getMessage());
	}

	@Test
	@DisplayName("已有进行中退款单被拒")
	void apply_activeRefundExists_rejected() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(refundMapper.countActiveByOrderId(ORDER_ID)).thenReturn(1L);
		TWTException e = assertThrows(TWTException.class, () -> service.apply(ORDER_ID, "refund_only", "damaged",
				new BigDecimal("1107.00"), null, null, BUYER_ID));
		assertEquals("该订单已有处理中的退款申请", e.getMessage());
	}

	@Test
	@DisplayName("申请成功：置 pending_seller + 订单冻结为 refunding + 记 prev_order_status + 写 apply 日志")
	void apply_ok_freezesOrder() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(refundMapper.countActiveByOrderId(ORDER_ID)).thenReturn(0L);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunding", List.of("shipped"), TENANT_ID)).thenReturn(1);
		ArgumentCaptor<TradeRefund> captor = ArgumentCaptor.forClass(TradeRefund.class);

		service.apply(ORDER_ID, "refund_only", "damaged", new BigDecimal("1107.00"), "屏幕有裂痕",
				List.of("https://oss/a.jpg"), BUYER_ID);

		verify(refundMapper).insert(captor.capture());
		TradeRefund saved = captor.getValue();
		assertEquals("pending_seller", saved.getStatus());
		assertEquals("shipped", saved.getPrevOrderStatus());
		assertEquals("refund_only", saved.getType());
		assertEquals(BUYER_ID, saved.getBuyerId());
		assertEquals(SELLER_ID, saved.getSellerId());
		verify(logMapper).insert(any(), eq(BUYER_ID), eq("apply"), any());
	}

	@Test
	@DisplayName("订单状态在申请瞬间被并发改动（UPDATE 0 行）→ 抛「订单状态已变更」且不落退款单")
	void apply_orderChangedConcurrently() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunding", List.of("shipped"), TENANT_ID)).thenReturn(0);
		assertEquals("订单状态已变更，请刷新后重试", assertThrows(TWTException.class,
				() -> service.apply(ORDER_ID, "refund_only", "damaged", new BigDecimal("1107.00"), null, null, BUYER_ID))
			.getMessage());
		verify(refundMapper, never()).insert(any());
	}

	@Test
	@DisplayName("并发重复申请：唯一索引冲突翻译为可读提示")
	void apply_duplicateKey_translated() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(orderMapper.updateOrderStatusIfIn(any(), any(), any(), any())).thenReturn(1);
		when(refundMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_order_active"));
		assertEquals("该订单已有处理中的退款申请", assertThrows(TWTException.class,
				() -> service.apply(ORDER_ID, "refund_only", "damaged", new BigDecimal("1107.00"), null, null, BUYER_ID))
			.getMessage());
	}

	@Test
	@DisplayName("非本人订单：查询自带 buyerId 过滤，查不到即拒")
	void apply_notOwner() {
		assertEquals("订单不存在或不属于当前用户",
				assertThrows(TWTException.class,
						() -> service.apply(ORDER_ID, "refund_only", "other", BigDecimal.ONE, null, null, 999L))
					.getMessage());
	}

	@Test
	@DisplayName("订单已被退款冻结且存在进行中退款单时，checkApply 带出 activeRefundId")
	void checkApply_frozenOrderWithActiveRefund_returnsActiveRefundId() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("refunding", new BigDecimal("1107.00"), null, null));
		TradeRefund active = new TradeRefund();
		active.setRefundId(9L);
		active.setOrderId(ORDER_ID);
		when(refundMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(active);

		Map<String, Object> check = service.checkApply(ORDER_ID, BUYER_ID);

		assertEquals(false, check.get("canApply"));
		assertEquals("该订单已有处理中的退款申请", check.get("denyReason"));
		assertEquals(9L, check.get("activeRefundId"));
	}

	@Test
	@DisplayName("冻结只接受读到的那个状态（并发发货不得被宽白名单吞掉并写错 prev_order_status）")
	void apply_freezesOnlyTheReadStatus() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(refundMapper.countActiveByOrderId(ORDER_ID)).thenReturn(0L);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunding", List.of("shipped"), TENANT_ID)).thenReturn(1);

		service.apply(ORDER_ID, "refund_only", "damaged", new BigDecimal("1107.00"), null, null, BUYER_ID);

		verify(orderMapper).updateOrderStatusIfIn(ORDER_ID, "refunding", List.of("shipped"), TENANT_ID);
	}

	@Test
	@DisplayName("证据图超过 3 张被拒")
	void apply_tooManyEvidenceImages() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		assertEquals("最多上传 3 张证据图", assertThrows(TWTException.class,
				() -> service.apply(ORDER_ID, "refund_only", "damaged", new BigDecimal("1107.00"), null,
						List.of("a", "b", "c", "d"), BUYER_ID))
			.getMessage());
	}

	@Test
	@DisplayName("checkApply 返回订单状态与允许类型：未发货只能仅退款")
	void checkApply_unshipped_returnsAllowedTypes() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("unshipped", new BigDecimal("1107.00"), null, null));
		when(refundMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);

		Map<String, Object> result = service.checkApply(ORDER_ID, BUYER_ID);

		assertEquals("unshipped", result.get("orderStatus"));
		assertEquals(List.of("refund_only"), result.get("allowedTypes"));
	}

	@Test
	@DisplayName("checkApply 返回订单状态与允许类型：已发货两种都允许")
	void checkApply_shipped_returnsBothTypes() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(refundMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);

		Map<String, Object> result = service.checkApply(ORDER_ID, BUYER_ID);

		assertEquals("shipped", result.get("orderStatus"));
		assertEquals(List.of("refund_only", "return_refund"), result.get("allowedTypes"));
	}

	@Test
	@DisplayName("申请退款后通知卖家")
	void apply_notifiesSeller() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(refundMapper.countActiveByOrderId(ORDER_ID)).thenReturn(0L);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunding", List.of("shipped"), TENANT_ID)).thenReturn(1);
		when(refundMapper.insert(any(TradeRefund.class))).thenAnswer(inv -> {
			inv.getArgument(0, TradeRefund.class).setRefundId(REFUND_ID);
			return 1;
		});

		service.apply(ORDER_ID, "refund_only", "not_as_described", new BigDecimal("1107.00"), null, null, BUYER_ID);

		verify(imService).sendNotice(eq(SELLER_ID), eq("order"), eq("新的退款申请"), any());
	}

	@Test
	@DisplayName("申请退款成功写 refund_apply（租户取订单）")
	void apply_recordsRefundApplyEvent() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(refundMapper.countActiveByOrderId(ORDER_ID)).thenReturn(0L);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunding", List.of("shipped"), TENANT_ID)).thenReturn(1);
		when(refundMapper.insert(any(TradeRefund.class))).thenAnswer(inv -> {
			inv.getArgument(0, TradeRefund.class).setRefundId(REFUND_ID);
			return 1;
		});

		service.apply(ORDER_ID, "refund_only", "damaged", new BigDecimal("1107.00"), null, null, BUYER_ID);

		ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(funnelEventService).record(captor.capture());
		assertEquals(FunnelEvent.TYPE_REFUND_APPLY, captor.getValue().getEventType());
		assertEquals("refund_apply:" + REFUND_ID, captor.getValue().getEventKey());
		assertEquals(TENANT_ID, captor.getValue().getTenantId());
		assertEquals(BUYER_ID, captor.getValue().getUserId());
		assertEquals(PRODUCT_ID, captor.getValue().getProductId());
	}

	@Test
	@DisplayName("IM 通知失败不影响退款申请落库")
	void apply_imFailure_doesNotRollback() {
		when(orderMapper.selectOrderByIdAndBuyerId(ORDER_ID, BUYER_ID, TENANT_ID))
			.thenReturn(order("shipped", new BigDecimal("1107.00"), null, null));
		when(refundMapper.countActiveByOrderId(ORDER_ID)).thenReturn(0L);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunding", List.of("shipped"), TENANT_ID)).thenReturn(1);
		when(refundMapper.insert(any(TradeRefund.class))).thenAnswer(inv -> {
			inv.getArgument(0, TradeRefund.class).setRefundId(REFUND_ID);
			return 1;
		});
		doThrow(new RuntimeException("im down")).when(imService).sendNotice(any(), any(), any(), any());

		assertDoesNotThrow(() -> service.apply(ORDER_ID, "refund_only", "not_as_described",
				new BigDecimal("1107.00"), null, null, BUYER_ID));
		verify(refundMapper).insert(any(TradeRefund.class));
	}

}