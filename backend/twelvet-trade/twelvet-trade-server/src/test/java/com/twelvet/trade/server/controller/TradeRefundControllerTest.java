package com.twelvet.trade.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.utils.SpringContextHolder;
import com.twelvet.trade.server.service.ITradeRefundService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * C 端与卖家端退款接口测试（Controller 直连 mock Service，不启 Spring 上下文）
 * <p>
 * 锁定不变量：身份一律来自登录态（请求体里的 userId 必须被忽略）；非买卖家看不到详情； body 值为 null 时不得产生字面量 "null"。
 * </p>
 */
class TradeRefundControllerTest {

	private static final Long BUYER_ID = 101L;

	private static final Long ORDER_ID = 1L;

	private static final Long REFUND_ID = 9L;

	private final ITradeRefundService refundService = mock(ITradeRefundService.class);

	private final TradeRefundController controller = new TradeRefundController(refundService);

	/**
	 * JsonResult.success 走 I18nUtils，其静态初始化要求 SpringContextHolder 中有 BeanFactory
	 */
	@BeforeAll
	static void initI18n() {
		ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
		when(beanFactory.getBean(MessageSource.class)).thenReturn(mock(MessageSource.class));
		new SpringContextHolder().postProcessBeanFactory(beanFactory);
	}

	@Test
	@DisplayName("apply 的 buyerId 取自 StpUtil，忽略请求体里的任何 userId")
	void apply_usesLoginId() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(BUYER_ID);
			when(refundService.apply(eq(ORDER_ID), eq("refund_only"), eq("damaged"), eq(new BigDecimal("1107.00")),
					eq("屏幕有裂痕"), any(), eq(BUYER_ID)))
				.thenReturn(REFUND_ID);
			Map<String, Object> body = new HashMap<>();
			body.put("orderId", ORDER_ID);
			body.put("type", "refund_only");
			body.put("reasonCode", "damaged");
			body.put("amount", "1107.00");
			body.put("description", "屏幕有裂痕");
			body.put("evidenceImages", List.of("https://oss/a.jpg"));
			// 伪造的 userId 必须被忽略
			body.put("userId", 999L);

			JsonResult<Long> result = controller.apply(body);

			assertEquals(REFUND_ID, result.getData());
			org.mockito.Mockito.verify(refundService)
				.apply(eq(ORDER_ID), eq("refund_only"), eq("damaged"), eq(new BigDecimal("1107.00")), eq("屏幕有裂痕"),
						eq(List.of("https://oss/a.jpg")), eq(BUYER_ID));
		}
	}

	@Test
	@DisplayName("detail 非买卖家不可见（返回 null 而非抛异常）")
	void detail_notParticipant() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(999L);
			when(refundService.detail(REFUND_ID, 999L)).thenReturn(null);

			JsonResult<Map<String, Object>> result = controller.detail(REFUND_ID);

			assertNull(result.getData());
		}
	}

	@Test
	@DisplayName("by-order 返回 canApply / denyReason / activeRefundId")
	void byOrder_shape() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(BUYER_ID);
			Map<String, Object> check = new LinkedHashMap<>();
			check.put("canApply", false);
			check.put("denyReason", "该订单已有处理中的退款申请");
			check.put("activeRefundId", REFUND_ID);
			when(refundService.checkApply(ORDER_ID, BUYER_ID)).thenReturn(check);

			JsonResult<Map<String, Object>> result = controller.byOrder(ORDER_ID);

			assertEquals(false, result.getData().get("canApply"));
			assertEquals("该订单已有处理中的退款申请", result.getData().get("denyReason"));
			assertEquals(REFUND_ID, result.getData().get("activeRefundId"));
		}
	}

	@Test
	@DisplayName("reasons 返回六项且含 types")
	void reasons_shape() {
		JsonResult<List<Map<String, Object>>> result = controller.reasons();

		assertEquals(6, result.getData().size());
		assertEquals("not_as_described", result.getData().get(0).get("code"));
		assertTrue(result.getData().get(0).get("types") instanceof List);
	}

	@Test
	@DisplayName("body 里值为 null 时不产生字面量 \"null\"")
	void strHelper_handlesNull() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(BUYER_ID);
			when(refundService.apply(eq(ORDER_ID), isNull(), isNull(), isNull(), isNull(), isNull(), eq(BUYER_ID)))
				.thenReturn(REFUND_ID);
			Map<String, Object> body = new HashMap<>();
			body.put("orderId", ORDER_ID);
			body.put("type", null);
			body.put("reasonCode", null);
			body.put("description", null);

			controller.apply(body);

			org.mockito.Mockito.verify(refundService)
				.apply(eq(ORDER_ID), isNull(), isNull(), isNull(), isNull(), isNull(), eq(BUYER_ID));
		}
	}

}