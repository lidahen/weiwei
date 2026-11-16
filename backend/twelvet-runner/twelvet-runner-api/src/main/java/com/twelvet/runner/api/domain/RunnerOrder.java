package com.twelvet.runner.api.domain;

import java.time.LocalDateTime;

/**
 * 互助接单记录（twelve_runner_order）
 * <p>
 * 支持换人/多次接单历史。 status: accepted / running / finished / rejected / cancelled
 * </p>
 *
 * @author twelvet
 */
public class RunnerOrder {

	/** 接单状态：已接单 */
	public static final String STATUS_ACCEPTED = "accepted";

	/** 接单状态：服务中 */
	public static final String STATUS_RUNNING = "running";

	/** 接单状态：已完成 */
	public static final String STATUS_FINISHED = "finished";

	/** 接单状态：拒单 */
	public static final String STATUS_REJECTED = "rejected";

	/** 接单状态：已取消 */
	public static final String STATUS_CANCELLED = "cancelled";

	private Long id;

	private Long taskId;

	private Long runnerId;

	private String status;

	private LocalDateTime assignTime;

	private LocalDateTime finishTime;

	private String rejectReason;

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

	public Long getRunnerId() {
		return runnerId;
	}

	public void setRunnerId(Long runnerId) {
		this.runnerId = runnerId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public LocalDateTime getAssignTime() {
		return assignTime;
	}

	public void setAssignTime(LocalDateTime assignTime) {
		this.assignTime = assignTime;
	}

	public LocalDateTime getFinishTime() {
		return finishTime;
	}

	public void setFinishTime(LocalDateTime finishTime) {
		this.finishTime = finishTime;
	}

	public String getRejectReason() {
		return rejectReason;
	}

	public void setRejectReason(String rejectReason) {
		this.rejectReason = rejectReason;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}
