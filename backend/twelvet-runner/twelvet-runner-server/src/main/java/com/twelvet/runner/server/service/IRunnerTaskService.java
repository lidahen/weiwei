package com.twelvet.runner.server.service;

import com.twelvet.runner.api.domain.RunnerTask;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;

import java.util.List;
import java.util.Map;

/**
 * 互助任务服务（下单人视角）
 *
 * @author twelvet
 */
public interface IRunnerTaskService {

	/**
	 * 发布任务
	 * <p>
	 * 免费任务：仅落库，is_paid=0<br>
	 * 付费任务：先创建 PayOrder（调用方在 controller 编排），status=pending 但需等待支付完成才允许接单
	 * </p>
	 * @param task 任务内容（不含 id/status）
	 * @param publisherId 下单人用户ID
	 * @return 任务ID
	 */
	Long publish(RunnerTask task, Long publisherId);

	/**
	 * 取消任务（仅 pending 状态可取消）
	 * <p>
	 * 付费任务：返回 payOrderId，由 controller 调 IPayOrderService.refund
	 * </p>
	 * @return 取消结果，含 payOrderId（如有）
	 */
	Map<String, Object> cancel(Long taskId, Long publisherId, String reason);

	/**
	 * 确认完成（pending_confirm → completed）
	 * <p>
	 * 付费任务：资金结算（当前仅落状态）
	 * </p>
	 */
	void confirmComplete(Long taskId, Long publisherId);

	/**
	 * 申请退款（pending_confirm → refund，进入 CS 审核）
	 * @return 退款参数（含 payOrderId 与 amount），由 controller 调 IPayOrderService.refund
	 */
	Map<String, Object> requestRefund(Long taskId, Long publisherId, String reason);

	/**
	 * 任务详情
	 */
	RunnerTask detail(Long taskId, Long publisherId);

	/**
	 * 我发布的任务（按状态过滤）
	 */
	List<RunnerTask> listMyPublished(Long publisherId, String status);

	/**
	 * 超时未接单扫描（用于 Quartz 定时任务）
	 * @return 受影响任务数
	 */
	int scanTimeoutPending();

	/**
	 * 退款对账扫描（cancelled + pay_status=paid 的滞留任务兜底重试退款；用于 Quartz）
	 * @return 成功退款任务数
	 */
	int reconcileStalePaidRefunds();

	/**
	 * 超时未确认扫描（pending_confirm 超 3 天 → 自动完成）
	 */
	int scanAutoConfirm();

	/** 平台统计 */
	Map<String, Object> statistics();

	/** 平台管理：分页 */
	TableDataInfo adminPage(Long pageNum, Long pageSize, String status, String keyword);

	/**
	 * 标记支付状态为 refunded（退款成功后由 controller 调用）
	 */
	void markRefunded(Long taskId);

}
