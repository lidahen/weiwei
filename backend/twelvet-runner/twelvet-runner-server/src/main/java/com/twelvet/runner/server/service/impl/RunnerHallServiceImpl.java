package com.twelvet.runner.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.runner.api.domain.RunnerTask;
import com.twelvet.runner.api.domain.RunnerTaskLog;
import com.twelvet.runner.server.mapper.RunnerTaskLogMapper;
import com.twelvet.runner.server.mapper.RunnerTaskMapper;
import com.twelvet.runner.server.service.IRunnerHallService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 互助大厅服务实现（接单人视角）
 *
 * @author twelvet
 */
@Service
public class RunnerHallServiceImpl implements IRunnerHallService {

	private final RunnerTaskMapper taskMapper;

	private final RunnerTaskLogMapper logMapper;

	private final RunnerTaskStateMachine stateMachine;

	public RunnerHallServiceImpl(RunnerTaskMapper taskMapper, RunnerTaskLogMapper logMapper,
			RunnerTaskStateMachine stateMachine) {
		this.taskMapper = taskMapper;
		this.logMapper = logMapper;
		this.stateMachine = stateMachine;
	}

	@Override
	public List<RunnerTask> hall(Integer taskType, BigDecimal minPrice, BigDecimal maxPrice, Integer limit) {
		return taskMapper.selectHall(stateMachine.currentTid(), taskType, minPrice, maxPrice,
				limit != null ? limit : 30);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean assign(Long taskId, Long runnerId) {
		// 状态机 + 落 runner_order
		boolean ok = stateMachine.doAssign(taskId, runnerId);
		if (!ok) {
			return false;
		}
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean start(Long taskId, Long runnerId) {
		RunnerTask task = stateMachine.requireTask(taskId);
		if (!stateMachine.isAssignee(task, runnerId)) {
			throw new TWTException("仅接单人可开始服务");
		}
		return stateMachine.doTransition(taskId, RunnerTask.STATUS_ACCEPTED, RunnerTask.STATUS_RUNNING, runnerId,
				RunnerTaskLog.ACTION_START, "开始服务");
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean finish(Long taskId, Long runnerId, String finishImages) {
		RunnerTask task = stateMachine.requireTask(taskId);
		if (!stateMachine.isAssignee(task, runnerId)) {
			throw new TWTException("仅接单人可提交完成");
		}
		boolean ok = stateMachine.doTransitionWithTime(taskId, RunnerTask.STATUS_RUNNING,
				RunnerTask.STATUS_PENDING_CONFIRM, "finish_time", runnerId, RunnerTaskLog.ACTION_FINISH, "提交完成");
		if (!ok) {
			return false;
		}
		// 保存完成凭证
		if (finishImages != null && !finishImages.isBlank()) {
			task.setFinishImages(finishImages);
			taskMapper.updateByIdForFinishImages(task.getId(), finishImages);
		}
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean reject(Long taskId, Long runnerId, String reason) {
		RunnerTask task = stateMachine.requireTask(taskId);
		if (!stateMachine.isAssignee(task, runnerId)) {
			throw new TWTException("仅接单人可拒单");
		}
		// 设计：accepted → rejected（任务侧先标 rejected 中间态以便日志区分，再回 pending 让其他人接）
		boolean ok = stateMachine.doTransition(taskId, RunnerTask.STATUS_ACCEPTED, RunnerTask.STATUS_PENDING, runnerId,
				RunnerTaskLog.ACTION_REJECT, reason);
		if (!ok) {
			return false;
		}
		return true;
	}

	@Override
	public List<RunnerTask> listMyTaken(Long runnerId, String status) {
		return taskMapper.selectMyTaken(runnerId, stateMachine.currentTid(), status, 50);
	}

	@Override
	public List<RunnerTaskLog> taskLogs(Long taskId) {
		return logMapper.selectByTaskId(taskId, stateMachine.currentTid());
	}

}
