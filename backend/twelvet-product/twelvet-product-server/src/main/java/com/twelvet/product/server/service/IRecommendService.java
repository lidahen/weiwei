package com.twelvet.product.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.api.domain.Product;

import java.util.List;
import java.util.Map;

/**
 * 首页推荐服务（docs/二手市场首页推荐设计.md）
 *
 * @author twelvet
 */
public interface IRecommendService {

	/**
	 * 首页推荐流：三路召回（兴趣类目/热门/新品）+ 规则打分 + 类目打散
	 * @param userId 当前用户ID(可空=匿名，降级热门+新品)
	 * @param catL1 一级类目过滤(可空，显式过滤时跳过类目打散)
	 * @param current 页码(1起)
	 * @param pageSize 每页条数
	 * @return 推荐流(含 reason 推荐理由)
	 */
	TableDataInfo<Product> homeRecommend(Long userId, String catL1, int current, int pageSize);

	/**
	 * 关注流：关注卖家的在售商品(时间倒序；内部自行 startPage，调用方用 PageUtils.getDataTable 包装)
	 * @param userId 当前用户ID(可空返回空列表)
	 * @return 商品列表
	 */
	List<Product> selectFollowFeed(Long userId);

	/**
	 * 全量重建用户兴趣画像(Quartz 每30分钟)
	 * @return 画像行数
	 */
	int refreshInterestStat();

	/**
	 * 全量刷新商品热度分(Quartz 每小时)
	 * @return 更新商品数
	 */
	int refreshHotScore();

	/**
	 * 详情页相似商品：向量召回命中 → 加载商品；向量通道不可用时降级同类目热门
	 * @param productId 商品ID
	 * @param limit 数量
	 * @return 商品列表(带 reason)
	 */
	List<Product> selectSimilar(Long productId, int limit);

	/**
	 * 同步商品向量索引到 ai-service（Quartz 每日 04:30，增量）
	 * @return 索引内商品总数
	 */
	int syncVectors();

	/**
	 * 推荐效果统计（管理端）：每日曝光/点击/UV（按 A/B 组拆分）+ 点击 Top 商品
	 * @param days 统计天数
	 * @param bucket A/B 组过滤(可空=全部；0=A 默认/1=B 实验组)
	 * @return {daily: [...], top: [...]}
	 */
	Map<String, Object> recommendStats(Integer days, Integer bucket);

	/**
	 * 记录一次商品浏览（view_count+1；登录用户额外写 product_view_log 流水， 供兴趣画像/向量 seed/CTR 统计消费。静默失败）
	 * @param productId 商品ID
	 * @param userId 用户ID(可空=匿名)
	 */
	void recordView(Long productId, Long userId);

	/**
	 * 记录一次搜索关键词（兴趣信号基础设施，静默失败）
	 * @param userId 用户ID(可空)
	 * @param keyword 关键词
	 */
	void logSearchKeyword(Long userId, String keyword);

	/**
	 * 实时兴趣更新：按商品类目增量 upsert 用户画像（浏览=1/收藏=3，静默失败）
	 * @param userId 用户ID
	 * @param productId 商品ID
	 * @param weight 行为权重
	 */
	void bumpInterest(Long userId, Long productId, int weight);

	/**
	 * 新用户兴趣引导：写入类目种子画像（source='init'，行为重建不清空；重复调用覆盖旧种子）
	 * @param userId 用户ID
	 * @param cats 一级类目编码列表(1~5个)
	 */
	void initInterest(Long userId, List<String> cats);

	/**
	 * 负反馈"不感兴趣"：记录流水（该商品不再推荐 + 类目打分降权，静默失败）
	 */
	void dislike(Long userId, Long productId);

	/**
	 * 个人搜索历史（去重，最近优先；匿名返回空）
	 */
	List<String> searchHistory(Long userId);

	/**
	 * 清空个人搜索历史
	 */
	void clearSearchHistory(Long userId);

	/**
	 * 热搜榜（近 7 天租户内搜索次数倒序）
	 * @return [{keyword, cnt}]
	 */
	List<Map<String, Object>> searchHot();

	/**
	 * AI 导购个性化上下文（兴趣类目 + 近期浏览摘要；无画像返回 null）
	 */
	String guideInterestContext(Long userId);

	/**
	 * 收藏降价扫描（Quartz 每小时）：先给无快照的存量收藏播种基线， 再对比快照与现价，降价则 IM 通知收藏者并刷新快照（幂等，不重复提醒）
	 * @return 通知人数
	 */
	int scanPriceDrop();

	/**
	 * 昨日浏览明细聚合进 product_stats_daily（Quartz 每日 05:00，幂等覆盖）
	 * @return 聚合商品数
	 */
	int refreshDailyStats();

	/**
	 * 清理过期流水（浏览/曝光/搜索词/负反馈，保留 90 天；Quartz 每日 03:50）
	 * @return 清理总行数
	 */
	int purgeExpiredLogs();

	/**
	 * 好物日报推送（Quartz 每日 07:30）：有画像用户按兴趣推 3 件好物
	 * @return 推送人数
	 */
	int pushDailyPicks();

}
