package com.twelvet.im.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 用户Mapper(IM模块内部使用)
 *
 * @author twelvet
 */
@Mapper
public interface ImUserMapper {

	/**
	 * 查询用户基本信息(仅返回IM需要的字段)
	 * @param userId 用户ID
	 * @return 用户信息
	 */
	Map<String, Object> selectUserBaseInfo(@Param("userId") Long userId);

	/**
	 * 查询用户归属租户（商业化第三轮：IM 跨租户发送校验）
	 * @param userId 用户ID
	 * @return 租户ID；用户不存在返回 null
	 */
	Long selectTenantIdByUserId(@Param("userId") Long userId);

	/**
	 * 用户在指定租户下是否有在售商品（P2 修复：marketplace 聊天对卖家放行的依据—— 卖家可能跨租户卖货而账号绑定在单一租户，见第四轮
	 * global_user_id）
	 * @param userId 用户ID
	 * @param tenantId 租户ID
	 * @return 在售商品数
	 */
	Long countOnsaleProductsInTenant(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

}
