package com.twelvet.trade.server.service.impl;

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款成功后的商品结算测试
 * <p>
 * 锁定不变量：
 * <ol>
 * <li>未发货退款 → restoreStock（它自己把 locked/sold 恢复 onsale 并 stock+1），不再叠状态更新</li>
 * <li>已发货/已收货退款 → 不回补库存，商品置 offshelf（locked 会让商品永久锁死）</li>
 * <li>商品处置失败不得让已成功的退款回滚</li>
 * </ol>
 * </p>
 */
class TradeRefundServiceImplSettleTest {

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

	private TradeRefund refund(String prevOrderStatus) {
		TradeRefund refund = new TradeRefund();
		refund.setRefundId(REFUND_ID);
		refund.setOrderId(ORDER_ID);
		refund.setBuyerId(BUYER_ID);
		refund.setSellerId(SELLER_ID);
		refund.setType("return_refund");
		refund.setReasonCode("damaged");
		refund.setAmount(new BigDecimal("1107.00"));
		refund.setStatus("refunding");
		refund.setPrevOrderStatus(prevOrderStatus);
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

	/**
	 * 走通 sellerConfirmReceipt → executeRefund → finishRefundSuccess 的成功路径
	 */
	private void refundSucceeds(String prevOrderStatus) {
		TradeOrder order = new TradeOrder();
		order.setOrderId(ORDER_ID);
		order.setProductId(PRODUCT_ID);
		when(refundMapper.selectById(REFUND_ID, TENANT_ID)).thenReturn(refund(prevOrderStatus));
		when(refundMapper.updateStatus(REFUND_ID, "buyer_returned", "refunding")).thenReturn(1);
		when(payLookupMapper.selectPaidByOrderId(ORDER_ID)).thenReturn(List.of(paidPay()));
		when(payOrderService.refund(eq("P1"), any(), any())).thenReturn(true);
		when(refundMapper.updateStatus(REFUND_ID, "refunding", "refund_success")).thenReturn(1);
		when(orderMapper.selectOrderDetail(ORDER_ID, TENANT_ID)).thenReturn(order);
		when(orderMapper.updateOrderStatusIfIn(ORDER_ID, "refunded", List.of("refunding"), TENANT_ID)).thenReturn(1);
	}

	@Test
	@DisplayName("未发货退款成功 → 只调 restoreStock（它自己把 locked/sold 恢复 onsale 并 stock+1）")
	void settle_unshipped_restoresStock() {
		refundSucceeds("unshipped");

		service.sellerConfirmReceipt(REFUND_ID, SELLER_ID);

		verify(productMapper).restoreStock(PRODUCT_ID, TENANT_ID);
		verify(productMapper, never()).updateStatusIfIn(any(), any(), any(), any());
	}

	@Test
	@DisplayName("已发货退款成功 → 不回补库存，locked/sold 置 offshelf")
	void settle_shipped_marksOffshelf() {
		refundSucceeds("shipped");

		service.sellerConfirmReceipt(REFUND_ID, SELLER_ID);

		verify(productMapper, never()).restoreStock(any(), any());
		verify(productMapper).updateStatusIfIn(PRODUCT_ID, "offshelf", List.of("locked", "sold"), TENANT_ID);
	}

	@Test
	@DisplayName("已确认收货后退款（商品已被 markSold 置 sold）→ 同样置 offshelf")
	void settle_done_marksOffshelfFromSold() {
		refundSucceeds("done");

		service.sellerConfirmReceipt(REFUND_ID, SELLER_ID);

		verify(productMapper, never()).restoreStock(any(), any());
		verify(productMapper).updateStatusIfIn(PRODUCT_ID, "offshelf", List.of("locked", "sold"), TENANT_ID);
	}

	@Test
	@DisplayName("商品更新 0 行不影响退款成功（退款单已是终态）")
	void settle_productUpdateFails_doesNotBreak() {
		refundSucceeds("shipped");
		when(productMapper.updateStatusIfIn(PRODUCT_ID, "offshelf", List.of("locked", "sold"), TENANT_ID))
			.thenReturn(0);

		assertDoesNotThrow(() -> service.sellerConfirmReceipt(REFUND_ID, SELLER_ID));

		verify(refundMapper).updateStatus(REFUND_ID, "refunding", "refund_success");
	}

}