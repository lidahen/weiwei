package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.utils.SpringContextHolder;
import com.twelvet.trade.server.service.ITradeRefundService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.MessageSource;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 运营退款管理接口测试
 * <p>
 * 锁定不变量：四个端点必须都带 marketplace:admin:refund 权限；detail 不校验买卖家归属但仍走 Service 的租户过滤； judge 的非法
 * decision 由 Service 抛业务异常（Controller 不吞）。
 * </p>
 */
class AdminRefundControllerTest {

	private static final Long REFUND_ID = 9L;

	private static final Long OPERATOR_ID = 900L;

	private final ITradeRefundService refundService = mock(ITradeRefundService.class);

	private final AdminRefundController controller = new AdminRefundController(refundService);

	@BeforeAll
	static void initI18n() {
		ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
		when(beanFactory.getBean(MessageSource.class)).thenReturn(mock(MessageSource.class));
		new SpringContextHolder().postProcessBeanFactory(beanFactory);
	}

	private static void assertAdminPermission(Method method) {
		SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
		if (permission == null) {
			permission = method.getDeclaringClass().getAnnotation(SaCheckPermission.class);
		}
		org.junit.jupiter.api.Assertions.assertNotNull(permission, method.getName() + " 缺少 @SaCheckPermission");
		org.junit.jupiter.api.Assertions.assertTrue(
				java.util.Arrays.asList(permission.value()).contains("marketplace:admin:refund"),
				method.getName() + " 权限必须是 marketplace:admin:refund");
	}

	@Test
	@DisplayName("四个端点都带 marketplace:admin:refund 注解")
	void allEndpointsAnnotated() throws NoSuchMethodException {
		assertAdminPermission(AdminRefundController.class.getMethod("page", String.class, Boolean.class));
		assertAdminPermission(AdminRefundController.class.getMethod("detail", Long.class));
		assertAdminPermission(AdminRefundController.class.getMethod("judge", Map.class));
		assertAdminPermission(AdminRefundController.class.getMethod("resolve", Map.class));
	}

	@Test
	@DisplayName("judge 的 decision 非法值由 Service 抛业务异常")
	void judge_invalidDecision() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(OPERATOR_ID);
			org.mockito.Mockito.doThrow(new TWTException("裁决结论不合法"))
				.when(refundService)
				.judge(REFUND_ID, "bogus", null, "理由", OPERATOR_ID);
			Map<String, Object> body = new HashMap<>();
			body.put("refundId", REFUND_ID);
			body.put("decision", "bogus");
			body.put("reason", "理由");

			assertEquals("裁决结论不合法", assertThrows(TWTException.class, () -> controller.judge(body)).getMessage());
		}
	}

	@Test
	@DisplayName("detail 不校验买卖家归属（运营要看全量）")
	void detail_noOwnershipCheck() {
		Map<String, Object> detail = new HashMap<>();
		detail.put("refund", new Object());
		detail.put("logs", java.util.List.of());
		detail.put("order", new Object());
		when(refundService.adminDetail(REFUND_ID)).thenReturn(detail);

		JsonResult<Map<String, Object>> result = controller.detail(REFUND_ID);

		assertEquals(detail, result.getData());
		verify(refundService).adminDetail(REFUND_ID);
	}

	@Test
	@DisplayName("judge 的 operatorId 取自登录态并透传")
	void judge_usesLoginId() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(OPERATOR_ID);
			Map<String, Object> body = new HashMap<>();
			body.put("refundId", REFUND_ID);
			body.put("decision", "partial");
			body.put("amount", "500.00");
			body.put("reason", "各让一步");

			controller.judge(body);

			verify(refundService).judge(REFUND_ID, "partial", new BigDecimal("500.00"), "各让一步", OPERATOR_ID);
		}
	}

}