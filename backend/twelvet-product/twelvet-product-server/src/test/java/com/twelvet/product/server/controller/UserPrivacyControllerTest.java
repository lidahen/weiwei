package com.twelvet.product.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.utils.SpringContextHolder;
import com.twelvet.product.server.service.IUserPrivacyService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.MessageSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 隐私设置接口测试
 * <p>
 * 锁定不变量：userId 只取登录态；body 里的 userId 被忽略；值解析后透传（Boolean/字符串 0/1 也接受）， 非法值由 Service
 * 抛业务异常，Controller 不吞。
 * </p>
 */
class UserPrivacyControllerTest {

	private static final Long LOGIN_ID = 101L;

	private final IUserPrivacyService privacyService = mock(IUserPrivacyService.class);

	private final UserPrivacyController controller = new UserPrivacyController(privacyService);

	@BeforeAll
	static void initI18n() {
		ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
		when(beanFactory.getBean(MessageSource.class)).thenReturn(mock(MessageSource.class));
		new SpringContextHolder().postProcessBeanFactory(beanFactory);
	}

	@Test
	@DisplayName("GET 用登录态读隐私设置")
	void get_usesLoginId() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(LOGIN_ID);
			Map<String, Object> privacy = new LinkedHashMap<>();
			privacy.put("allowMobileSearch", 1);
			privacy.put("allowFeedByStranger", 1);
			privacy.put("enableNearbyRecommend", 1);
			when(privacyService.getPrivacy(LOGIN_ID)).thenReturn(privacy);

			JsonResult<Map<String, Object>> result = controller.get();

			assertEquals(privacy, result.getData());
		}
	}

	@Test
	@DisplayName("POST 的 userId 永远取自登录态，请求体里的 userId 被忽略")
	void ignoresBodyUserId() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(LOGIN_ID);
			Map<String, Object> body = new HashMap<>();
			body.put("userId", 999L);
			body.put("allowMobileSearch", 0);

			controller.save(body);

			verify(privacyService).savePrivacy(eq(LOGIN_ID), eq(0), isNull(), isNull());
		}
	}

	@Test
	@DisplayName("Boolean / 字符串 0|1 归一为整数，null 字段保持 null（原值）")
	void save_normalizesFlagValues() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(LOGIN_ID);
			Map<String, Object> body = new HashMap<>();
			body.put("allowMobileSearch", true);
			body.put("allowFeedByStranger", "0");
			body.put("enableNearbyRecommend", null);

			controller.save(body);

			verify(privacyService).savePrivacy(LOGIN_ID, 1, 0, null);
		}
	}

	@Test
	@DisplayName("非法值由 Service 抛业务异常，Controller 不吞")
	void rejectsInvalidValue() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(LOGIN_ID);
			doThrow(new TWTException("开关值只能是 0 或 1")).when(privacyService).savePrivacy(LOGIN_ID, 5, null, null);
			Map<String, Object> body = new HashMap<>();
			body.put("allowMobileSearch", 5);

			assertEquals("开关值只能是 0 或 1", assertThrows(TWTException.class, () -> controller.save(body)).getMessage());
		}
	}

}