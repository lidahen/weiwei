package com.twelvet.runner.server.service;

import java.util.Map;

/**
 * 互助评价 & 信用服务
 * <p>
 * 设计：docs/跑腿信用与评价设计.md
 * </p>
 *
 * @author twelvet
 */
public interface IRunnerReviewService {

	/**
	 * 发布者对已确认完成任务评价（一任务一评）
	 * @return {reviewId, creditScore}（被评价接单人最新信用分）
	 */
	Map<String, Object> submitReview(Long taskId, Long publisherId, Integer rating, String content, String tags);

	/**
	 * 接单人信用画像（SQL 实时聚合）
	 * @return completedCount / totalCount / completedCount30d / acceptedCount30d /
	 * completionRate30d / penalizedCount / penalizedCount30d / avgRating / reviewCount /
	 * creditScore / hasData
	 */
	Map<String, Object> creditProfile(Long userId);

	/**
	 * 任务已评信息（供详情页展示）
	 */
	Map<String, Object> reviewByTask(Long taskId);

}
