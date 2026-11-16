package com.twelvet.trade.server.service.impl;

import com.twelvet.trade.server.domain.SettlementOrder;
import com.twelvet.trade.server.mapper.SellerAccountMapper;
import com.twelvet.trade.server.mapper.SettlementOrderMapper;
import com.twelvet.trade.server.service.ISellerAccountService;
import com.twelvet.trade.server.settlement.PayoutGateway;
import com.twelvet.trade.server.settlement.SettlementPayoutHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结算扫描 holding → pending_settle 测试
 * <p>
 * 锁定的不变量：只推进售后期届满的单；markPending 守卫失败（并发退款）按跳过不重试；holdDays 走配置。
 * </p>
 */
class SettlementScanTest {

	private final SettlementOrderMapper mapper = mock(SettlementOrderMapper.class);

	private final PayoutGateway payoutGateway = mock(PayoutGateway.class);

	private final SettlementPayoutHelper payoutHelper = mock(SettlementPayoutHelper.class);

	private final SellerAccountMapper sellerAccountMapper = mock(SellerAccountMapper.class);

	private final ISellerAccountService sellerAccountService = mock(ISellerAccountService.class);

	private final SettlementServiceImpl service = new SettlementServiceImpl(mapper, payoutGateway, payoutHelper,
			sellerAccountMapper, sellerAccountService);

	private static SettlementOrder holding(long settlementId) {
		SettlementOrder settlement = new SettlementOrder();
		settlement.setSettlementId(settlementId);
		settlement.setStatus("holding");
		return settlement;
	}

	private static void configureHoldDays(SettlementServiceImpl target, int days) {
		try {
			Field field = SettlementServiceImpl.class.getDeclaredField("holdDays");
			field.setAccessible(true);
			field.setInt(target, days);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	@DisplayName("届满 holding 单：逐条推进并计数")
	void dueHolding_isPromoted() {
		when(mapper.selectDueHoldings(200, 7)).thenReturn(List.of(holding(1L), holding(2L)));
		when(mapper.markPending(1L, 7)).thenReturn(1);
		when(mapper.markPending(2L, 7)).thenReturn(1);

		assertEquals(2, service.scanDueHoldings(200));
		verify(mapper).markPending(1L, 7);
		verify(mapper).markPending(2L, 7);
	}

	@Test
	@DisplayName("markPending 守卫失败（并发退款抢占）：跳过该条，不影响其余")
	void markPendingRowsZero_concurrentRefund_skipped() {
		when(mapper.selectDueHoldings(200, 7)).thenReturn(List.of(holding(1L), holding(2L)));
		when(mapper.markPending(1L, 7)).thenReturn(0);
		when(mapper.markPending(2L, 7)).thenReturn(1);

		assertDoesNotThrow(() -> assertEquals(1, service.scanDueHoldings(200)));
	}

	@Test
	@DisplayName("holdDays 走配置：10 天配置传 10 给扫描与推进")
	void scan_usesConfiguredHoldDays() {
		configureHoldDays(service, 10);
		when(mapper.selectDueHoldings(200, 10)).thenReturn(List.of(holding(1L)));
		when(mapper.markPending(1L, 10)).thenReturn(1);

		assertEquals(1, service.scanDueHoldings(200));
		verify(mapper).selectDueHoldings(200, 10);
		verify(mapper).markPending(1L, 10);
	}

	@Test
	@DisplayName("无候选：返回 0，不调用 markPending")
	void scan_noCandidates_returnsZero() {
		when(mapper.selectDueHoldings(200, 7)).thenReturn(List.of());

		assertEquals(0, service.scanDueHoldings(200));
		verify(mapper, never()).markPending(anyLong(), anyInt());
	}

}