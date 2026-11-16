package com.twelvet.runner.api.domain;

import java.time.LocalDateTime;

/**
 * 互助任务流转日志（twelve_runner_task_log）
 * <p>
 * 流水记录：publish / assign / reject / start / finish / confirm / cancel / refund / dispute
 * </p>
 *
 * @author twelvet
 */
public class RunnerTaskLog {

	/** 动作：发布 */
	public static final String ACTION_PUBLISH = "publish";

	/** 动作：接单 */
	public static final String ACTION_ASSIGN = "assign";

	/** 动作：拒单 */
	public static final String ACTION_REJECT = "reject";

	/** 动作：开始服务 */
	public static final String ACTION_START = "start";

	/** 动作：提交完成 */
	public static final String ACTION_FINISH = "finish";

	/** 动作：确认完成 */
	public static final String ACTION_CONFIRM = "confirm";

	/** 动作：取消 */
	public static final String ACTION_CANCEL = "cancel";

	/** 动作：退款 */
	public static final String ACTION_REFUND = "refund";

	/** 动作：投诉 */
	public static final String ACTION_DISPUTE = "dispute";

	/** 动作：评价（发布者评价接单人，docs/跑腿信用与评价设计.md） */
	public static final String ACTION_REVIEW = "review";

	private Long id;

	private Long taskId;

	private Long operatorId;

	private String action;

	private String remark;

	private LocalDateTime createTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getTaskId() {
		return taskId;
	}

	public void setTaskId(Long taskId) {
		this.taskId = taskId;
	}

	public Long getOperatorId() {
		return operatorId;
	}

	public void setOperatorId(Long operatorId) {
		this.operatorId = operatorId;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}
