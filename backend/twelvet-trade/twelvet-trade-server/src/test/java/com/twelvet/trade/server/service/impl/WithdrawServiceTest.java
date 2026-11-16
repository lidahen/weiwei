package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.trade.server.domain.SellerAccount;
import com.twelvet.trade.server.domain.SellerBalanceFlow;
import com.twelvet.trade.server.domain.TradeWithdraw;
import com.twelvet.trade.server.mapper.TradeWithdrawMapper;
import com.twelvet.trade.server.service.ISellerAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 卖家提现测试
 * <p>
 * 锁定的不变量：申请即扣余额（withdraw_out），余额/实名/金额校验不通过不落单；驳回用 adjust 退回； 审核与打款都有前置状态守卫；重复幂等键不得二次扣减。
 * </p>
 */
class WithdrawServiceTest {

	private final ISellerAccountService accountService = mock(ISellerAccountService.class);

	private final TradeWithdrawMapper withdrawMapper = mock(TradeWithdrawMapper.class);

	private final WithdrawServiceImpl service = new WithdrawServiceImpl(accountService, withdrawMapper);

	private SellerAccount account(String kycStatus, String balance) {
		SellerAccount account = new SellerAccount();
		account.setAccountId(11L);
		account.setSellerId(7L);
		account.setTenantId(42L);
		account.setKycStatus(kycStatus);
		account.setBalance(new BigDecimal(balance));
		account.setVersion(1);
		return account;
	}

	private TradeWithdraw withdraw() {
		TradeWithdraw withdraw = new TradeWithdraw();
		withdraw.setWithdrawId(33L);
		withdraw.setWithdrawNo("Wabc");
		withdraw.setAccountId(11L);
		withdraw.setSellerId(7L);
		withdraw.setTenantId(42L);
		withdraw.setAmount(new BigDecimal("40.00"));
		withdraw.setStatus("pending");
		return withdraw;
	}

	@Test
	@DisplayName("申请提现：落单 pending 并扣减余额，返回扣后余额")
	void apply_deductsBalanceAndWritesFlow() {
		when(accountService.getOrCreate(7L, 42L)).thenReturn(account("verified", "100.00"));
		when(withdrawMapper.insert(any(TradeWithdraw.class))).thenAnswer(invocation -> {
			TradeWithdraw inserted = invocation.getArgument(0);
			inserted.setWithdrawId(33L);
			return 1;
		});
		when(accountService.applyBalance(any(SellerBalanceFlow.class))).thenAnswer(invocation -> {
			SellerBalanceFlow flow = invocation.getArgument(0);
			flow.setBalanceAfter(new BigDecimal("60.00"));
			return true;
		});

		BigDecimal balance = service.apply(7L, 42L, new BigDecimal("40"));

		assertEquals(new BigDecimal("60.00"), balance);
		ArgumentCaptor<TradeWithdraw> withdrawCaptor = ArgumentCaptor.forClass(TradeWithdraw.class);
		verify(withdrawMapper).insert(withdrawCaptor.capture());
		TradeWithdraw created = withdrawCaptor.getValue();
		assertTrue(created.getWithdrawNo().startsWith("W"));
		assertEquals("pending", created.getStatus());
		assertEquals(11L, created.getAccountId());
		assertEquals(new BigDecimal("40"), created.getAmount());
		ArgumentCaptor<SellerBalanceFlow> flowCaptor = ArgumentCaptor.forClass(SellerBalanceFlow.class);
		verify(accountService).applyBalance(flowCaptor.capture());
		SellerBalanceFlow flow = flowCaptor.getValue();
		assertEquals("withdraw_out", flow.getType());
		assertEquals(new BigDecimal("-40"), flow.getAmount());
		assertEquals("withdraw:33", flow.getBizKey());
		assertEquals(33L, flow.getWithdrawId());
	}

	@Test
	@DisplayName("余额不足：拒绝且不落单")
	void apply_insufficientBalance_rejected() {
		when(accountService.getOrCreate(7L, 42L)).thenReturn(account("verified", "100.00"));

		assertThrows(TWTException.class, () -> service.apply(7L, 42L, new BigDecimal("120")));
		verify(withdrawMapper, never()).insert(any());
		verify(accountService, never()).applyBalance(any());
	}

	@Test
	@DisplayName("冻结卖家：拒绝提现（对账差异/负余额冻结期间不得抽走余额）")
	void apply_frozen_rejected() {
		SellerAccount frozen = account("verified", "100.00");
		frozen.setFrozen(1);
		when(accountService.getOrCreate(7L, 42L)).thenReturn(frozen);

		assertThrows(TWTException.class, () -> service.apply(7L, 42L, new BigDecimal("40")));
		verify(withdrawMapper, never()).insert(any());
		verify(accountService, never()).applyBalance(any());
	}

	@Test
	@DisplayName("未实名：拒绝提现")
	void apply_kycNotVerified_rejected() {
		when(accountService.getOrCreate(7L, 42L)).thenReturn(account("pending", "100.00"));

		assertThrows(TWTException.class, () -> service.apply(7L, 42L, new BigDecimal("40")));
		verify(withdrawMapper, never()).insert(any());
	}

	@Test
	@DisplayName("非正金额：拒绝提现")
	void apply_nonPositive_rejected() {
		assertThrows(TWTException.class, () -> service.apply(7L, 42L, BigDecimal.ZERO));
		verify(withdrawMapper, never()).insert(any());
	}

	@Test
	@DisplayName("驳回：pending→rejected 并用 adjust 退回余额")
	void reject_refundsBalance() {
		when(withdrawMapper.selectById(33L, 42L)).thenReturn(withdraw());
		when(withdrawMapper.review(33L, "pending", "rejected", 99L, "资料有误", 42L)).thenReturn(1);
		when(accountService.applyBalance(any(SellerBalanceFlow.class))).thenReturn(true);

		service.review(33L, "reject", "资料有误", 99L, 42L);

		ArgumentCaptor<SellerBalanceFlow> captor = ArgumentCaptor.forClass(SellerBalanceFlow.class);
		verify(accountService).applyBalance(captor.capture());
		SellerBalanceFlow flow = captor.getValue();
		assertEquals("adjust", flow.getType());
		assertEquals(new BigDecimal("40.00"), flow.getAmount());
		assertEquals("withdraw_reject:33", flow.getBizKey());
		assertEquals(33L, flow.getWithdrawId());
	}

	@Test
	@DisplayName("通过：pending→approved；并发已处理（0 行）抛异常")
	void approve_guarded() {
		when(withdrawMapper.review(33L, "pending", "approved", 99L, null, 42L)).thenReturn(1);
		service.review(33L, "approve", null, 99L, 42L);
		verify(accountService, never()).applyBalance(any());

		when(withdrawMapper.review(33L, "pending", "approved", 99L, null, 42L)).thenReturn(0);
		assertThrows(TWTException.class, () -> service.review(33L, "approve", null, 99L, 42L));
	}

	@Test
	@DisplayName("打款：凭证必填，approved→paid")
	void markPaid_requiresVoucher() {
		assertThrows(TWTException.class, () -> service.markPaid(33L, "  ", 99L, 42L));
		verify(withdrawMapper, never()).markPaid(any(), any(), any(), any());

		when(withdrawMapper.markPaid(33L, "https://oss/voucher.png", 99L, 42L)).thenReturn(1);
		service.markPaid(33L, "https://oss/voucher.png", 99L, 42L);
		verify(withdrawMapper).markPaid(33L, "https://oss/voucher.png", 99L, 42L);
	}

	@Test
	@DisplayName("幂等键重复（已扣过）：申请失败回滚，不二次扣减")
	void duplicateApply_idempotentKeyRejects() {
		when(accountService.getOrCreate(7L, 42L)).thenReturn(account("verified", "100.00"));
		when(withdrawMapper.insert(any(TradeWithdraw.class))).thenAnswer(invocation -> {
			TradeWithdraw inserted = invocation.getArgument(0);
			inserted.setWithdrawId(33L);
			return 1;
		});
		when(accountService.applyBalance(any(SellerBalanceFlow.class))).thenReturn(false);

		assertThrows(TWTException.class, () -> service.apply(7L, 42L, new BigDecimal("40")));
		verify(accountService).applyBalance(any(SellerBalanceFlow.class));
	}

}