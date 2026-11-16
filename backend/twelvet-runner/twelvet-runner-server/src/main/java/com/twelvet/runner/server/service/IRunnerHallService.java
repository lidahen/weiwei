package com.twelvet.runner.server.service;

import com.twelvet.runner.api.domain.RunnerTask;
import com.twelvet.runner.api.domain.RunnerTaskLog;

import java.util.List;

/**
 * 互助大厅服务（接单人视角）
 *
 * @author twelvet
 */
public interface IRunnerHallService {

	/**
	 * 互助大厅列表
	 */
	List<RunnerTask> hall(Integer taskType, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice,
			Integer limit);

	/**
	 * 接单（核心并发控制）
	 * <p>
	 * 乐观锁 UPDATE；失败 = 已被接单 / 过期 / 自接
	 * </p>
	 * @return 成功 true / 失败 false
	 */
	boolean assign(Long taskId, Long runnerId);

	/**
	 * 开始服务（accepted → running）
	 */
	boolean start(Long taskId, Long runnerId);

	/**
	 * 提交完成（running → pending_confirm，附凭证图）
	 */
	boolean finish(Long taskId, Long runnerId, String finishImages);

	/**
	 * 拒单（accepted → rejected，任务回 pending 让其他人接）
	 */
	boolean reject(Long taskId, Long runnerId, String reason);

	/**
	 * 我接的任务
	 */
	List<RunnerTask> listMyTaken(Long runnerId, String status);

	/**
	 * 任务流转日志
	 */
	List<RunnerTaskLog> taskLogs(Long taskId);

}
