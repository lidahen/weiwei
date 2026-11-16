package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.mapper.ProductMapper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 卖家审核与买家操作流转测试
 * <p>
 * 锁定不变量：每次推进都带前置状态守卫（UPDATE 0 行 → 「退款状态已变更」而非静默成功）； 归属校验；退货退款必须提供退货地址；拒绝/撤销按
 * prev_order_status 恢复订单； 申请介入必须把订单重新冻结为 refunding（否则介入期间买家能确认收货，形成钱货两失）。
 * </p>
 */
class TradeRefundServiceImplFlowTest {

	private static final Long REFUND_ID = 9L;

	private static final Long ORDER_ID = 1L;

	private static final Long BUYER_ID = 101L;

	private static final Long SELLER_ID = 202L;

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
		refund.setStatus(status);
		refund.setAmount(new BigDecimal("1107.00"));
		refund.setPrevOrderStatus("shipped");
		return refund;
	}

	@Test
	@DisplayName("卖家同意仅退款 → seller_agreed，不快照退货地址")
	void sellerAgree_refundOnly() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("pending_seller", "refund_only"));
		when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "seller_agreed")).thenReturn(1);
		// 仅退款分支会紧接推进 seller_agreed → refunding（Task 6）
		when(refundMapper.updateStatus(REFUND_ID, "seller_agreed", "refunding")).thenReturn(1);

		service.sellerAgree(REFUND_ID, null, SELLER_ID);

		verify(refundMapper).updateStatus(REFUND_ID, "pending_seller", "seller_agreed");
		verify(refundMapper, never()).updateReturnAddress(any(), any());
		verify(logMapper).insert(eq(REFUND_ID), eq(SELLER_ID), eq("seller_agree"), any());
	}

	@Test
	@DisplayName("卖家同意退货退款 → seller_agreed 且快照退货地址；地址为空则拒绝")
	void sellerAgree_returnRefund_requiresAddress() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("pending_seller", "return_refund"));

		TWTException e = assertThrows(TWTException.class, () -> service.sellerAgree(REFUND_ID, " ", SELLER_ID));

		assertEquals("请提供退货地址", e.getMessage());
		verify(refundMapper, never()).updateStatus(any(), any(), any());

		when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "seller_agreed")).thenReturn(1);
		service.sellerAgree(REFUND_ID, "北京市朝阳区某路 1 号", SELLER_ID);
		verify(refundMapper).updateReturnAddress(REFUND_ID, "北京市朝阳区某路 1 号");
	}

	@Test
	@DisplayName("卖家拒绝 → seller_rejected + 订单恢复 prev_order_status")
	void sellerReject_restoresOrder() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("pending_seller", "refund_only"));
		when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "seller_rejected")).thenReturn(1);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), TENANT_ID)).thenReturn(1);

		service.sellerReject(REFUND_ID, "商品已使用", SELLER_ID);

		verify(refundMapper).updateStatus(REFUND_ID, "pending_seller", "seller_rejected");
		verify(orderMapper).updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), TENANT_ID);
		verify(logMapper).insert(eq(REFUND_ID), eq(SELLER_ID), eq("seller_reject"), eq("商品已使用"));
	}

	@Test
	@DisplayName("买家填退货单号 → buyer_returned（仅 seller_agreed + return_refund）")
	void buyerReturn_ok() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("seller_agreed", "return_refund"));
		when(refundMapper.updateStatus(REFUND_ID, "seller_agreed", "buyer_returned")).thenReturn(1);

		service.buyerReturn(REFUND_ID, "顺丰", "SF123456", BUYER_ID);

		verify(refundMapper).updateStatus(REFUND_ID, "seller_agreed", "buyer_returned");
		verify(refundMapper).updateReturnLogistics(REFUND_ID, "顺丰", "SF123456");
		verify(logMapper).insert(eq(REFUND_ID), eq(BUYER_ID), eq("buyer_return"), any());
	}

	@Test
	@DisplayName("仅退款类型的退款单不能填退货物流")
	void buyerReturn_refundOnly_rejected() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("seller_agreed", "refund_only"));

		TWTException e = assertThrows(TWTException.class,
				() -> service.buyerReturn(REFUND_ID, "顺丰", "SF123456", BUYER_ID));

		assertEquals("仅退款无需填写退货物流", e.getMessage());
		verify(refundMapper, never()).updateStatus(any(), any(), any());
	}

	@Test
	@DisplayName("买家撤销 → closed + 订单按 prev_order_status 从 refunding 恢复")
	void buyerCancel_restoresOrder() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("pending_seller", "refund_only"));
		when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "closed")).thenReturn(1);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), TENANT_ID)).thenReturn(1);

		service.buyerCancel(REFUND_ID, BUYER_ID);

		verify(refundMapper).updateStatus(REFUND_ID, "pending_seller", "closed");
		verify(orderMapper).updateOrderStatusIfIn(ORDER_ID, "shipped", List.of("refunding"), TENANT_ID);
		verify(logMapper).insert(eq(REFUND_ID), eq(BUYER_ID), eq("buyer_cancel"), any());
	}

	@Test
	@DisplayName("撤销旧的被拒退款单时若有另一条进行中退款，不得解冻订单（否则货拿走 + 钱退回）")
	void buyerCancel_sellerRejected_otherActiveRefund_keepsOrderFrozen() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("seller_rejected", "refund_only"));
		when(refundMapper.updateStatus(REFUND_ID, "seller_rejected", "closed")).thenReturn(1);
		// 同订单存在另一条进行中的退款单（新单已把订单重新冻结，旧单的 active_flag 已是 NULL）
		when(refundMapper.countActiveByOrderId(ORDER_ID)).thenReturn(1L);

		service.buyerCancel(REFUND_ID, BUYER_ID);

		verify(refundMapper).updateStatus(REFUND_ID, "seller_rejected", "closed");
		verify(orderMapper, never()).updateOrderStatusIfIn(any(), any(), any(), any());
	}

	@Test
	@DisplayName("买家申请介入（seller_rejected）→ mediation + 订单重新冻结为 refunding")
	void buyerMediate_fromSellerRejected() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("seller_rejected", "refund_only"));
		when(refundMapper.updateStatus(REFUND_ID, "seller_rejected", "mediation")).thenReturn(1);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunding", List.of("shipped"), TENANT_ID)).thenReturn(1);

		service.buyerMediate(REFUND_ID, BUYER_ID);

		verify(refundMapper).updateStatus(REFUND_ID, "seller_rejected", "mediation");
		verify(orderMapper).updateOrderStatusIfIn(ORDER_ID, "refunding", List.of("shipped"), TENANT_ID);
		verify(logMapper).insert(eq(REFUND_ID), eq(BUYER_ID), eq("mediate"), any());
	}

	@Test
	@DisplayName("非卖家操作被拒")
	void sellerAgree_notOwner_rejected() {
		TradeRefund other = refund("pending_seller", "refund_only");
		other.setSellerId(999L);
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(other);

		TWTException e = assertThrows(TWTException.class, () -> service.sellerAgree(REFUND_ID, null, SELLER_ID));

		assertEquals("无权操作该退款", e.getMessage());
		verify(refundMapper, never()).updateStatus(any(), any(), any());
	}

	@Test
	@DisplayName("状态不匹配（UPDATE 0 行）→ 抛「退款状态已变更，请刷新」")
	void sellerAgree_wrongState_rejected() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("pending_seller", "refund_only"));
		when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "seller_agreed")).thenReturn(0);

		TWTException e = assertThrows(TWTException.class, () -> service.sellerAgree(REFUND_ID, null, SELLER_ID));

		assertEquals("退款状态已变更，请刷新", e.getMessage());
		verify(logMapper, never()).insert(any(), any(), any(), any());
	}

	@Test
	@DisplayName("退货物流公司为空 → 拒绝且不推进状态")
	void buyerReturn_blankCompany_rejected() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("seller_agreed", "return_refund"));

		TWTException e = assertThrows(TWTException.class,
				() -> service.buyerReturn(REFUND_ID, " ", "SF123", BUYER_ID));

		assertEquals("请填写退货物流公司", e.getMessage());
		verify(refundMapper, never()).updateStatus(any(), any(), any());
	}

	@Test
	@DisplayName("退货单号为空 → 拒绝且不推进状态")
	void buyerReturn_blankReturnNo_rejected() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("seller_agreed", "return_refund"));

		TWTException e = assertThrows(TWTException.class,
				() -> service.buyerReturn(REFUND_ID, "顺丰", null, BUYER_ID));

		assertEquals("请填写退货单号", e.getMessage());
		verify(refundMapper, never()).updateStatus(any(), any(), any());
	}

	@Test
	@DisplayName("卖家同意仅退款后通知买家")
	void sellerAgree_notifiesBuyer() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("pending_seller", "refund_only"));
		when(refundMapper.updateStatus(REFUND_ID, "pending_seller", "seller_agreed")).thenReturn(1);
		when(refundMapper.updateStatus(REFUND_ID, "seller_agreed", "refunding")).thenReturn(1);

		service.sellerAgree(REFUND_ID, null, SELLER_ID);

		verify(imService).sendNotice(eq(BUYER_ID), eq("order"), eq("卖家已同意退款"), any());
	}

	@Test
	@DisplayName("买家填写退货物流后通知卖家")
	void buyerReturn_notifiesSeller() {
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund("seller_agreed", "return_refund"));
		when(refundMapper.updateStatus(REFUND_ID, "seller_agreed", "buyer_returned")).thenReturn(1);

		service.buyerReturn(REFUND_ID, "顺丰", "SF123", BUYER_ID);

		verify(imService).sendNotice(eq(SELLER_ID), eq("order"), eq("买家已寄回退货"), any());
	}

}