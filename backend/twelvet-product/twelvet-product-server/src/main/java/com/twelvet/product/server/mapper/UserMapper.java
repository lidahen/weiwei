package com.twelvet.product.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 用户Mapper(product模块内部使用,避免跨模块依赖system-server)
 *
 * @author twelvet
 */
@Mapper
public interface UserMapper {

	/**
	 * 根据用户ID查询用户信息
	 * @param userId 用户ID
	 * @return 用户信息
	 */
	Map<String, Object> selectUserById(@Param("userId") Long userId);

	/**
	 * 引导查询：按用户 ID 查租户（此时租户上下文尚未建立，查询本身不带租户过滤）。 与 TradeOrderMapper.selectUserTenantId
	 * 同名同义；绝不使用调用方传入的租户标识。
	 * @param userId 用户ID
	 * @return 租户ID；用户不存在时为 null
	 */
	Long selectUserTenantId(@Param("userId") Long userId);

	/**
	 * 根据用户ID列表批量查询用户信息
	 * @param userIds 用户ID列表
	 * @return 用户列表
	 */
	List<Map<String, Object>> selectUserByIds(@Param("userIds") List<Long> userIds);

	/**
	 * 卖家信用画像（跨模块直查 trade_review / trade_order / trade_refund / trade_dispute）
	 * <p>
	 * 口径对齐接单人信用分：评价数/好评数/均分 + 成交与完成单数（全量 + 近30天） +
	 * 失信单三类计数（退款成功、纠纷判买家胜/部分、超时未发货）与去重后的失信单总数。
	 * </p>
	 * @param userId 卖家用户ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return reviewCount/goodCount/avgScore/completedCount/paidCount/近30天计数/失信计数
	 */
	Map<String, Object> selectSellerCreditProfile(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

	/**
	 * 批量卖家好评率(推荐打分用，一次查询避免 N+1)
	 * @param sellerIds 卖家ID列表
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return [{sellerId, reviewCount, goodRate}]
	 */
	List<Map<String, Object>> selectSellerGoodRates(@Param("sellerIds") List<Long> sellerIds,
			@Param("tenantId") Long tenantId);

	/**
	 * 用户"附近推荐"隐私开关(未配置行视为开启)
	 */
	Integer selectNearbyRecommendEnabled(@Param("userId") Long userId);

	/**
	 * 用户默认收货地址城市(同城个性化用)
	 */
	String selectDefaultCity(@Param("userId") Long userId);

	/**
	 * 隐私三开关（无行返回 null，由 Service 兜底默认全开）
	 * @param userId 用户ID
	 * @return allowMobileSearch/allowFeedByStranger/enableNearbyRecommend
	 */
	Map<String, Object> selectPrivacy(@Param("userId") Long userId);

	/**
	 * 保存隐私三开关（user_id 为主键的配置表；null 字段由 SQL COALESCE 保持原值）
	 * @param userId 用户ID
	 * @param allowMobileSearch 手机号搜索开关（0/1，null 保持原值）
	 * @param allowFeedByStranger 陌生人私信开关（0/1，null 保持原值）
	 * @param enableNearbyRecommend 附近推荐开关（0/1，null 保持原值）
	 * @return 影响行数
	 */
	int upsertPrivacy(@Param("userId") Long userId, @Param("allowMobileSearch") Integer allowMobileSearch,
			@Param("allowFeedByStranger") Integer allowFeedByStranger,
			@Param("enableNearbyRecommend") Integer enableNearbyRecommend);

	/**
	 * 按 ID 批量查用户（举报处理用：含 status/del_flag 供存在性与封禁态判定，user_type 供运营账号保护）
	 * @param userIds 用户ID列表
	 * @return 用户列表，每行含 user_id/nick_name/avatar/status/del_flag/user_type
	 */
	List<Map<String, Object>> selectByIds(@Param("userIds") List<Long> userIds);

	/**
	 * 更新用户账号状态（举报处置封号用；与 trade 的 updateUserStatus 并存，避免模块依赖成环）
	 * @param userId 用户ID
	 * @param status 账号状态（0正常 1停用）
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 影响行数
	 */
	int updateStatus(@Param("userId") Long userId, @Param("status") Integer status, @Param("tenantId") Long tenantId);

}
