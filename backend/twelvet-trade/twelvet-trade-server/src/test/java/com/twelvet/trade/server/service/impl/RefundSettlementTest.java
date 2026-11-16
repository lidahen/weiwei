package com.twelvet.trade.server.service.impl;

import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.crypto.SensitiveDataCipher;
import com.twelvet.trade.server.domain.SellerAccount;
import com.twelvet.trade.server.domain.SellerBalanceFlow;
import com.twelvet.trade.server.domain.SettlementOrder;
import com.twelvet.trade.server.domain.TradeRefund;
import com.twelvet.trade.server.mapper.SellerAccountMapper;
import com.twelvet.trade.server.mapper.SellerBalanceFlowMapper;
import com.twelvet.trade.server.mapper.SettlementOrderMapper;
import com.twelvet.trade.server.mapper.SettlementReconDiffMapper;
import com.twelvet.trade.server.settlement.PayoutGateway;
import com.twelvet.trade.server.settlement.SettlementPayoutHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 退款成功与结算互斥测试
 * <p>
 * 锁定的不变量：售后期内退款直接作废 holding/pending_settle；已结算/已提现退款按账本扣回并标记 clawback；
 * 扣回后余额为负必须冻结账户；重复回调不重复扣、不冻结；无结算单/无账户只记日志不抛。
 * </p>
 */
class RefundSettlementTest {

	private final SettlementOrderMapper settlementMapper = mock(SettlementOrderMapper.class);

	private final PayoutGateway payoutGateway = mock(PayoutGateway.class);

	private final SettlementPayoutHelper payoutHelper = mock(SettlementPayoutHelper.class);

	private final SellerAccountMapper accountMapper = mock(SellerAccountMapper.class);

	private final SellerBalanceFlowMapper flowMapper = mock(SellerBalanceFlowMapper.class);

	private final SensitiveDataCipher cipher = new SensitiveDataCipher(
			Base64.getEncoder().encodeToString(new byte[32]));

	private final SellerAccountServiceImpl accountService = new SellerAccountServiceImpl(accountMapper, cipher,
			flowMapper, mock(SettlementReconDiffMapper.class));

	private final SettlementServiceImpl service = new SettlementServiceImpl(settlementMapper, payoutGateway,
			payoutHelper, accountMapper, accountService);

	private TradeRefund refund() {
		TradeRefund refund = new TradeRefund();
		refund.setRefundId(99L);
		refund.setOrderId(1L);
		refund.setBuyerId(8L);
		refund.setSellerId(7L);
		refund.setAmount(new BigDecimal("50.00"));
		return refund;
	}

	private TradeOrder order() {
		TradeOrder order = new TradeOrder();
		order.setOrderId(1L);
		order.setTenantId(42L);
		order.setSellerId(7L);
		order.setBuyerId(8L);
		return order;
	}

	private SettlementOrder settlement(String status) {
		SettlementOrder settlement = new SettlementOrder();
		settlement.setSettlementId(11L);
		settlement.setOrderId(1L);
		settlement.setSellerId(7L);
		settlement.setBuyerId(8L);
		settlement.setTenantId(42L);
		settlement.setNetAmount(new BigDecimal("100.00"));
		settlement.setStatus(status);
		return settlement;
	}

	private SellerAccount account(String balance) {
		SellerAccount account = new SellerAccount();
		account.setAccountId(11L);
		account.setSellerId(7L);
		account.setTenantId(42L);
		account.setBalance(new BigDecimal(balance));
		account.setVersion(1);
		return account;
	}

	@Test
	@DisplayName("holding 期退款：markClosed 成功即止，不扣款")
	void holdingRefund_closesSettlement() {
		when(settlementMapper.markClosed(1L, 99L)).thenReturn(1);

		service.onRefundSuccess(refund(), order(), refund().getAmount());

		verify(settlementMapper).markClosed(1L, 99L);
		verify(settlementMapper, never()).selectByOrderId(anyLong());
		verifyNoInteractions(accountMapper, flowMapper);
	}

	@Test
	@DisplayName("pending_settle 期退款：同样走关闭，不再查结算单")
	void pendingSettleRefund_closesSettlement() {
		when(settlementMapper.markClosed(1L, 99L)).thenReturn(1);

		service.onRefundSuccess(refund(), order(), refund().getAmount());

		verify(settlementMapper).markClosed(1L, 99L);
		verify(settlementMapper, never()).selectByOrderId(anyLong());
	}

	@Test
	@DisplayName("已结算后的部分退款：按实际退款 30 扣回，而不是原申请 50")
	void settledPartialRefund_clawsBackExecutedAmount() {
		when(settlementMapper.markClosed(1L, 99L)).thenReturn(0);
		when(settlementMapper.selectByOrderId(1L)).thenReturn(settlement("settled"));
		when(accountMapper.selectBySeller(7L, 42L)).thenReturn(account("100.00"));
		when(flowMapper.insertIgnore(any(SellerBalanceFlow.class))).thenReturn(1);
		when(accountMapper.addBalance(11L, new BigDecimal("-30.00"), 1)).thenReturn(1);
		when(settlementMapper.markClawback(11L, 99L)).thenReturn(1);

		// 退款单申请金额是 50，实际执行（裁决部分退款）是 30：扣回必须用 30
		service.onRefundSuccess(refund(), order(), new BigDecimal("30.00"));

		ArgumentCaptor<SellerBalanceFlow> captor = ArgumentCaptor.forClass(SellerBalanceFlow.class);
		verify(flowMapper).insertIgnore(captor.capture());
		SellerBalanceFlow flow = captor.getValue();
		assertEquals("clawback", flow.getType());
		assertEquals(new BigDecimal("-30.00"), flow.getAmount());
		assertEquals("clawback:99", flow.getBizKey());
		assertEquals(11L, flow.getSettlementId());
		assertEquals(99L, flow.getRefundId());
		assertEquals(new BigDecimal("70.00"), flow.getBalanceAfter());
		verify(settlementMapper).markClawback(11L, 99L);
		verify(accountMapper, never()).setFrozen(any(), any(), any());
	}

	@Test
	@DisplayName("扣回后余额为负：冻结账户")
	void negativeBalance_freezesAccount() {
		when(settlementMapper.markClosed(1L, 99L)).thenReturn(0);
		when(settlementMapper.selectByOrderId(1L)).thenReturn(settlement("settled"));
		when(accountMapper.selectBySeller(7L, 42L)).thenReturn(account("30.00"));
		when(flowMapper.insertIgnore(any(SellerBalanceFlow.class))).thenReturn(1);
		when(accountMapper.addBalance(11L, new BigDecimal("-50.00"), 1)).thenReturn(1);
		when(settlementMapper.markClawback(11L, 99L)).thenReturn(1);

		service.onRefundSuccess(refund(), order(), refund().getAmount());

		verify(accountMapper).setFrozen(11L, 1, 42L);
	}

	@Test
	@DisplayName("withdrawn 结算单退款：同样扣回")
	void withdrawnSettlement_stillClawback() {
		when(settlementMapper.markClosed(1L, 99L)).thenReturn(0);
		when(settlementMapper.selectByOrderId(1L)).thenReturn(settlement("withdrawn"));
		when(accountMapper.selectBySeller(7L, 42L)).thenReturn(account("100.00"));
		when(flowMapper.insertIgnore(any(SellerBalanceFlow.class))).thenReturn(1);
		when(accountMapper.addBalance(11L, new BigDecimal("-50.00"), 1)).thenReturn(1);
		when(settlementMapper.markClawback(11L, 99L)).thenReturn(1);

		service.onRefundSuccess(refund(), order(), refund().getAmount());

		verify(settlementMapper).markClawback(11L, 99L);
	}

	@Test
	@DisplayName("无结算单：静默跳过（存量订单退款）")
	void noSettlement_noop() {
		when(settlementMapper.markClosed(1L, 99L)).thenReturn(0);
		when(settlementMapper.selectByOrderId(1L)).thenReturn(null);

		assertDoesNotThrow(() -> service.onRefundSuccess(refund(), order(), refund().getAmount()));
		verifyNoInteractions(accountMapper, flowMapper);
	}

	@Test
	@DisplayName("重复退款回调：幂等键命中不重复扣、不冻结、不重复标记")
	void duplicateRefundCallback_idempotent() {
		when(settlementMapper.markClosed(1L, 99L)).thenReturn(0);
		when(settlementMapper.selectByOrderId(1L)).thenReturn(settlement("settled"));
		when(accountMapper.selectBySeller(7L, 42L)).thenReturn(account("100.00"));
		when(flowMapper.insertIgnore(any(SellerBalanceFlow.class))).thenReturn(0);

		assertDoesNotThrow(() -> service.onRefundSuccess(refund(), order(), refund().getAmount()));
		verify(accountMapper, never()).addBalance(any(), any(), any());
		verify(accountMapper, never()).setFrozen(any(), any(), any());
		verify(settlementMapper, never()).markClawback(any(), any());
	}

}