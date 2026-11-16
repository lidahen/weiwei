package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.mapper.ProductMapper;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 运营端退款分页测试
 * <p>
 * 锁定不变量：`overdue` 超时筛选标志必须原样透传到 Mapper（SQL 侧按状态与停留时长判定）， 不得在 Service 层被丢弃或反转。
 * </p>
 */
class TradeRefundServiceImplAdminPageTest {

	private static final Long TENANT_ID = 42L;

	private final TradeRefundMapper refundMapper = mock(TradeRefundMapper.class);

	private final TradeRefundServiceImpl service = new TradeRefundServiceImpl(mock(TradeOrderMapper.class),
			refundMapper, mock(TradeRefundLogMapper.class), mock(TradePayLookupMapper.class),
			mock(IPayOrderService.class), mock(TradePayFlowMapper.class), mock(IImService.class),
			mock(ProductMapper.class), new TradeRefundTimeoutHelper(), mock(IFunnelEventService.class),
			mock(ISettlementService.class));

	@BeforeEach
	void setUp() {
		TenantContext.set(TENANT_ID);
		// PageUtils.startPage() 通过 ServletUtils 读 current / pageSize，单测必须提供请求上下文
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("current", "1");
		request.setParameter("pageSize", "10");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	@AfterEach
	void tearDown() {
		RequestContextHolder.resetRequestAttributes();
		TenantContext.clear();
	}

	@Test
	@DisplayName("overdue=true 原样透传给 Mapper（超时筛选由 SQL 判定）")
	void adminPage_overdue_passesFlagToMapper() {
		when(refundMapper.selectAdminList("mediation", true, TENANT_ID)).thenReturn(List.of());

		service.adminPage("mediation", true);

		verify(refundMapper).selectAdminList("mediation", true, TENANT_ID);
	}

}