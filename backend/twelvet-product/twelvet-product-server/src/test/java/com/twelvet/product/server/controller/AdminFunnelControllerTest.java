package com.twelvet.product.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.utils.SpringContextHolder;
import com.twelvet.product.api.service.IFunnelEventService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.MessageSource;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 社区漏斗看板接口：权限必须是 marketplace:admin:funnel，参数透传给 Service。
 */
class AdminFunnelControllerTest {

	private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);

	private final AdminFunnelController controller = new AdminFunnelController(funnelEventService);

	@BeforeAll
	static void initI18n() {
		ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
		when(beanFactory.getBean(MessageSource.class)).thenReturn(mock(MessageSource.class));
		new SpringContextHolder().postProcessBeanFactory(beanFactory);
	}

	@Test
	@DisplayName("stats 带 marketplace:admin:funnel 权限")
	void stats_hasPermission() throws NoSuchMethodException {
		Method method = AdminFunnelController.class.getMethod("stats", Integer.class, Long.class);
		SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
		if (permission == null) {
			permission = AdminFunnelController.class.getAnnotation(SaCheckPermission.class);
		}
		assertNotNull(permission, "stats 缺少 @SaCheckPermission");
		assertTrue(List.of(permission.value()).contains("marketplace:admin:funnel"));
	}

	@Test
	@DisplayName("days/tenantId 原样透传给 Service")
	void stats_delegates() {
		Map<String, Object> payload = Map.<String, Object>of("days", 7);
		when(funnelEventService.adminStats(7, 2L)).thenReturn(payload);

		JsonResult<Map<String, Object>> result = controller.stats(7, 2L);

		assertEquals(payload, result.getData());
		verify(funnelEventService).adminStats(7, 2L);
	}

}