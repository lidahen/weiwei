package com.twelvet.runner.server.service.impl;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.runner.api.domain.RunnerOrder;
import com.twelvet.runner.api.domain.RunnerTask;
import com.twelvet.runner.api.domain.RunnerTaskLog;
import com.twelvet.runner.server.event.RunnerTaskNotifyEvent;
import com.twelvet.runner.server.mapper.RunnerOrderMapper;
import com.twelvet.runner.server.mapper.RunnerTaskLogMapper;
import com.twelvet.runner.server.mapper.RunnerTaskMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 互助任务状态机 & 日志共享逻辑
 * <p>
 * 抽出来供 IRunnerTaskService（publisher）与 IRunnerHallService（runner）共用， 避免两边重复实现日志插入与状态迁移。
 * </p>
 *
 * @author twelvet
 */
@Component
public class RunnerTaskStateMachine {

	private final RunnerTaskMapper taskMapper;

	private final RunnerOrderMapper orderMapper;

	private final RunnerTaskLogMapper logMapper;

	private final TenantSupport tenantSupport;

	private final ApplicationEventPublisher eventPublisher;

	public RunnerTaskStateMachine(RunnerTaskMapper taskMapper, RunnerOrderMapper orderMapper,
			RunnerTaskLogMapper logMapper, TenantSupport tenantSupport, ApplicationEventPublisher eventPublisher) {
		this.taskMapper = taskMapper;
		this.orderMapper = orderMapper;
		this.logMapper = logMapper;
		this.tenantSupport = tenantSupport;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * 当前租户上下文（开关关闭时 null）
	 */
	public Long currentTid() {
		return TenantContext.get();
	}

	/**
	 * 取写路径租户（开关开启但无上下文时抛异常）
	 */
	public Long writeTid() {
		return tenantSupport.writeTid();
	}

	/**
	 * 接单：乐观锁原子操作 + 落 runner_order + 落日志
	 */
	public boolean doAssign(Long taskId, Long runnerId) {
		Long tid = currentTid();
		int rows = taskMapper.assignAtomically(taskId, tid, runnerId, LocalDateTime.now());
		if (rows == 0) {
			return false;
		}
		RunnerOrder order = new RunnerOrder();
		order.setTaskId(taskId);
		order.setRunnerId(runnerId);
		order.setStatus(RunnerOrder.STATUS_ACCEPTED);
		orderMapper.insert(order, tid);
		insertLog(taskId, runnerId, RunnerTaskLog.ACTION_ASSIGN, "接单成功");
		publishNotify(taskId, RunnerTaskLog.ACTION_ASSIGN, runnerId, "接单成功", tid);
		return true;
	}

	/**
	 * 状态迁移 + 落日志
	 */
	public boolean doTransition(Long taskId, String fromStatus, String toStatus, Long operatorId, String action,
			String remark) {
		return doTransition(taskId, fromStatus, toStatus, operatorId, action, remark, currentTid());
	}

	/**
	 * 带显式租户的状态迁移（Quartz 线程无租户上下文，须传任务自身租户）
	 */
	public boolean doTransition(Long taskId, String fromStatus, String toStatus, Long operatorId, String action,
			String remark, Long tid) {
		int rows = taskMapper.transitionStatus(taskId, fromStatus, toStatus, tid);
		if (rows == 0) {
			return false;
		}
		insertLog(taskId, operatorId, action, remark, tid);
		publishNotify(taskId, action, operatorId, remark, tid);
		return true;
	}

	/**
	 * 状态迁移 + 写时间戳列
	 */
	public boolean doTransitionWithTime(Long taskId, String fromStatus, String toStatus, String timeColumn,
			Long operatorId, String action, String remark) {
		return doTransitionWithTime(taskId, fromStatus, toStatus, timeColumn, operatorId, action, remark, currentTid());
	}

	/**
	 * 带显式租户的状态迁移 + 写时间戳列（Quartz 线程无租户上下文，须传任务自身租户）
	 */
	public boolean doTransitionWithTime(Long taskId, String fromStatus, String toStatus, String timeColumn,
			Long operatorId, String action, String remark, Long tid) {
		int rows = taskMapper.transitionWithTimestamp(taskId, fromStatus, toStatus, timeColumn, tid);
		if (rows == 0) {
			return false;
		}
		insertLog(taskId, operatorId, action, remark, tid);
		publishNotify(taskId, action, operatorId, remark, tid);
		return true;
	}

	/**
	 * 取消（带原因）
	 */
	public boolean doCancel(Long taskId, String toStatus, String reason, Long operatorId) {
		return doCancel(taskId, toStatus, reason, operatorId, currentTid());
	}

	/**
	 * 带显式租户的取消（Quartz 线程无租户上下文，须传任务自身租户； tenant_id 列 NOT NULL，currentTid() 为 null 时
	 * insertLog 会 SQL 异常）
	 */
	public boolean doCancel(Long taskId, String toStatus, String reason, Long operatorId, Long tid) {
		int rows = taskMapper.cancelTask(taskId, toStatus, reason, tid);
		if (rows == 0) {
			return false;
		}
		insertLog(taskId, operatorId, RunnerTaskLog.ACTION_CANCEL, reason, tid);
		publishNotify(taskId, RunnerTaskLog.ACTION_CANCEL, operatorId, reason, tid);
		return true;
	}

	/**
	 * 发布流转通知事件（@Async 监听器异步处理，失败不影响主流程）
	 */
	private void publishNotify(Long taskId, String action, Long operatorId, String remark, Long tenantId) {
		eventPublisher.publishEvent(new RunnerTaskNotifyEvent(taskId, action, operatorId, remark, tenantId));
	}

	/**
	 * 落流转日志
	 */
	public void insertLog(Long taskId, Long operatorId, String action, String remark) {
		insertLog(taskId, operatorId, action, remark, currentTid());
	}

	/**
	 * 带显式租户的落日志（Quartz 线程无租户上下文，tenant_id 列 NOT NULL，null 会 SQL 异常）
	 */
	public void insertLog(Long taskId, Long operatorId, String action, String remark, Long tid) {
		RunnerTaskLog log = new RunnerTaskLog();
		log.setTaskId(taskId);
		log.setOperatorId(operatorId);
		log.setAction(action);
		log.setRemark(remark);
		logMapper.insert(log, tid);
	}

	/**
	 * 校验任务存在 + 权限（仅任务 publisher / assignee 可操作）
	 * @return 任务；null 表示不存在或无权
	 */
	public RunnerTask requireTask(Long taskId) {
		Long tid = currentTid();
		return taskMapper.selectById(taskId, tid);
	}

	/**
	 * 校验当前用户是 publisher
	 */
	public boolean isPublisher(RunnerTask task, Long userId) {
		return task != null && task.getPublisherId() != null && task.getPublisherId().equals(userId);
	}

	/**
	 * 校验当前用户是 assignee
	 */
	public boolean isAssignee(RunnerTask task, Long userId) {
		return task != null && task.getAssigneeId() != null && task.getAssigneeId().equals(userId);
	}

}
