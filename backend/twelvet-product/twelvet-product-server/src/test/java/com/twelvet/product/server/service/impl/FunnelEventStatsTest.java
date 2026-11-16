package com.twelvet.product.server.service.impl;

import com.twelvet.product.server.mapper.FunnelEventMapper;
import com.twelvet.product.server.mapper.ProductMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 社区漏斗看板聚合。锁定的不变量：
 * <ol>
 * <li>tenantId 为空时只返回租户选项——绝不返回跨租户聚合（D7/D8：所有看板按租户切片）</li>
 * <li>转化率 = 本环节 / 上一环节，缺失环节为 null 而不是 0</li>
 * </ol>
 */
class FunnelEventStatsTest {

	private final FunnelEventMapper eventMapper = mock(FunnelEventMapper.class);

	private final FunnelEventServiceImpl service = new FunnelEventServiceImpl(eventMapper, mock(ProductMapper.class));

	@Test
	@DisplayName("tenantId 为空只返回租户选项，不查聚合数据（防串租）")
	void adminStats_withoutTenant_returnsOptionsOnly() {
		when(eventMapper.selectTenants(7))
			.thenReturn(List.of(Map.<String, Object>of("tenantId", 2, "tenantName", "试点社区")));

		Map<String, Object> result = service.adminStats(7, null);

		assertEquals(1, ((List<?>) result.get("tenants")).size());
		assertTrue(((List<?>) result.get("funnel")).isEmpty());
		verify(eventMapper, never()).selectSummary(anyInt(), any());
		verify(eventMapper, never()).selectDaily(anyInt(), any());
	}

	@Test
	@DisplayName("有租户：组装漏斗转化率（支付→确认）")
	@SuppressWarnings("unchecked")
	void adminStats_buildsConversion() {
		when(eventMapper.selectTenants(7)).thenReturn(List.of());
		when(eventMapper.selectSummary(7, 2L)).thenReturn(List.of(
				Map.<String, Object>of("eventType", "product_exposure", "count", 100L, "uv", 50L),
				Map.<String, Object>of("eventType", "pay_success", "count", 10L, "uv", 8L),
				Map.<String, Object>of("eventType", "confirm_receipt", "count", 8L, "uv", 7L)));
		when(eventMapper.selectDaily(7, 2L)).thenReturn(List.of());

		Map<String, Object> result = service.adminStats(7, 2L);

		List<Map<String, Object>> funnel = (List<Map<String, Object>>) result.get("funnel");
		assertEquals(6, funnel.size(), "漏斗固定 6 步：曝光→详情→会话→下单→支付→确认");
		Map<String, Object> confirm = funnel.stream()
			.filter(row -> "confirm_receipt".equals(row.get("key")))
			.findFirst()
			.orElseThrow();
		assertEquals(0.8, ((Number) confirm.get("conversion")).doubleValue(), 0.001);
		Map<String, Object> pay = funnel.stream()
			.filter(row -> "pay_success".equals(row.get("key")))
			.findFirst()
			.orElseThrow();
		assertEquals(null, pay.get("conversion"), "上一环节（下单）无数据时转化率为 null，不是 0");
		Map<String, Object> detail = funnel.stream()
			.filter(row -> "product_detail_view".equals(row.get("key")))
			.findFirst()
			.orElseThrow();
		assertEquals(0.0, ((Number) detail.get("conversion")).doubleValue(), 0.001, "本环节 0 次时转化率是 0");
	}

	@Test
	@DisplayName("days 缺省 7，越界夹到 1..90")
	void adminStats_daysClamped() {
		when(eventMapper.selectTenants(90)).thenReturn(List.of());
		when(eventMapper.selectSummary(90, 2L)).thenReturn(List.of());
		when(eventMapper.selectDaily(90, 2L)).thenReturn(List.of());

		service.adminStats(365, 2L);

		verify(eventMapper).selectTenants(90);
	}

}