package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.trade.server.crypto.SensitiveDataCipher;
import com.twelvet.trade.server.domain.SellerAccount;
import com.twelvet.trade.server.domain.SellerBalanceFlow;
import com.twelvet.trade.server.domain.SettlementOrder;
import com.twelvet.trade.server.mapper.SellerAccountMapper;
import com.twelvet.trade.server.mapper.SellerBalanceFlowMapper;
import com.twelvet.trade.server.mapper.SettlementOrderMapper;
import com.twelvet.trade.server.mapper.SettlementReconDiffMapper;
import com.twelvet.trade.server.settlement.MockPayoutGateway;
import com.twelvet.trade.server.settlement.PayoutGateway;
import com.twelvet.trade.server.settlement.PayoutResult;
import com.twelvet.trade.server.settlement.SettlementPayoutHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 打款批次与单笔结算测试
 * <p>
 * 锁定的不变量：markSettled 守卫失败（并发已结算）不得重复入账；入账与结算状态同事务；渠道可重试/入账冲突都记 fail_count 留给下一批次；过滤条件由
 * SQL 承担，批次不自作过滤。
 * </p>
 */
class SettlementPayoutTest {

	private final SettlementOrderMapper settlementMapper = mock(SettlementOrderMapper.class);

	private final PayoutGateway gateway = mock(PayoutGateway.class);

	private final SellerAccountMapper accountMapper = mock(SellerAccountMapper.class);

	private final SellerBalanceFlowMapper flowMapper = mock(SellerBalanceFlowMapper.class);

	private final SensitiveDataCipher cipher = new SensitiveDataCipher(
			Base64.getEncoder().encodeToString(new byte[32]));

	private final SellerAccountServiceImpl accountService = new SellerAccountServiceImpl(accountMapper, cipher,
			flowMapper, mock(SettlementReconDiffMapper.class));

	private final SettlementPayoutHelper helper = new SettlementPayoutHelper(settlementMapper, accountService);

	private final SettlementServiceImpl service = new SettlementServiceImpl(settlementMapper, gateway, helper,
			accountMapper, accountService);

	private SettlementOrder settlement() {
		SettlementOrder settlement = new SettlementOrder();
		settlement.setSettlementId(11L);
		settlement.setSellerId(7L);
		settlement.setTenantId(42L);
		settlement.setChannel("mock");
		settlement.setNetAmount(new BigDecimal("95.00"));
		settlement.setStatus("pending_settle");
		return settlement;
	}

	private SellerAccount account(String balance, int version) {
		SellerAccount account = new SellerAccount();
		account.setAccountId(11L);
		account.setSellerId(7L);
		account.setTenantId(42L);
		account.setBalance(new BigDecimal(balance));
		account.setVersion(version);
		return account;
	}

	@Test
	@DisplayName("单笔打款：markSettled 成功并同事务入账 settle_in")
	void payoutOne_marksSettledAndCreditsBalance() {
		when(settlementMapper.markSettled(11L, "mock", "MOCK11")).thenReturn(1);
		when(accountMapper.selectBySeller(7L, 42L)).thenReturn(account("10.00", 3));
		when(flowMapper.insertIgnore(any(SellerBalanceFlow.class))).thenReturn(1);
		when(accountMapper.addBalance(11L, new BigDecimal("95.00"), 3)).thenReturn(1);

		assertTrue(helper.payoutOne(settlement(), "MOCK11"));

		ArgumentCaptor<SellerBalanceFlow> captor = ArgumentCaptor.forClass(SellerBalanceFlow.class);
		verify(flowMapper).insertIgnore(captor.capture());
		SellerBalanceFlow flow = captor.getValue();
		assertEquals(11L, flow.getAccountId());
		assertEquals(7L, flow.getSellerId());
		assertEquals(42L, flow.getTenantId());
		assertEquals(11L, flow.getSettlementId());
		assertEquals("settle_in", flow.getType());
		assertEquals(new BigDecimal("95.00"), flow.getAmount());
		assertEquals(new BigDecimal("105.00"), flow.getBalanceAfter());
		assertEquals("settle:11", flow.getBizKey());
	}

	@Test
	@DisplayName("markSettled 0 行（并发已结算）：返回 false，不记账")
	void payoutOne_markSettledRowsZero_returnsFalse() {
		when(settlementMapper.markSettled(11L, "mock", "MOCK11")).thenReturn(0);

		assertFalse(helper.payoutOne(settlement(), "MOCK11"));
		verifyNoInteractions(accountMapper, flowMapper);
	}

	@Test
	@DisplayName("卖家账户缺失：抛 TWTException，结算状态随事务回滚")
	void payoutOne_accountMissing_throws() {
		when(settlementMapper.markSettled(11L, "mock", "MOCK11")).thenReturn(1);
		when(accountMapper.selectBySeller(7L, 42L)).thenReturn(null);

		assertThrows(TWTException.class, () -> helper.payoutOne(settlement(), "MOCK11"));
	}

	@Test
	@DisplayName("渠道可重试失败：fail_count+1，不入账")
	void gatewayRetry_incrementsFailCount_noBalance() {
		when(settlementMapper.selectPendingPayout(200, 3)).thenReturn(List.of(settlement()));
		when(gateway.submit(any(SettlementOrder.class))).thenReturn(PayoutResult.retry("timeout"));

		assertEquals(0, service.payoutBatch(200));
		verify(settlementMapper).incrementFailCount(11L);
		verify(settlementMapper, never()).markSettled(anyLong(), any(), any());
		verifyNoInteractions(accountMapper);
	}

	@Test
	@DisplayName("入账版本冲突：fail_count+1，批次不计数")
	void balanceConflict_batchIncrementsFailCount() {
		when(settlementMapper.selectPendingPayout(200, 3)).thenReturn(List.of(settlement()));
		when(gateway.submit(any(SettlementOrder.class))).thenReturn(PayoutResult.ok("MOCK11"));
		when(settlementMapper.markSettled(11L, "mock", "MOCK11")).thenReturn(1);
		when(accountMapper.selectBySeller(7L, 42L)).thenReturn(account("10.00", 3));
		when(flowMapper.insertIgnore(any(SellerBalanceFlow.class))).thenReturn(1);
		when(accountMapper.addBalance(eq(11L), eq(new BigDecimal("95.00")), eq(3))).thenReturn(0);

		assertEquals(0, service.payoutBatch(200));
		verify(settlementMapper).incrementFailCount(11L);
	}

	@Test
	@DisplayName("无候选（冻结/未实名由 SQL 过滤）：返回 0，不调用渠道")
	void frozenOrUnverified_notSelected() {
		when(settlementMapper.selectPendingPayout(200, 3)).thenReturn(List.of());

		assertEquals(0, service.payoutBatch(200));
		verifyNoInteractions(gateway);
	}

	@Test
	@DisplayName("mock 渠道按结算单幂等：重复提交返回同一渠道流水号")
	void mockGateway_submitIsIdempotent() {
		MockPayoutGateway mockGateway = new MockPayoutGateway();
		SettlementOrder settlement = settlement();

		PayoutResult first = mockGateway.submit(settlement);
		PayoutResult second = mockGateway.submit(settlement);

		assertTrue(first.isSuccess());
		assertEquals("MOCK11", first.getChannelTransNo());
		assertEquals(first.getChannelTransNo(), second.getChannelTransNo());
		assertEquals("success", mockGateway.query("MOCK11"));
	}

}