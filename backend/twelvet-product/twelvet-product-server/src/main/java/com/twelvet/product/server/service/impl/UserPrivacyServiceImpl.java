package com.twelvet.product.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.service.IUserPrivacyService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户隐私设置 Service 实现
 * <p>
 * user_privacy 无行/NULL 列一律视为开启（1），与推荐流的 selectNearbyRecommendEnabled 口径一致； 保存时 null
 * 字段原样透传给 SQL，由 COALESCE 保持原值。
 * </p>
 *
 * @author twelvet
 */
@Service
public class UserPrivacyServiceImpl implements IUserPrivacyService {

	private static final String DENY_FLAG_INVALID = "开关值只能是 0 或 1";

	private final UserMapper userMapper;

	public UserPrivacyServiceImpl(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	@Override
	public Map<String, Object> getPrivacy(Long userId) {
		Map<String, Object> row = userMapper.selectPrivacy(userId);
		Map<String, Object> result = new LinkedHashMap<>(4);
		result.put("allowMobileSearch", flag(row, "allowMobileSearch"));
		result.put("allowFeedByStranger", flag(row, "allowFeedByStranger"));
		result.put("enableNearbyRecommend", flag(row, "enableNearbyRecommend"));
		return result;
	}

	@Override
	public void savePrivacy(Long userId, Integer allowMobileSearch, Integer allowFeedByStranger,
			Integer enableNearbyRecommend) {
		validateFlag(allowMobileSearch);
		validateFlag(allowFeedByStranger);
		validateFlag(enableNearbyRecommend);
		userMapper.upsertPrivacy(userId, allowMobileSearch, allowFeedByStranger, enableNearbyRecommend);
	}

	/**
	 * 库值归一为 0/1；无行或 NULL 列视为开启
	 * <p>
	 * MySQL 的 tinyint(1) 经 JDBC 可能以 Boolean 返回（tinyInt1isBit 默认开启），两种形态都要认。
	 * </p>
	 */
	private static Integer flag(Map<String, Object> row, String key) {
		if (row == null) {
			return 1;
		}
		Object value = row.get(key);
		if (value == null) {
			return 1;
		}
		if (value instanceof Boolean b) {
			return b ? 1 : 0;
		}
		if (value instanceof Number n) {
			return n.intValue() == 0 ? 0 : 1;
		}
		return "0".equals(String.valueOf(value)) ? 0 : 1;
	}

	private static void validateFlag(Integer value) {
		if (value != null && value != 0 && value != 1) {
			throw new TWTException(DENY_FLAG_INVALID);
		}
	}

}