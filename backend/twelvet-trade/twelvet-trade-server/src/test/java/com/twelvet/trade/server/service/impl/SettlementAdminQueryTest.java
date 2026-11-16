package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.trade.server.domain.SettlementOrder;
import com.twelvet.trade.server.mapper.SellerAccountMapper;
import com.twelvet.trade.server.mapper.SettlementOrderMapper;
import com.twelvet.trade.server.service.ISellerAccountService;
import com.twelvet.trade.server.settlement.PayoutGateway;
import com.twelvet.trade.server.settlement.SettlementPayoutHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 运营结算查询测试
 * <p>
 * 锁定的不变量：分页走 PageHelper + PageUtils；详情查不到抛「结算单不存在」；返回字段完整可用于运营定位。
 * </p>
 */
class SettlementAdminQueryTest {

	private final SettlementOrderMapper settlementMapper = mock(SettlementOrderMapper.class);

	private final PayoutGateway payoutGateway = mock(PayoutGateway.class);

	private final SettlementPayoutHelper payoutHelper = mock(SettlementPayoutHelper.class);

	private final SellerAccountMapper sellerAccountMapper = mock(SellerAccountMapper.class);

	private final ISellerAccountService sellerAccountService = mock(ISellerAccountService.class);

	private final SettlementServiceImpl service = new SettlementServiceImpl(settlementMapper, payoutGateway,
			payoutHelper, sellerAccountMapper, sellerAccountService);

	private SettlementOrder settlement() {
		SettlementOrder settlement = new SettlementOrder();
		settlement.setSettlementId(11L);
		settlement.setOrderId(1L);
		settlement.setSellerId(7L);
		settlement.setBuyerId(8L);
		settlement.setTenantId(42L);
		settlement.setGrossAmount(new BigDecimal("100.00"));
		settlement.setCommissionAmount(new BigDecimal("0.00"));
		settlement.setNetAmount(new BigDecimal("100.00"));
		settlement.setStatus("holding");
		settlement.setChannel("mock");
		settlement.setFailCount(0);
		settlement.setCreateTime(LocalDateTime.of(2026, 11, 1, 10, 0));
		return settlement;
	}

	@Test
	@DisplayName("运营分页：PageHelper 启动并返回 TableDataInfo")
	void adminPage_returnsTableData() {
		List<SettlementOrder> rows = List.of(settlement());
		when(settlementMapper.selectAdminPage("holding", 42L)).thenReturn(rows);
		try (MockedStatic<PageUtils> pageUtils = mockStatic(PageUtils.class)) {
			pageUtils.when(() -> PageUtils.getDataTable(anyList()))
				.thenAnswer(invocation -> TableDataInfo.page(invocation.getArgument(0), 7L));

			TableDataInfo<SettlementOrder> result = service.adminPage("holding", 42L);

			assertEquals(rows, result.getRecords());
			assertEquals(7L, result.getTotal());
			pageUtils.verify(PageUtils::startPage);
		}
		verify(settlementMapper).selectAdminPage("holding", 42L);
	}

	@Test
	@DisplayName("运营详情：不存在时抛「结算单不存在」")
	void adminDetail_missing_throws() {
		when(settlementMapper.selectById(11L, 42L)).thenReturn(null);

		assertThrows(TWTException.class, () -> service.adminDetail(11L, 42L));
	}

	@Test
	@DisplayName("运营详情：返回结算单完整字段")
	void adminDetail_returnsFields() {
		when(settlementMapper.selectById(11L, 42L)).thenReturn(settlement());

		Map<String, Object> detail = service.adminDetail(11L, 42L);

		assertEquals(11L, detail.get("settlementId"));
		assertEquals(1L, detail.get("orderId"));
		assertEquals("holding", detail.get("status"));
		assertEquals(new BigDecimal("100.00"), detail.get("netAmount"));
		assertEquals("mock", detail.get("channel"));
	}

}