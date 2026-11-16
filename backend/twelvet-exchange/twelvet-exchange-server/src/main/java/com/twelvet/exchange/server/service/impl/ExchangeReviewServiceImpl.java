package com.twelvet.exchange.server.service.impl;

import com.twelvet.exchange.api.domain.ExchangeOrder;
import com.twelvet.exchange.server.domain.ExchangeOrderLog;
import com.twelvet.exchange.server.domain.ExchangeReview;
import com.twelvet.exchange.server.mapper.ExchangeReviewMapper;
import com.twelvet.exchange.server.service.IExchangeReviewService;
import com.twelvet.exchange.server.service.impl.ExchangeOrderStateMachine;
import com.twelvet.framework.core.exception.TWTException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 交换双向评价服务实现
 * <p>
 * completed 后双向评价（1-5 星 + 文字），uk_order_reviewer 防重复（docs/交换广场模块设计方案.md §4.6）。
 * 信用分实时计算无冗余表（docs/交换广场二期-智能匹配与信用设计.md §4.4）：
 * <pre>
 * base        = 收到评价均分 × 20（无评价默认 70，新人保护）
 * creditScore = clamp(0, 100, round(base * (0.7 + 0.3 * completionRate30d)) - 5 * penalizedCount30d)
 * </pre> penalizedCount30d 归因口径（MVP 近似）：用户直接违约（cancel/dispute）按 order_log.operator_id 记本人；
 * timeout/admin_force_cancel 为系统/管理员动作（operator_id 记 0/管理员），按订单参与方 JOIN 双方各计一次。
 * </p>
 *
 * @author twelvet
 */
@Service
public class ExchangeReviewServiceImpl implements IExchangeReviewService {

	private static final Logger log = LoggerFactory.getLogger(ExchangeReviewServiceImpl.class);

	/** 无评价用户的默认信用分（新人保护） */
	public static final int DEFAULT_CREDIT_SCORE = 70;

	/** 近30天每笔违约（直接取消/纠纷/超时/裁决取消）扣减分数 */
	public static final double PENALTY_PER_ABNORMAL_30D = 5.0;

	private static final int SCORE_MIN = 1;

	private static final int SCORE_MAX = 5;

	private final ExchangeReviewMapper reviewMapper;

	private final ExchangeOrderStateMachine stateMachine;

	public ExchangeReviewServiceImpl(ExchangeReviewMapper reviewMapper, ExchangeOrderStateMachine stateMachine) {
		this.reviewMapper = reviewMapper;
		this.stateMachine = stateMachine;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> submitReview(Long orderId, Long userId, Integer score, String content) {
		// 防刷 1：订单存在 + 仅参与方
		ExchangeOrder order = stateMachine.requireOrder(orderId);
		if (order == null) {
			throw new TWTException("订单不存在");
		}
		if (!stateMachine.isParticipant(order, userId)) {
			throw new TWTException("仅订单参与方可评价");
		}
		// 防刷 2：仅 completed 订单可评价
		if (!ExchangeOrder.STATUS_COMPLETED.equals(order.getStatus())) {
			throw new TWTException("仅已完成订单可评价，当前状态: " + order.getStatus());
		}
		// 防刷 3：评分范围
		if (score == null || score < SCORE_MIN || score > SCORE_MAX) {
			throw new TWTException("评分必须为 1-5 星");
		}
		if (content != null && content.length() > 500) {
			throw new TWTException("评语最多 500 字");
		}
		// 防刷 4：一单一评（DB 唯一键 uk_order_reviewer 兜底并发）
		if (reviewMapper.selectByOrderAndReviewer(orderId, userId, stateMachine.currentTid()) != null) {
			throw new TWTException("该订单已评价");
		}
		Long revieweeId = stateMachine.isOwnerA(order, userId) ? order.getOwnerBId() : order.getOwnerAId();
		if (revieweeId == null) {
			throw new TWTException("订单缺少对方信息，无法评价");
		}

		ExchangeReview review = new ExchangeReview();
		review.setOrderId(orderId);
		review.setReviewerId(userId);
		review.setRevieweeId(revieweeId);
		review.setScore(score);
		review.setContent(content);
		try {
			reviewMapper.insert(review, stateMachine.writeTid());
		}
		catch (DuplicateKeyException e) {
			throw new TWTException("该订单已评价");
		}

		// 落流转日志 + IM 通知被评价方（@Async，失败不影响主流程）
		stateMachine.insertLog(orderId, userId, ExchangeOrderLog.ACTION_REVIEW, score + "星");
		stateMachine.publishNotify(orderId, ExchangeOrderLog.ACTION_REVIEW, userId, score + "星",
				stateMachine.currentTid());

		Map<String, Object> resp = new HashMap<>();
		resp.put("reviewId", review.getId());
		resp.put("revieweeId", revieweeId);
		return resp;
	}

	@Override
	public Map<String, Object> creditProfile(Long userId) {
		Map<String, Object> raw = reviewMapper.selectCreditProfile(userId, stateMachine.currentTid());
		long completed30d = num(raw.get("completedOrders30d"));
		long total30d = num(raw.get("totalOrders30d"));
		long penalizedCount30d = num(raw.get("directPenaltyCount30d")) + num(raw.get("systemPenaltyCount30d"));
		Object avgObj = raw.get("avgScore");
		long reviewCount = num(raw.get("reviewCount"));

		Map<String, Object> resp = new HashMap<>();
		resp.put("userId", userId);
		resp.put("completedOrders30d", completed30d);
		resp.put("totalOrders30d", total30d);
		resp.put("completionRate30d", total30d > 0 ? Math.round(completed30d * 100.0 / total30d) / 100.0 : null);
		resp.put("penalizedCount30d", penalizedCount30d);
		resp.put("avgScore", avgObj);
		resp.put("reviewCount", reviewCount);
		boolean hasData = avgObj != null || completed30d > 0;

		// 信用分公式（clamp(0,100)，与 RunnerReviewServiceImpl.creditProfile 逐字一致）：
		// base = 收到评价均分 * 20（无评价默认 70，新人保护）
		// completionRate = completedOrders30d / totalOrders30d（近30天无订单记 1.0，中性不奖不罚）
		// creditScore = round(base * (0.7 + 0.3 * completionRate)) - 5 * penalizedCount30d
		// 违约归因：cancel/dispute 按 order_log.operator_id 记本人；timeout/admin_force_cancel 为系统/管理员
		// 动作（operator 记 0/管理员），按订单参与方双方各计一次（MVP 近似口径）
		double base = avgObj != null ? Double.parseDouble(String.valueOf(avgObj)) * 20 : DEFAULT_CREDIT_SCORE;
		double completionRate = total30d > 0 ? (double) completed30d / total30d : 1.0;
		double rawScore = base * (0.7 + 0.3 * completionRate) - PENALTY_PER_ABNORMAL_30D * penalizedCount30d;
		int creditScore = (int) Math.max(0, Math.min(100, Math.round(rawScore)));

		resp.put("creditScore", creditScore);
		resp.put("hasData", hasData);
		return resp;
	}

	private long num(Object o) {
		return o instanceof Number n ? n.longValue() : 0;
	}

}
