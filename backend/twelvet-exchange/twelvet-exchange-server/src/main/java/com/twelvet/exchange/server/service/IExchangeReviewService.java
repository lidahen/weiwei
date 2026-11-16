package com.twelvet.exchange.server.service;

import java.util.Map;

/**
 * 交换双向评价服务（completed 后 1-5 星，uk_order_reviewer 防重）
 *
 * @author twelvet
 */
public interface IExchangeReviewService {

	/**
	 * 提交评价（仅 completed 订单参与方，评价对象为对方）
	 * @return 评价结果（reviewId 等）
	 */
	Map<String, Object> submitReview(Long orderId, Long userId, Integer score, String content);

	/**
	 * 换物信用分画像（实时计算，无冗余表）：base=收到评价均分×20（无评价默认 70）， creditScore =
	 * clamp(0,100, round(base×(0.7+0.3×completionRate30d)) − 5×penalizedCount30d)（与 runner 逐字一致）
	 * @return creditScore/completedOrders30d/penalizedCount30d/avgScore 等
	 */
	Map<String, Object> creditProfile(Long userId);

}
