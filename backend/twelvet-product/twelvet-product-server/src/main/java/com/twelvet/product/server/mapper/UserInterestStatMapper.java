package com.twelvet.product.server.mapper;

import com.twelvet.product.server.domain.UserInterestStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户兴趣画像 Mapper（推荐召回）
 */
@Mapper
public interface UserInterestStatMapper {

	/** 全量重建：清空行为聚合画像（source='init' 的新用户引导种子保留） */
	int deleteAll();

	/** 浏览行为聚合（权重 1，衰减按 view_time） */
	int insertViewInterest();

	/** 收藏行为聚合（权重 3，衰减按 fav_time，累加到已有行） */
	int insertFavoriteInterest();

	/** 下单行为聚合（权重 5，衰减按 create_time，累加到已有行） */
	int insertOrderInterest();

	/** 用户兴趣 top 类目（按 cat_l1 汇总权重倒序；含 behavior 与 init 两类来源） */
	List<UserInterestStat> selectTopCats(@Param("userId") Long userId, @Param("tenantId") Long tenantId,
			@Param("limit") Integer limit);

	/** 实时兴趣：单事件增量 upsert（浏览=1/收藏=3），按商品类目落行 */
	int bumpInterestByProduct(@Param("userId") Long userId, @Param("productId") Long productId,
			@Param("weight") int weight);

	/** 新用户引导：清掉该用户旧种子行 */
	int deleteInitInterest(@Param("userId") Long userId);

	/** 新用户引导：写入类目种子行（source='init'，权重 5） */
	int insertInitInterest(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
			@Param("cats") List<String> cats);

	/** 用户近 N 天浏览过的商品 ID（推荐去重/向量 seed/协同 seed 用） */
	List<Long> selectRecentViewedProductIds(@Param("userId") Long userId, @Param("days") Integer days);

	/** 写入一条浏览记录（仅登录用户；匿名浏览只累加 view_count） */
	int insertViewLog(@Param("productId") Long productId, @Param("userId") Long userId);

	/** 浏览去重：同用户同商品 minutes 分钟内已浏览过则返回 1（防 F5 刷热度/画像） */
	int selectRecentViewExists(@Param("userId") Long userId, @Param("productId") Long productId,
			@Param("minutes") Integer minutes);

	/** "看过又看"协同（详情页版）：单商品 seed，按共现用户数倒序 */
	List<Long> selectAlsoViewedForProduct(@Param("productId") Long productId, @Param("days") Integer days,
			@Param("limit") Integer limit);

	/** 有在售商品的租户 ID 列表（好物日报/向量同步按租户遍历用） */
	List<Long> selectDistinctOnsaleTenants();

	/** 昨日浏览明细聚合进 product_stats_daily（幂等覆盖当日桶） */
	int upsertYesterdayViewStats();

	/** 清理 N 天前的浏览流水（保留策略） */
	int deleteOldViewLogs(@Param("days") Integer days);

	/** 清理 N 天前的负反馈流水 */
	int deleteOldDislikes(@Param("days") Integer days);

	/** "看过又看"协同：与 seed 商品被相同用户浏览过的其他商品（按共现用户数倒序） */
	List<Long> selectAlsoViewed(@Param("seedIds") List<Long> seedIds, @Param("days") Integer days,
			@Param("limit") Integer limit);

	/** 记录负反馈（"不感兴趣"） */
	int insertDislike(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
			@Param("productId") Long productId);

	/** 用户近 N 天负反馈（productId 精确排除 + catL1 打分降权聚合） */
	List<java.util.Map<String, Object>> selectUserDislikes(@Param("userId") Long userId, @Param("days") Integer days);

}
