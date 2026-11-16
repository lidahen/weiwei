package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.server.service.IReportService;
import com.twelvet.trade.server.mapper.AdminMarketplaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 运营举报接口委托契约。
 * <p>
 * /admin/marketplace/report/* 的 URL 与权限点保持不变（前端与菜单表已引用），内部改为委托 product 侧 IReportService
 * —— 保证 report 表只有一处读写 SQL。
 * </p>
 * <p>
 * 不测 Controller：handleReport 取 {@code StpUtil.getLoginIdAsLong()} 需要 Sa-Token 请求上下文，
 * standalone MockMvc 里没有。委托发生在 Service 层，测 Service 即可覆盖真实逻辑。
 * </p>
 *
 * @author twelvet
 */
class AdminReportDelegationTest {

	private AdminMarketplaceMapper adminMapper;

	private IReportService reportService;

	private AdminMarketplaceServiceImpl service;

	@BeforeEach
	void setUp() {
		adminMapper = mock(AdminMarketplaceMapper.class);
		reportService = mock(IReportService.class);
		service = new AdminMarketplaceServiceImpl(adminMapper, reportService);
	}

	@Test
	@DisplayName("列表委托 adminList，TableDataInfo 原样返回")
	void selectReportList_delegates() {
		TableDataInfo<Map<String, Object>> stub = TableDataInfo.page(List.of(Map.of("reportId", 1L)), 1L);
		when(reportService.adminList("all")).thenReturn(stub);

		TableDataInfo<Map<String, Object>> result = service.selectReportList("all");

		verify(reportService).adminList("all");
		assertSame(stub, result);
		assertEquals(1L, result.getTotal());
		// 举报列表不再走 trade 自己的 SQL（Mapper 已删方法，见 mapperHasNoReportSql）
		verifyNoInteractions(adminMapper);
	}

	@Test
	@DisplayName("处理委托 adminHandle，handlerId 原样透传")
	void handleReport_delegates() {
		assertEquals("已处理", service.handleReport(1L, "remove_product", "备注", 100L));
		verify(reportService).adminHandle(1L, "remove_product", "备注", 100L);
		verifyNoInteractions(adminMapper);
	}

	@Test
	@DisplayName("AdminMarketplaceMapper 不再声明举报 SQL（单一读写点）")
	void mapperHasNoReportSql() {
		for (Method m : AdminMarketplaceMapper.class.getMethods()) {
			if ("selectReportList".equals(m.getName()) || "handleReport".equals(m.getName())) {
				throw new AssertionError("举报 SQL 必须只在 product 的 ReportMapper 里：" + m.getName());
			}
		}
	}

	@Test
	@DisplayName("处置异常原样上抛，不吞成已处理")
	void handleReport_propagatesError() {
		org.mockito.Mockito.doThrow(new com.twelvet.framework.core.exception.TWTException("该举报已被处理"))
			.when(reportService)
			.adminHandle(anyLong(), anyString(), anyString(), anyLong());

		assertThrows(com.twelvet.framework.core.exception.TWTException.class,
				() -> service.handleReport(1L, "dismiss", "", 100L));
	}

}
