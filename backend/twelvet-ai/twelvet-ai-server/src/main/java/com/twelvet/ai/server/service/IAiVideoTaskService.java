package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiVideoTask;

import java.util.List;

/**
 * AI视频生成任务Service接口
 */
public interface IAiVideoTaskService {

	/**
	 * 查询视频生成任务
	 */
	AiVideoTask selectAiVideoTaskById(Long taskId);

	/**
	 * 查询视频生成任务列表
	 */
	List<AiVideoTask> selectAiVideoTaskList(AiVideoTask aiVideoTask);

	/**
	 * 新增视频生成任务
	 */
	int insertAiVideoTask(AiVideoTask aiVideoTask);

	/**
	 * 修改视频生成任务
	 */
	int updateAiVideoTask(AiVideoTask aiVideoTask);

	/**
	 * 删除视频生成任务
	 */
	int deleteAiVideoTaskById(Long taskId);

	/**
	 * 批量删除视频生成任务
	 */
	int deleteAiVideoTaskByIds(Long[] taskIds);

}
