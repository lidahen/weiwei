package com.twelvet.product.server.mapper;

import com.twelvet.product.api.domain.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProductMapper {

	List<Product> selectList(@Param("status") String status, @Param("catL1") String catL1, @Param("city") String city,
			@Param("keyword") String keyword, @Param("sellerId") Long sellerId, @Param("orderBy") String orderBy,
			@Param("tenantId") Long tenantId);

	/**
	 * 面向 AI 导购的检索:支持价格区间、成色与结果数量限制(不影响既有 selectList)
	 */
	List<Product> selectForAi(@Param("status") String status, @Param("catL1") String catL1, @Param("city") String city,
			@Param("keyword") String keyword, @Param("priceMin") BigDecimal priceMin,
			@Param("priceMax") BigDecimal priceMax, @Param("condition") String condition,
			@Param("orderBy") String orderBy, @Param("limit") Integer limit, @Param("tenantId") Long tenantId);

	Product selectById(@Param("productId") Long productId, @Param("tenantId") Long tenantId);

	List<Product> selectBySellerId(@Param("sellerId") Long sellerId, @Param("excludeProductId") Long excludeProductId,
			@Param("limit") Integer limit, @Param("tenantId") Long tenantId);

	// ====== 推荐召回（docs/二手市场首页推荐设计.md） ======

	/** 兴趣类目召回：指定一级类目的在售商品（近 days 天发布，时间倒序） */
	List<Product> selectInterestCandidates(@Param("tenantId") Long tenantId, @Param("catL1s") List<String> catL1s,
			@Param("days") Integer days, @Param("limit") Integer limit);

	/** 热门召回：在售商品按 hot_score 倒序 */
	List<Product> selectHotCandidates(@Param("tenantId") Long tenantId, @Param("limit") Integer limit);

	/** 新品召回：近 days 天发布的在售商品，时间倒序 */
	List<Product> selectRecentCandidates(@Param("tenantId") Long tenantId, @Param("days") Integer days,
			@Param("limit") Integer limit);

	/** 同城召回：指定城市的在售商品（近 days 天，时间倒序；受用户附近推荐开关控制） */
	List<Product> selectCityCandidates(@Param("tenantId") Long tenantId, @Param("city") String city,
			@Param("days") Integer days, @Param("limit") Integer limit);

	/** 关注流：指定卖家的在售商品，时间倒序 */
	List<Product> selectFollowFeed(@Param("tenantId") Long tenantId, @Param("sellerIds") List<Long> sellerIds);

	/** 按ID批量查在售商品（向量召回命中后加载；顺序由 Java 按 score 重排） */
	List<Product> selectOnsaleByIds(@Param("tenantId") Long tenantId, @Param("productIds") List<Long> productIds);

	/** 相似商品降级：同类目在售热门（排除自身） */
	List<Product> selectSimilarFallback(@Param("tenantId") Long tenantId, @Param("catL1") String catL1,
			@Param("excludeProductId") Long excludeProductId, @Param("limit") Integer limit);

	/** 全量刷新商品热度分（gravity 时间衰减，Quartz 每小时调用） */
	int refreshHotScore();

	int incrementView(@Param("productId") Long productId, @Param("tenantId") Long tenantId);

	List<Long> selectFavoriteProductIds(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

	/** 批量查询指定用户的指定商品收藏状态 */
	List<Long> selectFavoriteProductIdsByUserAndIds(@Param("userId") Long userId,
			@Param("productIds") List<Long> productIds, @Param("tenantId") Long tenantId);

	// ====== 发布/编辑/删除 ======

	int insert(Product product);

	int update(Product product);

	int updateStatus(@Param("productId") Long productId, @Param("status") String status,
			@Param("rejectReason") String rejectReason, @Param("tenantId") Long tenantId);

	/**
	 * 带前置状态守卫的更新（举报处置专用）
	 * <p>
	 * 状态判断必须在 SQL 里完成：先在 Java 侧读 status 再无条件 UPDATE，读与写之间买家可能已下单把状态改成
	 * {@code locked}，覆盖后已付款订单的 {@code markSold} 永远不再命中。
	 * </p>
	 * @param productId 商品ID
	 * @param status 目标状态
	 * @param expectedStatuses 允许被更新的前置状态（为空集合时不做任何更新）
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 影响行数；0 表示前置状态不满足或行不存在，调用方须按处置失败处理
	 */
	int updateStatusIfIn(@Param("productId") Long productId, @Param("status") String status,
			@Param("expectedStatuses") List<String> expectedStatuses, @Param("tenantId") Long tenantId);

	/**
	 * 按 ID 批量查商品（举报处理用：已下架/已驳回的商品也要能显示摘要，故不过滤 status）
	 * @param productIds 商品ID列表
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 商品列表
	 */
	List<Product> selectByIds(@Param("productIds") List<Long> productIds, @Param("tenantId") Long tenantId);

	/**
	 * 原子扣减库存（下单专用，防超卖）
	 * <p>
	 * 一条 UPDATE 同时兜底两类并发问题：{@code stock > 0} 防超卖（两买家同拍库存 1 恰好一单成功）、
	 * {@code status = 'onsale'} 防拍下非在售商品。影响行数为 0 表示商品已下架或已被拍下。 扣至 0 时置为
	 * {@code locked}（已锁定待支付）。
	 * </p>
	 * @param productId 商品ID
	 * @param tenantId 租户ID(为 null 不过滤，租户开关关闭时兜底)
	 * @return 影响行数，0 表示扣减失败
	 */
	int deductStock(@Param("productId") Long productId, @Param("tenantId") Long tenantId);

	/**
	 * 回补库存（订单取消/超时关单专用，与 {@link #deductStock} 逆操作）
	 * <p>
	 * stock + 1 回补；若商品因扣减被置为 {@code locked} 则恢复为 {@code onsale}，
	 * 其余状态保持不变（如已被卖家下架则不该被取消动作悄悄重新上架）。 调用方必须保证仅在同一订单原子取消成功（rows=1）后调用一次，防止双倍回补。
	 * </p>
	 * @param productId 商品ID
	 * @param tenantId 租户ID(为 null 不过滤，租户开关关闭时兜底)
	 * @return 影响行数
	 */
	int restoreStock(@Param("productId") Long productId, @Param("tenantId") Long tenantId);

	/**
	 * 交易完成标记商品已售（确认收货/自动确认专用）
	 * <p>
	 * {@code stock=0 AND status='locked'} 双守卫：多库存商品不会被误标 sold（仍有库存可继续卖）， 非锁定状态（如已下架）不被改动。
	 * </p>
	 * @param productId 商品ID
	 * @param tenantId 租户ID(为 null 不过滤，租户开关关闭时兜底)
	 * @return 影响行数
	 */
	int markSold(@Param("productId") Long productId, @Param("tenantId") Long tenantId);

	int deleteById(@Param("productId") Long productId, @Param("tenantId") Long tenantId);

	// ====== 草稿 ======

	String selectDraftBySellerId(@Param("sellerId") Long sellerId);

	int upsertDraft(@Param("sellerId") Long sellerId, @Param("snapshot") String snapshot);

	int deleteDraftBySellerId(@Param("sellerId") Long sellerId);

	Long countSoldProducts(@Param("sellerId") Long sellerId);

}