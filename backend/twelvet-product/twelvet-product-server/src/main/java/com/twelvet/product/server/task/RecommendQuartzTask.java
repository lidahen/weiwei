package com.twelvet.product.server.task;

import com.twelvet.product.server.service.IRecommendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 推荐定时任务 Bean（由 twelvet-quartz 通过 bean.method 反射调用）
 * <p>
 * 方法必须无参；sys_job 注册见 docs/db 基线（product-recommend 相关 Job）
 * </p>
 *
 * @author twelvet
 */
@Component("productRecommend")
public class RecommendQuartzTask {

	private static final Logger log = LoggerFactory.getLogger(RecommendQuartzTask.class);

	private final IRecommendService recommendService;

	public RecommendQuartzTask(IRecommendService recommendService) {
		this.recommendService = recommendService;
	}

	/**
	 * 每 30 分钟：全量重建用户兴趣画像（浏览1/收藏3/下单5，半衰期14天，窗口30天）
	 */
	public void refreshInterestStat() {
		int rows = recommendService.refreshInterestStat();
		log.info("[Quartz] UserInterestStatJob 重建完成：{} 条画像", rows);
	}

	/**
	 * 每小时：全量刷新商品热度分（gravity 时间衰减）
	 */
	public void refreshHotScore() {
		int rows = recommendService.refreshHotScore();
		log.info("[Quartz] ProductHotScoreJob 刷新完成：{} 个商品", rows);
	}

	/**
	 * 每日 04:30：增量同步商品向量索引到 ai-service（embedding 未配置时任务失败并记日志，不影响其他任务）
	 */
	public void syncVectors() {
		int count = recommendService.syncVectors();
		log.info("[Quartz] ProductVectorSyncJob 同步完成：{} 个商品在索引内", count);
	}

	/**
	 * 每小时（:20）：收藏降价扫描——先播种缺失的价格基线，再对比快照与现价推送 IM（幂等）
	 */
	public void scanPriceDrop() {
		int notified = recommendService.scanPriceDrop();
		log.info("[Quartz] PriceDropScanJob 扫描完成：降价通知 {} 人", notified);
	}

	/**
	 * 每日 05:00：昨日浏览明细聚合进 product_stats_daily（幂等覆盖）
	 */
	public void refreshDailyStats() {
		int rows = recommendService.refreshDailyStats();
		log.info("[Quartz] ProductDailyStatsJob 完成：{} 个商品", rows);
	}

	/**
	 * 每日 03:50：清理 90 天前的浏览/曝光/搜索词/负反馈流水
	 */
	public void purgeExpiredLogs() {
		int rows = recommendService.purgeExpiredLogs();
		log.info("[Quartz] LogPurgeJob 完成：清理 {} 行", rows);
	}

	/**
	 * 每日 07:30：好物日报——有画像用户按兴趣推 3 件好物
	 */
	public void pushDailyPicks() {
		int pushed = recommendService.pushDailyPicks();
		log.info("[Quartz] DailyPicksJob 完成：推送 {} 人", pushed);
	}

}
