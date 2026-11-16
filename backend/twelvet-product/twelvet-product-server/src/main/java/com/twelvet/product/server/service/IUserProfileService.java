package com.twelvet.product.server.service;

import com.twelvet.product.api.domain.Product;

import java.util.List;
import java.util.Map;

/**
 * 用户资料/关注/地址Service接口
 *
 * @author twelvet
 */
public interface IUserProfileService {

	/**
	 * 查询用户资料(聚合:用户信息+商品统计+关注统计+关注状态)
	 * @param userId 被查看用户ID
	 * @param viewerId 查看者ID(可为null)
	 * @return 用户资料聚合数据
	 */
	Map<String, Object> selectUserProfile(Long userId, Long viewerId);

	/**
	 * 卖家信用画像（信用分 + 完成率 + 失信扣分明细）
	 * <p>
	 * 口径对齐接单人信用分：base = 评分×20（无评价默认 80）， creditScore = clamp(round(base × (0.7 + 0.3 ×
	 * 近30天完成率)) - 5 × 近30天失信单数, 0, 100)。
	 * </p>
	 * @param userId 卖家用户ID
	 * @return 信用分及其构成明细，便于前端解释口径
	 */
	Map<String, Object> creditProfile(Long userId);

	/**
	 * 关注用户
	 * @param userId 当前用户ID
	 * @param targetUserId 目标用户ID
	 * @return 结果
	 */
	String followUser(Long userId, Long targetUserId);

	/**
	 * 取消关注
	 * @param userId 当前用户ID
	 * @param targetUserId 目标用户ID
	 * @return 结果
	 */
	String unfollowUser(Long userId, Long targetUserId);

	/**
	 * 查询关注/粉丝列表
	 * @param userId 用户ID
	 * @param tab 类型(follows/fans)
	 * @return 列表
	 */
	List<Map<String, Object>> selectFollowList(Long userId, String tab);

	/**
	 * 查询用户收货地址列表
	 * @param userId 用户ID
	 * @return 地址列表
	 */
	List<Map<String, Object>> selectAddressList(Long userId);

	/**
	 * 新增收货地址
	 * @param userId 用户ID
	 * @param address 地址信息
	 * @return 结果
	 */
	String saveAddress(Long userId, Map<String, Object> address);

	/**
	 * 删除收货地址
	 * @param addressId 地址ID
	 * @param userId 用户ID
	 * @return 结果
	 */
	String removeAddress(Long addressId, Long userId);

	/**
	 * 查询卖家在售商品
	 * @param sellerId 卖家ID
	 * @param excludeProductId 排除商品ID
	 * @param limit 限制数量
	 * @return 商品列表
	 */
	List<Product> selectOnSaleProducts(Long sellerId, Long excludeProductId, int limit);

}
