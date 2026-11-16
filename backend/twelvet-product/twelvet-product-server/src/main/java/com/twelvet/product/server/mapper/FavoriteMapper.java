package com.twelvet.product.server.mapper;

import com.twelvet.product.api.domain.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 收藏Mapper
 *
 * @author twelvet
 */
@Mapper
public interface FavoriteMapper {

	/**
	 * 查询收藏商品详情列表
	 * @param userId 用户ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 收藏商品列表
	 */
	List<Product> selectFavoriteProductList(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

	/**
	 * 查询收藏数量
	 * @param userId 用户ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 数量
	 */
	Long countFavorites(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

	/**
	 * 新增收藏
	 * @param userId 用户ID
	 * @param productId 商品ID
	 * @param originalPrice 收藏时价格快照(降价提醒基线)
	 * @param tenantId 租户ID(为 null 落 default)
	 * @return 结果
	 */
	int insertFavorite(@Param("userId") Long userId, @Param("productId") Long productId,
			@Param("originalPrice") java.math.BigDecimal originalPrice, @Param("tenantId") Long tenantId);

	/**
	 * 降价扫描：收藏价格快照高于现价的在售商品收藏记录
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return [{userId, productId, snapPrice, newPrice, title}]
	 */
	List<java.util.Map<String, Object>> selectPriceDrops(@Param("tenantId") Long tenantId);

	/** 存量收藏播种价格基线（original_price 为空者取现价，不通知） */
	int seedMissingSnapshots(@Param("tenantId") Long tenantId);

	/** 通知后刷新价格快照（保证同一轮降价只提醒一次） */
	int updateFavoriteSnapshot(@Param("userId") Long userId, @Param("productId") Long productId,
			@Param("price") java.math.BigDecimal price);

	/**
	 * 租户系统发送人（IM 通知 sender，取租户内最小 user_id 的在职用户，同 AI 日报做法）
	 */
	Long selectSenderUserId(@Param("tenantId") Long tenantId);

	/** 取消收藏时回减商品 want_count（GREATEST 防负数） */
	int decrementWantCount(@Param("productId") Long productId);

	/** 租户内全部在职用户 ID（好物日报推送范围） */
	List<Long> selectTenantUserIds(@Param("tenantId") Long tenantId);

	/**
	 * 删除收藏
	 * @param userId 用户ID
	 * @param productId 商品ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 结果
	 */
	int deleteFavorite(@Param("userId") Long userId, @Param("productId") Long productId,
			@Param("tenantId") Long tenantId);

	/**
	 * 增加商品想要的数量
	 * @param productId 商品ID
	 * @return 结果
	 */
	int incrementWantCount(@Param("productId") Long productId);

}
