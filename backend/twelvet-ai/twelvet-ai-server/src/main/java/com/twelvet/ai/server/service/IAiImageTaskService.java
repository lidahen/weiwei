package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiImageTask;

import java.util.List;

/**
 * AI图片生成任务Service接口
 */
public interface IAiImageTaskService {

	/**
	 * 查询图片生成任务
	 */
	AiImageTask selectAiImageTaskById(Long taskId);

	/**
	 * 查询图片生成任务列表
	 */
	List<AiImageTask> selectAiImageTaskList(AiImageTask aiImageTask);

	/**
	 * 新增图片生成任务
	 */
	int insertAiImageTask(AiImageTask aiImageTask);

	/**
	 * 修改图片生成任务
	 */
	int updateAiImageTask(AiImageTask aiImageTask);

	/**
	 * 删除图片生成任务
	 */
	int deleteAiImageTaskById(Long taskId);

	/**
	 * 批量删除图片生成任务
	 */
	int deleteAiImageTaskByIds(Long[] taskIds);

}
