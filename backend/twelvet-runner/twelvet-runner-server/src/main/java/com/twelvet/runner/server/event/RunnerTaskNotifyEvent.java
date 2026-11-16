package com.twelvet.runner.server.event;

/**
 * 互助任务状态流转通知事件
 * <p>
 * 状态机流转成功后发布，由 {@code RunnerTaskNotifyListener} 异步消费： 调 IImService 发 IM system 消息 + 落
 * twelve_runner_notify_log。 事件必须携带 tenantId（@Async 线程取不到 TenantContext ThreadLocal）。
 * </p>
 *
 * @author twelvet
 */
public class RunnerTaskNotifyEvent {

	private final Long taskId;

	private final String action;

	private final Long operatorId;

	private final String remark;

	private final Long tenantId;

	public RunnerTaskNotifyEvent(Long taskId, String action, Long operatorId, String remark, Long tenantId) {
		this.taskId = taskId;
		this.action = action;
		this.operatorId = operatorId;
		this.remark = remark;
		this.tenantId = tenantId;
	}

	public Long getTaskId() {
		return taskId;
	}

	public String getAction() {
		return action;
	}

	public Long getOperatorId() {
		return operatorId;
	}

	public String getRemark() {
		return remark;
	}

	public Long getTenantId() {
		return tenantId;
	}

}
