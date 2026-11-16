package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评价读取接口的匿名可访问性契约测试。
 *
 * <p>
 * 商品详情页（/product/detail/:id）与用户主页（/user/profile/:userId）已开放给游客浏览，
 * 这两个页面都会调用评价列表接口。该接口若要求登录，游客请求会 401，而 401 走全局错误处理， 在公开页会把游客踢去登录页 —— 公开页对游客失效。
 * </p>
 *
 * <p>
 * 生产环境（pro profile）的 security.ignore 白名单不含 /trade/review/**（只有 dev profile 含），
 * 所以「靠白名单放行」在 pro 下必失效。本测试钉死的是注解层面的可匿名访问， 与 profile 无关，避免 dev 能跑、pro 一上线就 401 的隐性断裂。
 * </p>
 */
class TradeReviewControllerAnonymousAccessTest {

	private static boolean hasSaIgnore(Method method) {
		if (method.getAnnotation(SaIgnore.class) != null) {
			return true;
		}
		SaIgnore onClass = method.getDeclaringClass().getAnnotation(SaIgnore.class);
		return onClass != null;
	}

	@Test
	@DisplayName("商品页评价列表可匿名访问")
	void productReviewsIsAnonymous() throws NoSuchMethodException {
		Method m = TradeReviewController.class.getMethod("productReviews", Long.class, int.class, int.class);
		assertTrue(hasSaIgnore(m), "productReviews 必须带 @SaIgnore，否则游客访问商品详情页会被 401 踢去登录");
	}

	@Test
	@DisplayName("卖家收到的评价列表可匿名访问")
	void userReviewsIsAnonymous() throws NoSuchMethodException {
		Method m = TradeReviewController.class.getMethod("userReviews", Long.class, int.class, int.class);
		assertTrue(hasSaIgnore(m), "userReviews 必须带 @SaIgnore，否则游客访问用户主页会被 401 踢去登录");
	}

	@Test
	@DisplayName("写操作（提交评价）不得匿名访问")
	void submitIsNotAnonymous() throws NoSuchMethodException {
		Method m = TradeReviewController.class.getMethod("submit", java.util.Map.class);
		assertTrue(m.getAnnotation(SaIgnore.class) == null, "submit 是写操作，绝不能匿名可访问");
	}

}
