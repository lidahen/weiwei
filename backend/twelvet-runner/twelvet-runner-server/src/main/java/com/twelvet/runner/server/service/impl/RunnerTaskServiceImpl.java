package com.twelvet.runner.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.runner.api.domain.RunnerTask;
import com.twelvet.runner.api.domain.RunnerTaskLog;
import com.twelvet.runner.server.mapper.RunnerTaskMapper;
import com.twelvet.runner.server.service.IRunnerTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 下单人视角服务实现
 *
 * @author twelvet
 */
@Service
public class RunnerTaskServiceImpl implements IRunnerTaskService {

	private static final Logger log = LoggerFactory.getLogger(RunnerTaskServiceImpl.class);

	private final RunnerTaskMapper taskMapper;

	private final RunnerTaskStateMachine stateMachine;

	private final IPayOrderService payOrderService;

	public RunnerTaskServiceImpl(RunnerTaskMapper taskMapper, RunnerTaskStateMachine stateMachine,
			IPayOrderService payOrderService) {
		this.taskMapper = taskMapper;
		this.stateMachine = stateMachine;
		this.payOrderService = payOrderService;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long publish(RunnerTask task, Long publisherId) {
		// 校验
		if (task == null) {
			throw new TWTException("任务内容不能为空");
		}
		if (task.getTaskType() == null) {
			throw new TWTException("任务类型不能为空");
		}
		if (task.getTitle() == null || task.getTitle().isBlank()) {
			throw new TWTException("任务标题不能为空");
		}
		if (task.getDeadline() == null || !task.getDeadline().isAfter(LocalDateTime.now())) {
			throw new TWTException("接单截止时间必须在未来");
		}
		// is_paid 默认 1
		if (task.getIsPaid() == null) {
			task.setIsPaid(RunnerTask.IS_PAID_YES);
		}
		// 免费任务（互助）：无报酬；付费任务：price 必须 > 0
		if (task.getIsPaid() == RunnerTask.IS_PAID_NO) {
			if (task.getPrice() == null) {
				task.setPrice(java.math.BigDecimal.ZERO);
			}
		}
		else {
			if (task.getPrice() == null || task.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
				throw new TWTException("付费任务必须设置报酬");
			}
		}

		task.setTaskNo(generateTaskNo());
		task.setPublisherId(publisherId);
		task.setStatus(RunnerTask.STATUS_PENDING);
		task.setTenantId(stateMachine.writeTid());
		task.setCreateBy(String.valueOf(publisherId));
		taskMapper.insert(task);

		// 流转日志
		stateMachine.insertLog(task.getId(), publisherId, RunnerTaskLog.ACTION_PUBLISH, "发布任务");
		return task.getId();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> cancel(Long taskId, Long publisherId, String reason) {
		RunnerTask task = stateMachine.requireTask(taskId);
		if (!stateMachine.isPublisher(task, publisherId)) {
			throw new TWTException("仅任务发布者可取消");
		}
		if (task == null) {
			throw new TWTException("任务不存在");
		}
		// 仅 pending / accepted 可取消；其他状态走 CS 审核
		if (!RunnerTask.STATUS_PENDING.equals(task.getStatus())
				&& !RunnerTask.STATUS_ACCEPTED.equals(task.getStatus())) {
			throw new TWTException("当前状态不允许取消: " + task.getStatus());
		}

		boolean ok = stateMachine.doCancel(taskId, RunnerTask.STATUS_CANCELLED, reason, publisherId);
		if (!ok) {
			throw new TWTException("取消失败（状态已被其他操作改变）");
		}

		Map<String, Object> resp = new HashMap<>();
		resp.put("taskId", taskId);
		resp.put("status", RunnerTask.STATUS_CANCELLED);
		resp.put("isPaid", task.getIsPaid());
		resp.put("refundAmount", task.getPrice());
		// 付费任务返回 outTradeNo，由 controller 端调 IPayOrderService.refund
		if (task.getIsPaid() != null && task.getIsPaid() == RunnerTask.IS_PAID_YES && task.getPayOutTradeNo() != null) {
			resp.put("payOutTradeNo", task.getPayOutTradeNo());
			resp.put("payOrderId", task.getPayOrderId());
			resp.put("needRefund", true);
		}
		else {
			resp.put("needRefund", false);
		}
		return resp;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void confirmComplete(Long taskId, Long publisherId) {
		RunnerTask task = stateMachine.requireTask(taskId);
		if (task == null) {
			throw new TWTException("任务不存在");
		}
		if (!stateMachine.isPublisher(task, publisherId)) {
			throw new TWTException("仅任务发布者可确认");
		}
		if (!RunnerTask.STATUS_PENDING_CONFIRM.equals(task.getStatus())) {
			throw new TWTException("当前状态不允许确认: " + task.getStatus());
		}

		boolean ok = stateMachine.doTransitionWithTime(taskId, RunnerTask.STATUS_PENDING_CONFIRM,
				RunnerTask.STATUS_COMPLETED, "confirm_time", publisherId, RunnerTaskLog.ACTION_CONFIRM, "下单人确认完成");
		if (!ok) {
			throw new TWTException("确认失败（状态已被改变）");
		}
		// 付费任务 → 资金结算（当前仅落状态；自动结算需支付服务支持分账/转账 API）
		if (task.getIsPaid() != null && task.getIsPaid() == RunnerTask.IS_PAID_YES) {
			log.info("付费任务完成：待结算 taskId={}, payOrderId={}, price={}", taskId, task.getPayOrderId(), task.getPrice());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> requestRefund(Long taskId, Long publisherId, String reason) {
		RunnerTask task = stateMachine.requireTask(taskId);
		if (task == null) {
			throw new TWTException("任务不存在");
		}
		if (!stateMachine.isPublisher(task, publisherId)) {
			throw new TWTException("仅任务发布者可申请退款");
		}
		if (!RunnerTask.STATUS_PENDING_CONFIRM.equals(task.getStatus())) {
			throw new TWTException("仅待确认状态可申请退款");
		}
		if (task.getIsPaid() == null || task.getIsPaid() != RunnerTask.IS_PAID_YES) {
			throw new TWTException("免费任务无需退款");
		}

		// 状态机：pending_confirm → refund
		boolean ok = stateMachine.doCancel(taskId, RunnerTask.STATUS_REFUND, reason, publisherId);
		if (!ok) {
			throw new TWTException("退款申请失败");
		}
		stateMachine.insertLog(taskId, publisherId, "refund", "申请退款: " + reason);

		Map<String, Object> resp = new HashMap<>();
		resp.put("taskId", taskId);
		resp.put("payOutTradeNo", task.getPayOutTradeNo());
		resp.put("payOrderId", task.getPayOrderId());
		resp.put("refundAmount", task.getPrice());
		resp.put("refundReason", reason);
		return resp;
	}

	@Override
	public RunnerTask detail(Long taskId, Long publisherId) {
		RunnerTask task = stateMachine.requireTask(taskId);
		if (task == null) {
			return null;
		}
		// publisher / assignee 可见
		if (!stateMachine.isPublisher(task, publisherId) && !stateMachine.isAssignee(task, publisherId)) {
			throw new TWTException("无权查看该任务");
		}
		return task;
	}

	@Override
	public List<RunnerTask> listMyPublished(Long publisherId, String status) {
		return taskMapper.selectMyPublished(publisherId, stateMachine.currentTid(), status, 50);
	}

	@Override
	public int scanTimeoutPending() {
		List<RunnerTask> expired = taskMapper.selectExpiredPending(LocalDateTime.now(), 200);
		int count = 0;
		for (RunnerTask task : expired) {
			// Quartz 线程无租户上下文，显式传任务自身租户；单条失败不影响整批
			try {
				if (stateMachine.doCancel(task.getId(), RunnerTask.STATUS_CANCELLED, "超时未接单", task.getPublisherId(),
						task.getTenantId())) {
					count++;
					refundTimeoutTaskQuietly(task);
				}
			}
			catch (Exception e) {
				log.error("互助任务超时取消失败，跳过该任务: taskId={}", task.getId(), e);
			}
		}
		return count;
	}

	@Override
	public int reconcileStalePaidRefunds() {
		// 退款对账：取消成功但 pay_status 仍 paid 的滞留任务（超时/用户取消路径退款失败漏下的），
		// 兜底重试；refund 非 paid 拒绝 + markPayRefundedByTaskId 仅 paid 可翻，天然幂等
		List<RunnerTask> stale = taskMapper.selectCancelledPaid(200);
		int count = 0;
		for (RunnerTask task : stale) {
			try {
				if (payOrderService.refund(task.getPayOutTradeNo(), task.getPrice(), "退款对账补偿")) {
					taskMapper.markPayRefundedByTaskId(task.getId());
					count++;
					log.info("退款对账补偿完成: taskId={}, outTradeNo={}", task.getId(), task.getPayOutTradeNo());
				}
				else {
					log.warn("退款对账补偿失败（下轮重试）: taskId={}, outTradeNo={}", task.getId(),
							task.getPayOutTradeNo());
				}
			}
			catch (Exception e) {
				log.error("退款对账补偿异常（下轮重试）: taskId={}, outTradeNo={}", task.getId(), task.getPayOutTradeNo(),
						e);
			}
		}
		return count;
	}

	/**
	 * 超时取消的付费任务自动原路退款（尽力而为：单条失败不影响整批）。
	 * <p>
	 * 失败滞留任务由 {@link #reconcileStalePaidRefunds()} 对账兜底重试。幂等保障：
	 * trade_pay refund 入口非 paid 拒绝，重复扫描不会双退；与用户取消路径并发时
	 * 双方都经 stateMachine.doCancel CAS，只有取消赢家走到退款。
	 * </p>
	 */
	private void refundTimeoutTaskQuietly(RunnerTask task) {
		if (task.getIsPaid() == null || task.getIsPaid() != RunnerTask.IS_PAID_YES || task.getPayOutTradeNo() == null) {
			return;
		}
		try {
			boolean ok = payOrderService.refund(task.getPayOutTradeNo(), task.getPrice(), "任务超时未接单，自动退款");
			if (ok) {
				taskMapper.markPayRefundedByTaskId(task.getId());
				log.info("互助任务超时自动退款完成: taskId={}, outTradeNo={}", task.getId(), task.getPayOutTradeNo());
			}
			else {
				log.warn("互助任务超时自动退款失败（任务保持 paid 待人工处理）: taskId={}, outTradeNo={}", task.getId(),
						task.getPayOutTradeNo());
			}
		}
		catch (Exception e) {
			log.error("互助任务超时自动退款异常: taskId={}, outTradeNo={}", task.getId(), task.getPayOutTradeNo(), e);
		}
	}

	@Override
	public int scanAutoConfirm() {
		// 提交完成超过 3 天 → 自动确认
		LocalDateTime threshold = LocalDateTime.now().minusDays(3);
		List<RunnerTask> list = taskMapper.selectExpiredPendingConfirm(threshold, 200);
		int count = 0;
		for (RunnerTask task : list) {
			// Quartz 线程无租户上下文，显式传任务自身租户；单条失败不影响整批
			try {
				if (stateMachine.doTransitionWithTime(task.getId(), RunnerTask.STATUS_PENDING_CONFIRM,
						RunnerTask.STATUS_COMPLETED, "confirm_time", task.getPublisherId(),
						RunnerTaskLog.ACTION_CONFIRM, "超时自动确认", task.getTenantId())) {
					count++;
				}
			}
			catch (Exception e) {
				log.error("互助任务超时自动确认失败，跳过该任务: taskId={}", task.getId(), e);
			}
		}
		return count;
	}

	@Override
	public Map<String, Object> statistics() {
		return taskMapper.selectStatistics(stateMachine.currentTid());
	}

	@Override
	public TableDataInfo adminPage(Long pageNum, Long pageSize, String status, String keyword) {
		// 平台管理：全状态分页查询（tenantId 取当前上下文，为 null 时不限定租户，与 mapper 现有租户处理一致）
		long pn = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		long ps = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 200);
		long offset = (pn - 1) * ps;
		String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
		String st = (status == null || status.isBlank()) ? null : status.trim();
		Long tid = stateMachine.currentTid();
		List<RunnerTask> list = taskMapper.selectAdminPage(tid, st, kw, offset, ps);
		long total = taskMapper.countAdminPage(tid, st, kw);
		return TableDataInfo.page(list, total);
	}

	@Override
	public void markRefunded(Long taskId) {
		// 独立退款语句：仅 paid→refunded、不限任务状态——取消后 status='cancelled'，
		// 共用 markPayStatusByTaskId 的 cancelled 守卫会拦截退款标记（final review 发现 #1）
		taskMapper.markPayRefundedByTaskId(taskId);
	}

	private String generateTaskNo() {
		String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		int rand = (int) (Math.random() * 1000000);
		return "RUN" + time + String.format("%06d", rand);
	}

}
