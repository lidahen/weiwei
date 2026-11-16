package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.trade.server.crypto.SensitiveDataCipher;
import com.twelvet.trade.server.domain.SellerAccount;
import com.twelvet.trade.server.domain.SellerBalanceFlow;
import com.twelvet.trade.server.mapper.SellerAccountMapper;
import com.twelvet.trade.server.mapper.SellerBalanceFlowMapper;
import com.twelvet.trade.server.mapper.SettlementReconDiffMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 卖家余额记账测试
 * <p>
 * 锁定的不变量：biz_key 重复即幂等不重复入账；负数金额（扣回/提现）同样走账本；balanceAfter = 旧余额 + 变动额；
 * 账户缺失或版本冲突必须抛出（由调用方事务整体回滚）。
 * </p>
 */
class SellerBalanceApplyTest {

	private final SellerAccountMapper accountMapper = mock(SellerAccountMapper.class);

	private final SellerBalanceFlowMapper flowMapper = mock(SellerBalanceFlowMapper.class);

	private final SensitiveDataCipher cipher = new SensitiveDataCipher(
			Base64.getEncoder().encodeToString(new byte[32]));

	private final SellerAccountServiceImpl service = new SellerAccountServiceImpl(accountMapper, cipher, flowMapper,
			mock(SettlementReconDiffMapper.class));

	private SellerAccount account(String balance, int version) {
		SellerAccount account = new SellerAccount();
		account.setAccountId(11L);
		account.setSellerId(7L);
		account.setTenantId(42L);
		account.setBalance(new BigDecimal(balance));
		account.setVersion(version);
		return account;
	}

	private SellerBalanceFlow flow(String amount) {
		SellerBalanceFlow flow = new SellerBalanceFlow();
		flow.setSellerId(7L);
		flow.setTenantId(42L);
		flow.setSettlementId(11L);
		flow.setType("settle_in");
		flow.setAmount(new BigDecimal(amount));
		flow.setBizKey("settle:11");
		return flow;
	}

	@Test
	@DisplayName("重复 biz_key：返回 false 且不改余额")
	void duplicateBizKey_returnsFalse_noBalanceChange() {
		when(accountMapper.selectBySeller(7L, 42L)).thenReturn(account("10.00", 3));
		when(flowMapper.insertIgnore(any(SellerBalanceFlow.class))).thenReturn(0);

		assertFalse(service.applyBalance(flow("95.00")));
		verify(accountMapper, never()).addBalance(any(), any(), any());
	}

	@Test
	@DisplayName("负数金额允许（扣回/提现），balanceAfter 为扣减后余额")
	void negativeAmount_deductsBalance() {
		SellerBalanceFlow flow = flow("-30.00");
		when(accountMapper.selectBySeller(7L, 42L)).thenReturn(account("100.00", 3));
		when(flowMapper.insertIgnore(any(SellerBalanceFlow.class))).thenReturn(1);
		when(accountMapper.addBalance(11L, new BigDecimal("-30.00"), 3)).thenReturn(1);

		assertTrue(service.applyBalance(flow));
		assertEquals(new BigDecimal("70.00"), flow.getBalanceAfter());
		assertEquals(11L, flow.getAccountId());
	}

	@Test
	@DisplayName("记账成功：落库时 balanceAfter 已等于变动后余额（不是先插 NULL 再回填）")
	void success_setsBalanceAfterBeforeInsert() {
		SellerBalanceFlow flow = flow("95.00");
		when(accountMapper.selectBySeller(7L, 42L)).thenReturn(account("10.00", 3));
		java.util.concurrent.atomic.AtomicReference<BigDecimal> insertedBalanceAfter = new java.util.concurrent.atomic.AtomicReference<>();
		when(flowMapper.insertIgnore(any(SellerBalanceFlow.class))).thenAnswer(invocation -> {
			insertedBalanceAfter.set(((SellerBalanceFlow) invocation.getArgument(0)).getBalanceAfter());
			return 1;
		});
		when(accountMapper.addBalance(11L, new BigDecimal("95.00"), 3)).thenReturn(1);

		assertTrue(service.applyBalance(flow));
		assertEquals(new BigDecimal("105.00"), insertedBalanceAfter.get());
		assertEquals(new BigDecimal("105.00"), flow.getBalanceAfter());
	}

	@Test
	@DisplayName("账户缺失：抛异常，不写流水")
	void accountMissing_throws() {
		when(accountMapper.selectBySeller(7L, 42L)).thenReturn(null);

		assertThrows(TWTException.class, () -> service.applyBalance(flow("95.00")));
		verify(flowMapper, never()).insertIgnore(any());
	}

	@Test
	@DisplayName("余额版本冲突：抛异常（事务回滚，下一批次重来）")
	void versionConflict_throws() {
		when(accountMapper.selectBySeller(7L, 42L)).thenReturn(account("10.00", 3));
		when(flowMapper.insertIgnore(any(SellerBalanceFlow.class))).thenReturn(1);
		when(accountMapper.addBalance(11L, new BigDecimal("95.00"), 3)).thenReturn(0);

		assertThrows(TWTException.class, () -> service.applyBalance(flow("95.00")));
	}

}