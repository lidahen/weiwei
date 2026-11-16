package com.twelvet.product.server.service;

import java.util.Map;

/**
 * 用户隐私设置 Service
 *
 * @author twelvet
 */
public interface IUserPrivacyService {

	/**
	 * 读取隐私三开关（无行或列为 NULL 时一律视为开启）
	 * @param userId 用户ID
	 * @return {allowMobileSearch: 1|0, allowFeedByStranger: 1|0, enableNearbyRecommend:
	 * 1|0}
	 */
	Map<String, Object> getPrivacy(Long userId);

	/**
	 * 保存隐私三开关（null 字段保持原值）
	 * @param userId 用户ID
	 * @param allowMobileSearch 手机号搜索开关（0/1，null 保持原值）
	 * @param allowFeedByStranger 陌生人私信开关（0/1，null 保持原值）
	 * @param enableNearbyRecommend 附近推荐开关（0/1，null 保持原值）
	 */
	void savePrivacy(Long userId, Integer allowMobileSearch, Integer allowFeedByStranger,
			Integer enableNearbyRecommend);

}