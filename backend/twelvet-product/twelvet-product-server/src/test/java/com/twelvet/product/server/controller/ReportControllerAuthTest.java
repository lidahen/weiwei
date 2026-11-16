package com.twelvet.product.server.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 举报接口的可匿名访问性契约。
 * <p>
 * /report/submit 是写操作，绝不能 @SaIgnore —— 游客写举报会绕过租户与频率约束。 与
 * TradeReviewControllerAnonymousAccessTest 同一思路：钉注解，与 profile 无关。
 * </p>
 *
 * @author twelvet
 */
class ReportControllerAuthTest {

	@Test
	@DisplayName("提交举报不得匿名可访问")
	void submitIsNotAnonymous() throws NoSuchMethodException {
		Method m = ReportController.class.getMethod("submit", Map.class);
		assertNull(m.getAnnotation(SaIgnore.class), "submit 是写操作，绝不能匿名可访问");
		assertNull(ReportController.class.getAnnotation(SaIgnore.class), "类级也不能匿名");
	}

	@Test
	@DisplayName("动态举报仍走 FeedController，且不是匿名接口")
	void feedReportNotAnonymous() throws NoSuchMethodException {
		Method m = FeedController.class.getMethod("report", Long.class, String.class, String.class);
		assertNull(m.getAnnotation(SaIgnore.class), "动态举报是写操作，不能匿名");
	}

}
