package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.trade.server.domain.SettlementReconDiff;
import com.twelvet.trade.server.mapper.SellerBalanceFlowMapper;
import com.twelvet.trade.server.mapper.SettlementOrderMapper;
import com.twelvet.trade.server.mapper.SettlementReconDiffMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 每日对账测试
 * <p>
 * 锁定的不变量：六类差异各入队一次；uk_diff 命中不计入新增；入队后按业务日冻结卖家； 处理差异只改差异状态，不自动解冻账户。
 * </p>
 */
class ReconServiceTest {

	private static final LocalDate BIZ_DATE = LocalDate.of(2026, 11, 1);

	private final SettlementOrderMapper orderMapper = mock(SettlementOrderMapper.class);

	private final SellerBalanceFlowMapper flowMapper = mock(SellerBalanceFlowMapper.class);

	private final SettlementReconDiffMapper diffMapper = mock(SettlementReconDiffMapper.class);

	private final ReconServiceImpl service = new ReconServiceImpl(orderMapper, flowMapper, diffMapper);

	@Test
	@DisplayName("六类差异各入队一条，冻结该业务日卖家")
	void detectsSixDiffTypes() {
		when(orderMapper.selectMissingSettlement(BIZ_DATE)).thenReturn(
				List.of(Map.of("orderId", 1L, "sellerId", 7L, "tenantId", 42L, "channelAmount", new BigDecimal("100.00"))));
		when(orderMapper.selectPhantomSettlement(BIZ_DATE)).thenReturn(List
				.of(Map.of("settlementId", 11L, "orderId", 2L, "sellerId", 7L, "tenantId", 42L, "platformAmount",
						new BigDecimal("100.00"))));
		when(orderMapper.selectAmountMismatch(BIZ_DATE)).thenReturn(List.of(Map.of("settlementId", 12L, "orderId", 3L,
				"sellerId", 7L, "tenantId", 42L, "channelAmount", new BigDecimal("90.00"), "platformAmount",
				new BigDecimal("100.00"))));
		when(orderMapper.selectRefundStatusMismatch(BIZ_DATE)).thenReturn(List.of(Map.of("refundId", 99L, "orderId", 4L,
				"settlementId", 13L, "sellerId", 7L, "tenantId", 42L, "status", "settled")));
		when(orderMapper.selectDuplicateSettlement(BIZ_DATE))
			.thenReturn(List.of(Map.of("orderId", 5L, "cnt", 2L, "sellerId", 7L, "tenantId", 42L)));
		when(flowMapper.selectAccountsWithLedgerDrift()).thenReturn(List.of(Map.of("accountId", 11L, "sellerId", 7L,
				"tenantId", 42L, "balance", new BigDecimal("30.00"), "flowSum", new BigDecimal("10.00"))));
		when(flowMapper.selectAccountsWithLastBalanceDrift()).thenReturn(List.of());
		when(diffMapper.insertIgnore(any(SettlementReconDiff.class))).thenReturn(1);

		assertEquals(6, service.reconcile(BIZ_DATE));

		ArgumentCaptor<SettlementReconDiff> captor = ArgumentCaptor.forClass(SettlementReconDiff.class);
		verify(diffMapper, times(6)).insertIgnore(captor.capture());
		Set<String> types = captor.getAllValues().stream().map(SettlementReconDiff::getType).collect(Collectors.toSet());
		assertEquals(Set.of("missing_settlement", "phantom_settlement", "amount_mismatch", "status_mismatch",
				"duplicate_settlement", "ledger_mismatch"), types);
		assertEquals(BIZ_DATE, captor.getValue().getBizDate());
		verify(diffMapper).freezeSellersWithOpenDiffs("2026-11-01");
	}

	@Test
	@DisplayName("uk_diff 命中：不抛异常，新增计数只算成功入队")
	void duplicateDiff_insertIgnored() {
		when(orderMapper.selectMissingSettlement(BIZ_DATE)).thenReturn(
				List.of(Map.of("orderId", 1L, "sellerId", 7L, "tenantId", 42L, "channelAmount", new BigDecimal("100.00"))));
		when(diffMapper.insertIgnore(any(SettlementReconDiff.class))).thenReturn(0);

		assertEquals(0, service.reconcile(BIZ_DATE));
	}

	@Test
	@DisplayName("账本合计正确但最后一条 balance_after 不符：独立入队 :last 差异（不依赖合计漂移）")
	void lastBalanceDrift_detectedIndependentOfSum() {
		when(flowMapper.selectAccountsWithLedgerDrift()).thenReturn(List.of());
		when(flowMapper.selectAccountsWithLastBalanceDrift()).thenReturn(List.of(Map.of("accountId", 11L, "sellerId",
				7L, "tenantId", 42L, "balance", new BigDecimal("105.00"), "lastBalance", new BigDecimal("0.00"))));
		when(diffMapper.insertIgnore(any(SettlementReconDiff.class))).thenReturn(1);

		assertEquals(1, service.reconcile(BIZ_DATE));

		ArgumentCaptor<SettlementReconDiff> captor = ArgumentCaptor.forClass(SettlementReconDiff.class);
		verify(diffMapper).insertIgnore(captor.capture());
		SettlementReconDiff diff = captor.getValue();
		assertEquals("ledger_mismatch", diff.getType());
		assertEquals("ledger:11:2026-11-01:last", diff.getBizKey());
		assertEquals(new BigDecimal("0.00"), diff.getChannelAmount());
		assertEquals(new BigDecimal("105.00"), diff.getPlatformAmount());
	}

	@Test
	@DisplayName("对账结束必冻结该业务日有未闭环差异的卖家")
	void freezeAfterInsert() {
		service.reconcile(BIZ_DATE);

		verify(diffMapper).freezeSellersWithOpenDiffs("2026-11-01");
	}

	@Test
	@DisplayName("处理差异：open 行数 1 成功；0 行抛「差异已处理」")
	void resolve_setsResolved() {
		when(diffMapper.resolve(5L, 99L, "已修复", 42L)).thenReturn(1);
		service.resolve(5L, "已修复", 99L, 42L);
		verify(diffMapper).resolve(5L, 99L, "已修复", 42L);

		when(diffMapper.resolve(5L, 99L, "已修复", 42L)).thenReturn(0);
		assertThrows(TWTException.class, () -> service.resolve(5L, "已修复", 99L, 42L));
	}

	@Test
	@DisplayName("处理差异不自动解冻账户（解冻是运营独立动作）")
	void unfreeze_onlyWhenNoOpenDiff() {
		when(diffMapper.resolve(anyLong(), any(), any(), any())).thenReturn(1);

		service.resolve(5L, "已修复", 99L, 42L);

		// 对账服务不持有任何卖家账户写入通道：处理差异只改差异状态
		verifyNoInteractions(orderMapper, flowMapper);
		verify(diffMapper, never()).freezeSellersWithOpenDiffs(any());
	}

}