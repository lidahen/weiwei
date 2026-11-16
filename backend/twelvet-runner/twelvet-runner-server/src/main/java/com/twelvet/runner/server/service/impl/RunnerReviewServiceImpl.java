package com.twelvet.runner.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.runner.api.domain.RunnerOrder;
import com.twelvet.runner.api.domain.RunnerTask;
import com.twelvet.runner.api.domain.RunnerTaskLog;
import com.twelvet.runner.server.domain.RunnerReview;
import com.twelvet.runner.server.event.RunnerTaskNotifyEvent;
import com.twelvet.runner.server.mapper.RunnerOrderMapper;
import com.twelvet.runner.server.mapper.RunnerReviewMapper;
import com.twelvet.runner.server.service.IRunnerReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 互助评价 & 信用服务实现
 * <p>
 * 设计：docs/跑腿信用与评价设计.md 信用分不加冗余表，SQL 实时聚合评价 + 任务 + 接单数据： <pre>
 * base        = avgRating * 20（无评价默认 80）
 * creditScore = clamp(0, 100, round(base * (0.7 + 0.3 * completionRate30d)) - 5 * penalizedCount30d)
 * </pre> penalizedCount30d：近30天接到手后进入 cancelled/refund/disputed 的任务数，每笔扣 5 分。
 * </p>
 *
 * @author twelvet
 */
@Service
public class RunnerReviewServiceImpl implements IRunnerReviewService {

	private static final Logger log = LoggerFactory.getLogger(RunnerReviewServiceImpl.class);

	/** 无评价用户的默认信用分（新人保护） */
	public static final int DEFAULT_CREDIT_SCORE = 80;

	/** 近30天每笔异常单（接单后被取消/退款/纠纷）扣减分数 */
	public static final double PENALTY_PER_ABNORMAL_30D = 5.0;

	private static final int RATING_MIN = 1;

	private static final int RATING_MAX = 5;

	private final RunnerReviewMapper reviewMapper;

	private final RunnerOrderMapper orderMapper;

	private final RunnerTaskStateMachine stateMachine;

	private final ApplicationEventPublisher eventPublisher;

	public RunnerReviewServiceImpl(RunnerReviewMapper reviewMapper, RunnerOrderMapper orderMapper,
			RunnerTaskStateMachine stateMachine, ApplicationEventPublisher eventPublisher) {
		this.reviewMapper = reviewMapper;
		this.orderMapper = orderMapper;
		this.stateMachine = stateMachine;
		this.eventPublisher = eventPublisher;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> submitReview(Long taskId, Long publisherId, Integer rating, String content,
			String tags) {
		// 防刷 1：任务存在 + 仅发布者
		RunnerTask task = stateMachine.requireTask(taskId);
		if (task == null) {
			throw new TWTException("任务不存在");
		}
		if (!stateMachine.isPublisher(task, publisherId)) {
			throw new TWTException("仅任务发布者可评价");
		}
		// 防刷 2：仅确认完成（completed）后可评价
		if (!RunnerTask.STATUS_COMPLETED.equals(task.getStatus())) {
			throw new TWTException("仅已确认完成的任务可评价，当前状态: " + task.getStatus());
		}
		// 防刷 3：评分范围
		if (rating == null || rating < RATING_MIN || rating > RATING_MAX) {
			throw new TWTException("评分必须为 1-5 星");
		}
		if (content != null && content.length() > 500) {
			throw new TWTException("评语最多 500 字");
		}
		// 防刷 4：一任务一评（DB 唯一键 uk_task 兜底并发）
		if (reviewMapper.selectByTaskId(taskId, stateMachine.currentTid()) != null) {
			throw new TWTException("该任务已评价");
		}
		if (task.getAssigneeId() == null) {
			throw new TWTException("任务无接单人，无法评价");
		}

		RunnerReview review = new RunnerReview();
		review.setTaskId(taskId);
		review.setTaskNo(task.getTaskNo());
		review.setOrderId(resolveOrderId(taskId, task.getAssigneeId()));
		review.setPublisherId(publisherId);
		review.setRunnerId(task.getAssigneeId());
		review.setRating(rating);
		review.setTags(tags);
		review.setContent(content);
		try {
			reviewMapper.insert(review, stateMachine.writeTid());
		}
		catch (DuplicateKeyException e) {
			throw new TWTException("该任务已评价");
		}

		// IM 通知被评价方（@Async，失败不影响主流程）
		Long tid = stateMachine.currentTid();
		eventPublisher.publishEvent(
				new RunnerTaskNotifyEvent(taskId, RunnerTaskLog.ACTION_REVIEW, publisherId, rating + "星", tid));

		Map<String, Object> resp = new HashMap<>();
		resp.put("reviewId", review.getId());
		resp.put("creditScore", creditProfile(task.getAssigneeId()).get("creditScore"));
		return resp;
	}

	@Override
	public Map<String, Object> creditProfile(Long userId) {
		Map<String, Object> raw = reviewMapper.selectCreditProfile(userId, stateMachine.currentTid());
		long completedCount = num(raw.get("completedCount"));
		long totalCount = num(raw.get("totalCount"));
		long completed30d = num(raw.get("completedCount30d"));
		long accepted30d = num(raw.get("acceptedCount30d"));
		long penalizedCount = num(raw.get("penalizedCount"));
		long penalizedCount30d = num(raw.get("penalizedCount30d"));
		long reviewCount = num(raw.get("reviewCount"));
		Object avgObj = raw.get("avgRating");

		Map<String, Object> resp = new HashMap<>();
		resp.put("userId", userId);
		resp.put("completedCount", completedCount);
		resp.put("totalCount", totalCount);
		resp.put("completedCount30d", completed30d);
		resp.put("acceptedCount30d", accepted30d);
		resp.put("completionRate30d", accepted30d > 0 ? Math.round(completed30d * 100.0 / accepted30d) / 100.0 : null);
		resp.put("penalizedCount", penalizedCount);
		resp.put("penalizedCount30d", penalizedCount30d);
		resp.put("avgRating", avgObj);
		resp.put("reviewCount", reviewCount);
		boolean hasData = avgObj != null || completedCount > 0;

		// 信用分公式（clamp(0,100)）：
		// base = avgRating * 20（无评价默认 80，新人保护）
		// completionRate = completedCount30d / acceptedCount30d（近30天无接单记 1.0，中性不奖不罚）
		// creditScore = round(base * (0.7 + 0.3 * completionRate)) - 5 *
		// penalizedCount30d
		// 异常单定义：接到手（assignee_id=本人）后进入 cancelled / refund / disputed 的任务，
		// 每笔扣 5 分（仅计近30天）；全量异常单数见 penalizedCount。
		double base = avgObj != null ? Double.parseDouble(String.valueOf(avgObj)) * 20 : DEFAULT_CREDIT_SCORE;
		double completionRate = accepted30d > 0 ? (double) completed30d / accepted30d : 1.0;
		double rawScore = base * (0.7 + 0.3 * completionRate) - PENALTY_PER_ABNORMAL_30D * penalizedCount30d;
		int creditScore = (int) Math.max(0, Math.min(100, Math.round(rawScore)));

		resp.put("creditScore", creditScore);
		resp.put("hasData", hasData);
		return resp;
	}

	@Override
	public Map<String, Object> reviewByTask(Long taskId) {
		RunnerReview review = reviewMapper.selectByTaskId(taskId, stateMachine.currentTid());
		if (review == null) {
			return null;
		}
		Map<String, Object> resp = new HashMap<>();
		resp.put("rating", review.getRating());
		resp.put("content", review.getContent());
		resp.put("tags", review.getTags());
		resp.put("createTime", review.getCreateTime());
		resp.put("runnerId", review.getRunnerId());
		return resp;
	}

	private Long resolveOrderId(Long taskId, Long runnerId) {
		List<RunnerOrder> orders = orderMapper.selectByTaskId(taskId, stateMachine.currentTid());
		if (orders == null) {
			return null;
		}
		return orders.stream()
			.filter(o -> runnerId.equals(o.getRunnerId()))
			.map(RunnerOrder::getId)
			.findFirst()
			.orElse(null);
	}

	private long num(Object o) {
		return o instanceof Number n ? n.longValue() : 0;
	}

}
