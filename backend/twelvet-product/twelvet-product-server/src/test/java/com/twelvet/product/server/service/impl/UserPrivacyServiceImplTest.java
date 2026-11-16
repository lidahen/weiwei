package com.twelvet.product.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.product.server.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 隐私三开关 Service 测试
 * <p>
 * 锁定不变量：无行/NULL 列一律视为开启（1）；只接受 0/1；null 字段表示保持原值（原样透传给 SQL 的 COALESCE）。
 * </p>
 */
class UserPrivacyServiceImplTest {

	private static final Long USER_ID = 101L;

	private final UserMapper userMapper = mock(UserMapper.class);

	private final UserPrivacyServiceImpl service = new UserPrivacyServiceImpl(userMapper);

	@Test
	@DisplayName("无记录时返回默认全开")
	void get_noRow_returnsDefaults() {
		when(userMapper.selectPrivacy(USER_ID)).thenReturn(null);

		Map<String, Object> result = service.getPrivacy(USER_ID);

		assertEquals(1, result.get("allowMobileSearch"));
		assertEquals(1, result.get("allowFeedByStranger"));
		assertEquals(1, result.get("enableNearbyRecommend"));
	}

	@Test
	@DisplayName("有行但列为 NULL 时同样默认开启")
	void get_nullColumns_returnsDefaults() {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("allowMobileSearch", null);
		row.put("allowFeedByStranger", null);
		row.put("enableNearbyRecommend", null);
		when(userMapper.selectPrivacy(USER_ID)).thenReturn(row);

		Map<String, Object> result = service.getPrivacy(USER_ID);

		assertEquals(1, result.get("allowMobileSearch"));
		assertEquals(1, result.get("allowFeedByStranger"));
		assertEquals(1, result.get("enableNearbyRecommend"));
	}

	@Test
	@DisplayName("有值时按库中取值返回（Boolean 也归一为 0/1）")
	void get_presentValues() {
		Map<String, Object> row = new HashMap<>();
		row.put("allowMobileSearch", 0);
		row.put("allowFeedByStranger", true);
		row.put("enableNearbyRecommend", false);
		when(userMapper.selectPrivacy(USER_ID)).thenReturn(row);

		Map<String, Object> result = service.getPrivacy(USER_ID);

		assertEquals(0, result.get("allowMobileSearch"));
		assertEquals(1, result.get("allowFeedByStranger"));
		assertEquals(0, result.get("enableNearbyRecommend"));
	}

	@Test
	@DisplayName("保存时 null 字段原样透传（SQL COALESCE 保持原值）")
	void save_nullFieldsPassedThrough() {
		service.savePrivacy(USER_ID, 0, null, null);

		verify(userMapper).upsertPrivacy(USER_ID, 0, null, null);
	}

	@Test
	@DisplayName("开关值只接受 0/1")
	void rejectsInvalidValue() {
		assertEquals("开关值只能是 0 或 1",
				assertThrows(TWTException.class, () -> service.savePrivacy(USER_ID, 2, null, null)).getMessage());
		assertEquals("开关值只能是 0 或 1",
				assertThrows(TWTException.class, () -> service.savePrivacy(USER_ID, null, -1, null)).getMessage());
		verifyNoInteractions(userMapper);
	}

}